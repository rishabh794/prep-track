package com.preptrack.forecast.service;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.forecast.dto.ReadinessForecastDto;
import com.preptrack.forecast.engine.ForwardSimulator;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.sheet.entity.SheetProblem;
import com.preptrack.sheet.entity.UserSheet;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final InterviewRepository interviewRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserPatternStateRepository userPatternStateRepository;
    private final UserSheetRepository userSheetRepository;
    private final SheetProblemRepository sheetProblemRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final UserProblemStatusRepository userProblemStatusRepository;
    private final PatternRepository patternRepository;
    private final ForwardSimulator forwardSimulator;
    private final com.preptrack.planner.engine.InterviewRelevanceCalculator interviewRelevanceCalculator;

    @Transactional(readOnly = true)
    public ReadinessForecastDto getForecastForInterview(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));

        if (interview.getApplication() == null || interview.getApplication().getUser() == null ||
                !interview.getApplication().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Interview not found: " + interviewId);
        }

        LocalDate today = LocalDate.now();
        LocalDate interviewDate = interview.getInterviewDate();
        long daysRemaining = 0;
        if (interviewDate != null && !interviewDate.isBefore(today)) {
            daysRemaining = ChronoUnit.DAYS.between(today, interviewDate);
        }

        String companyName = interview.getApplication().getCompanyName();

        // 1. Fetch User Settings
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElse(UserSettings.builder()
                        .dailyTimeBudgetMin(90)
                        .maxNewProblemsDay(3)
                        .maxRevisionsDay(10)
                        .targetSkillLevel(0.8)
                        .build());

        int budget = settings.getDailyTimeBudgetMin() != null ? settings.getDailyTimeBudgetMin() : 90;
        int maxNew = settings.getMaxNewProblemsDay() != null ? settings.getMaxNewProblemsDay() : 3;
        int maxRev = settings.getMaxRevisionsDay() != null ? settings.getMaxRevisionsDay() : 10;
        double targetSkill = settings.getTargetSkillLevel() != null ? settings.getTargetSkillLevel() : 0.8;

        // 2. Fetch User Pattern States
        List<UserPatternState> patternStates = userPatternStateRepository.findByUserIdWithPattern(userId);
        Map<String, UserPatternState> stateByName = patternStates.stream()
                .filter(ps -> ps.getPattern() != null && ps.getPattern().getName() != null)
                .collect(Collectors.toMap(ps -> ps.getPattern().getName(), ps -> ps, (a, b) -> a));

        // 3. Find tracked problem IDs from user sheets (scalar lightweight query)
        List<UserSheet> userSheets = userSheetRepository.findByUserIdWithSheet(userId);
        List<Long> sheetIds = userSheets.stream().map(us -> us.getSheet().getId()).toList();
        Set<Long> problemIds = sheetIds.isEmpty() ? Collections.emptySet()
                : sheetProblemRepository.findDistinctProblemIdsBySheetIdIn(sheetIds);

        // 4. Identify which problems user already solved
        List<UserProblemStatus> userStatuses = userProblemStatusRepository.findByUserId(userId);
        Set<Long> solvedProblemIds = userStatuses.stream()
                .filter(s -> s.getStatus() == ProblemStatus.SOLVED_INDEPENDENTLY || s.getStatus() == ProblemStatus.SOLVED_WITH_HELP)
                .map(s -> s.getProblem().getId())
                .collect(Collectors.toSet());

        // 5. Group available unsolved problems by pattern
        Map<String, Integer> availableProblemsPerPattern = new HashMap<>();
        Map<String, Pattern> allTargetPatterns = new LinkedHashMap<>();

        if (!problemIds.isEmpty()) {
            List<ProblemPattern> pps = problemPatternRepository.findByProblemIdInWithPattern(new ArrayList<>(problemIds));
            for (ProblemPattern pp : pps) {
                if (pp.getPattern() != null) {
                    String pName = pp.getPattern().getName();
                    allTargetPatterns.putIfAbsent(pName, pp.getPattern());
                    if (!solvedProblemIds.contains(pp.getProblem().getId())) {
                        availableProblemsPerPattern.put(pName, availableProblemsPerPattern.getOrDefault(pName, 0) + 1);
                    }
                }
            }
        }

        // Fallback: if user hasn't selected any sheets yet or no problems linked, load standard patterns
        if (allTargetPatterns.isEmpty()) {
            List<Pattern> all = patternRepository.findAll();
            for (Pattern p : all) {
                allTargetPatterns.put(p.getName(), p);
                availableProblemsPerPattern.put(p.getName(), 5);
            }
        }

        // 6. Build pattern snapshots for simulator
        List<ForwardSimulator.PatternSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, Pattern> entry : allTargetPatterns.entrySet()) {
            String pName = entry.getKey();
            UserPatternState state = stateByName.get(pName);

            double curSkill = state != null && state.getSkill() != null ? state.getSkill() : 0.0;
            double curStability = state != null && state.getStability() != null ? state.getStability() : 1.0;
            Integer daysSincePractice = null;
            if (state != null && state.getLastPracticedAt() != null) {
                daysSincePractice = (int) ChronoUnit.DAYS.between(state.getLastPracticedAt().toLocalDate(), today);
                if (daysSincePractice < 0) daysSincePractice = 0;
            }

            int available = availableProblemsPerPattern.getOrDefault(pName, 0);
            double companyRelevance = calculateCompanyPatternRelevance(companyName, pName);

            snapshots.add(ForwardSimulator.PatternSnapshot.builder()
                    .patternName(pName)
                    .currentSkill(curSkill)
                    .currentStability(curStability)
                    .daysSinceLastPracticed(daysSincePractice)
                    .availableProblems(available)
                    .companyRelevance(companyRelevance)
                    .build());
        }

        // 7. Run forward simulation
        return forwardSimulator.simulate(
                interviewId,
                interviewDate,
                companyName,
                daysRemaining,
                budget,
                maxNew,
                maxRev,
                targetSkill,
                snapshots
        );
    }

    private double calculateCompanyPatternRelevance(String companyName, String patternName) {
        return interviewRelevanceCalculator.calculateCompanyPatternRelevance(companyName, patternName);
    }
}
