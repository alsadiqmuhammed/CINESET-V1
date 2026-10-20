package com.madar.launcher.command

import com.madar.launcher.fmt
import android.provider.Settings as SystemSettings
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.ContactEntry
import com.madar.launcher.data.MediaKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Stack

/**
 * Rule-based Arabic (MSA + Iraqi) / English intent parser. Runs fully offline in well under a millisecond,
 * turning free text into ranked [Suggestion]s.
 */
class CommandParser(
    private val apps: () -> List<AppEntry>,
    private val contacts: (String) -> List<ContactEntry>,
    private val stacks: () -> List<Stack>,
    private val custom: () -> List<com.madar.launcher.data.CustomCommand> = { emptyList() },
) {

    fun parse(raw: String): List<Suggestion> {
        val q = normalize(raw)
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(' ')
        val head = tokens.first()
        val rest = tokens.drop(1).joinToString(" ")

        val out = mutableListOf<Suggestion>()

        // The user's own commands come first: they asked for exactly this phrase.
        custom().forEach { cmd ->
            val phrase = normalize(cmd.phrase)
            if (phrase.isNotEmpty() && (q == phrase || q.startsWith("$phrase "))) {
                val text = raw.trim().split(Regex("\\s+")).drop(phrase.split(' ').size).joinToString(" ")
                out += Suggestion(cmd.phrase, text.ifEmpty { cmd.target.title }, "أمرك", Action.RunCustom(cmd, text))
            } else if (phrase.startsWith(q) && q.length >= 2) {
                out += Suggestion(cmd.phrase, cmd.target.title, "أمرك", Action.RunCustom(cmd, ""))
            }
        }

        Calc.tryEvaluate(q)?.let {
            out += Suggestion("= $it", "اضغط للنسخ", "حاسبة", Action.CopyResult(it))
        }

        when {
            head in OPEN && rest.isNotEmpty() -> out += appSuggestions(rest)
            head in CALL || q.startsWith("رن على") -> out += callSuggestions(stripLead(if (head in CALL) rest else q.removePrefix("رن على")))
            head in SEND || q.startsWith("ارسل رساله") -> out += sendSuggestions(q, tokens)
            channelOf(head) != null && rest.isNotEmpty() -> out += messageSuggestions(rest, channelOf(head)!!)
            head in TIMER -> parseDuration(rest)?.let { secs ->
                out += Suggestion("مؤقت ${formatDuration(secs)}", "يبدأ فوراً", "مؤقت", Action.SetTimer(secs, ""))
            }
            head in REMIND -> parseReminder(rest)?.let { out += it }
            head in ALARM -> parseClock(rest)?.let { (h, m) ->
                out += Suggestion("منبه %02d:%02d".fmt(h, m), "يتضبط بساعة الجهاز", "منبه", Action.SetAlarm(h, m, ""))
            }
            head in SEARCH && rest.isNotEmpty() -> out += Suggestion("ابحث عن \"$rest\"", "بالمتصفح", "بحث", Action.WebSearch(rest))
            head in YOUTUBE && rest.isNotEmpty() -> out += Suggestion("يوتيوب: $rest", "بحث في يوتيوب", "بحث", Action.YoutubeSearch(rest))
            head in MAPS && rest.isNotEmpty() -> out += Suggestion("الخريطة: $rest", "بحث في الخرائط", "خرائط", Action.MapsSearch(rest))
            head in NOTE && rest.isNotEmpty() -> out += Suggestion("ملاحظة: $rest", "تنحفظ بصندوق الملاحظات", "ملاحظة", Action.QuickNote(raw.trim().substringAfter(' ')))
            head in PROJECT -> out += stackSuggestions(rest, raw.trim().substringAfter(' ', ""))
            head in POMODORO -> {
                val mins = parseDuration(rest)?.div(60) ?: 25
                out += Suggestion("جلسة تركيز $mins دقيقة", "بومودورو", "تركيز", Action.StartPomodoro(mins, ""))
            }
        }

        modeFor(q)?.let { out += Suggestion("طور ${it.title}", it.subtitle, "طور", Action.SwitchMode(it)) }
        keywordActions(q, tokens).let { out += it }

        if (out.none { it.action is Action.LaunchApp }) out += appSuggestions(q, limit = 4)
        if (out.isEmpty() || out.size < 3) {
            out += contacts(q).take(2).map { Suggestion("اتصل بـ ${it.name}", it.phone, "اتصال", Action.Call(it)) }
            out += stacks().filter { !it.archived && normalize(it.title).contains(q) }.take(2)
                .map { Suggestion(it.title, "${it.items.size} عناصر", "مشروع", Action.OpenStack(it)) }
        }
        out += Suggestion("ابحث عن \"${raw.trim()}\"", "بالمتصفح", "بحث", Action.WebSearch(raw.trim()))
        return out.distinctBy { it.action }
    }

    // region Apps & contacts

    fun matchApps(q: String): List<AppEntry> {
        val query = normalize(q)
        if (query.isEmpty()) return emptyList()
        val aliasPackages = ALIASES.filterKeys { it.startsWith(query) || query.startsWith(it) }.values.flatten().toSet()
        return apps()
            .mapNotNull { app ->
                val score = when {
                    app.packageName in aliasPackages -> 0
                    app.searchKey == query -> 0
                    app.searchKey.startsWith(query) -> 1
                    app.searchKey.split(' ').any { it.startsWith(query) } -> 2
                    app.searchKey.contains(query) -> 3
                    initials(app.searchKey).startsWith(query) && query.length >= 2 -> 4
                    else -> return@mapNotNull null
                }
                score to app
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun appSuggestions(q: String, limit: Int = 6) = matchApps(q).take(limit).map {
        Suggestion(it.label, it.kind.title, "تطبيق", Action.LaunchApp(it), app = it)
    }

    private fun callSuggestions(name: String) = contactsFor(name).map {
        Suggestion("اتصل بـ ${it.name}", it.phone, "اتصال", Action.Call(it))
    }

    private fun messageSuggestions(rest: String, channel: Channel): List<Suggestion> {
        // "واتساب احمد شلونك" → contact is the longest leading phrase that matches someone.
        val words = rest.split(' ')
        for (n in words.size downTo 1) {
            val name = stripLead(words.take(n).joinToString(" "))
            val found = contacts(name)
            if (found.isNotEmpty()) {
                val text = words.drop(n).joinToString(" ")
                return found.take(3).map {
                    Suggestion("${channel.title} ← ${it.name}", text.ifEmpty { "فتح المحادثة" }, "رسالة", Action.Message(it, channel, text))
                }
            }
        }
        return emptyList()
    }

    private fun sendSuggestions(q: String, tokens: List<String>): List<Suggestion> {
        val kind = MEDIA.entries.firstOrNull { (k, _) -> tokens.any { t -> t.removePrefix("ال") == k || t == k } }?.value
        val channel = tokens.firstNotNullOfOrNull { channelOf(it) }
        if (kind == null) {
            // "ارسل لاحمد شلونك بالواتساب" — a plain message.
            val body = tokens.drop(1).filterNot { channelOf(it) != null || it == "رساله" }.joinToString(" ")
            return messageSuggestions(body, channel ?: Channel.WHATSAPP)
        }
        val leftover = tokens.drop(1).filterNot { t ->
            t in LATEST || channelOf(t) != null || MEDIA.containsKey(t) || MEDIA.containsKey(t.removePrefix("ال"))
        }.joinToString(" ")
        val people = if (leftover.isBlank()) emptyList() else contactsFor(leftover).take(2)
        val target = channel?.title ?: "مشاركة"
        if (people.isEmpty()) {
            return listOf(Suggestion("ارسل آخر ${kind.title}", target, "مشاركة", Action.ShareLatest(kind, channel, null)))
        }
        return people.map {
            Suggestion("آخر ${kind.title} ← ${it.name}", target, "مشاركة", Action.ShareLatest(kind, channel, it))
        }
    }

    private fun contactsFor(name: String): List<ContactEntry> {
        // "علي" is both a preposition and a name, so try the raw text before stripping.
        val stripped = stripLead(name)
        return listOf(name.trim(), stripped, stripped.removePrefix("ب"), stripped.removePrefix("ل"))
            .distinct()
            .filter { it.isNotBlank() }
            .firstNotNullOfOrNull { candidate -> contacts(candidate).takeIf { it.isNotEmpty() } }
            .orEmpty()
            .take(3)
    }

    private fun stackSuggestions(rest: String, rawTitle: String): List<Suggestion> {
        val matches = stacks().filter { !it.archived && (rest.isEmpty() || normalize(it.title).contains(rest)) }
            .map { Suggestion(it.title, "${it.items.size} عناصر", "مشروع", Action.OpenStack(it)) }
        val create = if (rest.isNotEmpty() && matches.none { normalize(it.title) == rest }) {
            listOf(Suggestion("مشروع جديد: $rawTitle", "إنشاء مساحة عمل", "مشروع", Action.CreateStack(rawTitle)))
        } else emptyList()
        return matches + create
    }

    // endregion

    // region Keywords

    private fun keywordActions(q: String, tokens: List<String>): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        fun has(vararg words: String) = words.any { w -> normalize(w).let { n -> tokens.contains(n) || q == n } }
        if (has("كشاف", "فلاش", "ضوء", "torch", "flashlight")) out += Suggestion("الكشاف", "تشغيل / إطفاء", "نظام", Action.Torch)
        if (has("واي فاي", "وايفاي", "wifi", "انترنت", "نت")) out += Suggestion("الإنترنت", "لوحة الاتصال", "نظام", Action.SystemPanel(SystemSettings.Panel.ACTION_INTERNET_CONNECTIVITY))
        if (has("بلوتوث", "bluetooth")) out += Suggestion("البلوتوث", "الإعدادات", "نظام", Action.SystemPanel(SystemSettings.ACTION_BLUETOOTH_SETTINGS))
        if (has("صوت", "الصوت", "volume")) out += Suggestion("مستوى الصوت", "لوحة الصوت", "نظام", Action.SystemPanel(SystemSettings.Panel.ACTION_VOLUME))
        if (has("بطاريه", "battery")) out += Suggestion("البطارية", "استهلاك البطارية", "نظام", Action.SystemPanel(android.content.Intent.ACTION_POWER_USAGE_SUMMARY))
        if (has("ملخص", "الملخص", "اشعارات", "الاشعارات", "digest")) out += Suggestion("الملخص الهادئ", "الإشعارات المجمّعة", "مدار", Action.OpenDigest)
        if (has("اعدادات مدار", "اعدادات", "settings", "ضبط")) out += Suggestion("إعدادات مدار", "الأطوار، الملخص، الأذونات", "مدار", Action.OpenSettings)
        if (has("تقرير", "التقرير", "report", "احصائيات")) out += Suggestion("تقرير اليوم", "وين راح انتباهك", "مدار", Action.OpenReport)
        if (has("الارشيف", "ارشيف", "archive")) out += Suggestion("أرشيف المشاريع", "المشاريع المنتهية", "مدار", Action.OpenArchive)
        if (has("التطبيقات", "كل التطبيقات", "apps")) out += Suggestion("كل التطبيقات", "القائمة الكاملة", "مدار", Action.OpenDrawer)
        return out
    }

    private fun modeFor(q: String): Mode? = when (q) {
        in FOCUS_WORDS -> Mode.FOCUS
        in FLOW_WORDS -> Mode.FLOW
        in SANCTUARY_WORDS -> Mode.SANCTUARY
        else -> null
    }

    private fun channelOf(token: String): Channel? {
        val t = token.removePrefix("ب").removePrefix("ال").removePrefix("عال").removePrefix("ع")
        return when {
            WHATSAPP.any { t == it || token == it } -> Channel.WHATSAPP
            TELEGRAM.any { t == it || token == it } -> Channel.TELEGRAM
            token in SMS -> Channel.SMS
            else -> null
        }
    }

    // endregion

    // region Time

    private fun parseReminder(rest: String): Suggestion? {
        // "ذكرني بعد 20 دقيقه اطفي الفرن"
        val body = rest.removePrefix("بعد ").trim()
        val words = body.split(' ')
        for (n in minOf(3, words.size) downTo 1) {
            val secs = parseDuration(words.take(n).joinToString(" ")) ?: continue
            val label = words.drop(n).joinToString(" ")
            return Suggestion("تذكير بعد ${formatDuration(secs)}", label.ifEmpty { "تذكير" }, "تذكير", Action.SetTimer(secs, label))
        }
        return null
    }

    internal fun parseDuration(text: String): Int? {
        val t = normalize(text).removePrefix("بعد ").trim()
        if (t.isEmpty()) return null
        SPECIAL_DURATIONS[t]?.let { return it }
        val m = Regex("^(\\d+(?:\\.\\d+)?|[\\p{L}]+)\\s*(.*)$").find(t) ?: return null
        val amount = m.groupValues[1].toDoubleOrNull() ?: NUMBER_WORDS[m.groupValues[1]]?.toDouble() ?: return null
        val unit = m.groupValues[2].trim()
        if (unit.contains(' ')) return null
        val factor = when {
            unit.isEmpty() || unit.startsWith("د") || unit.startsWith("min") || unit == "m" -> 60
            unit.startsWith("ساع") || unit.startsWith("h") -> 3600
            unit.startsWith("ثا") || unit.startsWith("ث") || unit.startsWith("s") -> 1
            else -> return null
        }
        return (amount * factor).toInt().takeIf { it in 1..86_400 }
    }

    internal fun parseClock(text: String): Pair<Int, Int>? {
        val t = normalize(text).removePrefix("الساعه").removePrefix("ساعه").trim()
        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toIntOrNull() ?: if (t.contains("ونص")) 30 else if (t.contains("وربع")) 15 else 0
        val evening = listOf("مساء", "بالليل", "الليل", "العصر", "المغرب", "pm", "عصرا", "الظهر").any { t.contains(it) }
        if (evening && hour < 12) hour += 12
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    private fun formatDuration(seconds: Int): String = when {
        seconds % 3600 == 0 -> "${seconds / 3600} ساعة"
        seconds >= 3600 -> "${seconds / 3600} س ${seconds % 3600 / 60} د"
        seconds % 60 == 0 -> "${seconds / 60} دقيقة"
        seconds > 60 -> "${seconds / 60} د ${seconds % 60} ث"
        else -> "$seconds ثانية"
    }

    // endregion

    private fun stripLead(s: String): String {
        var t = normalize(s)
        for (p in LEADS) t = t.removePrefix(p).trim()
        return t
    }

    private fun initials(s: String) = s.split(' ').filter { it.isNotEmpty() }.joinToString("") { it.first().toString() }

    private companion object {
        fun set(vararg words: String) = words.map(::normalize).toSet()

        val LEADS = listOf("على", "الى", "لـ", "ع").map { normalize(it) + " " }

        val OPEN = set("افتح", "فتح", "شغل", "فوت", "open", "run")
        val CALL = set("اتصل", "اتصال", "خابر", "دك", "call", "ring")
        val SEND = set("ارسل", "دز", "ابعث", "طرش", "share", "send", "شارك")
        val TIMER = set("مؤقت", "تايمر", "timer", "عداد")
        val REMIND = set("ذكرني", "نبهني بعد", "remind")
        val ALARM = set("منبه", "نبهني", "صحيني", "قعدني", "alarm")
        val SEARCH = set("ابحث", "دور", "كوكل", "جوجل", "google", "search")
        val YOUTUBE = set("يوتيوب", "youtube")
        val MAPS = set("خريطه", "خرائط", "وين", "maps", "طريق")
        val NOTE = set("ملاحظه", "سجل", "اكتب", "note")
        val PROJECT = set("مشروع", "ستاك", "project", "stack")
        val POMODORO = set("بومودورو", "pomodoro", "ركز")
        val LATEST = set("اخر", "آخر", "last", "latest")
        val WHATSAPP = set("واتساب", "واتس", "وتساب", "whatsapp", "wa")
        val TELEGRAM = set("تليكرام", "تلكرام", "تيليكرام", "تلغرام", "تلي", "telegram", "tg")
        val SMS = set("sms", "مسج", "رساله")

        val FOCUS_WORDS = set("تركيز", "طور التركيز", "focus", "شغل")
        val FLOW_WORDS = set("تدفق", "عادي", "طور التدفق", "flow", "يومي")
        val SANCTUARY_WORDS = set("سكون", "هدوء", "طور السكون", "نوم", "night", "سكينه")

        val MEDIA = mapOf(
            normalize("صورة") to MediaKind.PHOTO, normalize("صوره") to MediaKind.PHOTO, "photo" to MediaKind.PHOTO,
            normalize("فيديو") to MediaKind.VIDEO, "video" to MediaKind.VIDEO, normalize("مقطع") to MediaKind.VIDEO,
            normalize("سكرين") to MediaKind.SCREENSHOT, normalize("لقطه") to MediaKind.SCREENSHOT, "screenshot" to MediaKind.SCREENSHOT,
            "pdf" to MediaKind.PDF, normalize("بي دي اف") to MediaKind.PDF,
            normalize("ملف") to MediaKind.ANY, "file" to MediaKind.ANY,
            normalize("صوت") to MediaKind.AUDIO, normalize("تسجيل") to MediaKind.AUDIO, normalize("بصمه") to MediaKind.AUDIO,
        )

        val NUMBER_WORDS = mapOf(
            "واحد" to 1, "وحده" to 1, "اثنين" to 2, "ثنين" to 2, "ثلاث" to 3, "ثلاثه" to 3, "اربع" to 4, "اربعه" to 4,
            "خمس" to 5, "خمسه" to 5, "ست" to 6, "سته" to 6, "سبع" to 7, "سبعه" to 7, "ثمان" to 8, "ثمانيه" to 8,
            "تسع" to 9, "تسعه" to 9, "عشر" to 10, "عشره" to 10, "ربع" to 15, "عشرين" to 20, "ثلاثين" to 30,
            "اربعين" to 40, "خمسين" to 50, "ستين" to 60,
        ).mapKeys { normalize(it.key) }

        val SPECIAL_DURATIONS = mapOf(
            "ربع ساعه" to 900, "نص ساعه" to 1800, "نصف ساعه" to 1800, "ساعه" to 3600, "ساعتين" to 7200,
            "دقيقه" to 60, "دقيقتين" to 120, "ساعه ونص" to 5400, "ثلث ساعه" to 1200,
        ).mapKeys { normalize(it.key) }

        /** Common Arabic names for apps whose labels are in English. */
        val ALIASES: Map<String, List<String>> = mapOf(
            "تليكرام" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "تلكرام" to listOf("org.telegram.messenger"),
            "واتساب" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "واتس" to listOf("com.whatsapp"),
            "يوتيوب" to listOf("com.google.android.youtube"),
            "انستا" to listOf("com.instagram.android"),
            "انستكرام" to listOf("com.instagram.android"),
            "فيسبوك" to listOf("com.facebook.katana", "com.facebook.lite"),
            "فيس" to listOf("com.facebook.katana"),
            "ماسنجر" to listOf("com.facebook.orca"),
            "سناب" to listOf("com.snapchat.android"),
            "تيك توك" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "تكتوك" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "تويتر" to listOf("com.twitter.android"),
            "اكس" to listOf("com.twitter.android"),
            "كروم" to listOf("com.android.chrome"),
            "متصفح" to listOf("com.android.chrome", "com.heytap.browser"),
            "جيميل" to listOf("com.google.android.gm"),
            "ايميل" to listOf("com.google.android.gm"),
            "خرائط" to listOf("com.google.android.apps.maps"),
            "كاميرا" to listOf("com.oplus.camera", "com.android.camera", "com.google.android.GoogleCamera"),
            "كامره" to listOf("com.oplus.camera", "com.android.camera"),
            "معرض" to listOf("com.coloros.gallery3d", "com.google.android.apps.photos"),
            "صور" to listOf("com.coloros.gallery3d", "com.google.android.apps.photos"),
            "ساعه" to listOf("com.coloros.alarmclock", "com.google.android.deskclock"),
            "حاسبه" to listOf("com.coloros.calculator", "com.google.android.calculator"),
            "اعدادات" to listOf("com.android.settings"),
            "متجر" to listOf("com.android.vending"),
            "بلاي" to listOf("com.android.vending"),
            "سبوتيفاي" to listOf("com.spotify.music"),
            "انغامي" to listOf("com.anghami"),
            "نتفلكس" to listOf("com.netflix.mediaclient"),
            "شاهد" to listOf("net.mbc.shahid", "com.shahid.mobile"),
            "ملفات" to listOf("com.coloros.filemanager", "com.google.android.apps.nbu.files"),
            "رسائل" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "هاتف" to listOf("com.google.android.dialer", "com.android.contacts"),
            "تلفون" to listOf("com.google.android.dialer", "com.android.contacts"),
            "كلود" to listOf("com.anthropic.claude"),
            "قران" to listOf("com.quran.labs.androidquran"),
            "تقويم" to listOf("com.google.android.calendar", "com.coloros.calendar"),
            "ملاحظات" to listOf("com.coloros.note", "com.google.android.keep"),
        ).mapKeys { normalize(it.key) }
    }
}
