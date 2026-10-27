package com.preptrack.audit;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.preptrack.ai.repository.RecallCheckRepository;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.problem.repository.ProblemRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit exploratory HTTP audit. Run with -Dtest=ThirtyDayBackendAudit; normal test runs skip it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:thirty_day_http_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.open-in-view=false", "gemini.api-key=", "spring.devtools.restart.enabled=false"
})
@ActiveProfiles("test")
@Import(ThirtyDayBackendAudit.AuditConfiguration.class)
class ThirtyDayBackendAudit {
    static final ZoneId HOST_ZONE = ZoneId.of("Asia/Kolkata");
    static final LocalDate START = LocalDate.of(2026, 9, 28);
    static final String PASSWORD = "Audit-only-password-42!";
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired AuditClock clock;
    @Autowired ProblemRepository problems;
    @Autowired ProblemPatternRepository links;
    @Autowired UserProblemAttemptRepository attempts;
    @Autowired RecallCheckRepository recallChecks;
    @Autowired UserSettingsRepository settings;
    @Autowired com.preptrack.pattern.repository.UserPatternStateRepository patternStates;
    Path outputDirectory = Path.of("target", "user-journey-audit");
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    final List<Map<String, Object>> trace = new ArrayList<>();
    final List<Map<String, Object>> days = new ArrayList<>();
    final Map<String, Map<String, Object>> findings = new LinkedHashMap<>();
    final Map<String, Integer> exercised = new TreeMap<>();
    final Map<Long, List<Link>> problemLinks = new HashMap<>();
    final Random outcomes = new Random(20261027);
    JsonNode policy;
    int checks;
    int failureIndex;
    long neetcode;
    long blind;
    long rainwater;
    long prerequisite;
    long twoSum;
    long duplicate;
    long anagram;
    record Link(long patternId, double weight) {}
    record Reply(int status, JsonNode body, int traceId) {}
    static class UserSession {
        final String label;
        final String email;
        String token;
        long userId;
        UserSession(String label) { this.label = label; this.email = label + "@audit.example.test"; }
    }

