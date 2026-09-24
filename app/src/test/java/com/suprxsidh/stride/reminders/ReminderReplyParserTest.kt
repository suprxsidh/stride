package com.suprxsidh.stride.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderReplyParserTest {

    @Test
    fun `parseWeightKg accepts a plain decimal`() {
        assertEquals(81.4, ReminderReplyParser.parseWeightKg("81.4")!!, 0.001)
    }

    @Test
    fun `parseWeightKg trims surrounding whitespace`() {
        assertEquals(81.4, ReminderReplyParser.parseWeightKg("  81.4  ")!!, 0.001)
    }

    @Test
    fun `parseWeightKg accepts a comma decimal separator`() {
        assertEquals(81.4, ReminderReplyParser.parseWeightKg("81,4")!!, 0.001)
    }

    @Test
    fun `parseWeightKg rejects non-numeric text`() {
        assertNull(ReminderReplyParser.parseWeightKg("about eighty one"))
    }

    @Test
    fun `parseWeightKg rejects blank text`() {
        assertNull(ReminderReplyParser.parseWeightKg("   "))
    }

    @Test
    fun `parseWeightKg rejects implausibly low values`() {
        assertNull(ReminderReplyParser.parseWeightKg("5"))
    }

    @Test
    fun `parseWeightKg rejects implausibly high values`() {
        // A very plausible real-world typo: "814" instead of "81.4".
        assertNull(ReminderReplyParser.parseWeightKg("814"))
    }

    @Test
    fun `parseWeightKg accepts the exact boundary values`() {
        assertEquals(20.0, ReminderReplyParser.parseWeightKg("20")!!, 0.001)
        assertEquals(400.0, ReminderReplyParser.parseWeightKg("400")!!, 0.001)
    }

    @Test
    fun `parseMealDescription trims and passes through non-blank text`() {
        assertEquals("2 rotis and chole", ReminderReplyParser.parseMealDescription("  2 rotis and chole  "))
    }

    @Test
    fun `parseMealDescription rejects blank text`() {
        assertNull(ReminderReplyParser.parseMealDescription("   "))
    }
}
