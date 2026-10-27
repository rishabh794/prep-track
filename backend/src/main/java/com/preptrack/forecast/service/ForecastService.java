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
import com.preptrack.forecast.dto.ForecastStatus;
import com.preptrack.config.EngineProperties;
import com.preptrack.common.service.TimeService;
import com.preptrack.forecast.engine.ForwardSimulator;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
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
    private final ForwardSimulator forwardSimulator;
    private final com.preptrack.planner.engine.InterviewRelevanceCalculator interviewRelevanceCalculator;
    private final EngineProperties engine;
    private final TimeService timeService;

    @Transactional(readOnly = true)
    public ReadinessForecastDto getForecastForInterview(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));

        if (interview.getApplication() == null || interview.getApplication().getUser() == null ||
                !interview.getApplication().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Interview not found: " + interviewId);
        }

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElse(UserSettings.builder()
                        .dailyTimeBudgetMin(engine.getDefaultBudgetMinutes())
                        .maxNewProblemsDay(engine.getDefaultMaxNewProblems())
                        .maxRevisionsDay(engine.getDefaultMaxRevisions())
                        .targetSkillLevel(engine.getDefaultTargetSkill())
                        .build());
        LocalDate today = timeService.getLocalDate(settings);
        LocalDate interviewDate = interview.getInterviewDate();
        long daysRemaining = interviewDate == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(today, interviewDate));
        String companyName = interview.getApplication().getCompanyName();
        int budget = settings.getDailyTimeBudgetMin() != null ? settings.getDailyTimeBudgetMin() : engine.getDefaultBudgetMinutes();
        int maxNew = settings.getMaxNewProblemsDay() != null ? settings.getMaxNewProblemsDay() : engine.getDefaultMaxNewProblems();
        int maxRev = settings.getMaxRevisionsDay() != null ? settings.getMaxRevisionsDay() : engine.getDefaultMaxRevisions();
        double targetSkill = settings.getTargetSkillLevel() != null ? settings.getTargetSkillLevel() : engine.getDefaultTargetSkill();

        if (interviewDate == null || interviewDate.isBefore(today)) {
            return unavailable(interview, today, daysRemaining, settings,
                    interviewDate == null ? ForecastStatus.INSUFFICIENT_DATA : ForecastStatus.INTERVIEW_PASSED,
                    interviewDate == null ? "Add an interview date to generate a forecast."
                            : "This interview date has passed. Add a future interview to generate a readiness forecast.");
        }

        // 2. Fetch User Pattern States
        List<UserPatternState> patternStates = userPatternStateRepository.findByUserIdWithPattern(userId);
        Map<Long, UserPatternState> stateByPattern = patternStates.stream()
                .filter(ps -> ps.getPattern() != null && ps.getPattern().getName() != null)
                .collect(Collectors.toMap(ps -> ps.getPattern().getId(), ps -> ps, (a, b) -> a));

        // 3. Load the distinct selected catalog with its actual problem time estimates.
        List<UserSheet> userSheets = userSheetRepository.findByUserIdWithSheet(userId);
        List<Long> sheetIds = userSheets.stream().map(us -> us.getSheet().getId()).toList();
        if (sheetIds.isEmpty()) {
            return unavailable(interview, today, daysRemaining, settings, ForecastStatus.ONBOARDING_REQUIRED,
                    "Select at least one problem sheet to generate a forecast.");
        }
        Map<Long, com.preptrack.problem.entity.Problem> problems = new LinkedHashMap<>();
        sheetProblemRepository.findBySheetIdInOrderByDisplayOrderAsc(sheetIds)
                .forEach(sp -> problems.putIfAbsent(sp.getProblem().getId(), sp.getProblem()));
        Set<Long> problemIds = problems.keySet();

        // 4. Identify which problems user already solved
        List<UserProblemStatus> userStatuses = userProblemStatusRepository.findByUserId(userId);
        Set<Long> solvedProblemIds = userStatuses.stream()
                .filter(s -> s.getStatus() == ProblemStatus.SOLVED_INDEPENDENTLY || s.getStatus() == ProblemStatus.SOLVED_WITH_HELP)
                .map(s -> s.getProblem().getId())
                .collect(Collectors.toSet());

        // 5. Group available unsolved problems by pattern
        Map<Long, Integer> availableProblemsPerPattern = new HashMap<>();
        Map<Long, Pattern> allTargetPatterns = new TreeMap<>();
        Map<Long, IntSummaryStatistics> problemMinutesPerPattern = new HashMap<>();
        Set<Long> linkedProblems = new HashSet<>();

        if (!problemIds.isEmpty()) {
            List<ProblemPattern> pps = problemPatternRepository.findByProblemIdInWithPattern(new ArrayList<>(problemIds));
            for (ProblemPattern pp : pps) {
                if (pp.getPattern() != null) {
                    Long patternId = pp.getPattern().getId();
                    Long problemId = pp.getProblem().getId();
                    allTargetPatterns.putIfAbsent(patternId, pp.getPattern());
                    linkedProblems.add(problemId);
                    if (!solvedProblemIds.contains(pp.getProblem().getId())) {
                        availableProblemsPerPattern.merge(patternId, 1, Integer::sum);
                        Integer minutes = problems.get(problemId).getEstimatedMinutes();
                        problemMinutesPerPattern.computeIfAbsent(patternId, ignored -> new IntSummaryStatistics())
                                .accept(minutes != null && minutes > 0 ? minutes : engine.getDefaultProblemMinutes());
                    }
                }
            }
        }

        if (allTargetPatterns.isEmpty()) {
            return unavailable(interview, today, daysRemaining, settings, ForecastStatus.INSUFFICIENT_DATA,
                    "Your selected sheets have no problems with pattern links. Select another sheet or update the catalog.");
        }

        // 6. Build pattern snapshots for simulator
        List<ForwardSimulator.PatternSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<Long, Pattern> entry : allTargetPatterns.entrySet()) {
            String pName = entry.getValue().getName();
            UserPatternState state = stateByPattern.get(entry.getKey());

            double curSkill = state != null && state.getSkill() != null ? state.getSkill() : 0.0;
            double curStability = state != null && state.getStability() != null ? state.getStability() : engine.getInitialStability();
            Integer daysSincePractice = null;
            if (state != null && state.getLastPracticedAt() != null) {
                daysSincePractice = (int) ChronoUnit.DAYS.between(timeService.getPracticeDate(state.getLastPracticedAt(), settings), today);
                if (daysSincePractice < 0) daysSincePractice = 0;
            }

            int available = availableProblemsPerPattern.getOrDefault(entry.getKey(), 0);
            double companyRelevance = interviewRelevanceCalculator.calculateCompanyPatternRelevance(companyName, entry.getValue());
            IntSummaryStatistics minutes = problemMinutesPerPattern.get(entry.getKey());

            snapshots.add(ForwardSimulator.PatternSnapshot.builder()
                    .patternName(pName)
                    .currentSkill(curSkill)
                    .currentStability(curStability)
                    .daysSinceLastPracticed(daysSincePractice)
                    .availableProblems(available)
                    .companyRelevance(companyRelevance)
                    .estimatedProblemMinutes(minutes == null ? engine.getDefaultProblemMinutes() : (int) Math.ceil(minutes.getAverage()))
                    .build());
        }

        // 7. Run forward simulation
        ReadinessForecastDto result = forwardSimulator.simulate(
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
        result.setAsOfDate(today);
        result.setProjectedThroughDate(today.plusDays(result.getSimulationDays()));
        result.setStatusMessage("Approximate readiness based on your selected sheets and current study settings.");
        List<String> limitations = new ArrayList<>(result.getLimitations());
        if (linkedProblems.size() < problems.size()) {
            limitations.add((problems.size() - linkedProblems.size()) + " selected problems have no pattern links and are excluded.");
        }
        if (Boolean.TRUE.equals(result.getHorizonCapped())) {
            limitations.add("The simulation stops after " + result.getSimulationDays() + " days; the projection is not for the full interview horizon.");
        }
        result.setLimitations(List.copyOf(limitations));
        return result;
    }

    private ReadinessForecastDto unavailable(Interview interview, LocalDate today, long daysRemaining,
                                             UserSettings settings, ForecastStatus status, String message) {
        return ReadinessForecastDto.builder()
                .interviewId(interview.getId()).interviewDate(interview.getInterviewDate())
                .companyName(interview.getApplication().getCompanyName()).daysRemaining(daysRemaining)
                .status(status).statusMessage(message).overallReadiness(null)
                .dailyBudgetMinutes(settings.getDailyTimeBudgetMin() != null ? settings.getDailyTimeBudgetMin() : engine.getDefaultBudgetMinutes())
                .maxNewProblemsDay(settings.getMaxNewProblemsDay() != null ? settings.getMaxNewProblemsDay() : engine.getDefaultMaxNewProblems())
                .maxRevisionsDay(settings.getMaxRevisionsDay() != null ? settings.getMaxRevisionsDay() : engine.getDefaultMaxRevisions())
                .targetSkillLevel(settings.getTargetSkillLevel() != null ? settings.getTargetSkillLevel() : engine.getDefaultTargetSkill())
                .simulationDays(0).asOfDate(today).projectedThroughDate(today).horizonCapped(false)
                .model(ForwardSimulator.MODEL).modelVersion(ForwardSimulator.MODEL_VERSION)
                .policyVersion(engine.getPolicyVersion())
                .limitations(ForwardSimulator.LIMITATIONS).patternProjections(List.of()).recommendations(List.of(message)).build();
    }
}
