package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.profile.DeviceClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class DeviceClockTest {

    @Test
    fun `zone follows the current system default`() {
        val original = TimeZone.getDefault()
        try {
            val first = TimeZone.getTimeZone("Pacific/Auckland")
            TimeZone.setDefault(first)
            assertEquals(first.toZoneId(), DeviceClock.zone)

            val second = TimeZone.getTimeZone("America/Los_Angeles")
            TimeZone.setDefault(second)
            assertEquals(second.toZoneId(), DeviceClock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