    @Test
    void exerciseThirtyVirtualDaysAndBoundaryCasesThroughRealHttp() throws Exception {
        clock.set(START.atTime(9, 0).atZone(HOST_ZONE).toInstant());
        try {
            for (var link : links.findAllWithPatternAndProblem()) problemLinks.computeIfAbsent(link.getProblem().getId(), ignored -> new ArrayList<>())
                    .add(new Link(link.getPattern().getId(), link.getWeight()));
            var catalog = call(null, "GET", "/api/sheets/available", null);
            assertEquals(200, catalog.status());
            neetcode = named(catalog.body(), "name", "NeetCode 150").path("id").asLong();
            blind = named(catalog.body(), "name", "Blind 75").path("id").asLong();
            twoSum = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
            duplicate = problems.findByCanonicalTitle("Contains Duplicate").orElseThrow().getId();
            anagram = problems.findByCanonicalTitle("Valid Anagram").orElseThrow().getId();
            rainwater = problems.findByCanonicalTitle("Trapping Rain Water").orElseThrow().getId();
            prerequisite = problems.findByCanonicalTitle("Next Greater Element I").orElseThrow().getId();
            var anonymous = call(null, "GET", "/api/plan/today", null);
            verify(anonymous.status() == 401, "P1", "AUTHENTICATION", "Private study data requires authentication", anonymous, null);
            UserSession learner = register("month-learner");
            UserSession outsider = register("other-user");
            policy = call(learner, "GET", "/api/planner/policy", null).body();
            call(learner, "GET", "/api/companies", null);
            call(learner, "GET", "/api/catalog/health", null);
            onboard(learner, List.of(neetcode, blind), 90, 3, 10);
            long interview = addInterview(learner, "Google", START.plusDays(21));
            var privateForecast = call(outsider, "GET", "/api/forecast/interviews/" + interview, null);
            verify(privateForecast.status() == 404 || privateForecast.status() == 403, "P1", "FORECAST_OWNERSHIP",
                    "Another user must not access the learner's forecast", privateForecast, null);
            int budget = 90, maxNew = 3, maxReviews = 10;
            for (int day = 1; day <= 30; day++) {
                clock.set(START.plusDays(day - 1).atTime(9, 0).atZone(HOST_ZONE).toInstant());
                login(learner);
                Reply forecast = call(learner, "GET", "/api/forecast/interviews/" + interview, null);
                Reply initial = call(learner, "GET", "/api/plan/today", null);
                assertEquals(200, initial.status(), "Daily plan must remain usable for all 30 days");
                Reply reread = call(learner, "GET", "/api/plan/today", null);
                verify(initial.body().path("planId").equals(reread.body().path("planId")), "P1", "PLAN_NOT_PERSISTED", "Reading a plan must retain its identity", initial, reread);
                checkPlan(initial, budget, maxNew, maxReviews);
                if (day == 4) {
                    var first = planItems(initial.body()).getFirst();
                    call(learner, "PATCH", "/api/plan/items/" + first.path("planItemId").asLong() + "/skip", Map.of("reason", "Short on time today"));
                    initial = call(learner, "POST", "/api/plan/today/regenerate", Map.of());
                    checkPlan(initial, budget, maxNew, maxReviews);
                }
                if (day == 10 || day == 19) {
                    budget = day == 10 ? 120 : 60;
                    maxNew = day == 10 ? 4 : 2;
                    maxReviews = day == 10 ? 6 : 4;
                    call(learner, "PUT", "/api/onboarding/settings", Map.of("dailyTimeBudgetMin", budget, "maxNewProblemsDay", maxNew, "maxRevisionsDay", maxReviews));
                    initial = call(learner, "POST", "/api/plan/today/regenerate", Map.of());
                    verify(initial.body().path("dailyBudgetMinutes").asInt() == budget, "P1", "REGENERATED_BUDGET_STALE",
                            "A regenerated plan must report its effective current budget (expected " + budget + ")", initial, null);
                    checkPlan(initial, budget, maxNew, maxReviews);
                }
                boolean missed = Set.of(7, 15, 24).contains(day);
                int completed = 0;
                if (!missed) {
                    for (JsonNode item : planItems(initial.body())) {
                        if (item.path("completed").asBoolean() || item.path("skipped").asBoolean()) continue;
                        study(learner, item, day);
                        completed++;
                    }
                }
                Reply after = call(learner, "GET", "/api/plan/today", null);
                verify(after.body().path("totalCount").equals(initial.body().path("totalCount")), "P1", "PLAN_TREADMILL", "Completing work must not refill the daily plan", initial, after);
                verify(after.body().path("completedCount").asInt() == completed, "P1", "PLAN_COMPLETION_MISMATCH", "Completed count must match submitted work", after, null);
                Reply states = call(learner, "GET", "/api/user/pattern-states", null);
                checkStateBounds(states);
                Reply progress = call(learner, "GET", "/api/user/sheets", null);
                Reply due = call(learner, "GET", "/api/revisions/due", null);
                var metrics = new LinkedHashMap<String, Object>();
                metrics.put("day", day); metrics.put("date", START.plusDays(day - 1).toString());
                metrics.put("studiedItems", completed); metrics.put("missedStudy", missed);
                metrics.put("configuredBudget", budget); metrics.put("planBudget", after.body().path("dailyBudgetMinutes").asInt());
                metrics.put("planMinutes", after.body().path("totalEstimatedMinutes").asInt());
                metrics.put("sheetProgress", progress.body()); metrics.put("dueRevisionCount", due.body().isArray() ? due.body().size() : -1);
                metrics.put("averageSkill", average(states.body(), "skill")); metrics.put("averageRetention", average(states.body(), "retention"));
                metrics.put("averageStability", average(states.body(), "stability"));
                metrics.put("forecast", forecast.body()); metrics.put("patternStates", states.body());
                metrics.put("finalPlan", after.body()); days.add(metrics);
                if (day == 22) {
                    var extraction = call(learner, "POST", "/api/interviews/" + interview + "/feedback", Map.of("rawFeedback", "I struggled with DP Memo on Subsets but solved a hash map question. The overall result was partial."));
                    if (extraction.status() >= 500) finding("P1", "INTERVIEW_FEEDBACK_HTTP_500", "Submitting interview feedback returns HTTP 500", "The interview ownership check dereferences a detached lazy application", extraction);
                    long weak = problemLinks.get(twoSum).getFirst().patternId();
                    var confirmation = Map.of("outcome", "PARTIAL", "topics", List.of(Map.of("patternId", weak, "wasWeak", true)));
                    call(learner, "POST", "/api/interviews/" + interview + "/confirm-topics", confirmation);
                    call(learner, "POST", "/api/interviews/" + interview + "/confirm-topics", confirmation);
                    interview = addInterview(learner, "Meta", START.plusDays(45));
                }
                System.out.println("AUDIT day " + day + ": items=" + completed + ", skill=" + metrics.get("averageSkill") + ", retention=" + metrics.get("averageRetention"));
            }
            Reply history = call(learner, "GET", "/api/attempts?size=100", null);
            int studied = days.stream().mapToInt(day -> (Integer) day.get("studiedItems")).sum();
            verify(history.status() == 200 && history.body().path("totalElements").asInt() == studied,
                    "P1", "INCOMPLETE_ACTIVITY_HISTORY", "History contains every coding and recall event in the month", history, null);
            boundaryChecks(outsider);
            assertEquals(30, days.size());
            requireRequestedFixes();
        } finally {
            export();
        }
    }

