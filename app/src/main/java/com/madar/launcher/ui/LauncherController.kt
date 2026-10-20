package com.madar.launcher.ui

import com.madar.launcher.fmt
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings as SystemSettings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.madar.launcher.MadarApp
import com.madar.launcher.MainActivity
import com.madar.launcher.command.Action
import com.madar.launcher.command.Channel
import com.madar.launcher.command.CommandParser
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.ContactEntry
import com.madar.launcher.data.MediaKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Settings
import com.madar.launcher.data.StackItem
import com.madar.launcher.data.newItemId
import com.madar.launcher.data.toDayMinute
import com.madar.launcher.service.FrictionService
import com.madar.launcher.service.Reminders
import java.time.LocalTime

sealed interface Overlay {
    data object None : Overlay
    data class Palette(val initial: String = "") : Overlay
    data class Gate(val app: AppEntry, val outOfMode: Boolean) : Overlay
    data class StackView(val id: String) : Overlay
    data class AppMenu(val app: AppEntry) : Overlay
    data object Digest : Overlay
    data object Settings : Overlay
    data object Archive : Overlay
    data object Report : Overlay
}

/** Owns overlay navigation and executes every [Action]; the single place UI talks to the system. */
class LauncherController(private val activity: MainActivity, val app: MadarApp) {

    val haptics = Haptics(activity)
    val parser = CommandParser(
        apps = { app.apps.apps.value },
        contacts = { app.contacts.search(it) },
        stacks = { app.stacks.stacks.value },
    )

    var overlay by mutableStateOf<Overlay>(if (app.settings.value.onboarded) Overlay.None else Overlay.Settings)
    var message by mutableStateOf<String?>(null)
    var torchOn by mutableStateOf(false)
        private set

