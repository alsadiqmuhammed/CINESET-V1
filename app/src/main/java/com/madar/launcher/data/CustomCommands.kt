package com.madar.launcher.data

import kotlinx.serialization.Serializable

/** Where a user-defined command goes. */
@Serializable
enum class CommandTarget(val title: String) {
    /** Broadcast `com.madar.launcher.COMMAND` (extras: command, text); a Tasker "Intent Received" profile picks it up. */
    BROADCAST("بث لـ Tasker"),

    /** Run a named Tasker task directly (needs Tasker's "Allow External Access"). */
    TASKER_TASK("مهمة Tasker"),

    /** Open a URI; `{text}` is replaced with whatever follows the phrase. */
    URI("رابط / تطبيق"),
}

/**
 * Open action protocol: "شغل الضوء" → Tasker → Home Assistant, without Madar ever touching the network.
 * Anything typed after [phrase] travels along as the command's text.
 */
@Serializable
data class CustomCommand(
    val phrase: String,
    val target: CommandTarget,
    /** Task name, broadcast command id, or URI template, depending on [target]. */
    val value: String,
)
