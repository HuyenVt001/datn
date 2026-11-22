package com.example.snapget.core.designsystem.component.list

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Nhan "Expires in N days" tren the link moi (sheet ban be). */
class InviteExpiryLabelTest {

    private val dayMs = 24L * 60 * 60 * 1000

    /** ISO kieu server ("2026-08-18T00:00:00.000Z") cho ngay cach hom nay [offsetDays] ngay. */
    private fun isoInDays(offsetDays: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(System.currentTimeMillis() + offsetDays * dayMs)
    }

    @Test
    fun `several days left shows day count`() {
        // Chi doc phan ngay -> 30 ngay sau co the ra 29 tuy gio hien tai
        val label = inviteExpiryLabel(isoInDays(30))
        assert(label == "Expires in 30 days" || label == "Expires in 29 days") { label.toString() }
    }

    @Test
    fun `expired says a new link will be generated`() {
        assertEquals("Expired — a new link will be generated", inviteExpiryLabel(isoInDays(-2)))
    }

    @Test
    fun `null or malformed hides the label`() {
        assertNull(inviteExpiryLabel(null))
        assertNull(inviteExpiryLabel("not-a-date"))
    }
}
