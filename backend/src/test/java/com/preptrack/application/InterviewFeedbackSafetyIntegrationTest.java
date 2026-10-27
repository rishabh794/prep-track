package com.preptrack.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.dto.ExtractedPatternDto;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.ai.service.FeedbackExtractionService;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No test transaction: detached entities and actual service transaction boundaries are exercised. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "gemini.api-key="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewFeedbackSafetyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PatternRepository patterns;
    @Autowired UserPatternStateRepository states;
    @MockitoBean FeedbackExtractionService extraction;
    String token;
    long userId;
    long interview;
    long pattern;

    @BeforeEach
    void setup() throws Exception {
        reset(extraction);
        JsonNode auth = postJson("/api/auth/register", null, Map.of("email", UUID.randomUUID() + "@feedback.test",
                "password", "Feedback-test-123!", "displayName", "Feedback test"), 201);
        token = auth.path("token").asText();
        userId = auth.path("userId").asLong();
        long application = postJson("/api/applications", token, Map.of("companyName", "Google"), 201).path("id").asLong();
        interview = postJson("/api/applications/" + application + "/interviews", token,
                Map.of("interviewDate", LocalDate.now().minusDays(1).toString()), 201).path("id").asLong();
        pattern = patterns.findAll().getFirst().getId();
    }

    @Test
    void feedbackHasShortPersistenceTransactionsAndAiRunsWithoutTransaction() throws Exception {
        when(extraction.extract(anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return FeedbackExtractionResult.builder().aiUsed(false).requiresConfirmation(true)
                    .extractedPatterns(List.of(ExtractedPatternDto.builder().patternId(pattern).wasWeak(true).build())).build();
        });
        postJson("/api/interviews/" + interview + "/feedback", token,
                Map.of("rawFeedback", "I struggled with the approach."), 200);
        JsonNode read = getJson("/api/interviews", token);
        JsonNode saved = null;
        for (JsonNode candidate : read) if (candidate.path("id").asLong() == interview) saved = candidate;
        assertThat(saved).isNotNull();
        assertThat(saved.path("rawFeedback").asText()).isEqualTo("I struggled with the approach.");
        assertThat(saved.path("topics").size()).isEqualTo(1);
        assertThat(saved.path("topics").get(0).path("confirmed").asBoolean()).isFalse();
    }

    @Test
    void outsiderCannotInvokeExtractionForAnOwnedInterview() throws Exception {
        JsonNode outsider = postJson("/api/auth/register", null, Map.of("email", UUID.randomUUID() + "@feedback.test",
                "password", "Feedback-test-123!", "displayName", "Other"), 201);
        postJson("/api/interviews/" + interview + "/feedback", outsider.path("token").asText(),
                Map.of("rawFeedback", "Not my interview"), 404);
        verifyNoInteractions(extraction);
    }

    @Test
    void concurrentConfirmationsAndOutcomeEditsCannotRepeatTheBoost() throws Exception {
        String body = json.writeValueAsString(Map.of("outcome", "PARTIAL", "topics", List.of(Map.of("patternId", pattern, "wasWeak", true))));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(ignored -> executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/interviews/" + interview + "/confirm-topics")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            for (var call : calls) assertThat(call.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(states.findByUserIdAndPatternId(userId, pattern).orElseThrow().getInterviewBoost()).isEqualTo(0.3);
        mvc.perform(put("/api/interviews/" + interview).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("interviewDate", LocalDate.now().toString(), "outcome", "UPCOMING"))))
                .andExpect(status().isOk());
        JsonNode confirmed = postJson("/api/interviews/" + interview + "/confirm-topics", token,
                Map.of("outcome", "PARTIAL", "topics", List.of(Map.of("patternId", pattern, "wasWeak", true))), 200);
        assertThat(confirmed.path("topicsConfirmedAt").isTextual()).isTrue();
        assertThat(states.findByUserIdAndPatternId(userId, pattern).orElseThrow().getInterviewBoost()).isEqualTo(0.3);
    }

    @Test
    void invalidTopicsAndUnfinishedOutcomeReturn400WithoutApplyingBoost() throws Exception {
        String endpoint = "/api/interviews/" + interview + "/confirm-topics";
        postJson(endpoint, token, Map.of("outcome", "PARTIAL", "topics", List.of(Map.of("wasWeak", true))), 400);
        postJson(endpoint, token, Map.of("outcome", "PARTIAL", "topics", List.of(Map.of("patternId", pattern), Map.of("patternId", pattern))), 400);
        postJson(endpoint, token, Map.of("outcome", "UPCOMING", "topics", List.of(Map.of("patternId", pattern, "wasWeak", true))), 400);
        assertThat(states.findByUserIdAndPatternId(userId, pattern)).isEmpty();
        assertThat(getJson("/api/interviews/" + interview + "/topics", token)).isEmpty();
    }

    private JsonNode postJson(String path, String auth, Object body, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (auth != null) request.header("Authorization", "Bearer " + auth);
        return json.readTree(mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, String auth) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
