package com.parksync.backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FeeCalculatorTest {
    private final Instant start = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void chargesAtLeastOneHourAndRoundsPartialHoursUp() {
        assertEquals(1, FeeCalculator.billableHours(start, start.plusSeconds(1)));
        assertEquals(1, FeeCalculator.billableHours(start, start.plusSeconds(3600)));
        assertEquals(2, FeeCalculator.billableHours(start, start.plusSeconds(3601)));
        assertEquals(3, FeeCalculator.billableHours(start, start.plusSeconds(7201)));
    }

    @Test
    void calculatesServerSideEstimateToTwoDecimalPlaces() {
        assertEquals(new BigDecimal("160.00"),
                FeeCalculator.estimate(new BigDecimal("80.00"), start, start.plusSeconds(3601)));
    }

    @Test
    void rejectsInvalidPeriodsAndRates() {
        assertThrows(IllegalArgumentException.class, () -> FeeCalculator.billableHours(start, start));
        assertThrows(IllegalArgumentException.class, () ->
                FeeCalculator.estimate(new BigDecimal("0.00"), start, start.plusSeconds(1)));
    }
}