package com.preptrack.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.problem.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real service transactions: no test-wide transaction or open-in-view session masks API behavior. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_calendar_activity_api;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.open-in-view=false", "gemini.api-key="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserCalendarActivityApiIntegrationTest.CalendarConfiguration.class)
class UserCalendarActivityApiIntegrationTest {
    static final Instant BOUNDARY = Instant.parse("2026-10-27T23:45:00Z");
    static final ZoneId HOST_ZONE = ZoneId.of("Asia/Kolkata");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CalendarClock clock;
    @Autowired ProblemRepository problems;
    String token;
    long twoSum;
    long duplicate;

    @BeforeEach
    void setup() throws Exception {
        clock.set(BOUNDARY);
        token = register();
        JsonNode sheets = getJson("/api/sheets/available", token);
        long sheet = 0;
        for (JsonNode candidate : sheets) if (candidate.path("name").asText().equals("NeetCode 150")) sheet = candidate.path("id").asLong();
        assertThat(sheet).isPositive();
        postJson("/api/onboarding/sheets", token, Map.of("sheetIds", List.of(sheet)), 200);
        settings(Map.of("dailyTimeBudgetMin", 90, "maxNewProblemsDay", 3, "maxRevisionsDay", 10, "timezone", "UTC"), 200);
        postJson("/api/onboarding/complete", token, Map.of(), 200);
        twoSum = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
        duplicate = problems.findByCanonicalTitle("Contains Duplicate").orElseThrow().getId();
    }

    @Test
    void bothZeroSlotLimitsProduceARestDayPlan() throws Exception {
        JsonNode response = settings(Map.of("maxNewProblemsDay", 0, "maxRevisionsDay", 0), 200);
        assertThat(response.path("maxNewProblemsDay").asInt()).isZero();
        assertThat(response.path("maxRevisionsDay").asInt()).isZero();
        JsonNode plan = getJson("/api/plan/today", token);
        assertThat(items(plan)).isEmpty();
        assertThat(plan.path("totalCount").asInt()).isZero();
        assertThat(plan.path("totalEstimatedMinutes").asInt()).isZero();
        assertThat(plan.path("date").asText()).isEqualTo("2026-10-27");
    }

    @Test
    void zeroLimitsSupportActualRevisionOnlyAndNewOnlyPlans() throws Exception {
        postJson("/api/attempts", token, Map.of("problemId", twoSum, "outcome", "COULD_NOT_SOLVE",
                "failureReason", "IMPLEMENTATION_GAP", "timeTakenSec", 600, "hintsUsed", 0), 200);
        clock.set(BOUNDARY.plus(Duration.ofDays(1)));
        settings(Map.of("maxNewProblemsDay", 0, "maxRevisionsDay", 3), 200);
        JsonNode revisions = getJson("/api/plan/today", token);
        assertThat(items(revisions)).isNotEmpty();
        for (JsonNode section : revisions.path("sections")) {
            if (section.path("type").asText().equals("NEW_PROBLEM")) assertThat(section.path("items")).isEmpty();
        }
        settings(Map.of("maxNewProblemsDay", 3, "maxRevisionsDay", 0), 200);
        JsonNode acquisition = postJson("/api/plan/today/regenerate", token, Map.of(), 200);
        assertThat(items(acquisition)).isNotEmpty();
        for (JsonNode section : acquisition.path("sections")) {
            if (!section.path("items").isEmpty()) assertThat(section.path("type").asText()).isEqualTo("NEW_PROBLEM");
        }
    }

    @Test
    void timezoneRoundTripsAndAnInvalidUpdateCannotPartiallyMutateSettings() throws Exception {
        assertThat(getJson("/api/auth/me", token).path("settings").path("timezone").asText()).isEqualTo("UTC");
        JsonNode changed = settings(Map.of("timezone", "America/Los_Angeles", "dailyTimeBudgetMin", 123), 200);
        assertThat(changed.path("timezone").asText()).isEqualTo("America/Los_Angeles");
        settings(Map.of("timezone", "Mars/Base", "dailyTimeBudgetMin", 60), 400);
        settings(Map.of("timezone", ""), 400);
        JsonNode current = getJson("/api/auth/me", token).path("settings");
        assertThat(current.path("timezone").asText()).isEqualTo("America/Los_Angeles");
        assertThat(current.path("dailyTimeBudgetMin").asInt()).isEqualTo(123);
    }

