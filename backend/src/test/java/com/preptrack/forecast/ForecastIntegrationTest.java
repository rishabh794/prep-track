package com.preptrack.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.dto.RegisterRequest;
import com.preptrack.onboarding.dto.OnboardingInterviewRequest;
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

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ForecastIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SheetRepository sheetRepository;

    private String authToken;
    private Long interviewId;

    @BeforeEach
    void setUp() throws Exception {
        // Register user 1
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("forecastuser@example.com")
                .password("password123")
                .displayName("Forecast Tester")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        authToken = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("token").asText();

        // Select Blind 75 sheet
        Sheet blind75 = sheetRepository.findByName("Blind 75").orElseThrow();
        SheetSelectionRequest sheetReq = SheetSelectionRequest.builder()
                .sheetIds(List.of(blind75.getId()))
                .build();

        mockMvc.perform(post("/api/onboarding/sheets")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sheetReq)))
                .andExpect(status().isOk());

        // Create upcoming interview for Google
        LocalDate interviewDate = LocalDate.now().plusDays(20);
        OnboardingInterviewRequest interviewReq = OnboardingInterviewRequest.builder()
                .companyName("Google")
                .role("Software Engineer")
                .interviewDate(interviewDate)
                .notes("Algorithm rounds")
                .build();

        MvcResult interviewResult = mockMvc.perform(post("/api/onboarding/interviews")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(interviewReq)))
                .andExpect(status().isCreated())
                .andReturn();

        interviewId = objectMapper.readTree(interviewResult.getResponse().getContentAsString()).get("interviewId").asLong();
    }

    @Test
    @DisplayName("GET /api/forecast/interviews/{id} returns projections, readiness score, and recommendations")
    void testGetForecastSuccess() throws Exception {
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewId", is(interviewId.intValue())))
                .andExpect(jsonPath("$.companyName", is("Google")))
                .andExpect(jsonPath("$.daysRemaining", is(20)))
                .andExpect(jsonPath("$.overallReadiness", notNullValue()))
                .andExpect(jsonPath("$.patternProjections", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.patternProjections[0].pattern", notNullValue()))
                .andExpect(jsonPath("$.patternProjections[0].currentSkill", notNullValue()))
                .andExpect(jsonPath("$.patternProjections[0].projectedSkill", notNullValue()))
                .andExpect(jsonPath("$.patternProjections[0].status", notNullValue()))
                .andExpect(jsonPath("$.recommendations", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("GET /api/forecast/interviews/{id} fails when accessing another user's interview")
    void testUnauthorizedInterviewAccess() throws Exception {
        // Register user 2
        RegisterRequest registerReq2 = RegisterRequest.builder()
                .email("otheruser@example.com")
                .password("password123")
                .displayName("Other User")
                .build();

        MvcResult regResult2 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq2)))
                .andExpect(status().isCreated())
                .andReturn();

        String token2 = objectMapper.readTree(regResult2.getResponse().getContentAsString()).get("token").asText();

        // User 2 attempts to view User 1's interview forecast
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }
}
