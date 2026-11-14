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
}