    @Test
    void supplementalFlows() throws Exception {
        outputDirectory = outputDirectory.resolve("supplemental");
        clock.set(START.atTime(9, 0).atZone(HOST_ZONE).toInstant());
        try {
            var owner = register("supplemental-owner");
            var outsider = register("supplemental-outsider");
            Reply created = call(owner, "POST", "/api/applications", Map.of("companyName", "Google", "role", "Backend Engineer", "status", "APPLIED"));
            assertEquals(201, created.status());
            long application = created.body().path("id").asLong();
            Reply fetched = call(owner, "GET", "/api/applications/" + application, null);
            verify(fetched.status() == 200, "P1", "APPLICATION_READ", "An application can be read after creation", fetched, null);
            call(owner, "GET", "/api/applications", null);
            Reply blocked = call(outsider, "GET", "/api/applications/" + application, null);
            verify(blocked.status() == 404 || blocked.status() == 403, "P1", "APPLICATION_OWNERSHIP", "Applications remain private", blocked, null);
            Reply updated = call(owner, "PUT", "/api/applications/" + application, Map.of("companyName", "Google", "notes", "Recruiter replied", "status", "OA"));
            verify(updated.status() == 200 && updated.body().path("status").asText().equals("OA"), "P1", "APPLICATION_UPDATE", "Application status updates persist", updated, null);
            Reply interview = call(owner, "POST", "/api/applications/" + application + "/interviews", Map.of("interviewDate", START.plusDays(12).toString(), "roundName", "Technical"));
            assertEquals(201, interview.status());
            long id = interview.body().path("id").asLong();
            call(owner, "GET", "/api/applications/" + application + "/interviews", null);
            call(owner, "GET", "/api/interviews", null);
            call(owner, "GET", "/api/interviews/upcoming", null);
            Reply noSheet = call(owner, "GET", "/api/forecast/interviews/" + id, null);
            verify(noSheet.status() == 200 && noSheet.body().path("status").asText().equals("ONBOARDING_REQUIRED"), "P2", "EMPTY_FORECAST_STATE", "A forecast explains that no sheets are selected", noSheet, null);
            Reply moved = call(owner, "PUT", "/api/interviews/" + id, Map.of("interviewDate", START.plusDays(14).toString(), "notes", "Rescheduled"));
            verify(moved.status() == 200 && moved.body().path("interviewDate").asText().equals(START.plusDays(14).toString()), "P1", "INTERVIEW_UPDATE", "Rescheduling changes the interview date", moved, null);
            Reply wrongOwner = call(outsider, "DELETE", "/api/interviews/" + id, null);
            verify(wrongOwner.status() == 404 || wrongOwner.status() == 403, "P1", "INTERVIEW_OWNERSHIP", "Another user cannot delete an interview", wrongOwner, null);
            Reply feedback = call(owner, "POST", "/api/interviews/" + id + "/feedback", Map.of("rawFeedback", "I struggled with dynamic programming and solved a hash map problem."));
            if (feedback.status() >= 500) finding("P1", "INTERVIEW_FEEDBACK_HTTP_500", "Submitting interview feedback returns HTTP 500", "The interview ownership check dereferences a detached lazy application", feedback);

            long pattern = links.findAllWithPatternAndProblem().getFirst().getPattern().getId();
            var repeatedTopics = List.of(Map.of("patternId", pattern, "wasWeak", true), Map.of("patternId", pattern, "wasWeak", true));
            Reply duplicates = call(owner, "POST", "/api/interviews/" + id + "/confirm-topics", Map.of("outcome", "PARTIAL", "topics", repeatedTopics));
            if (duplicates.status() == 200 && duplicates.body().path("topics").size() == 2) {
                double boost = patternStates.findByUserIdAndPatternId(owner.userId, pattern).orElseThrow().getInterviewBoost();
                finding("P2", "DUPLICATE_WEAK_TOPICS", "Duplicate interview topics apply the weakness boost twice", "One confirmation contains the same pattern twice; stored interviewBoost=" + boost, duplicates);
            }
            Reply malformed = call(owner, "POST", "/api/interviews/" + id + "/confirm-topics", Map.of("outcome", "PARTIAL", "topics", List.of(Map.of("wasWeak", true))));
            if (malformed.status() >= 500) finding("P2", "MALFORMED_TOPICS_HTTP_500", "A missing pattern ID in confirmed topics causes HTTP 500", "Invalid nested input should return 400 without changing feedback", malformed);

            Reply second = call(owner, "POST", "/api/applications/" + application + "/interviews", Map.of("interviewDate", START.plusDays(16).toString()));
            long secondId = second.body().path("id").asLong();
            long otherPattern = links.findAllWithPatternAndProblem().stream().map(link -> link.getPattern().getId()).filter(p -> p != pattern).findFirst().orElseThrow();
            var unfinished = Map.of("outcome", "UPCOMING", "topics", List.of(Map.of("patternId", otherPattern, "wasWeak", true)));
            Reply firstConfirm = call(owner, "POST", "/api/interviews/" + secondId + "/confirm-topics", unfinished);
            double beforeBoost = patternStates.findByUserIdAndPatternId(owner.userId, otherPattern).map(state -> state.getInterviewBoost()).orElse(0.0);
            Reply secondConfirm = call(owner, "POST", "/api/interviews/" + secondId + "/confirm-topics", unfinished);
            double afterBoost = patternStates.findByUserIdAndPatternId(owner.userId, otherPattern).map(state -> state.getInterviewBoost()).orElse(0.0);
            if (secondConfirm.status() == 200 && afterBoost > beforeBoost) finding("P2", "UNFINISHED_TOPIC_REPLAY", "Confirming topics with an upcoming outcome can repeatedly boost a pattern", "The same interview changes boost from " + beforeBoost + " to " + afterBoost, firstConfirm, secondConfirm);
            call(owner, "GET", "/api/interviews/" + secondId + "/topics", null);
            Reply deleted = call(owner, "DELETE", "/api/interviews/" + secondId, null);
            verify(deleted.status() == 204, "P1", "INTERVIEW_DELETE", "An interview with topics can be deleted", deleted, null);
            Reply applicationDeleted = call(owner, "DELETE", "/api/applications/" + application, null);
            verify(applicationDeleted.status() == 204, "P1", "APPLICATION_DELETE", "An application and its interviews can be deleted", applicationDeleted, null);
            verify(call(owner, "GET", "/api/applications/" + application, null).status() == 404, "P1", "DELETED_APPLICATION_READ", "Deleted applications are unavailable", applicationDeleted, null);
            Reply history = call(owner, "GET", "/api/attempts", null);
            if (history.status() == 405 || history.status() == 500) finding("GAP", "NO_ATTEMPT_HISTORY_API", "There is no user attempt-history endpoint", "The route only supports POST; no alternate history route exists in the controller inventory", history);
            Reply unknown = call(owner, "GET", "/api/this-route-does-not-exist", null);
            if (history.status() == 500 || unknown.status() == 500) finding("P2", "ROUTING_ERRORS_HTTP_500", "Unsupported methods and unknown API paths return server errors", "Expected 405 for GET /api/attempts and 404 for an unknown API path", history, unknown);
            Reply invalidDate = call(owner, "GET", "/api/plan/not-a-date", null);
            verify(invalidDate.status() == 400, "P2", "INVALID_DATE_VALIDATION", "Malformed plan dates return 400", invalidDate, null);
            Reply catalog = call(owner, "GET", "/api/sheets/available", null);
            long sheet = named(catalog.body(), "name", "NeetCode 150").path("id").asLong();
            onboard(owner, List.of(sheet), 90, 3, 10);
            long problem = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
            for (String path : List.of("/api/health", "/api/patterns", "/api/companies/google", "/api/companies/resolve?name=Google",
                    "/api/companies/resolve?name=UnknownCompany", "/api/user/sheets/" + sheet + "/problems",
                    "/api/problems/" + problem, "/api/problems/" + problem + "/sources", "/api/problems/" + problem + "/relations")) {
                Reply read = call(owner, "GET", path, null);
                verify(read.status() == 200, "P2", "CORE_READ_ENDPOINT", "Core user read endpoints succeed: " + path, read, null);
            }
            Reply documentedHistory = call(owner, "GET", "/api/problems/" + problem + "/attempts", null);
            if (documentedHistory.status() == 404 || documentedHistory.status() == 500) finding("GAP", "NO_ATTEMPT_HISTORY_API", "There is no user attempt-history endpoint", "The project document's per-problem history route is absent too", documentedHistory);
            long pastId = addInterview(owner, "Google", START.minusDays(1));
            Reply past = call(owner, "GET", "/api/forecast/interviews/" + pastId, null);
            verify(past.status() == 200 && past.body().path("status").asText().equals("INTERVIEW_PASSED") && past.body().path("overallReadiness").isNull(), "P2", "PAST_FORECAST_STATE", "Past interviews return an unavailable forecast without invented readiness", past, null);
            long futureId = addInterview(owner, "Meta", START.plusDays(800));
            Reply capped = call(owner, "GET", "/api/forecast/interviews/" + futureId, null);
            verify(capped.status() == 200 && capped.body().path("horizonCapped").asBoolean() && capped.body().path("projectedThroughDate").asText().compareTo(capped.body().path("interviewDate").asText()) < 0,
                    "P2", "CAPPED_FORECAST_STATE", "Distant forecasts disclose the actual projected-through date", capped, null);
            requireRequestedFixes();
        } finally { export(); }
    }

