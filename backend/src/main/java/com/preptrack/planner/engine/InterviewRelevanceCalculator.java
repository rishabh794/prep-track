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
        return calculateWithInterviews(upcoming, pattern, currentDate);
    }

    public InterviewContext calculateWithInterviews(List<Interview> upcoming, Pattern pattern, LocalDate currentDate) {
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
        double companyRelevance = (pattern != null && company != null)
                ? calculateCompanyPatternRelevance(company, pattern.getName())
                : 1.0;
        // Proximity boost: closer to interview (within 14 days) gives higher relevance
        double boost = 0.5;
        double relevance = companyRelevance * (1.0 + boost * ((14.0 - (double) minDays) / 14.0));

        return InterviewContext.builder()
                .relevanceMultiplier(Math.round(relevance * 100.0) / 100.0)
                .daysRemaining(minDays)
                .companyName(company)
                .build();
    }

    public double calculateCompanyPatternRelevance(String companyName, String patternName) {
        String company = companyName != null ? companyName.toLowerCase().trim() : "";
        String pattern = patternName != null ? patternName.toLowerCase() : "";

        // Low-frequency patterns for specific companies
        if (company.contains("google")) {
            if (pattern.contains("trie") || pattern.contains("bit manipulation")) {
                return 0.4;
            }
            if (pattern.contains("dp") || pattern.contains("pointer") || pattern.contains("window")
                    || pattern.contains("graph") || pattern.contains("stack")) {
                return 1.2;
            }
        } else if (company.contains("meta") || company.contains("facebook")) {
            if (pattern.contains("trie")) {
                return 0.4;
            }
            if (pattern.contains("pointer") || pattern.contains("window") || pattern.contains("tree")) {
                return 1.2;
            }
        } else if (company.contains("amazon")) {
            if (pattern.contains("bit manipulation")) {
                return 0.4;
            }
            if (pattern.contains("bfs") || pattern.contains("dfs") || pattern.contains("heap") || pattern.contains("topological")) {
                return 1.2;
            }
        }

        // Generic tech company low-frequency patterns
        if (pattern.contains("trie") || pattern.contains("bit manipulation")) {
            return 0.5;
        }

        return 1.0;
    }

    public double getEffectiveInterviewBoost(com.preptrack.pattern.entity.UserPatternState state, LocalDate currentDate) {
        if (state == null || state.getInterviewBoost() == null || state.getInterviewBoost() <= 0.0 || state.getInterviewBoostSetAt() == null) {
            return 0.0;
        }
        LocalDate setDate = state.getInterviewBoostSetAt().toLocalDate();
        long daysSince = ChronoUnit.DAYS.between(setDate, currentDate);
        if (daysSince >= 14 || daysSince < 0) {
            return 0.0;
        }
        double decayFactor = (14.0 - (double) daysSince) / 14.0;
        return Math.round(state.getInterviewBoost() * decayFactor * 100.0) / 100.0;
    }
}
