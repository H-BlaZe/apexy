package com.apexy.alarm.missions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhrasesTest {
    @Test fun repeatedWordsKeepTheLettersButChangeCase() {
        val rnd = Random(4)
        repeat(100) {
            val line = Phrases.repeated(rnd, 4)
            val parts = line.split(", ")
            assertEquals(3, parts.size)
            // Same words each time, ignoring case.
            assertTrue(line, parts.all { it.equals(parts[0], ignoreCase = true) })
        }
        assertTrue(Phrases.pick(listOf(Phrases.AWAKE), 3, emptyList()).exactCase)
    }

    @Test fun customListIsUsedAndEmptyCustomFallsBack() {
        val mine = listOf("Go for a run", "Water the plants")
        repeat(50) { assertTrue(Phrases.pick(listOf(Phrases.CUSTOM), 3, mine).text in mine) }
        assertTrue(Phrases.pick(listOf(Phrases.CUSTOM), 3, emptyList()).text.isNotBlank())
    }

    @Test fun avoidsRepeatingThePreviousPhrase() {
        val rnd = Random(9)
        var last = ""
        repeat(100) {
            val next = Phrases.pick(listOf(Phrases.TONGUE), 3, emptyList(), avoid = last, rnd = rnd).text
            assertNotEquals(last, next)
            last = next
        }
    }

    @Test fun phraseListsSurviveTheCodec() {
        val m = Mission(MissionType.TYPING, phrases = listOf(Phrases.POEM, Phrases.CUSTOM))
        assertEquals(m.phrases, MissionCodec.decode(MissionCodec.encode(listOf(m))).single().phrases)
        // Older saves without the field get the defaults.
        val old = MissionCodec.decode("""[{"type":"TYPING","difficulty":3,"count":2}]""").single()
        assertEquals(Mission(MissionType.TYPING).phrases, old.phrases)
    }

    @Test fun newMissionTypesRoundTrip() {
        val list = listOf(Mission(MissionType.NFC, codes = listOf("04A1B2C3")), Mission(MissionType.LIGHT, 4, 10), Mission(MissionType.STEADY, 2, 20))
        assertEquals(list, MissionCodec.decode(MissionCodec.encode(list)))
        assertTrue(!Mission(MissionType.NFC).isReady)
    }
}
