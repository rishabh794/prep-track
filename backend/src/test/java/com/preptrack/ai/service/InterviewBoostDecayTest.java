package com.preptrack.ai.service;

import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.planner.engine.InterviewRelevanceCalculator;
import com.preptrack.company.service.CompanyProfileService;
import com.preptrack.config.EngineProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class InterviewBoostDecayTest {

    @Mock
    private InterviewRepository interviewRepository;

    @Test
    @DisplayName("Interview boost decays linearly over 14 days and drops to 0 afterwards")
    void testInterviewBoostDecay() {
        InterviewRelevanceCalculator calculator = new InterviewRelevanceCalculator(interviewRepository, mock(CompanyProfileService.class), new EngineProperties(), mock(com.preptrack.common.service.TimeService.class));

        LocalDate today = LocalDate.of(2026, 11, 15);

        // Day 0: just confirmed today
        UserPatternState day0State = UserPatternState.builder()
                .interviewBoost(0.3)
                .interviewBoostSetAt(LocalDateTime.of(2026, 11, 15, 10, 0))
                .build();
        double boostDay0 = calculator.getEffectiveInterviewBoost(day0State, today);
        assertThat(boostDay0).isEqualTo(0.30);

        // Day 7: 7 days later -> half decay (0.3 * (14-7)/14 = 0.15)
        UserPatternState day7State = UserPatternState.builder()
                .interviewBoost(0.3)
                .interviewBoostSetAt(LocalDateTime.of(2026, 11, 8, 10, 0))
                .build();
        double boostDay7 = calculator.getEffectiveInterviewBoost(day7State, today);
        assertThat(boostDay7).isEqualTo(0.15);

        // Day 14: exactly 14 days -> 0.0
        UserPatternState day14State = UserPatternState.builder()
                .interviewBoost(0.3)
                .interviewBoostSetAt(LocalDateTime.of(2026, 11, 1, 10, 0))
                .build();
        double boostDay14 = calculator.getEffectiveInterviewBoost(day14State, today);
        assertThat(boostDay14).isEqualTo(0.0);

        // Day 20: 20 days later -> 0.0
        UserPatternState day20State = UserPatternState.builder()
                .interviewBoost(0.3)
                .interviewBoostSetAt(LocalDateTime.of(2026, 10, 26, 10, 0))
                .build();
        double boostDay20 = calculator.getEffectiveInterviewBoost(day20State, today);
        assertThat(boostDay20).isEqualTo(0.0);
    }
}
