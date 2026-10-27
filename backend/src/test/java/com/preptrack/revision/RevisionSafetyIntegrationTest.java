package com.preptrack.revision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.dto.RecallGradingResult;
import com.preptrack.ai.entity.RecallGrade;
import com.preptrack.ai.repository.RecallCheckRepository;
import com.preptrack.ai.service.RecallGradingService;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.revision.repository.RevisionCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Real requests without a test transaction, matching production's closed-view persistence. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:revision_safety;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.open-in-view=false", "gemini.api-key="
})
@ActiveProfiles("test")
class RevisionSafetyIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired ProblemRepository problems;
    @Autowired RevisionCardRepository cards;
    @Autowired RecallCheckRepository checks;
    @Autowired UserPatternStateRepository states;
    @Autowired UserProblemAttemptRepository attempts;
    @MockitoBean RecallGradingService grading;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String token;
    long userId;
    long problemId;

    @BeforeEach
    void registerLearner() throws Exception {
        when(grading.grade(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Remote grading must not hold a transaction");
            return RecallGradingResult.builder().grade(RecallGrade.PASS).reasoning("Correct approach").build();
        });
        var registration = call("POST", "/api/auth/register", Map.of("email", UUID.randomUUID() + "@revision.test", "password", "Password-42!", "displayName", "Revision Learner"));
        assertThat(registration.statusCode()).isEqualTo(201);
        JsonNode account = json.readTree(registration.body());
        token = account.path("token").asText();
        userId = account.path("userId").asLong();
        problemId = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
    }

    @Test
    void bothRecallRoutesGradeWithoutLazyAccessOrAnOpenTransaction() throws Exception {
        long card = createCard();
        var byCard = call("POST", "/api/revisions/" + card + "/recall", Map.of("userApproach", "Use a hash map for complements"));
        assertThat(byCard.statusCode()).isEqualTo(200);
        long check = json.readTree(byCard.body()).path("recallCheckId").asLong();
        assertThat(checks.findById(check).orElseThrow().getConfirmedAt()).isNull();
        var anotherProblem = problems.findByCanonicalTitle("Valid Anagram").orElseThrow().getId();
        assertThat(call("POST", "/api/revisions/problem/" + anotherProblem + "/recall", Map.of("userApproach", "Count character frequencies")).statusCode()).isEqualTo(200);
        assertThat(cards.findByUserIdAndProblemId(userId, anotherProblem)).isPresent();

        var outsiderRegistration = call("POST", "/api/auth/register", Map.of("email", UUID.randomUUID() + "@revision.test", "password", "Password-42!", "displayName", "Other Learner"));
        token = json.readTree(outsiderRegistration.body()).path("token").asText();
        assertThat(call("POST", "/api/revisions/" + card + "/recall", Map.of("userApproach", "A valid explanation")).statusCode()).isEqualTo(404);
    }

    @Test
    void repeatedAndConcurrentConfirmationApplyOneUpdateAndOneHistoryEvent() throws Exception {
        long card = createCard();
        long check = grade(card);
        var before = state();
        long historyBefore = historyCount();
        var payload = Map.of("recallCheckId", check, "userOverride", "PASS");
        var request = request("POST", "/api/revisions/" + card + "/review", payload);
        var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);
        assertThat(first.get().statusCode()).isEqualTo(200);
        assertThat(second.get().statusCode()).isEqualTo(200);
        var after = state();
        assertThat(after.attempts).isEqualTo(before.attempts + 1);
        assertThat(after.skill).isEqualTo(before.skill);
        assertThat(after.stability).isEqualTo(before.stability * 1.5);
        assertThat(historyCount()).isEqualTo(historyBefore + 1);
        assertThat(checks.findById(check).orElseThrow().getConfirmedAt()).isNotNull();
        assertThat(call("POST", "/api/revisions/" + card + "/review", payload).statusCode()).isEqualTo(200);
        assertThat(state()).isEqualTo(after);
        assertThat(historyCount()).isEqualTo(historyBefore + 1);
        assertThat(attempts.findByUserIdOrderByAttemptedAtDesc(userId).stream()
                .filter(event -> event.getRecallCheckId() != null && event.getRecallCheckId() == check)
                .map(event -> event.getActivityType().name()).toList()).containsExactly("RECALL_REVIEW");
        assertThat(call("POST", "/api/revisions/" + card + "/review", Map.of("recallCheckId", check, "userOverride", "FAIL")).statusCode()).isEqualTo(400);
        assertThat(state()).isEqualTo(after);
    }

    @Test
    void legacyChecksRequireFreshGradingWithoutChangingLearningState() throws Exception {
        long card = createCard();
        long check = grade(card);
        var legacy = checks.findById(check).orElseThrow();
        legacy.setLegacyInvalidated(true);
        checks.save(legacy);
        var before = state();
        long historyBefore = historyCount();
        var rejected = call("POST", "/api/revisions/" + card + "/review", Map.of("recallCheckId", check));
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(json.readTree(rejected.body()).path("message").asText()).contains("fresh recall");
        assertThat(state()).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
        assertThat(call("POST", "/api/revisions/" + card + "/review", Map.of("recallCheckId", grade(card))).statusCode()).isEqualTo(200);
        assertThat(historyCount()).isEqualTo(historyBefore + 1);
    }

    @Test
    void recallWithHintsCannotReceiveIndependentCreditOrConsumeTheCheck() throws Exception {
        long card = createCard();
        long check = grade(card);
        var before = state();
        long historyBefore = historyCount();
        assertThat(call("POST", "/api/revisions/" + card + "/review", Map.of("recallCheckId", check, "hintsUsed", 4)).statusCode()).isEqualTo(400);
        assertThat(state()).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
        assertThat(checks.findById(check).orElseThrow().getConfirmedAt()).isNull();
        assertThat(call("POST", "/api/revisions/" + card + "/review", Map.of("recallCheckId", check, "hintsUsed", 4, "userOverride", "PARTIAL")).statusCode()).isEqualTo(200);
        assertThat(historyCount()).isEqualTo(historyBefore + 1);
    }

    @Test
    void codingReviewsAreRecordedAndInvalidSignalsDoNotChangeProgress() throws Exception {
        long card = createCard();
        var before = state();
        long historyBefore = historyCount();
        for (Map<String, ?> input : java.util.List.of(
                Map.of("outcome", "NOT_A_REAL_OUTCOME"), Map.of("outcome", "SOLVED_INDEPENDENTLY", "hintsUsed", 2),
                Map.of("outcome", "SOLVED_WITH_HELP", "timeTakenSec", -1))) {
            assertThat(call("POST", "/api/revisions/" + card + "/review", input).statusCode()).isEqualTo(400);
            assertThat(state()).isEqualTo(before);
        }
        assertThat(historyCount()).isEqualTo(historyBefore);
        assertThat(call("POST", "/api/revisions/problem/" + problemId + "/review", Map.of(
                "outcome", "SOLVED_WITH_HELP", "failureReason", "IMPLEMENTATION_GAP", "timeTakenSec", 1200, "hintsUsed", 1, "notes", "Review invariant update order")).statusCode()).isEqualTo(200);
        assertThat(historyCount()).isEqualTo(historyBefore + 1);
        var event = attempts.findByUserIdOrderByAttemptedAtDesc(userId).stream().filter(entry -> entry.getActivityType().name().equals("CODING_REVIEW")).findFirst().orElseThrow();
        assertThat(event.getTimeTakenSec()).isEqualTo(1200);
        assertThat(event.getHintsUsed()).isEqualTo(1);
        assertThat(event.getNotes()).isEqualTo("Review invariant update order");
        assertThat(event.getFollowUpType().name()).isEqualTo("CODE_FROM_SCRATCH");
    }

    private long createCard() throws Exception {
        assertThat(call("POST", "/api/attempts", Map.of("problemId", problemId, "outcome", "SOLVED_INDEPENDENTLY", "timeTakenSec", 600, "hintsUsed", 0)).statusCode()).isEqualTo(200);
        return cards.findByUserIdAndProblemId(userId, problemId).orElseThrow().getId();
    }

    private long grade(long cardId) throws Exception {
        var result = call("POST", "/api/revisions/" + cardId + "/recall", Map.of("userApproach", "Use a hash map for complements"));
        assertThat(result.statusCode()).isEqualTo(200);
        return json.readTree(result.body()).path("recallCheckId").asLong();
    }

    private record PatternSnapshot(double skill, double stability, int attempts) {}
    private PatternSnapshot state() {
        var state = states.findByUserIdWithPattern(userId).stream().filter(s -> s.getPattern().getSlug().equals("hash-map-lookup")).findFirst().orElseThrow();
        return new PatternSnapshot(state.getSkill(), state.getStability(), state.getTotalAttempts());
    }
    private long historyCount() { return attempts.findByUserIdOrderByAttemptedAtDesc(userId).size(); }
    private HttpResponse<String> call(String method, String path, Object body) throws Exception {
        return http.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest request(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }
}
