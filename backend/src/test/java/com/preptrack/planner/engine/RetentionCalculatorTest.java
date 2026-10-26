package com.preptrack.planner.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RetentionCalculatorTest {

    private final RetentionCalculator calculator = new RetentionCalculator();

    @Test
    @DisplayName("RetentionCalculator returns 0.0 for NULL last_practiced_at")
    void testNullLastPracticedAtReturnsZero() {
        double retention = calculator.compute(null, 10.0, LocalDateTime.now());
        assertThat(retention).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Retention is 1.0 when practiced today")
    void testPracticedTodayReturnsOne() {
        LocalDateTime now = LocalDateTime.now();
        double retention = calculator.compute(now, 10.0, now);
        assertThat(retention).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Retention decays exponentially with days and stability")
    void testExponentialDecay() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime fiveDaysAgo = now.minusDays(5);
        double stability = 5.0; // after 5 days with stability 5, retention = exp(-5/5) = 1/e ≈ 0.367879

        double retention = calculator.compute(fiveDaysAgo, stability, now);
        assertThat(retention).isCloseTo(Math.exp(-1.0), within(0.001));
    }
}
