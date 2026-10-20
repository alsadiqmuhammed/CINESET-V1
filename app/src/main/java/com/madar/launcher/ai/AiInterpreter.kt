package com.madar.launcher.ai

import com.madar.launcher.command.Action
import com.madar.launcher.fmt
import com.madar.launcher.command.Channel
import com.madar.launcher.command.CommandParser
import com.madar.launcher.command.Suggestion
import com.madar.launcher.data.ContactEntry
import com.madar.launcher.data.MediaKind
import com.madar.launcher.data.Mode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the model's JSON into the same [Action]s the rule parser produces, so the LLM never acts
 * on its own: it only proposes rows the user taps. Anything it names (apps, people) is resolved
 * against what's really on the phone; unknown names are dropped rather than guessed.
 */
class AiInterpreter(
    private val parser: CommandParser,
    private val contacts: (String) -> List<ContactEntry>,
) {

    fun interpret(modelOutput: String): List<Suggestion> {
        val root = extractJson(modelOutput) ?: return answerOnly(modelOutput)
        val steps = (root["steps"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(root)
        return steps.flatMap { step(it) }.distinctBy { it.action }.take(8).ifEmpty { answerOnly(modelOutput) }
    }

    private fun step(o: JsonObject): List<Suggestion> {
        fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        fun int(key: String) = o[key]?.jsonPrimitive?.intOrNull
        return when (str("action")) {
            "open_app" -> parser.matchApps(str("app")).take(1).map {
                Suggestion(it.label, "افتح", TAG, Action.LaunchApp(it), app = it)
            }
            "call" -> person(str("contact")).map { Suggestion("اتصل بـ ${it.name}", it.phone, TAG, Action.Call(it)) }
            "message" -> {
                val channel = channel(str("channel")) ?: Channel.WHATSAPP
                person(str("contact")).map {
                    Suggestion("${channel.title} ← ${it.name}", str("text").ifEmpty { "فتح المحادثة" }, TAG, Action.Message(it, channel, str("text")))
                }
            }
            "share_latest" -> {
                val kind = kind(str("kind"))
                val channel = channel(str("channel"))
                val who = person(str("contact")).firstOrNull()
                listOf(Suggestion("آخر ${kind.title}" + (who?.let { " ← ${it.name}" } ?: ""), channel?.title ?: "مشاركة", TAG, Action.ShareLatest(kind, channel, who)))
            }
            "timer" -> int("minutes")?.takeIf { it in 1..1440 }?.let {
                listOf(Suggestion("مؤقت $it دقيقة", str("text").ifEmpty { "يبدأ فوراً" }, TAG, Action.SetTimer(it * 60, str("text"))))
            }.orEmpty()
            "alarm" -> {
                val h = int("hour")
                val m = int("minute") ?: 0
                if (h != null && h in 0..23 && m in 0..59) listOf(Suggestion("منبه %02d:%02d".fmt(h, m), "", TAG, Action.SetAlarm(h, m, str("text")))) else emptyList()
            }
            "search" -> str("query").takeIf { it.isNotEmpty() }?.let { listOf(Suggestion("ابحث عن \"$it\"", "بالمتصفح", TAG, Action.WebSearch(it))) }.orEmpty()
            "maps" -> str("query").takeIf { it.isNotEmpty() }?.let { listOf(Suggestion("الخريطة: $it", "", TAG, Action.MapsSearch(it))) }.orEmpty()
            "note" -> str("text").takeIf { it.isNotEmpty() }?.let { listOf(Suggestion("ملاحظة: $it", "صندوق الملاحظات", TAG, Action.QuickNote(it))) }.orEmpty()
            "project" -> str("title").takeIf { it.isNotEmpty() }?.let { listOf(Suggestion("مشروع جديد: $it", "مساحة عمل", TAG, Action.CreateStack(it))) }.orEmpty()
            "mode" -> mode(str("mode"))?.let { listOf(Suggestion("طور ${it.title}", it.subtitle, TAG, Action.SwitchMode(it))) }.orEmpty()
            "answer" -> str("text").takeIf { it.isNotEmpty() }?.let { listOf(Suggestion(it, "اضغط للنسخ", TAG, Action.CopyResult(it))) }.orEmpty()
            else -> emptyList()
        }
    }

    private fun person(name: String): List<ContactEntry> = if (name.isBlank()) emptyList() else contacts(name).take(2)

    private fun answerOnly(text: String): List<Suggestion> {
        val clean = text.replace(Regex("```[a-z]*"), "").trim().take(400)
        return if (clean.isEmpty()) emptyList() else listOf(Suggestion(clean, "جواب الذكاء المحلي · اضغط للنسخ", TAG, Action.CopyResult(clean)))
    }

    private fun channel(s: String) = when (s.lowercase()) {
        "whatsapp" -> Channel.WHATSAPP
        "telegram" -> Channel.TELEGRAM
        "sms" -> Channel.SMS
        else -> null
    }

    private fun kind(s: String) = when (s.lowercase()) {
        "photo", "image" -> MediaKind.PHOTO
        "video" -> MediaKind.VIDEO
        "screenshot" -> MediaKind.SCREENSHOT
        "pdf" -> MediaKind.PDF
        "audio" -> MediaKind.AUDIO
        else -> MediaKind.ANY
    }

    private fun mode(s: String) = when (s.lowercase()) {
        "focus" -> Mode.FOCUS
        "flow" -> Mode.FLOW
        "sanctuary", "rest" -> Mode.SANCTUARY
        else -> null
    }

    companion object {
        const val TAG = "ذكاء"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Small models wrap JSON in prose or code fences; take the outermost {...}. */
        fun extractJson(text: String): JsonObject? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull()
        }

        fun prompt(command: String, nowLabel: String): String = """<start_of_turn>user
You are the command interpreter of MADAR, an Arabic (Iraqi dialect) phone launcher. It is $nowLabel.
Convert the user's request into JSON only, no prose. Either one action object, or {"steps":[...]} for several.
Actions:
{"action":"open_app","app":"<app name>"}
{"action":"call","contact":"<name>"}
{"action":"message","contact":"<name>","channel":"whatsapp|telegram|sms","text":"<message>"}
{"action":"share_latest","kind":"photo|video|screenshot|pdf|audio|file","contact":"<name or empty>","channel":"whatsapp|telegram|sms|"}
{"action":"timer","minutes":<int>,"text":"<label>"}
{"action":"alarm","hour":<0-23>,"minute":<0-59>}
{"action":"search","query":"<text>"}
{"action":"maps","query":"<place>"}
{"action":"note","text":"<text>"}
{"action":"project","title":"<project name>"}
{"action":"mode","mode":"focus|flow|sanctuary"}
{"action":"answer","text":"<short answer in Arabic>"}
Keep names exactly as the user wrote them.
Request: $command<end_of_turn>
<start_of_turn>model
"""
    }
}
