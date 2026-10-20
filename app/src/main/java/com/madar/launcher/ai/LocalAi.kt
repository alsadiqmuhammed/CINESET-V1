package com.madar.launcher.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface AiState {
    data object Missing : AiState
    data class Importing(val progress: Float) : AiState
    /** Model file present but not in memory; loads on first use. */
    data object Idle : AiState
    data object Loading : AiState
    data object Ready : AiState
    data object Thinking : AiState
    data class Failed(val reason: String) : AiState
}

/**
 * On-device LLM (e.g. Gemma 3 1B int4 as a MediaPipe .task file). Madar has no INTERNET permission,
 * so the user brings the model file in once. It is loaded only when asked and released after a
 * short idle, so a ~1 GB model never sits in RAM while the phone is in the pocket.
 */
class LocalAi(private val context: Context, private val scope: CoroutineScope) {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(dir, "madar.task")

    private val _state = MutableStateFlow<AiState>(if (modelFile.exists()) AiState.Idle else AiState.Missing)
    val state: StateFlow<AiState> = _state.asStateFlow()

    private var engine: LlmInference? = null
    private val lock = Mutex()
    private var idleJob: Job? = null

    val modelSizeMb: Long get() = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0

    fun importModel(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                release()
                _state.value = AiState.Importing(0f)
                val result = runCatching {
                    val total = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    val tmp = File(dir, "incoming.part")
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(1 shl 20)
                            var copied = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                copied += n
                                if (total > 0) _state.value = AiState.Importing(copied.toFloat() / total)
                            }
                        }
                    }
                    check(tmp.length() > 50L * 1024 * 1024) { "الملف صغير على نموذج لغة" }
                    modelFile.delete()
                    check(tmp.renameTo(modelFile))
                }
                _state.value = result.fold({ AiState.Idle }, { AiState.Failed(it.message ?: "فشل الاستيراد") })
            }
        }
    }

    fun deleteModel() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                release()
                modelFile.delete()
                _state.value = AiState.Missing
            }
        }
    }

    /** Runs one prompt; loads the model if needed (GPU first, CPU fallback). */
    suspend fun generate(prompt: String): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            if (!modelFile.exists()) return@withLock null
            idleJob?.cancel()
            val llm = engine ?: run {
                _state.value = AiState.Loading
                load(LlmInference.Backend.GPU) ?: load(LlmInference.Backend.CPU)
            }
            if (llm == null) {
                _state.value = AiState.Failed("ما گدرت أحمّل النموذج. تأكد إنه ملف .task متوافق مع MediaPipe")
                return@withLock null
            }
            engine = llm
            _state.value = AiState.Thinking
            val out = runCatching { llm.generateResponse(prompt) }.getOrNull()
            _state.value = AiState.Ready
            scheduleRelease()
            out
        }
    }

    private fun load(backend: LlmInference.Backend): LlmInference? = runCatching {
        LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(768)
                .setPreferredBackend(backend)
                .build(),
        )
    }.getOrNull()

    private fun scheduleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            lock.withLock {
                release()
                if (modelFile.exists()) _state.value = AiState.Idle
            }
        }
    }

    private fun release() {
        runCatching { engine?.close() }
        engine = null
    }

    private companion object {
        const val IDLE_MS = 90_000L
    }
}
