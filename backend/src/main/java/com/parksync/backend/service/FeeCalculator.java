package com.parksync.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

public final class FeeCalculator {
    private FeeCalculator() { }

    public static int billableHours(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Departure must be after arrival.");
        }
        long seconds = Math.max(1, Duration.between(start, end).toSeconds());
        return Math.toIntExact(Math.max(1, (seconds + 3599) / 3600));
    }

    public static BigDecimal estimate(BigDecimal hourlyRate, Instant start, Instant end) {
        if (hourlyRate == null || hourlyRate.signum() <= 0) {
            throw new IllegalArgumentException("A valid hourly rate is required.");
        }
        return hourlyRate.multiply(BigDecimal.valueOf(billableHours(start, end)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}