    @Test
    void plansForecastsAttemptCompletionAndRevisionDatesUseTheUsersDay() throws Exception {
        JsonNode interview = postJson("/api/onboarding/interviews", token,
                Map.of("companyName", "Google", "interviewDate", "2026-11-10"), 201);
        long interviewId = interview.path("interviewId").asLong();
        JsonNode plan = getJson("/api/plan/today", token);
        assertThat(plan.path("date").asText()).isEqualTo("2026-10-27");
        assertThat(getJson("/api/forecast/interviews/" + interviewId, token).path("asOfDate").asText()).isEqualTo("2026-10-27");
        JsonNode selected = items(plan).getFirst();
        long problem = selected.path("problemId").asLong();
        JsonNode attempted = independentAttempt(problem);
        assertThat(attempted.path("planItemCompleted").asBoolean()).isTrue();
        assertThat(attempted.path("revisionScheduled").path("nextReviewDate").asText()).isEqualTo("2026-10-30");
        JsonNode after = getJson("/api/plan/today", token);
        assertThat(after.path("planId")).isEqualTo(plan.path("planId"));
        assertThat(items(after).stream().filter(item -> item.path("problemId").asLong() == problem).findFirst().orElseThrow().path("completed").asBoolean()).isTrue();

        // The server is already on October 30, but the UTC user is still on October 29.
        clock.set(BOUNDARY.plus(Duration.ofDays(2)));
        assertThat(findProblem(getJson("/api/revisions/due", token), problem)).isNull();
        assertThat(findProblem(getJson("/api/revisions/due?date=2026-10-30", token), problem)).isNotNull();
        clock.set(BOUNDARY.plus(Duration.ofDays(3)));
        assertThat(findProblem(getJson("/api/revisions/due", token), problem)).isNotNull();
        JsonNode reviewPlan = getJson("/api/plan/today", token);
        assertThat(reviewPlan.path("date").asText()).isEqualTo("2026-10-30");
        assertThat(items(reviewPlan).stream().anyMatch(item -> item.path("problemId").asLong() == problem)).isTrue();
        JsonNode review = postJson("/api/revisions/problem/" + problem + "/review", token, Map.of("outcome", "SOLVED_INDEPENDENTLY"), 200);
        assertThat(review.path("nextReviewDate").asText()).isEqualTo("2026-10-31");
        JsonNode reviewed = getJson("/api/plan/today", token);
        assertThat(items(reviewed).stream().filter(item -> item.path("problemId").asLong() == problem).findFirst().orElseThrow().path("completed").asBoolean()).isTrue();
    }