    private void study(UserSession user, JsonNode item, int day) throws Exception {
        long problemId = item.path("problemId").asLong();
        Reply before = call(user, "GET", "/api/user/pattern-states", null);
        boolean recallOnly = item.path("auditType").asText().equals("REVISION") && item.path("followUpType").isNull();
        double rate = day <= 7 ? 0.35 : day <= 14 ? 0.55 : day <= 21 ? 0.75 : 0.85;
        double draw = outcomes.nextDouble();
        String outcome = draw < rate ? "SOLVED_INDEPENDENTLY" : draw < Math.min(0.95, rate + 0.20) ? "SOLVED_WITH_HELP" : "COULD_NOT_SOLVE";
        Reply submitted;
        if (recallOnly) {
            String explanation = outcome.equals("SOLVED_INDEPENDENTLY") ? problems.findById(problemId).orElseThrow().getReferenceApproach()
                    : outcome.equals("SOLVED_WITH_HELP") ? "I recall the main data structure, but not the update order or boundary checks." : "I don't know";
            Reply due = call(user, "GET", "/api/revisions/due", null);
            JsonNode card = find(due.body(), "problemId", problemId);
            String path = card == null ? "/api/revisions/problem/" + problemId : "/api/revisions/" + card.path("id").asLong();
            Reply grading = call(user, "POST", path + "/recall", Map.of("userApproach", explanation));
            if (grading.status() >= 500) {
                finding("P1", "CARD_RECALL_HTTP_500", "Recall by revision-card ID returns HTTP 500", "The normal card recall route failed; the by-problem route was used to continue the month", grading);
                grading = call(user, "POST", "/api/revisions/problem/" + problemId + "/recall", Map.of("userApproach", explanation));
            }
            if (grading.status() != 200) return;
            var review = new LinkedHashMap<String, Object>();
            review.put("recallCheckId", grading.body().path("recallCheckId").asLong());
            if (day % 6 == 0) review.put("userOverride", "PARTIAL");
            submitted = call(user, "POST", path + "/review", review);
            outcome = submitted.body().path("lastOutcome").asText(outcome);
        } else if (item.path("auditType").asText().equals("REVISION")) {
            submitted = call(user, "POST", "/api/revisions/problem/" + problemId + "/review", Map.of("outcome", outcome));
        } else {
            submitted = attempt(user, problemId, outcome, item.path("estimatedMinutes").asInt() * 60,
                    outcome.equals("SOLVED_INDEPENDENTLY") ? 0 : 1, outcome.equals("SOLVED_INDEPENDENTLY") ? null : failureReason());
        }
        exercised.merge(outcome, 1, Integer::sum);
        Reply after = call(user, "GET", "/api/user/pattern-states", null);
        checkLearning(before, after, problemId, outcome, recallOnly, submitted);
    }

