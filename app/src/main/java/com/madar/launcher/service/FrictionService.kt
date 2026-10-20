package com.madar.launcher.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.madar.launcher.MadarApp
import com.madar.launcher.MainActivity
import com.madar.launcher.data.AppEntry
import com.madar.launcher.ui.GateContent
import com.madar.launcher.ui.Haptics
import com.madar.launcher.ui.LocalPalette
import com.madar.launcher.ui.MadarTheme
import com.madar.launcher.ui.Pill
import com.madar.launcher.ui.Type
import com.madar.launcher.ui.matte

/**
 * System-wide micro-friction. Event-driven only (window changes): no polling, no wake locks.
 *  - An attention-heavy app reaching the foreground without a live intention gets the gate on top,
 *    whether it was opened from Madar, a notification, or recents.
 *  - When the declared budget runs out while still inside, the screen slowly loses its colour
 *    over two minutes, then settles in grayscale until the user leaves. Nothing is ever locked.
 */
class FrictionService : AccessibilityService() {

    private val app get() = application as MadarApp
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private lateinit var haptics: Haptics

    private var foreground: String? = null
    private var foregroundSince = 0L

    private var gate: Overlay? = null
    private var gatePackage: String? = null
    private var chip: Overlay? = null
    private var veil: View? = null
    private var draining = false
    private var focusRequest: AudioFocusRequest? = null

    private val timeUp = Runnable { onBudgetSpent() }
    private val drainStep = object : Runnable {
        override fun run() = stepDrain()
    }

    override fun onServiceConnected() {
        running = true
        windows = getSystemService(WindowManager::class.java)
        haptics = Haptics(this)
        Grayscale.release(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Madar counts as "left the app" only when its home activity shows; its own overlays don't.
        val isHome = pkg == packageName && event.className?.toString() == MainActivity::class.java.name
        if (pkg == packageName && !isHome) return
        if (!isHome && app.apps.findPackage(pkg) == null) return // keyboards, system UI, dialogs
        if (pkg == foreground) return
        onForegroundChanged(foreground, pkg)
    }

    private fun onForegroundChanged(old: String?, new: String) {
        val now = System.currentTimeMillis()
        if (old != null && isGated(old)) {
            val session = app.friction.session(old)
            val overrun = session?.let { now - maxOf(foregroundSince, it.endsAt) } ?: 0
            app.friction.recordUsage(old, now - foregroundSince, overrun)
        }
        main.removeCallbacks(timeUp)
        restoreColour()
        dismissChip()
        if (gatePackage != null && gatePackage != new) dismissGate()

        foreground = new
        foregroundSince = now

        if (!app.settings.value.frictionEnabled || !isGated(new)) return
        val session = app.friction.activeSession(new, now)
        if (session != null) {
            main.postDelayed(timeUp, session.endsAt - now)
        } else if (gatePackage != new) {
            showGate(new)
        }
    }

    private fun isGated(pkg: String): Boolean {
        val entry = app.apps.findPackage(pkg) ?: return false
        return entry.kind.gated || !entry.kind.visibleIn(app.currentMode())
    }

    // region Gate

    private fun showGate(pkg: String) {
        val entry = app.apps.findPackage(pkg) ?: return
        val mode = app.currentMode()
        app.friction.recordPrompt()
        haptics.heavy()
        pauseMedia()
        gatePackage = pkg
        gate = Overlay(focusable = true, fullScreen = true, onBack = { retreat(entry) }) {
            MadarTheme(mode) {
                GateContent(
                    app = entry,
                    outOfMode = !entry.kind.visibleIn(mode),
                    mode = mode,
                    haptics = haptics,
                    onChoose = { i ->
                        val s = app.friction.startSession(pkg, i.label, i.minutes)
                        dismissGate()
                        main.removeCallbacks(timeUp)
                        main.postDelayed(timeUp, s.endsAt - System.currentTimeMillis())
                    },
                    onRetreat = { retreat(entry) },
                )
            }
        }.also { it.show() }
    }

    private fun retreat(entry: AppEntry) {
        app.friction.recordRetreat(entry.packageName)
        dismissGate()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun dismissGate() {
        gate?.remove()
        gate = null
        gatePackage = null
        resumeMedia()
    }

    /** Taking transient audio focus makes video and music apps pause behind the gate. */
    private fun pauseMedia() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request
    }

    private fun resumeMedia() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // endregion

    // region Time up

    private fun onBudgetSpent() {
        val pkg = foreground ?: return
        if (!isGated(pkg)) return
        val entry = app.apps.findPackage(pkg) ?: return
        val session = app.friction.session(pkg)
        haptics.heavy()
        showChip(entry, canExtend = session?.extended == false)
        startDrain()
    }

    private fun showChip(entry: AppEntry, canExtend: Boolean) {
        dismissChip()
        chip = Overlay(focusable = false, fullScreen = false) {
            MadarTheme(app.currentMode()) {
                TimeUpChip(
                    label = entry.label,
                    canExtend = canExtend,
                    onDone = {
                        haptics.completion()
                        app.friction.endSession(entry.packageName)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    },
                    onExtend = {
                        val s = app.friction.extend(entry.packageName) ?: return@TimeUpChip
                        restoreColour()
                        dismissChip()
                        main.postDelayed(timeUp, s.endsAt - System.currentTimeMillis())
                    },
                )
            }
        }.also { it.show() }
    }

    private fun dismissChip() {
        chip?.remove()
        chip = null
    }

    /**
     * Progressive desaturation: a neutral veil thickens in small steps for two minutes (blending
     * toward grey drains saturation), then true grayscale takes over and the veil lifts.
     */
    private fun startDrain() {
        if (draining) return
        draining = true
        val v = View(this).apply {
            setBackgroundColor(VEIL_COLOR)
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windows.addView(v, params) }.onSuccess { veil = v }
        main.post(drainStep)
    }

    private fun stepDrain() {
        val v = veil ?: return
        val next = (v.alpha + VEIL_MAX / DRAIN_STEPS).coerceAtMost(VEIL_MAX)
        v.animate().alpha(next).setDuration(STEP_MS / 2).setInterpolator(LinearInterpolator()).start()
        if (next < VEIL_MAX) {
            main.postDelayed(drainStep, STEP_MS)
        } else if (Grayscale.hold(this)) {
            // The system now renders grayscale; lift the veil so contrast comes back, colour doesn't.
            v.animate().alpha(0f).setDuration(1500).start()
        }
    }

    private fun restoreColour() {
        main.removeCallbacks(drainStep)
        draining = false
        veil?.let { v -> runCatching { windows.removeView(v) } }
        veil = null
        Grayscale.release(this)
    }

    // endregion

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        dismissGate()
        dismissChip()
        restoreColour()
        super.onDestroy()
    }

