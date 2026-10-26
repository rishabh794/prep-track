package com.preptrack.planner.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class RetentionCalculator {

    /**
     * Computes retention for a pattern based on stability and time since last practice.
     * Note: If lastPracticedAt is NULL, the pattern has never been practiced and MUST return 0.0.
     */
    public double compute(LocalDateTime lastPracticedAt, double stability, LocalDateTime now) {
        if (lastPracticedAt == null) {
            return 0.0;
        }
        long days = ChronoUnit.DAYS.between(lastPracticedAt.toLocalDate(), now.toLocalDate());
        if (days <= 0) {
            return 1.0;
        }
        double stab = stability <= 0.0 ? 1.0 : stability;
        return Math.exp(-(double) days / stab);
    }

    public double compute(LocalDateTime lastPracticedAt, double stability) {
        return compute(lastPracticedAt, stability, LocalDateTime.now());
    }
}