    private void boundaryChecks(UserSession outsider) throws Exception {
        clock.set(START.plusDays(35).atTime(9, 0).atZone(HOST_ZONE).toInstant());
        UserSession probe = register("boundary-user");
        onboard(probe, List.of(neetcode), 90, 3, 10);
        Reply negative = attempt(probe, twoSum, "SOLVED_INDEPENDENTLY", -120, -3, null);
        if (negative.status() == 200) finding("P2", "NEGATIVE_ATTEMPT_SIGNALS", "Negative time and hint counts are accepted", "timeTakenSec=-120 and hintsUsed=-3 were accepted as an independent solve", negative);
        Reply missingReason = attempt(probe, duplicate, "SOLVED_WITH_HELP", 600, 1, null);
        if (missingReason.status() == 200 && missingReason.body().path("revisionScheduled").path("followUpType").isNull()) {
            finding("P2", "FAILURE_REASON_OPTIONAL", "Assisted or failed attempts can omit their failure reason", "The response schedules no failure-specific follow-up when a reason is omitted", missingReason);
        }
        Reply contradictory = attempt(probe, anagram, "SOLVED_INDEPENDENTLY", 600, 4, null);
        if (contradictory.status() == 200) finding("P2", "CONTRADICTORY_SOLVE_SIGNALS", "Independent solves can include used hints", "An independent outcome with four hints still received independent learning credit", contradictory);
        Reply invalidOutcome = call(probe, "POST", "/api/revisions/problem/" + twoSum + "/review", Map.of("outcome", "NOT_A_REAL_OUTCOME"));
        if (invalidOutcome.status() == 200) finding("P2", "INVALID_REVIEW_BECOMES_FAILURE", "Invalid revision outcome silently becomes a failure", "Unknown outcome was accepted and converted to " + invalidOutcome.body().path("lastOutcome").asText(), invalidOutcome);
        Reply grade = call(probe, "POST", "/api/revisions/problem/" + twoSum + "/recall", Map.of("userApproach", problems.findById(twoSum).orElseThrow().getReferenceApproach()));
        if (grade.status() == 200) {
            var payload = Map.of("recallCheckId", grade.body().path("recallCheckId").asLong(), "userOverride", "PASS");
            call(probe, "POST", "/api/revisions/problem/" + twoSum + "/review", payload);
            Reply before = call(probe, "GET", "/api/user/pattern-states", null);
            Reply repeated = call(probe, "POST", "/api/revisions/problem/" + twoSum + "/review", payload);
            Reply after = call(probe, "GET", "/api/user/pattern-states", null);
            long patternId = problemLinks.get(twoSum).getFirst().patternId();
            if (repeated.status() == 200 && find(after.body(), "patternId", patternId).path("totalAttempts").asInt()
                    > find(before.body(), "patternId", patternId).path("totalAttempts").asInt()) {
                finding("P1", "RECALL_CHECK_REPLAY", "Reusing one confirmed recall check inflates learning state", "The same recallCheckId was accepted again and increased attempts/stability", grade, before, repeated, after);
            }
        }
        Reply noNew = call(probe, "PUT", "/api/onboarding/settings", Map.of("maxNewProblemsDay", 0));
        if (noNew.status() == 400) finding("GAP", "NO_REVISION_ONLY_MODE", "Users cannot choose a revision-only day", "The settings API rejects maxNewProblemsDay=0", noNew);
        call(probe, "PUT", "/api/onboarding/settings", Map.of("timezone", "America/Los_Angeles"));
        Reply profile = call(probe, "GET", "/api/auth/me", null);
        if (!profile.body().path("settings").has("timezone")) finding("GAP", "TIMEZONE_NOT_USER_CONFIGURABLE", "Timezone cannot be read or changed through user settings", "A timezone update is silently ignored and the settings response omits timezone", profile);
        Reply history = call(probe, "GET", "/api/attempts", null);
        if (history.status() == 405) finding("GAP", "NO_ATTEMPT_HISTORY_API", "There is no user attempt-history endpoint", "POST /api/attempts exists, but GET returns 405; problem detail also contains no attempt history", history);

        // A timezone boundary is tested without changing production time-handling code.
        long forecastId = addInterview(probe, "Google", START.plusDays(60));
        clock.set(Instant.parse("2026-10-27T23:45:00Z"));
        Reply plan = call(probe, "GET", "/api/plan/today", null);
        Reply forecast = call(probe, "GET", "/api/forecast/interviews/" + forecastId, null);
        if (!plan.body().path("date").equals(forecast.body().path("asOfDate"))) finding("P1", "DATE_BOUNDARY_DISAGREEMENT",
                "Today plan and forecast disagree on the user's date", "Host timezone Asia/Kolkata; stored user timezone UTC; plan="
                        + plan.body().path("date").asText() + ", forecast=" + forecast.body().path("asOfDate").asText(), plan, forecast);
        if (!planItems(plan.body()).isEmpty()) {
            long item = planItems(plan.body()).getFirst().path("planItemId").asLong();
            Reply forbidden = call(outsider, "PATCH", "/api/plan/items/" + item + "/skip", Map.of("reason", "Not my plan"));
            verify(forbidden.status() == 404 || forbidden.status() == 403, "P1", "PLAN_ITEM_OWNERSHIP", "Another user must not modify a plan item", forbidden, null);
            JsonNode chosen = planItems(plan.body()).getFirst();
            attempt(probe, chosen.path("problemId").asLong(), "SOLVED_INDEPENDENTLY", 900, 0, null);
            Reply skipCompleted = call(probe, "PATCH", "/api/plan/items/" + item + "/skip", Map.of("reason", "Already done"));
            JsonNode dual = planItems(skipCompleted.body()).stream().filter(i -> i.path("planItemId").asLong() == item).findFirst().orElse(null);
            if (dual != null && dual.path("completed").asBoolean() && dual.path("skipped").asBoolean()) finding("P2", "ITEM_BOTH_COMPLETE_AND_SKIPPED",
                    "A plan item can be both completed and skipped", "Skipping completed work succeeds and returns both flags true", skipCompleted);
        }
        checkPrerequisiteFlow();
        checkRecallFamilyRedundancy();
        Reply sim = call(probe, "POST", "/api/simulator/run", Map.of("days", 3, "learnerCount", 2));
        if (sim.status() == 201) {
            String id = sim.body().path("runId").asText();
            Reply saved = call(probe, "GET", "/api/simulator/results/" + id, null);
            verify(saved.status() == 200 && saved.body().path("results").equals(sim.body().path("results")), "P1", "SIMULATOR_PERSISTENCE", "Saved simulation results must reproduce the original response", sim, saved);
            Reply other = call(outsider, "GET", "/api/simulator/results/" + id, null);
            verify(other.status() == 404 || other.status() == 403, "P1", "SIMULATOR_OWNERSHIP", "Simulation results must remain private to their owner", other, null);
        }
    }

