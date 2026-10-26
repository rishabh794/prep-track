package com.preptrack.planner.engine;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.pattern.entity.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InterviewRelevanceCalculator {

    private final InterviewRepository interviewRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewContext {
        private Double relevanceMultiplier;
        private Long daysRemaining;
        private String companyName;
    }

    public InterviewContext calculate(Long userId, Pattern pattern, LocalDate currentDate) {
        List<Interview> upcoming = interviewRepository.findByUserIdAndOutcome(userId, InterviewOutcome.UPCOMING);

        Interview nearest = null;
        long minDays = Long.MAX_VALUE;

        for (Interview i : upcoming) {
            if (i.getInterviewDate() != null && !i.getInterviewDate().isBefore(currentDate)) {
                long days = ChronoUnit.DAYS.between(currentDate, i.getInterviewDate());
                if (days < minDays) {
                    minDays = days;
                    nearest = i;
                }
            }
        }

        if (nearest == null || minDays > 14) {
            return InterviewContext.builder()
                    .relevanceMultiplier(1.0)
                    .daysRemaining(null)
                    .companyName(null)
                    .build();
        }

        String company = nearest.getApplication() != null ? nearest.getApplication().getCompanyName() : "Upcoming";
        // Proximity boost: closer to interview (within 14 days) gives higher relevance
        double boost = 0.5;
        double relevance = 1.0 + boost * ((14.0 - (double) minDays) / 14.0);

        return InterviewContext.builder()
                .relevanceMultiplier(Math.round(relevance * 100.0) / 100.0)
                .daysRemaining(minDays)
                .companyName(company)
                .build();
    }
}
