package com.preptrack.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.dto.RegisterRequest;
import com.preptrack.onboarding.dto.OnboardingInterviewRequest;
import com.preptrack.onboarding.dto.SheetSelectionRequest;
import com.preptrack.onboarding.dto.UpdateSettingsRequest;
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

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OnboardingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SheetRepository sheetRepository;

    private String authToken;
    private Long blind75Id;
    private Long neetcode150Id;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("onboardinguser@example.com")
                .password("password123")
                .displayName("Onboarding Tester")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        authToken = objectMapper.readTree(responseJson).get("token").asText();

        Sheet blind75 = sheetRepository.findByName("Blind 75").orElseThrow();
        Sheet neetcode = sheetRepository.findByName("NeetCode 150").orElseThrow();
        blind75Id = blind75.getId();
        neetcode150Id = neetcode.getId();
    }

    @Test
    @DisplayName("Complete onboarding flow: sheet selection with dynamic overlap, settings update, interview creation, and completion")
    void testFullOnboardingFlow() throws Exception {
        // Step 1: Select Sheets (Blind 75 + NeetCode 150)
        SheetSelectionRequest sheetReq = SheetSelectionRequest.builder()
                .sheetIds(List.of(blind75Id, neetcode150Id))
                .build();

        mockMvc.perform(post("/api/onboarding/sheets")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sheetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedSheets", hasItems("Blind 75", "NeetCode 150")))
                .andExpect(jsonPath("$.totalProblemsAcrossSheets", is(65)))
                .andExpect(jsonPath("$.uniqueProblems", is(36)))
                .andExpect(jsonPath("$.overlapProblems", is(29)));

        // Step 2: Update Settings
        UpdateSettingsRequest settingsReq = UpdateSettingsRequest.builder()
                .dailyTimeBudgetMin(120)
                .maxNewProblemsDay(4)
                .maxRevisionsDay(12)
                .targetSkillLevel(0.85)
                .build();

        mockMvc.perform(put("/api/onboarding/settings")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settingsReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyTimeBudgetMin", is(120)))
                .andExpect(jsonPath("$.maxNewProblemsDay", is(4)))
                .andExpect(jsonPath("$.maxRevisionsDay", is(12)))
                .andExpect(jsonPath("$.targetSkillLevel", is(0.85)));

        // Step 3: Add Upcoming Interview (Creates Application + Interview)
        OnboardingInterviewRequest interviewReq = OnboardingInterviewRequest.builder()
                .companyName("Google")
                .role("SDE-1")
                .interviewDate(LocalDate.of(2026, 11, 15))
                .notes("Focus on DP and Graphs")
                .build();

        mockMvc.perform(post("/api/onboarding/interviews")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(interviewReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName", is("Google")))
                .andExpect(jsonPath("$.role", is("SDE-1")))
                .andExpect(jsonPath("$.interviewDate", is("2026-11-15")))
                .andExpect(jsonPath("$.outcome", is("UPCOMING")));

        // Verify Interview is retrieved from GET /api/interviews/upcoming
        mockMvc.perform(get("/api/interviews/upcoming")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].companyName", is("Google")))
                .andExpect(jsonPath("$[0].outcome", is("UPCOMING")));

        // Step 4: Complete Onboarding
        mockMvc.perform(post("/api/onboarding/complete")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingComplete", is(true)));

        // Step 5: Check User's Selected Sheets & Problems
        mockMvc.perform(get("/api/user/sheets")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].sheetName", hasItems("Blind 75", "NeetCode 150")));

        mockMvc.perform(get("/api/user/sheets/" + blind75Id + "/problems")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(29)))
                .andExpect(jsonPath("$[0].canonicalTitle").isString())
                .andExpect(jsonPath("$[0].status", is("NOT_STARTED")))
                .andExpect(jsonPath("$[0].patterns").isArray());
    }
}