    @Test
    void historyContainsTypedActivitiesWithStableTiePaginationAndPrivateFiltering() throws Exception {
        long first = independentAttempt(twoSum).path("attemptId").asLong();
        long second = independentAttempt(duplicate).path("attemptId").asLong();
        postJson("/api/revisions/problem/" + twoSum + "/review", token, Map.of("outcome", "SOLVED_INDEPENDENTLY"), 200);
        JsonNode recall = postJson("/api/revisions/problem/" + twoSum + "/recall", token,
                Map.of("userApproach", problems.findById(twoSum).orElseThrow().getReferenceApproach()), 200);
        postJson("/api/revisions/problem/" + twoSum + "/review", token,
                Map.of("recallCheckId", recall.path("recallCheckId").asLong(), "userOverride", "PASS"), 200);
        JsonNode newest = getJson("/api/attempts?page=0&size=2", token);
        JsonNode older = getJson("/api/attempts?page=1&size=2", token);
        assertThat(newest.path("totalElements").asLong()).isEqualTo(4);
        assertThat(newest.path("totalPages").asInt()).isEqualTo(2);
        assertThat(newest.path("last").asBoolean()).isFalse();
        assertThat(older.path("last").asBoolean()).isTrue();
        assertThat(newest.path("content").get(0).path("activityType").asText()).isEqualTo("RECALL_REVIEW");
        assertThat(newest.path("content").get(1).path("activityType").asText()).isEqualTo("CODING_REVIEW");
        assertThat(older.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(older.path("content").get(1).path("id").asLong()).isEqualTo(first);
        assertThat(older.path("content").get(0).path("activityType").asText()).isEqualTo("CODING_ATTEMPT");
        for (JsonNode page : List.of(newest, older)) for (JsonNode event : page.path("content")) {
            assertThat(event.path("attemptedAt").asText()).isEqualTo(BOUNDARY.toString());
        }
        JsonNode filtered = getJson("/api/attempts?problemId=" + twoSum + "&size=100", token);
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(3);
        assertThat(filtered.path("content")).isEqualTo(getJson("/api/problems/" + twoSum + "/attempts?size=100", token).path("content"));
        String other = register();
        assertThat(getJson("/api/attempts", other).path("totalElements").asLong()).isZero();
        assertThat(getJson("/api/problems/" + twoSum + "/attempts", other).path("content")).isEmpty();
    }

    @Test
    void notesArePrivateReadableAndClearableWithoutLearningOrHistoryChanges() throws Exception {
        JsonNode before = getJson("/api/user/pattern-states", token);
        JsonNode written = putJson("/api/problems/" + twoSum + "/note", token, Map.of("insightNote", "Look up the complement before insertion."), 200);
        assertThat(written.path("insightNote").asText()).isEqualTo("Look up the complement before insertion.");
        assertThat(getJson("/api/problems/" + twoSum + "/note", token).path("insightNote")).isEqualTo(written.path("insightNote"));
        assertThat(getJson("/api/problems/" + twoSum, token).path("insightNote")).isEqualTo(written.path("insightNote"));
        String other = register();
        assertThat(getJson("/api/problems/" + twoSum + "/note", other).path("insightNote").isNull()).isTrue();
        assertThat(getJson("/api/problems/" + twoSum, other).path("insightNote").isNull()).isTrue();
        putJson("/api/problems/" + twoSum + "/note", other, Map.of("insightNote", "Someone else's note"), 200);
        assertThat(getJson("/api/problems/" + twoSum + "/note", token).path("insightNote")).isEqualTo(written.path("insightNote"));
        putJson("/api/problems/" + twoSum + "/note", token, Map.of("insightNote", ""), 200);
        assertThat(getJson("/api/problems/" + twoSum, token).path("insightNote").asText()).isEmpty();
        assertThat(getJson("/api/user/pattern-states", token)).isEqualTo(before);
        assertThat(getJson("/api/attempts", token).path("totalElements").asLong()).isZero();
    }

    @Test
    void newReadApisRequireAuthenticationAndRejectInvalidPagination() throws Exception {
        for (String path : List.of("/api/attempts", "/api/problems/" + twoSum + "/attempts", "/api/problems/" + twoSum + "/note")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(put("/api/problems/" + twoSum + "/note").contentType(MediaType.APPLICATION_JSON).content("{\"insightNote\":\"private\"}"))
                .andExpect(status().isUnauthorized());
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=2147483647&size=100")) {
            mvc.perform(get("/api/attempts" + query).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/problems/" + twoSum + "/attempts" + query).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        }
        putJson("/api/problems/" + twoSum + "/note", token, Map.of(), 400);
    }

    private JsonNode independentAttempt(long problem) throws Exception {
        return postJson("/api/attempts", token, Map.of("problemId", problem, "outcome", "SOLVED_INDEPENDENTLY", "timeTakenSec", 600, "hintsUsed", 0), 200);
    }

    private String register() throws Exception {
        return postJson("/api/auth/register", null, Map.of("email", UUID.randomUUID() + "@calendar.test", "password", "Calendar-test-123!", "displayName", "Calendar test"), 201).path("token").asText();
    }

    private JsonNode settings(Map<String, Object> body, int expected) throws Exception {
        return putJson("/api/onboarding/settings", token, body, expected);
    }

    private JsonNode postJson(String path, String auth, Object body, int expected) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (auth != null) request.header("Authorization", "Bearer " + auth);
        String result = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.nullNode() : json.readTree(result);
    }

    private JsonNode putJson(String path, String auth, Object body, int expected) throws Exception {
        return json.readTree(mvc.perform(put(path).header("Authorization", "Bearer " + auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, String auth) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<JsonNode> items(JsonNode plan) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode section : plan.path("sections")) section.path("items").forEach(result::add);
        return result;
    }

    private JsonNode findProblem(JsonNode cards, long problemId) {
        for (JsonNode card : cards) if (card.path("problemId").asLong() == problemId) return card;
        return null;
    }

    static class CalendarClock extends Clock {
        final AtomicReference<Instant> current;
        final ZoneId zone;
        CalendarClock() { this(new AtomicReference<>(BOUNDARY), HOST_ZONE); }
        CalendarClock(AtomicReference<Instant> current, ZoneId zone) { this.current = current; this.zone = zone; }
        void set(Instant instant) { current.set(instant); }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId zone) { return new CalendarClock(current, zone); }
        public Instant instant() { return current.get(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CalendarConfiguration {
        @Bean @Primary CalendarClock calendarClock() { return new CalendarClock(); }
        @Bean HibernatePropertiesCustomizer calendarTimestampClock(CalendarClock clock) {
            return properties -> properties.put("hibernate.testing.clock", clock);
        }
    }
}
