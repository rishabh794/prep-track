package com.preptrack.planner.engine;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.company.service.CompanyProfileService;
import com.preptrack.config.EngineProperties;
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
    private final CompanyProfileService companyProfiles;
    private final EngineProperties policy;

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

        int proximityWindow = policy.getInterviewProximityWindowDays();
        if (nearest == null || minDays > proximityWindow) {
            return InterviewContext.builder()
                    .relevanceMultiplier(1.0)
                    .daysRemaining(null)
                    .companyName(null)
                    .build();
        }

        String company = nearest.getApplication() != null ? nearest.getApplication().getCompanyName() : "Upcoming";
        double companyRelevance = (pattern != null && company != null)
                ? calculateCompanyPatternRelevance(company, pattern)
                : 1.0;
        double boost = policy.getInterviewProximityMaxBoost();
        double relevance = companyRelevance * (1.0 + boost * ((proximityWindow - (double) minDays) / proximityWindow));

        return InterviewContext.builder()
                .relevanceMultiplier(Math.round(relevance * 100.0) / 100.0)
                .daysRemaining(minDays)
                .companyName(company)
                .build();
    }

    public double calculateCompanyPatternRelevance(String companyName, String patternName) {
        return companyProfiles.relevance(companyName, patternName);
    }

    public double calculateCompanyPatternRelevance(String companyName, Pattern pattern) {
        return companyProfiles.relevance(companyName, pattern);
    }

    public double getEffectiveInterviewBoost(com.preptrack.pattern.entity.UserPatternState state, LocalDate currentDate) {
        if (state == null || state.getInterviewBoost() == null || state.getInterviewBoost() <= 0.0 || state.getInterviewBoostSetAt() == null) {
            return 0.0;
        }
        LocalDate setDate = state.getInterviewBoostSetAt().toLocalDate();
        long daysSince = ChronoUnit.DAYS.between(setDate, currentDate);
        int decayDays = policy.getInterviewBoostDecayDays();
        if (daysSince >= decayDays || daysSince < 0) {
            return 0.0;
        }
        double decayFactor = (decayDays - (double) daysSince) / decayDays;
        return Math.round(state.getInterviewBoost() * decayFactor * 100.0) / 100.0;
    }
}