    private void checkPrerequisiteFlow() throws Exception {
        clock.set(START.plusDays(40).atTime(9, 0).atZone(HOST_ZONE).toInstant());
        var user = register("prerequisite-user");
        onboard(user, List.of(neetcode), 240, 1, 10);
        Reply failure = attempt(user, rainwater, "COULD_NOT_SOLVE", 2700, 0, "PATTERN_GAP");
        clock.set(START.plusDays(41).atTime(9, 0).atZone(HOST_ZONE).toInstant());
        Reply next = call(user, "GET", "/api/plan/today", null);
        var items = planItems(next.body());
        boolean retry = items.stream().anyMatch(i -> i.path("problemId").asLong() == rainwater);
        boolean warmup = items.stream().anyMatch(i -> i.path("problemId").asLong() == prerequisite);
        verify(warmup && !retry, "P1", "PREREQUISITE_ONLY_TEXT", "The actual prerequisite is scheduled before retrying the original failed problem", failure, next);
    }

    private void checkRecallFamilyRedundancy() throws Exception {
        clock.set(START.plusDays(43).atTime(9, 0).atZone(HOST_ZONE).toInstant());
        var user = register("pattern-family-user");
        onboard(user, List.of(neetcode), 90, 1, 10);
        for (long problem : List.of(twoSum, duplicate, anagram)) attempt(user, problem, "SOLVED_INDEPENDENTLY", 900, 0, null);
        clock.set(START.plusDays(50).atTime(9, 0).atZone(HOST_ZONE).toInstant());
        Reply plan = call(user, "GET", "/api/plan/today", null);
        var recalls = planItems(plan.body()).stream().filter(i -> i.path("auditType").asText().equals("REVISION") && i.path("followUpType").isNull()).toList();
        long samePattern = recalls.stream().filter(i -> Set.of(twoSum, duplicate, anagram).contains(i.path("problemId").asLong())).count();
        if (samePattern > 1) finding("P2", "REDUNDANT_PATTERN_RECALLS", "Multiple recall-only revisions cover the same pattern in one plan",
                samePattern + " hash-map recall cards are scheduled although the first successful recall refreshes that pattern's retention", plan);
        call(user, "GET", "/api/user/pattern-states", null);
    }

    private void checkLearning(Reply before, Reply after, long problemId, String outcome, boolean recallOnly, Reply operation) {
        for (Link link : problemLinks.getOrDefault(problemId, List.of())) {
            JsonNode old = find(before.body(), "patternId", link.patternId()), current = find(after.body(), "patternId", link.patternId());
            if (old == null || current == null || operation.status() != 200) continue;
            double oldSkill = old.path("skill").asDouble(), oldStability = old.path("stability").asDouble();
            double expectedSkill = oldSkill, expectedStability = oldStability;
            switch (outcome) {
                case "SOLVED_INDEPENDENTLY" -> {
                    if (!recallOnly) expectedSkill = Math.min(1, oldSkill + policy.path("independentSolveSkillGain").asDouble() * link.weight());
                    expectedStability = Math.min(policy.path("maxStability").asDouble(), oldStability * policy.path("independentSolveStabilityMultiplier").asDouble());
                }
                case "SOLVED_WITH_HELP" -> expectedSkill = Math.min(1, oldSkill + policy.path("withHelpSkillGain").asDouble() * link.weight());
                case "COULD_NOT_SOLVE" -> {
                    expectedSkill = Math.max(0, oldSkill - policy.path("couldNotSolveSkillDecay").asDouble() * link.weight());
                    expectedStability = Math.max(policy.path("initialStability").asDouble(), oldStability * policy.path("failureStabilityMultiplier").asDouble());
                }
            }
            verify(Math.abs(current.path("skill").asDouble() - expectedSkill) <= 0.011, "P1", "SKILL_UPDATE_MATH", "Skill update must follow the active policy", operation, after);
            verify(Math.abs(current.path("stability").asDouble() - expectedStability) <= 0.14, "P1", "STABILITY_UPDATE_MATH", "Stability update must follow the active policy (allowing response rounding)", operation, after);
        }
    }

