package com.preptrack.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.sheet.repository.SheetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
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

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "gemini.api-key="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PlannerRecoveryIntegrationTest.CalendarConfiguration.class)
class PlannerRecoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ProblemRepository problems;
    @Autowired SheetRepository sheets;
    @Autowired MutableClock clock;

    @Test
    void patternGapSchedulesTheActualWarmupThenReturnsToTheFailedProblem() throws Exception {
        clock.set(Instant.parse("2026-10-27T12:00:00Z"));
        String token = registerAndSelectSheet(240, 0, 10);
        long hard = problems.findByCanonicalTitle("Trapping Rain Water").orElseThrow().getId();
        long warmup = problems.findByCanonicalTitle("Next Greater Element I").orElseThrow().getId();
        attempt(token, hard, "COULD_NOT_SOLVE", "PATTERN_GAP");

        clock.set(Instant.parse("2026-10-28T12:00:00Z"));
        JsonNode dayTwo = getPlan(token);
        assertThat(items(dayTwo)).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(warmup);
            assertThat(item.path("followUpType").asText()).isEqualTo("EASIER_PREREQUISITE");
            assertThat(item.path("followUpDetail").asText()).contains("Trapping Rain Water");
            assertThat(item.path("estimatedMinutes").asInt()).isEqualTo(problems.findById(warmup).orElseThrow().getEstimatedMinutes());
        });
        assertThat(items(dayTwo)).noneMatch(item -> item.path("problemId").asLong() == hard);
        assertThat(items(dayTwo).stream().map(item -> item.path("problemId").asLong()).distinct().count()).isEqualTo(items(dayTwo).size());

        attempt(token, warmup, "SOLVED_INDEPENDENTLY", null);
        JsonNode completed = getPlan(token);
        assertThat(items(completed)).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(warmup);
            assertThat(item.path("completed").asBoolean()).isTrue();
        });
        assertThat(completed.path("planId")).isEqualTo(dayTwo.path("planId"));
        assertThat(completed.path("totalCount")).isEqualTo(dayTwo.path("totalCount"));

        clock.set(Instant.parse("2026-10-29T12:00:00Z"));
        JsonNode dayThree = getPlan(token);
        assertThat(items(dayThree)).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(hard);
            assertThat(item.path("followUpType").asText()).isEqualTo("CODE_FROM_SCRATCH");
            assertThat(item.path("followUpDetail").asText()).contains("Prerequisite completed");
        });
        assertThat(items(dayThree)).noneMatch(item -> item.path("problemId").asLong() == warmup);
    }

    @Test
    void aMissingPrerequisiteFallsBackToAnExplicitCodingRecovery() throws Exception {
        clock.set(Instant.parse("2026-11-01T12:00:00Z"));
        String token = registerAndSelectSheet(90, 0, 10);
        long problem = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
        attempt(token, problem, "COULD_NOT_SOLVE", "PATTERN_GAP");
        clock.set(Instant.parse("2026-11-02T12:00:00Z"));
        JsonNode plan = getPlan(token);
        assertThat(items(plan)).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(problem);
            assertThat(item.path("followUpType").asText()).isEqualTo("CODE_FROM_SCRATCH");
            assertThat(item.path("followUpDetail").asText()).contains("No linked prerequisite");
        });
    }

    @Test
    void recallCannotReplaceCodingEvidenceOrHideALaterFailedCodingAttempt() throws Exception {
        clock.set(Instant.parse("2026-11-06T12:00:00Z"));
        String token = registerAndSelectSheet(240, 0, 10);
        long hard = problems.findByCanonicalTitle("Trapping Rain Water").orElseThrow().getId();
        long warmup = problems.findByCanonicalTitle("Next Greater Element I").orElseThrow().getId();
        attempt(token, hard, "COULD_NOT_SOLVE", "PATTERN_GAP");
        confirmRecallPass(token, warmup);

        clock.set(Instant.parse("2026-11-07T12:00:00Z"));
        JsonNode afterRecall = getPlan(token);
        assertThat(items(afterRecall)).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(warmup);
            assertThat(item.path("followUpType").asText()).isEqualTo("EASIER_PREREQUISITE");
        });
        assertThat(items(afterRecall)).noneMatch(item -> item.path("problemId").asLong() == hard);

        attempt(token, warmup, "SOLVED_INDEPENDENTLY", null);
        // Both coding events share a timestamp. The later event ID must win.
        attempt(token, warmup, "COULD_NOT_SOLVE", "IMPLEMENTATION_GAP");
        confirmRecallPass(token, warmup);
        clock.set(Instant.parse("2026-11-08T12:00:00Z"));
        JsonNode afterMaskedFailure = getPlan(token);
        assertThat(items(afterMaskedFailure)).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(warmup);
            assertThat(item.path("followUpType").asText()).isEqualTo("EASIER_PREREQUISITE");
        });
        assertThat(items(afterMaskedFailure)).noneMatch(item -> item.path("problemId").asLong() == hard);

        // Independent coding reviews are evidence too, not only first-attempt submissions.
        mvc.perform(post("/api/revisions/problem/" + warmup + "/review").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("outcome", "SOLVED_INDEPENDENTLY", "hintsUsed", 0))))
                .andExpect(status().isOk());
        clock.set(Instant.parse("2026-11-09T12:00:00Z"));
        assertThat(items(getPlan(token))).anySatisfy(item -> {
            assertThat(item.path("problemId").asLong()).isEqualTo(hard);
            assertThat(item.path("followUpDetail").asText()).contains("Prerequisite completed");
        });
    }

    @Test
    void regenerationReflectsBudgetChangesAndExcludesSkippedWork() throws Exception {
        clock.set(Instant.parse("2026-11-04T12:00:00Z"));
        String token = registerAndSelectSheet(90, 3, 10);
        JsonNode initial = getPlan(token);
        JsonNode first = items(initial).getFirst();
        mvc.perform(patch("/api/plan/items/" + first.path("planItemId").asLong() + "/skip")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Busy\"}"))
                .andExpect(status().isOk());
        settings(token, 120, 4, 10);
        JsonNode regenerated = mapper.readTree(mvc.perform(post("/api/plan/today/regenerate").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int activeMinutes = items(regenerated).stream().filter(item -> !item.path("skipped").asBoolean())
                .mapToInt(item -> item.path("estimatedMinutes").asInt()).sum();
        assertThat(regenerated.path("dailyBudgetMinutes").asInt()).isEqualTo(120);
        assertThat(regenerated.path("totalEstimatedMinutes").asInt()).isEqualTo(activeMinutes).isLessThanOrEqualTo(120);
        assertThat(regenerated.path("skippedCount").asInt()).isEqualTo(1);
        assertThat(regenerated.path("skippedMinutes").asInt()).isEqualTo(first.path("estimatedMinutes").asInt());
        settings(token, 60, 2, 10);
        JsonNode smaller = mapper.readTree(mvc.perform(post("/api/plan/today/regenerate").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(smaller.path("dailyBudgetMinutes").asInt()).isEqualTo(60);
        assertThat(smaller.path("totalEstimatedMinutes").asInt()).isLessThanOrEqualTo(60);
        assertThat(smaller.path("skippedCount").asInt()).isEqualTo(1);
    }

    private String registerAndSelectSheet(int budget, int fresh, int reviews) throws Exception {
        JsonNode auth = mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@recovery.test", "password", "password123", "displayName", "Recovery"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = "Bearer " + auth.path("token").asText();
        long sheet = sheets.findAll().stream().filter(s -> s.getName().equals("NeetCode 150")).findFirst().orElseThrow().getId();
        mvc.perform(post("/api/onboarding/sheets").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("sheetIds", List.of(sheet))))).andExpect(status().isOk());
        settings(token, budget, fresh, reviews);
        return token;
    }

    private void settings(String token, int budget, int fresh, int reviews) throws Exception {
        mvc.perform(put("/api/onboarding/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("dailyTimeBudgetMin", budget, "maxNewProblemsDay", fresh, "maxRevisionsDay", reviews))))
                .andExpect(status().isOk());
    }

    private void attempt(String token, long problem, String outcome, String reason) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("problemId", problem, "outcome", outcome, "timeTakenSec", 900, "hintsUsed", 0));
        if (reason != null) body.put("failureReason", reason);
        mvc.perform(post("/api/attempts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk());
    }

    private void confirmRecallPass(String token, long problem) throws Exception {
        JsonNode graded = mapper.readTree(mvc.perform(post("/api/revisions/problem/" + problem + "/recall")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("userApproach", "Maintain a monotonic stack of unresolved elements and resolve them on a greater value."))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/revisions/problem/" + problem + "/review").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("recallCheckId", graded.path("recallCheckId").asLong(), "userOverride", "PASS"))))
                .andExpect(status().isOk());
    }

    private JsonNode getPlan(String token) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/plan/today").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<JsonNode> items(JsonNode plan) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode section : plan.path("sections")) section.path("items").forEach(result::add);
        return result;
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-10-27T12:00:00Z"));
        void set(Instant next) { instant.set(next); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant.get(), zone); }
        public Instant instant() { return instant.get(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CalendarConfiguration {
        @Bean @Primary MutableClock plannerTestClock() { return new MutableClock(); }
        @Bean HibernatePropertiesCustomizer plannerHistoryClock(MutableClock clock) {
            return properties -> properties.put("hibernate.testing.clock", clock);
        }
    }
}
