package com.preptrack.forecast.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Assumptions of the explicitly approximate, pattern-level readiness model. */
@Data
@Configuration
@ConfigurationProperties(prefix = "preptrack.forecast")
@Validated
public class ForecastProperties {
    @Min(1) @Max(365) private int maxHorizonDays = 60;
    @DecimalMin("0.01") @DecimalMax("0.99") private double reviewTriggerRetention = 0.70;
    @DecimalMin("0.0") @DecimalMax("1.0") private double revisionSkillGain = 0.02;
    @DecimalMin("1.0") @DecimalMax("5.0") private double revisionStabilityMultiplier = 1.45;
    @DecimalMin("1.0") @DecimalMax("5.0") private double newProblemStabilityMultiplier = 1.40;
}