    private void checkPlan(Reply response, int budget, int maxNew, int maxReview) {
        List<JsonNode> items = planItems(response.body());
        List<JsonNode> active = items.stream().filter(i -> !i.path("skipped").asBoolean()).toList();
        int minutes = active.stream().mapToInt(i -> i.path("estimatedMinutes").asInt()).sum();
        verify(minutes <= budget, "P1", "ACTIVE_PLAN_OVER_BUDGET", "Active planned work must fit the configured budget", response, null);
        verify(items.stream().map(i -> i.path("problemId").asLong()).distinct().count() == items.size(), "P1", "DUPLICATE_PROBLEM_IN_PLAN", "Each problem must appear once in a daily plan", response, null);
        long fresh = active.stream().filter(i -> i.path("auditType").asText().equals("NEW_PROBLEM")).count();
        verify(fresh <= maxNew && active.size() - fresh <= maxReview, "P1", "DAILY_SLOT_CAPS", "Daily acquisition and shared review/retry caps must hold", response, null);
        if (response.body().path("totalEstimatedMinutes").asInt() > budget && minutes <= budget) finding("P2", "SKIPPED_TIME_IN_PLAN_TOTAL",
                "Plan totals include skipped work after regeneration", "Reported minutes exceed the budget even though active work fits", response);
        for (JsonNode item : items) {
            JsonNode breakdown = item.path("valueBreakdown");
            if (breakdown.isNull() || breakdown.isMissingNode()) continue;
            double sum = 0;
            for (JsonNode part : breakdown.path("patterns")) sum += part.path("contribution").asDouble();
            double derived = sum * breakdown.path("retryBoost").asDouble(1) * breakdown.path("followUpMultiplier").asDouble(1) * breakdown.path("diversityPenalty").asDouble(1) * breakdown.path("overdueBonus").asDouble(1);
            double total = breakdown.path("totalValue").asDouble();
            if (Math.abs(derived - total) > 0.06) finding("P2", "VALUE_BREAKDOWN_INCOMPLETE", "Some plan values cannot be reconstructed from their returned breakdown",
                    "Visible terms produce " + round(derived) + " but totalValue=" + total + "; overdue bonuses are not exposed", response);
        }
        Map<Long, Long> selectedPrimary = active.stream().filter(i -> i.path("auditType").asText().equals("NEW_PROBLEM"))
                .collect(Collectors.groupingBy(this::primaryPattern, Collectors.counting()));
        for (JsonNode item : active) {
            if (item.path("auditType").asText().equals("NEW_PROBLEM") && item.path("valueBreakdown").path("diversityPenalty").asDouble(1) < 1
                    && selectedPrimary.getOrDefault(primaryPattern(item), 0L) == 1) finding("P2", "DIVERSITY_PENALTY_WITHOUT_REPEAT",
                    "A new problem is penalized for repeated pattern coverage when no matching new item is selected",
                    "Diversity penalty is applied to the candidate pool before final knapsack selection", response);
        }
    }

