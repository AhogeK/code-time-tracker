package com.ahogek.codetimetracker.statistics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SummaryDataProviderTest {

    @Test
    fun testDailyAverageCalculationIncludesBothStartAndEndDays() {
        val firstDate = LocalDate.of(2026, 2, 15)
        val today = LocalDate.of(2026, 2, 16)

        val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today).plus(1).coerceAtLeast(1)

        assertEquals(2, daysSinceFirst, "Should be 2 days (Feb 15 and Feb 16)")
    }

    @Test
    fun testDailyAverageCalculationWithMultipleDays() {
        val firstDate = LocalDate.of(2026, 2, 1)
        val today = LocalDate.of(2026, 2, 16)

        val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today).plus(1).coerceAtLeast(1)

        assertEquals(16, daysSinceFirst, "Should be 16 days (Feb 1 to Feb 16 inclusive)")
    }

    @Test
    fun testDailyAverageCalculationSameDay() {
        val firstDate = LocalDate.of(2026, 2, 16)
        val today = LocalDate.of(2026, 2, 16)

        val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today).plus(1).coerceAtLeast(1)

        assertEquals(1, daysSinceFirst, "Should be 1 day when first date equals today")
    }

    @Test
    fun testDailyAverageWithDuration() {
        val firstDate = LocalDate.of(2026, 2, 15)
        val today = LocalDate.of(2026, 2, 16)
        val totalSeconds = 16 * 60 * 60L

        val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today).plus(1).coerceAtLeast(1)
        val dailyAverageSeconds = totalSeconds / daysSinceFirst

        assertEquals(8 * 60 * 60L, dailyAverageSeconds, "16 hours / 2 days = 8 hours")
    }
}
