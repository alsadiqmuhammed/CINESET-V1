package com.madar.launcher.command

import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.ContactEntry
import com.madar.launcher.data.MediaKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Stack

enum class Channel(val title: String, val packageName: String?) {
    WHATSAPP("واتساب", "com.whatsapp"),
    TELEGRAM("تليكرام", "org.telegram.messenger"),
    SMS("رسالة نصية", null),
}

/** Everything the command bar can do. Executed by [com.madar.launcher.ui.LauncherController]. */
sealed interface Action {
    data class LaunchApp(val app: AppEntry) : Action
    data class Call(val contact: ContactEntry) : Action
    data class Message(val contact: ContactEntry, val channel: Channel, val text: String) : Action
    data class ShareLatest(val kind: MediaKind, val channel: Channel?, val contact: ContactEntry?) : Action
    data class SetTimer(val seconds: Int, val label: String) : Action
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : Action
    data class WebSearch(val query: String) : Action
    data class YoutubeSearch(val query: String) : Action
    data class MapsSearch(val query: String) : Action
    data class SwitchMode(val mode: Mode) : Action
    data class OpenStack(val stack: Stack) : Action
    data class CreateStack(val title: String) : Action
    data class QuickNote(val text: String) : Action
    data class CopyResult(val value: String) : Action
    data class StartPomodoro(val minutes: Int, val task: String) : Action
    data class SystemPanel(val intentAction: String) : Action
    data object Torch : Action
    data class AskAi(val query: String) : Action
    data class ImportBlueprint(val blueprint: com.madar.launcher.data.Blueprint) : Action
    data class RunCustom(val command: com.madar.launcher.data.CustomCommand, val text: String) : Action
    data object OpenDigest : Action
    data object OpenSettings : Action
    data object OpenArchive : Action
    data object OpenReport : Action
    data object OpenDrawer : Action
}

/** One row in the palette; the first row runs when the user presses enter. */
data class Suggestion(
    val title: String,
    val subtitle: String,
    val tag: String,
    val action: Action,
    val app: AppEntry? = null,
)
