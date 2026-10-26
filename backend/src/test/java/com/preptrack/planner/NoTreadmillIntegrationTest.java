package com.preptrack.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.attempt.dto.CreateAttemptRequest;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.auth.dto.RegisterRequest;
import com.preptrack.onboarding.dto.SheetSelectionRequest;
import com.preptrack.sheet.entity.Sheet;
import com.preptrack.sheet.repository.SheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoTreadmillIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SheetRepository sheetRepository;

    private String authToken;
    private Long sheetId;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("planneruser@example.com")
                .password("password123")
                .displayName("Planner Tester")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        authToken = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();

        List<Sheet> sheets = sheetRepository.findAll();
        assertThat(sheets).isNotEmpty();
        sheetId = sheets.get(0).getId();

        SheetSelectionRequest sheetReq = SheetSelectionRequest.builder()
                .sheetIds(List.of(sheetId))
                .build();

        mockMvc.perform(post("/api/onboarding/sheets")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sheetReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Completing an item does not refill the budget (No-Treadmill Invariant)")
    void testNoTreadmillInvariant() throws Exception {
        // 1. Initial plan generation on first GET /api/plan/today
        MvcResult planResult = mockMvc.perform(get("/api/plan/today")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").exists())
                .andExpect(jsonPath("$.completedCount").value(0))
                .andReturn();

        JsonNode initialPlan = objectMapper.readTree(planResult.getResponse().getContentAsString());
        long planId = initialPlan.get("planId").asLong();
        int initialTotalCount = initialPlan.get("totalCount").asInt();
        assertThat(initialTotalCount).isGreaterThan(0);

        JsonNode firstSection = initialPlan.get("sections").get(0);
        JsonNode firstItem = firstSection.get("items").get(0);
        long firstProblemId = firstItem.get("problemId").asLong();
        long firstItemId = firstItem.get("planItemId").asLong();

        // 2. Complete item #1 via POST /api/attempts
        CreateAttemptRequest attemptReq = CreateAttemptRequest.builder()
                .problemId(firstProblemId)
                .outcome(AttemptOutcome.SOLVED_INDEPENDENTLY)
                .timeTakenSec(1200)
                .hintsUsed(0)
                .build();

        mockMvc.perform(post("/api/attempts")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attemptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planItemCompleted").value(true));

        // 3. Re-fetch GET /api/plan/today
        MvcResult refetchResult = mockMvc.perform(get("/api/plan/today")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refetchedPlan = objectMapper.readTree(refetchResult.getResponse().getContentAsString());

        // Verifications of No-Treadmill Invariant:
        // A) Same planId
        assertThat(refetchedPlan.get("planId").asLong()).isEqualTo(planId);
        // B) Same totalCount (budget was NOT refilled with new items!)
        assertThat(refetchedPlan.get("totalCount").asInt()).isEqualTo(initialTotalCount);
        // C) completedCount incremented to 1
        assertThat(refetchedPlan.get("completedCount").asInt()).isEqualTo(1);

        // Find the completed item in refetched plan
        boolean item1Completed = false;
        for (JsonNode sec : refetchedPlan.get("sections")) {
            for (JsonNode item : sec.get("items")) {
                if (item.get("planItemId").asLong() == firstItemId) {
                    item1Completed = item.get("completed").asBoolean();
                }
            }
        }
        assertThat(item1Completed).isTrue();

        // 4. Test Skip Endpoint: PATCH /api/plan/items/{itemId}/skip
        if (initialTotalCount > 1) {
            JsonNode secondItem = firstSection.get("items").size() > 1
                    ? firstSection.get("items").get(1)
                    : initialPlan.get("sections").get(initialPlan.get("sections").size() - 1).get("items").get(0);
            long secondItemId = secondItem.get("planItemId").asLong();

            mockMvc.perform(patch("/api/plan/items/" + secondItemId + "/skip")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"Skip test\"}"))
                    .andExpect(status().isOk());
        }

        // 5. Test Regenerate Endpoint: POST /api/plan/today/regenerate
        mockMvc.perform(post("/api/plan/today/regenerate")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").exists());
    }
}
