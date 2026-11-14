package com.fridge.main.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ExpiryReminderSchedulerTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `before nine schedules later the same day`() {
        val now = ZonedDateTime.of(2026, 11, 14, 8, 30, 0, 0, zone)

        assertEquals(
            Duration.ofMinutes(30).toMillis(),
            ExpiryReminderScheduler.initialDelayMillis(now),
        )
    }

    @Test
    fun `after nine schedules next day`() {
        val now = ZonedDateTime.of(2026, 11, 14, 9, 30, 0, 0, zone)

        assertEquals(
            Duration.ofHours(23).plusMinutes(30).toMillis(),
            ExpiryReminderScheduler.initialDelayMillis(now),
        )
    }

    @Test
    fun `exactly nine waits until next day`() {
        val now = ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, zone)

        assertEquals(
            Duration.ofDays(1).toMillis(),
            ExpiryReminderScheduler.initialDelayMillis(now),
        )
    }
}