    private val camera = activity.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    init {
        camera.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }, null)
    }

    // region Modes

    fun modeFor(settings: Settings, nowMillis: Long = System.currentTimeMillis()): Mode = settings.modeAt(nowMillis)

    fun setMode(mode: Mode) {
        val s = app.settings.value
        val now = LocalTime.now().toDayMinute()
        val scheduled = s.schedule.modeAt(now)
        if (mode == scheduled) {
            app.settings.edit { it.copy(modeOverride = null, overrideUntil = 0) }
            return
        }
        val next = s.schedule.nextBoundaryAfter(now)
        val minutesAhead = (next - now).mod(24 * 60).let { if (it == 0) 24 * 60 else it }
        app.settings.edit {
            it.copy(modeOverride = mode, overrideUntil = System.currentTimeMillis() / 60_000 + minutesAhead)
        }
    }

    val currentMode: Mode get() = modeFor(app.settings.value)

    // endregion

    // region Navigation

    /** Composition-bound scope (it carries the frame clock that spring animations need); set by [MadarRoot]. */
    lateinit var uiScope: CoroutineScope

    /** 0 = command bar hidden, 1 = fully out. Driven by the finger, then by a spring. */
    val pull = Animatable(0f)

    private val paletteSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    fun open(o: Overlay) {
        overlay = o
        uiScope.launch { pull.animateTo(if (o is Overlay.Palette) 1f else 0f, paletteSpring) }
    }

    fun close() {
        val wasPalette = overlay is Overlay.Palette
        overlay = Overlay.None
        if (wasPalette || pull.value > 0f) uiScope.launch { pull.animateTo(0f, paletteSpring) }
    }

    fun pullPalette(delta: Float) {
        uiScope.launch { pull.snapTo((pull.value + delta * 1.4f).coerceIn(0f, 1f)) }
    }

    /** [velocity] in screen-heights per second, positive = upward. */
    fun releasePalette(velocity: Float) {
        if (pull.value == 0f && velocity <= 0f) return
        val opening = pull.value > 0.3f || velocity > 1.2f
        if (opening) {
            haptics.tick()
            overlay = Overlay.Palette()
        }
        uiScope.launch {
            pull.animateTo(if (opening) 1f else 0f, paletteSpring, initialVelocity = velocity)
        }
    }

    fun toast(text: String) {
        message = text
    }

    fun listen(onText: (String) -> Unit) = activity.listen(onText)

    fun requestPermissions(permissions: Array<String>, then: () -> Unit) = activity.requestPermissions(permissions, then)

    fun pickDocument(onPicked: (Uri) -> Unit) = activity.pickDocument(onPicked)

    val isDefaultHome: Boolean get() = activity.isDefaultHome

    fun requestHomeRole() = activity.requestHomeRole()

    // endregion

    // region Launching

    /** Entry point for opening any app from the UI; routes attention-costly apps through the intent gate. */
    fun requestLaunch(entry: AppEntry, source: Rect? = null) {
        val outOfMode = !entry.kind.visibleIn(currentMode)
        if (entry.kind.gated || outOfMode) {
            haptics.heavy()
            open(Overlay.Gate(entry, outOfMode))
        } else {
            haptics.light()
            launch(entry, source)
        }
    }

    /** Launch after the gate, with an intention and a time budget that nudges when it runs out. */
    fun launchWithIntent(entry: AppEntry, intention: String, minutes: Int) {
        haptics.light()
        val session = app.friction.startSession(entry.packageName, intention, minutes)
        // With the friction service on, time-up is handled in-app by desaturation; otherwise fall back to a nudge.
        if (!FrictionService.running) {
            Reminders.schedule(
                activity, entry.packageName.hashCode(), session.endsAt,
                Reminders.KIND_GATE,
                "انتهى وقت ${entry.label}",
                "قلت \"$intention\" لمدة $minutes دقائق. شنو الخطوة الجاية؟",
            )
        }
        launch(entry, null)
    }

    fun launch(entry: AppEntry, source: Rect?) {
        close()
        runCatching {
            val la = activity.getSystemService(LauncherApps::class.java)
            val opts = source?.let {
                ActivityOptions.makeClipRevealAnimation(activity.window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
            }
            la.startMainActivity(entry.component, Process.myUserHandle(), source, opts)
        }.onFailure {
            app.apps.reload()
            toast("ما گدرت أفتح ${entry.label}")
        }
    }

    fun openAppInfo(entry: AppEntry) {
        close()
        activity.getSystemService(LauncherApps::class.java)
            .startAppDetailsActivity(entry.component, Process.myUserHandle(), null, null)
    }

    fun uninstall(entry: AppEntry) {
        close()
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null)))
    }

    fun togglePin(entry: AppEntry) {
        haptics.tick()
        app.settings.edit {
            it.copy(pinned = if (entry.key in it.pinned) it.pinned - entry.key else it.pinned + entry.key)
        }
    }

    // endregion

    // region Actions

    fun execute(action: Action) {
        when (action) {
            is Action.LaunchApp -> requestLaunch(action.app)
            is Action.Call -> call(action.contact)
            is Action.Message -> message(action.contact, action.channel, action.text)
            is Action.ShareLatest -> shareLatest(action.kind, action.channel, action.contact)
            is Action.SetTimer -> done {
                start(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        .apply { if (action.label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, action.label) },
                )
                toast("المؤقت اشتغل")
            }
            is Action.SetAlarm -> done {
                start(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                )
                toast("المنبه انضبط %02d:%02d".fmt(action.hour, action.minute))
            }
            is Action.WebSearch -> done {
                if (!start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query), quiet = true)) {
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query))))
                }
            }
            is Action.YoutubeSearch -> done {
                val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", action.query)
                if (!start(yt, quiet = true)) {
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(action.query))))
                }
            }
            is Action.MapsSearch -> done { start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.query)))) }
            is Action.SwitchMode -> { setMode(action.mode); close() }
            is Action.OpenStack -> open(Overlay.StackView(action.stack.id))
            is Action.CreateStack -> {
                haptics.light()
                open(Overlay.StackView(app.stacks.create(action.title).id))
            }
            is Action.QuickNote -> done {
                addToInbox(action.text)
                toast("انحفظت بصندوق الملاحظات")
            }
            is Action.CopyResult -> done {
                activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("madar", action.value))
            }
            is Action.StartPomodoro -> done { startPomodoro(action.minutes) }
            is Action.SystemPanel -> done { start(Intent(action.intentAction)) }
            Action.Torch -> done { toggleTorch() }
            Action.OpenDigest -> open(Overlay.Digest)
            Action.OpenSettings -> open(Overlay.Settings)
            Action.OpenArchive -> open(Overlay.Archive)
            Action.OpenReport -> open(Overlay.Report)
            Action.OpenDrawer -> open(Overlay.Palette())
        }
    }

    private inline fun done(block: () -> Unit) {
        close()
        block()
        haptics.completion()
    }

    fun call(contact: ContactEntry) = done {
        val granted = activity.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        start(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", contact.phone, null)))
    }

    private fun message(contact: ContactEntry, channel: Channel, text: String) = done {
        val digits = internationalDigits(contact.phone)
        when (channel) {
            Channel.WHATSAPP -> {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digits" + if (text.isNotEmpty()) "&text=" + Uri.encode(text) else "")
                if (!start(Intent(Intent.ACTION_VIEW, uri).setPackage(Channel.WHATSAPP.packageName), quiet = true)) start(Intent(Intent.ACTION_VIEW, uri))
            }
            Channel.TELEGRAM -> {
                if (text.isNotEmpty()) {
                    activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("madar", text))
                    toast("النص منسوخ، الصقه بالمحادثة")
                }
                start(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$digits")))
            }
            Channel.SMS -> start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.phone, null)).putExtra("sms_body", text))
        }
    }

    private fun shareLatest(kind: MediaKind, channel: Channel?, contact: ContactEntry?) {
        val needsAllFiles = kind == MediaKind.PDF || kind == MediaKind.ANY
        if (needsAllFiles && !hasAllFilesAccess()) {
            toast("مدار يحتاج إذن الوصول للملفات حتى يلگى آخر ${kind.title}")
            start(Intent(SystemSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.packageName)))
            return
        }
        val permission = when (kind) {
            MediaKind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            MediaKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
            MediaKind.PHOTO, MediaKind.SCREENSHOT -> Manifest.permission.READ_MEDIA_IMAGES
            else -> null
        }
        if (permission != null && !needsAllFiles && activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            !hasAllFilesAccess()
        ) {
            activity.requestPermission(permission) { if (it) shareLatest(kind, channel, contact) }
            return
        }
        val file = app.media.latest(kind)
        if (file == null) {
            toast("ما لگيت ${kind.title}")
            return
        }
        done {
            val send = Intent(Intent.ACTION_SEND)
                .setType(file.mime)
                .putExtra(Intent.EXTRA_STREAM, file.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (channel?.packageName != null) send.setPackage(channel.packageName)
            // WhatsApp opens the chat directly when given the contact's JID.
            if (channel == Channel.WHATSAPP && contact != null) send.putExtra("jid", internationalDigits(contact.phone) + "@s.whatsapp.net")
            if (channel?.packageName == null || !start(send, quiet = true)) {
                start(Intent.createChooser(send, "مشاركة ${file.name}"))
            }
        }
    }

    fun startPomodoro(minutes: Int) {
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        app.settings.edit { it.copy(pomodoroEndsAt = endsAt, pomodoroMinutes = minutes) }
        Reminders.schedule(activity, POMODORO_CODE, endsAt, Reminders.KIND_POMODORO, "خلصت جلسة التركيز", "خذ استراحة ٥ دقائق")
        if (currentMode != Mode.FOCUS) setMode(Mode.FOCUS)
    }

    fun stopPomodoro() {
        app.settings.edit { it.copy(pomodoroEndsAt = 0) }
        Reminders.cancel(activity, POMODORO_CODE, Reminders.KIND_POMODORO)
        haptics.tick()
    }

    fun addToInbox(text: String) {
        val inbox = app.stacks.stacks.value.firstOrNull { it.title == INBOX && !it.archived } ?: app.stacks.create(INBOX)
        app.stacks.addItem(inbox.id, StackItem.Note(newItemId(), text))
    }

    private fun toggleTorch() {
        val id = torchId ?: return toast("ما بيه فلاش")
        camera.setTorchMode(id, !torchOn)
    }

    @SuppressLint("WrongConstant")
    fun expandNotifications() {
        runCatching {
            val sbm = activity.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    fun nextAlarm(): Long? = activity.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime

    /** Starts an intent; returns false (and toasts unless [quiet]) when nothing can handle it. */
    fun start(intent: Intent, quiet: Boolean = false): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) toast("ما لگيت تطبيق يفتح هذا")
        false
    } catch (e: SecurityException) {
        if (!quiet) toast("ما عندي صلاحية لهذا")
        false
    }

    // endregion

    companion object {
        const val INBOX = "صندوق الملاحظات"
        private const val POMODORO_CODE = 7001

        /** Contacts are stored as +9647…, 07…, or 7…; WhatsApp and Telegram want 9647…. */
        fun internationalDigits(phone: String): String {
            val digits = phone.filter(Char::isDigit)
            return when {
                phone.trim().startsWith("+") -> digits
                digits.startsWith("00") -> digits.drop(2)
                digits.startsWith("07") && digits.length == 11 -> "964" + digits.drop(1)
                digits.startsWith("7") && digits.length == 10 -> "964$digits"
                else -> digits
            }
        }
    }
}

fun hasAllFilesAccess(): Boolean = runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
