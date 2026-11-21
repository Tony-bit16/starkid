package com.fridge.main.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `defaults match product contract`() {
        val settings = AppSettings()

        assertEquals(3, settings.reminderDays)
        assertEquals(9, settings.reminderHour)
        assertEquals(0, settings.reminderMinute)
        assertEquals(true, settings.notificationsEnabled)
    }

    @Test
    fun `out of range values are normalized`() {
        val settings = AppSettings(
            reminderDays = 99,
            reminderHour = -2,
            reminderMinute = 90,
            notificationsEnabled = false,
        ).normalized()

        assertEquals(7, settings.reminderDays)
        assertEquals(0, settings.reminderHour)
        assertEquals(59, settings.reminderMinute)
        assertEquals(false, settings.notificationsEnabled)
    }

    @Test
    fun `lower bound is preserved`() {
        val settings = AppSettings(reminderDays = 1, reminderHour = 0, reminderMinute = 0)

        assertEquals(1, settings.normalized().reminderDays)
    }
}
