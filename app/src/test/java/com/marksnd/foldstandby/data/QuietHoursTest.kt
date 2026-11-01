package com.marksnd.foldstandby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class QuietHoursTest {
    private fun m(h: Int, min: Int = 0) = h * 60 + min

    @Test fun sameDayRange() {
        assertTrue(isQuiet(m(3), m(0), m(6)))
        assertTrue(isQuiet(m(0), m(0), m(6)))
        assertFalse(isQuiet(m(6), m(0), m(6))) // 끝 시각은 포함하지 않는다
        assertFalse(isQuiet(m(12), m(0), m(6)))
    }

    @Test fun rangeAcrossMidnight() {
        assertTrue(isQuiet(m(23), m(22), m(6)))
        assertTrue(isQuiet(m(22), m(22), m(6)))
        assertTrue(isQuiet(m(0), m(22), m(6)))
        assertTrue(isQuiet(m(5, 59), m(22), m(6)))
        assertFalse(isQuiet(m(6), m(22), m(6)))
        assertFalse(isQuiet(m(21, 59), m(22), m(6)))
        assertFalse(isQuiet(m(12), m(22), m(6)))
    }

    @Test fun sameStartAndEndMeansNever() {
        assertFalse(isQuiet(m(3), m(3), m(3)))
        assertFalse(isQuiet(m(12), 0, 0))
    }

    @Test fun minuteOfDayUsesZone() {
        val seoul = TimeZone.getTimeZone("Asia/Seoul")
        // 2026-10-25 09:41 KST = 00:41 UTC
        val ms = java.time.ZonedDateTime.of(2026, 10, 25, 9, 41, 7, 0, java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        assertEquals(m(9, 41), minuteOfDay(ms, seoul))
        assertEquals(m(0, 41), minuteOfDay(ms, TimeZone.getTimeZone("UTC")))
    }

    @Test fun formatsMinutes() {
        assertEquals("00:00", formatMinute(0))
        assertEquals("06:30", formatMinute(m(6, 30)))
        assertEquals("23:59", formatMinute(m(23, 59)))
    }
}
