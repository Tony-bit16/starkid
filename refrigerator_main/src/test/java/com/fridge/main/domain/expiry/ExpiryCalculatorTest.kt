package com.fridge.main.domain.expiry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExpiryCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val storedAt = LocalDate.of(2026, 11, 10)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `expiry date is stored date plus shelf life days`() {
        val expiry = ExpiryCalculator.expiryDate(
            storedAt = storedAt,
            shelfLifeDays = 5,
            zoneId = zone,
        )

        assertEquals(LocalDate.of(2026, 11, 15), expiry)
        assertEquals(
            1,
            ExpiryCalculator.daysLeft(
                storedAt = storedAt,
                shelfLifeDays = 5,
                today = LocalDate.of(2026, 11, 14),
                zoneId = zone,
            ),
        )
    }

    @Test
    fun `three days left is still expiring soon`() {
        assertTrue(ExpiryCalculator.isExpiringSoon(0))
        assertTrue(ExpiryCalculator.isExpiringSoon(3))
        assertFalse(ExpiryCalculator.isExpiringSoon(4))
    }

    @Test
    fun `negative days left is expired and not expiring soon`() {
        assertTrue(ExpiryCalculator.isExpired(-1))
        assertFalse(ExpiryCalculator.isExpiringSoon(-1))
    }

    @Test
    fun `calendar day difference survives spring dst transition`() {
        // 2026-03-08 美东进入夏令时：当天只有 23 小时。
        // 若用毫秒差 / 86400000，跨过这一天可能出现少算 1 天的经典 bug。
        val newYork = ZoneId.of("America/New_York")
        val stored = LocalDate.of(2026, 3, 7)
            .atStartOfDay(newYork)
            .toInstant()
            .toEpochMilli()

        val expiry = ExpiryCalculator.expiryDate(stored, 3, newYork)
        val daysLeft = ExpiryCalculator.daysLeft(
            storedAt = stored,
            shelfLifeDays = 3,
            today = LocalDate.of(2026, 3, 10),
            zoneId = newYork,
        )

        assertEquals(LocalDate.of(2026, 3, 10), expiry)
        assertEquals(0, daysLeft)
        assertTrue(ExpiryCalculator.isExpiringSoon(daysLeft))
    }
}
