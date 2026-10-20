package com.madar.launcher

import com.madar.launcher.ai.AiInterpreter
import com.madar.launcher.command.Action
import com.madar.launcher.command.Channel
import com.madar.launcher.command.CommandParser
import com.madar.launcher.command.normalize
import com.madar.launcher.data.ContactEntry
import com.madar.launcher.data.MediaKind
import com.madar.launcher.data.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInterpreterTest {

    private val ahmed = ContactEntry("أحمد علي", "07701234567", normalize("أحمد علي"))
    private val contacts = { q: String -> listOf(ahmed).filter { q.isNotBlank() && it.searchKey.contains(normalize(q)) } }
    private val ai = AiInterpreter(CommandParser({ emptyList() }, contacts, { emptyList() }), contacts)

    private fun actions(out: String) = ai.interpret(out).map { it.action }

    @Test fun singleActionInsideProseAndFences() {
        val out = "Sure!\n```json\n{\"action\":\"timer\",\"minutes\":20,\"text\":\"الفرن\"}\n```"
        assertEquals(listOf(Action.SetTimer(1200, "الفرن")), actions(out))
    }

    @Test fun multiStepPlan() {
        val out = """{"steps":[
            {"action":"project","title":"سفرة أربيل"},
            {"action":"alarm","hour":6,"minute":30},
            {"action":"message","contact":"احمد","channel":"whatsapp","text":"نطلع الساعة 7"},
            {"action":"maps","query":"قلعة أربيل"}
        ]}"""
        assertEquals(
            listOf(
                Action.CreateStack("سفرة أربيل"),
                Action.SetAlarm(6, 30, ""),
                Action.Message(ahmed, Channel.WHATSAPP, "نطلع الساعة 7"),
                Action.MapsSearch("قلعة أربيل"),
            ),
            actions(out),
        )
    }

    @Test fun unknownPeopleAndAppsAreDroppedNotGuessed() {
        val out = """{"steps":[{"action":"call","contact":"زينب"},{"action":"open_app","app":"تطبيق غير موجود"},{"action":"mode","mode":"focus"}]}"""
        assertEquals(listOf(Action.SwitchMode(Mode.FOCUS)), actions(out))
    }

    @Test fun shareLatestResolvesKindAndPerson() {
        assertEquals(
            listOf(Action.ShareLatest(MediaKind.PDF, Channel.TELEGRAM, ahmed)),
            actions("""{"action":"share_latest","kind":"pdf","contact":"أحمد","channel":"telegram"}"""),
        )
    }

    @Test fun outOfRangeValuesAreRejected() {
        val rows = actions("""{"steps":[{"action":"alarm","hour":27,"minute":0},{"action":"timer","minutes":0}]}""")
        // Nothing valid survives, so the raw text comes back as a copyable answer instead.
        assertTrue(rows.single() is Action.CopyResult)
    }

    @Test fun plainTextBecomesAnAnswer() {
        val rows = ai.interpret("عاصمة العراق هي بغداد.")
        assertEquals(Action.CopyResult("عاصمة العراق هي بغداد."), rows.single().action)
    }
}
