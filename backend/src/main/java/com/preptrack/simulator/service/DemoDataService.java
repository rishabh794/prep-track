package com.preptrack.simulator.service;

import com.preptrack.application.entity.*;
import com.preptrack.application.repository.*;
import com.preptrack.attempt.entity.*;
import com.preptrack.attempt.repository.*;
import com.preptrack.auth.entity.*;
import com.preptrack.auth.repository.*;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.entity.*;
import com.preptrack.planner.repository.DailyPlanRepository;
import com.preptrack.revision.entity.*;
import com.preptrack.revision.repository.RevisionCardRepository;
import com.preptrack.sheet.entity.UserSheet;
import com.preptrack.sheet.repository.*;
import com.preptrack.simulator.dto.SimulationConfig;
import com.preptrack.simulator.engine.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DemoDataService {
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder passwords;
    private final SimulationCatalogService catalogs;
    private final PrepTrackStrategy strategy;
    private final SheetRepository sheets;
    private final UserSheetRepository userSheets;
    private final UserProblemAttemptRepository attempts;
    private final UserProblemStatusRepository statuses;
    private final UserPatternStateRepository states;
    private final RevisionCardRepository revisions;
    private final DailyPlanRepository plans;
    private final ApplicationRepository applications;
    private final InterviewRepository interviews;
    private final EntityManager entityManager;
    private final Clock clock;

    /** Explicit opt-in only; never overwrites an existing account. All history is marked synthetic. */
    @Transactional
    public Long generate(String email, String password) {
        email = email == null ? null : email.toLowerCase(Locale.ROOT).trim();
        if (email == null || !email.contains("@") || password == null || password.length() < 8) {
            throw new BadRequestException("Set DEMO_EMAIL and DEMO_PASSWORD (at least eight characters)");
        }
        if (users.findByEmail(email).isPresent()) throw new BadRequestException("Demo email already exists; choose a new email. Existing history was preserved.");
        var catalog = catalogs.load(null);
        var user = users.save(User.builder().email(email).passwordHash(passwords.encode(password)).displayName("PrepTrack Demo").build());
        settings.save(UserSettings.builder().user(user).onboardingComplete(true).timezone("Asia/Kolkata").build());
        var sheet = sheets.findByName(catalog.sheetName()).orElseThrow();
        userSheets.save(UserSheet.builder().user(user).sheet(sheet).build());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        LocalDate firstDay = today.minusDays(10);
        var config = new SimulationConfig(10, 1, 90, 3, 10, 0.8, 42L, sheet.getId());
        var learner = new SyntheticLearner(catalog, new ExponentialForgetting(), config.seed());
        Map<Long, UserProblemStatus> statusMap = new HashMap<>();
        Map<Long, LocalDateTime> attemptDates = new LinkedHashMap<>();
        Map<Long, LocalDateTime> planDates = new LinkedHashMap<>();
        for (int day = 1; day <= 10; day++) {
            LocalDate date = firstDay.plusDays(day - 1);
            var items = strategy.generatePlan(catalog, learner, config, day);
            var plan = DailyPlan.builder().user(user).planDate(date).budgetMinutes(90)
                    .totalEstimatedMin(items.stream().mapToInt(SyntheticLearner.StudyItem::minutes).sum()).totalValue(0.0).build();
            int order = 0;
            int elapsed = 0;
            for (var item : items) {
                PlanItemType type = !item.revision() ? PlanItemType.NEW_PROBLEM
                        : learner.cards().get(item.problem().getId()).outcome == AttemptOutcome.COULD_NOT_SOLVE
                        ? PlanItemType.RETRY : PlanItemType.REVISION;
                var observation = learner.study(item, day);
                elapsed += item.minutes();
                LocalDateTime timestamp = date.atTime(18, 0).plusMinutes(elapsed);
                FailureReason failure = observation.followUp() == null ? null : switch (observation.followUp()) {
                    case EASIER_PREREQUISITE -> FailureReason.PATTERN_GAP;
                    case CODE_FROM_SCRATCH -> FailureReason.IMPLEMENTATION_GAP;
                    case EDGE_CASE_LIST -> FailureReason.EDGE_CASE_MISS;
                    case TIMED_RETRY -> FailureReason.TOO_SLOW;
                };
                var attempt = attempts.save(UserProblemAttempt.builder().user(user).problem(item.problem())
                        .outcome(observation.outcome()).failureReason(failure).followUpType(observation.followUp())
                        .isRevision(item.revision()).timeTakenSec(item.minutes() * 60)
                        .hintsUsed(observation.outcome() == AttemptOutcome.SOLVED_WITH_HELP ? 1 : 0)
                        .notes("Synthetic demo history; generated offline without AI calls.").build());
                attemptDates.put(attempt.getId(), timestamp);
                var status = statusMap.computeIfAbsent(item.problem().getId(), id -> UserProblemStatus.builder().user(user).problem(item.problem()).build());
                status.setTotalAttempts(status.getTotalAttempts() + 1);
                status.setLastOutcome(observation.outcome());
                status.setLastFailureReason(failure);
                status.setLastAttemptedAt(timestamp);
                status.setStatus(switch (observation.outcome()) {
                    case SOLVED_INDEPENDENTLY -> ProblemStatus.SOLVED_INDEPENDENTLY;
                    case SOLVED_WITH_HELP -> ProblemStatus.SOLVED_WITH_HELP;
                    case COULD_NOT_SOLVE -> ProblemStatus.ATTEMPTED;
                });
                if (observation.outcome() == AttemptOutcome.SOLVED_INDEPENDENTLY) status.setIndependentSolves(status.getIndependentSolves() + 1);
                if (observation.outcome() != AttemptOutcome.COULD_NOT_SOLVE && status.getFirstSolvedAt() == null) status.setFirstSolvedAt(timestamp);
                status.setInsightNote("Synthetic demo note: explain the pattern and check boundary cases.");
                plan.getItems().add(DailyPlanItem.builder().dailyPlan(plan).problem(item.problem()).displayOrder(order++)
                        .itemType(type).estimatedMinutes(item.minutes()).followUpType(observation.followUp())
                        .valueScore(0.0).reasonSummary("Synthetic demo history").completed(true).completedAt(timestamp).attempt(attempt).build());
            }
            plans.save(plan);
            planDates.put(plan.getId(), date.atTime(18, 0));
        }
        statuses.saveAll(statusMap.values());
        for (var state : learner.estimates().values()) {
            state.setUser(user);
            if (state.getLastPracticedAt() != null) {
                long day = ChronoUnit.DAYS.between(SyntheticLearner.EPOCH, state.getLastPracticedAt().toLocalDate());
                state.setLastPracticedAt(firstDay.plusDays(day - 1).atTime(19, 30));
                states.save(state);
            }
        }
        for (var problem : catalog.problems()) {
            var card = learner.cards().get(problem.getId());
            if (card == null) continue;
            revisions.save(RevisionCard.builder().user(user).problem(problem).reviewCount(card.reviews)
                    .lastOutcome(card.outcome).requiredFollowUp(card.followUp).lastReviewedAt(statusMap.get(problem.getId()).getLastAttemptedAt())
                    .nextReviewDate(firstDay.plusDays(card.dueDay - 1)).build());
        }
        var application = applications.save(Application.builder().user(user).companyName("Google").role("Software Engineer")
                .status(ApplicationStatus.INTERVIEW).appliedDate(today.minusDays(12)).notes("Synthetic demo application").build());
        interviews.save(Interview.builder().application(application).interviewDate(today.plusDays(12))
                .roundName("Coding round").notes("Synthetic demo interview").build());
        // CreationTimestamp supplies current timestamps on inserts; explicitly backdate generated history after flush.
        entityManager.flush();
        attemptDates.forEach((id, date) -> entityManager.createNativeQuery("UPDATE user_problem_attempts SET attempted_at = :date WHERE id = :id")
                .setParameter("date", date).setParameter("id", id).executeUpdate());
        planDates.forEach((id, date) -> entityManager.createNativeQuery("UPDATE daily_plans SET generated_at = :date WHERE id = :id")
                .setParameter("date", date).setParameter("id", id).executeUpdate());
        entityManager.clear();
        return user.getId();
    }
}
