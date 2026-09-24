package com.example.model

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearMonthTest {

    private val utc = TimeZone.UTC

    @Test
    fun previousFromJanuaryGoesToDecemberOfPreviousYear() {
        assertEquals(YearMonth(2025, 11), YearMonth(2026, 0).previous())
    }

    @Test
    fun nextFromDecemberGoesToJanuaryOfNextYear() {
        assertEquals(YearMonth(2027, 0), YearMonth(2026, 11).next())
    }

    @Test
    fun plusMonthsHandlesLargeNegativeOffsets() {
        assertEquals(YearMonth(2024, 11), YearMonth(2026, 1).plusMonths(-14))
    }

    @Test
    fun timestampRoundTrip() {
        val august = YearMonth(2026, 7)
        val ts = august.toEpochMillis(day = 22, hour = 14, timeZone = utc)
        assertTrue(august.containsTimestamp(ts, utc))
        assertFalse(august.next().containsTimestamp(ts, utc))
        assertEquals(august, YearMonth.fromTimestamp(ts, utc))
    }
}