    /** A Compose surface in an accessibility overlay window, with the owners Compose expects. */
    private inner class Overlay(
        private val focusable: Boolean,
        private val fullScreen: Boolean,
        private val onBack: (() -> Unit)? = null,
        private val content: @Composable () -> Unit,
    ) : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        private val view = object : AbstractComposeView(this@FrictionService) {
            @Composable
            override fun Content() = content()

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && onBack != null) {
                    if (event.action == KeyEvent.ACTION_UP) onBack.invoke()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        fun show() {
            saved.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
            var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP }
            runCatching { windows.addView(view, params) }
        }

        fun remove() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            runCatching { windows.removeView(view) }
        }
    }

    companion object {
        /** True while the system has the service bound; the launcher uses it to skip duplicate nudges. */
        @Volatile var running = false
            private set

        private const val VEIL_COLOR = 0xFF5A5A5C.toInt()
        private const val VEIL_MAX = 0.55f
        private const val DRAIN_STEPS = 30
        private const val STEP_MS = 4_000L // 30 × 4 s = two minutes
    }
}

@Composable
private fun TimeUpChip(label: String, canExtend: Boolean, onDone: () -> Unit, onExtend: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .matte(p, RoundedCornerShape(18.dp), edge = p.signal.copy(alpha = 0.6f))
            .padding(16.dp),
    ) {
        Text("TIME · UP", style = Type.tech, color = p.signal)
        Text("انتهى الوقت اللي حددته لـ $label", style = Type.title, color = p.ink)
        Text("الألوان راح تهدأ تدريجياً لحد ما تطلع.", style = Type.caption, color = p.muted)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("خلصت", selected = true, color = p.calm, onClick = onDone)
            if (canExtend) {
                Spacer(Modifier.width(2.dp))
                Pill("+5 دقائق", onClick = onExtend)
            }
        }
    }
}
