package com.suprxsidh.stride.reminders

/**
 * Feature E (completeness pass, spec §6): parsing/validation for the two kinds of direct-reply
 * text a reminder notification can receive. Pulled out as pure functions (no Context/Android
 * dependency) so they're plain-JUnit testable, independent of the Robolectric-based receiver
 * tests.
 */
object ReminderReplyParser {
    /** Sane human bodyweight bounds in kg -- guards against a mis-typed reply (e.g. "814") being
     * logged as a real weigh-in. Matches this app's kg-only convention (SPEC.md has no imperial
     * unit support anywhere). */
    private const val MIN_WEIGHT_KG = 20.0
    private const val MAX_WEIGHT_KG = 400.0

    /** Parses a weigh-in reminder's direct-reply text (e.g. "81.4") into kg, or null if it isn't
     * a valid, plausible weight. Accepts either '.' or ',' as the decimal separator since a
     * phone's numeric keyboard can offer either depending on locale. */
    fun parseWeightKg(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (value < MIN_WEIGHT_KG || value > MAX_WEIGHT_KG) return null
        return value
    }

    /** Meal/snack direct-reply text (e.g. "2 rotis and chole") has no numeric shape to validate
     * -- the only real requirement is that there's something to send to Gemini at all. */
    fun parseMealDescription(text: String): String? = text.trim().takeIf { it.isNotEmpty() }
}