    private long primaryPattern(JsonNode item) {
        JsonNode best = null;
        for (JsonNode pattern : item.path("valueBreakdown").path("patterns")) if (best == null || pattern.path("linkWeight").asDouble() > best.path("linkWeight").asDouble()) best = pattern;
        return best == null ? -1 : best.path("patternId").asLong();
    }
    private void checkStateBounds(Reply states) {
        for (JsonNode state : states.body()) verify(state.path("skill").asDouble() >= 0 && state.path("skill").asDouble() <= 1
                && state.path("retention").asDouble() >= 0 && state.path("retention").asDouble() <= 1
                && state.path("stability").asDouble() >= policy.path("initialStability").asDouble()
                && state.path("stability").asDouble() <= policy.path("maxStability").asDouble(), "P1", "PATTERN_STATE_BOUNDS", "Pattern states must remain in physical bounds", states, null);
    }
    private String failureReason() { return List.of("PATTERN_GAP", "IMPLEMENTATION_GAP", "EDGE_CASE_MISS", "TOO_SLOW").get(failureIndex++ % 4); }
    private Reply attempt(UserSession user, long problem, String outcome, int seconds, int hints, String reason) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("problemId", problem); body.put("outcome", outcome); body.put("timeTakenSec", seconds); body.put("hintsUsed", hints);
        if (reason != null) body.put("failureReason", reason);
        body.put("insightNote", "Audit user note: explain the invariant and boundary conditions.");
        return call(user, "POST", "/api/attempts", body);
    }
    private UserSession register(String label) throws Exception {
        var user = new UserSession(label);
        Reply response = call(null, "POST", "/api/auth/register", Map.of("email", user.email, "password", PASSWORD, "displayName", label));
        assertEquals(201, response.status(), "Account setup is required for this HTTP audit");
        user.token = response.body().path("token").asText(); user.userId = response.body().path("userId").asLong();
        return user;
    }
    private void login(UserSession user) throws Exception {
        Reply response = call(null, "POST", "/api/auth/login", Map.of("email", user.email, "password", PASSWORD));
        assertEquals(200, response.status()); user.token = response.body().path("token").asText();
    }
    private void onboard(UserSession user, List<Long> selected, int budget, int maxNew, int maxReviews) throws Exception {
        assertEquals(200, call(user, "POST", "/api/onboarding/sheets", Map.of("sheetIds", selected)).status());
        assertEquals(200, call(user, "PUT", "/api/onboarding/settings", Map.of("dailyTimeBudgetMin", budget, "maxNewProblemsDay", maxNew, "maxRevisionsDay", maxReviews, "targetSkillLevel", 0.8)).status());
        assertEquals(200, call(user, "POST", "/api/onboarding/complete", Map.of()).status());
        call(user, "GET", "/api/auth/me", null);
    }
    private long addInterview(UserSession user, String company, LocalDate date) throws Exception {
        Reply response = call(user, "POST", "/api/onboarding/interviews", Map.of("companyName", company, "role", "Software Engineer", "interviewDate", date.toString()));
        assertEquals(201, response.status()); return response.body().path("interviewId").asLong();
    }
    private Reply call(UserSession user, String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(60));
        if (user != null) builder.header("Authorization", "Bearer " + user.token);
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        long start = System.nanoTime();
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json;
        try { json = response.body().isBlank() ? mapper.nullNode() : mapper.readTree(response.body()); }
        catch (Exception e) { json = mapper.getNodeFactory().textNode(response.body()); }
        int id = trace.size() + 1;
        var entry = new LinkedHashMap<String, Object>();
        entry.put("traceId", id); entry.put("virtualInstant", clock.instant().toString());
        entry.put("actor", user == null ? "anonymous/authentication" : user.label); entry.put("method", method); entry.put("path", path);
        entry.put("request", redact(mapper.valueToTree(body))); entry.put("status", response.statusCode());
        entry.put("response", redact(json)); entry.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        trace.add(entry); exercised.merge(method + " " + path.replaceAll("/[0-9]+(?=/|$)", "/{id}"), 1, Integer::sum);
        return new Reply(response.statusCode(), json, id);
    }
    private JsonNode redact(JsonNode node) {
        if (node == null) return mapper.nullNode();
        JsonNode copy = node.deepCopy();
        if (copy.isObject()) {
            var object = (ObjectNode) copy;
            object.remove(List.of("token", "password", "passwordHash"));
            var fields = new ArrayList<String>(); object.fieldNames().forEachRemaining(fields::add);
            fields.forEach(field -> object.set(field, redact(object.get(field))));
        } else if (copy.isArray()) {
            for (int i = 0; i < copy.size(); i++) ((ArrayNode) copy).set(i, redact(copy.get(i)));
        }
        return copy;
    }
    private List<JsonNode> planItems(JsonNode plan) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode section : plan.path("sections")) for (JsonNode item : section.path("items")) {
            ObjectNode copy = item.deepCopy(); copy.put("auditType", section.path("type").asText()); result.add(copy);
        }
        return result;
    }
    private JsonNode named(JsonNode array, String field, String value) { for (JsonNode node : array) if (node.path(field).asText().equals(value)) return node; throw new IllegalStateException("Missing fixture " + value); }
    private JsonNode find(JsonNode array, String field, long value) { for (JsonNode node : array) if (node.path(field).asLong() == value) return node; return null; }
    private double average(JsonNode states, String field) { double sum = 0; int n = 0; for (JsonNode state : states) { sum += state.path(field).asDouble(); n++; } return n == 0 ? 0 : round(sum / n); }
    private static double round(double value) { return Math.round(value * 10000) / 10000.0; }
    private void verify(boolean condition, String severity, String code, String title, Reply first, Reply second) {
        checks++; if (!condition) finding(severity, code, title, "Expected invariant did not hold", first, second);
    }
    @SuppressWarnings("unchecked")
    private void finding(String severity, String code, String title, String detail, Reply... replies) {
        var finding = findings.computeIfAbsent(code, ignored -> {
            var value = new LinkedHashMap<String, Object>(); value.put("severity", severity); value.put("code", code); value.put("title", title);
            value.put("detail", detail); value.put("occurrences", 0); value.put("examples", new ArrayList<List<Integer>>()); return value;
        });
        finding.put("occurrences", (int) finding.get("occurrences") + 1);
        var examples = (List<List<Integer>>) finding.get("examples");
        if (examples.size() < 5) examples.add(Arrays.stream(replies).filter(Objects::nonNull).map(Reply::traceId).toList());
    }
    private void export() throws Exception {
        Path directory = Files.createDirectories(outputDirectory);
        var writer = mapper.writerWithDefaultPrettyPrinter();
        writer.writeValue(directory.resolve("requests.json").toFile(), trace);
        writer.writeValue(directory.resolve("daily-snapshots.json").toFile(), days);
        writer.writeValue(directory.resolve("findings.json").toFile(), findings.values());
        var summary = new LinkedHashMap<String, Object>();
        summary.put("baselineCommit", "15e0c2d"); summary.put("backendVersion", "working tree with audit fixes"); summary.put("completedVirtualDays", days.size()); summary.put("startDate", START.toString());
        summary.put("httpRequests", trace.size()); summary.put("invariantChecks", checks); summary.put("findings", findings.values()); summary.put("routes", exercised);
        summary.put("allAttemptsStored", attempts.count()); summary.put("recallChecksStored", recallChecks.count());
        summary.put("method", "Real loopback HTTP server, H2 PostgreSQL mode, real security/controllers/services/repositories, virtual request-scoped calendar, offline recall grading");
        writer.writeValue(directory.resolve("summary.json").toFile(), summary);
        System.out.println("AUDIT complete: " + days.size() + " days, " + trace.size() + " HTTP requests, " + findings.size() + " findings");
    }

    private void requireRequestedFixes() {
        long serverErrors = trace.stream().filter(entry -> (Integer) entry.get("status") >= 500).count();
        assertEquals(0, serverErrors, "No user journey request should return HTTP 500");
        var unresolved = findings.values().stream().filter(finding -> Set.of("P1", "GAP").contains(finding.get("severity")))
                .map(finding -> finding.get("code")).toList();
        assertTrue(unresolved.isEmpty(), "Requested fixes still failing: " + unresolved);
    }

    static final class AuditClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;
        AuditClock() { this(new AtomicReference<>(START.atTime(9, 0).atZone(HOST_ZONE).toInstant()), HOST_ZONE); }
        private AuditClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void set(Instant next) { instant.set(next); }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId next) { return new AuditClock(instant, next); }
        public Instant instant() { return instant.get(); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class AuditConfiguration {
        @Bean @Primary AuditClock auditClock() { return new AuditClock(); }
        @Bean HibernatePropertiesCustomizer auditTimestampClock(AuditClock clock) {
            return properties -> properties.put("hibernate.testing.clock", clock);
        }
        @Bean FilterRegistrationBean<OncePerRequestFilter> auditCalendar(AuditClock clock) {
            var registration = new FilterRegistrationBean<OncePerRequestFilter>();
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            registration.setFilter(new OncePerRequestFilter() {
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                    LocalDate day = LocalDate.ofInstant(clock.instant(), HOST_ZONE);
                    LocalDateTime time = LocalDateTime.ofInstant(clock.instant(), HOST_ZONE);
                    try (MockedStatic<LocalDate> dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS);
                         MockedStatic<LocalDateTime> times = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                        dates.when(LocalDate::now).thenReturn(day);
                        times.when(LocalDateTime::now).thenReturn(time);
                        chain.doFilter(request, response);
                    }
                }
            });
            return registration;
        }
    }
}
