package com.example.snapget.core.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dong "2h ago" duoi ten nguoi gui loi moi ket ban. */
class TimeAgoTest {

    private val iso = "2026-11-15T10:00:00.000Z"
    private val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse("2026-11-15T10:00:00")!!.time

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `time thresholds`() {
        assertEquals("just now", timeAgo(iso, base + 30_000))
        assertEquals("5m ago", timeAgo(iso, base + 5 * minute))
        assertEquals("2h ago", timeAgo(iso, base + 2 * hour + 10 * minute))
        assertEquals("3d ago", timeAgo(iso, base + 3 * day))
        assertEquals("2w ago", timeAgo(iso, base + 15 * day))
    }

    @Test
    fun `future time from clock skew counts as just now`() {
        assertEquals("just now", timeAgo(iso, base - 5 * minute))
    }

    @Test
    fun `null or malformed is hidden`() {
        assertNull(timeAgo(null))
        assertNull(timeAgo("2026-11-15"))
        assertNull(timeAgo("not-a-date-at-all-xx"))
    }
}
