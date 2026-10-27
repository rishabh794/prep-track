package com.preptrack.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.dto.RegisterRequest;
import com.preptrack.onboarding.dto.OnboardingInterviewRequest;
import com.preptrack.onboarding.dto.SheetSelectionRequest;
import com.preptrack.sheet.entity.Sheet;
import com.preptrack.sheet.repository.SheetRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import com.preptrack.sheet.entity.UserSheet;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.application.repository.InterviewRepository;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired private UserSheetRepository userSheetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InterviewRepository interviewRepository;

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
        LocalDate interviewDate = LocalDate.now(ZoneOffset.UTC).plusDays(20);
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
                .andExpect(jsonPath("$.status", is("AVAILABLE")))
                .andExpect(jsonPath("$.dailyBudgetMinutes", is(90)))
                .andExpect(jsonPath("$.simulationDays", is(20)))
                .andExpect(jsonPath("$.model", is("PATTERN_APPROXIMATION")))
                .andExpect(jsonPath("$.modelVersion", notNullValue()))
                .andExpect(jsonPath("$.asOfDate", is(LocalDate.now(ZoneOffset.UTC).toString())))
                .andExpect(jsonPath("$.projectedThroughDate", is(LocalDate.now(ZoneOffset.UTC).plusDays(20).toString())))
                .andExpect(jsonPath("$.limitations", hasSize(greaterThan(0))))
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

    @Test
    void noSelectedSheetsReturnsOnboardingStateWithoutInventedReadiness() throws Exception {
        var user = userRepository.findByEmail("forecastuser@example.com").orElseThrow();
        userSheetRepository.deleteAll(userSheetRepository.findByUserIdWithSheet(user.getId()));
        userSheetRepository.flush();
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ONBOARDING_REQUIRED")))
                .andExpect(jsonPath("$.overallReadiness", nullValue()))
                .andExpect(jsonPath("$.patternProjections", hasSize(0)))
                .andExpect(jsonPath("$.simulationDays", is(0)));
    }

    @Test
    void emptySelectedSheetReturnsInsufficientData() throws Exception {
        var user = userRepository.findByEmail("forecastuser@example.com").orElseThrow();
        userSheetRepository.deleteAll(userSheetRepository.findByUserIdWithSheet(user.getId()));
        userSheetRepository.flush();
        var empty = sheetRepository.save(Sheet.builder().name("Empty forecast fixture").isActive(true).build());
        userSheetRepository.saveAndFlush(UserSheet.builder().user(user).sheet(empty).build());
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("INSUFFICIENT_DATA")))
                .andExpect(jsonPath("$.patternProjections", hasSize(0))).andExpect(jsonPath("$.overallReadiness", nullValue()));
    }

    @Test
    void settingsAndHorizonAreReportedAccurately() throws Exception {
        mockMvc.perform(put("/api/onboarding/settings").header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyTimeBudgetMin\":45,\"maxNewProblemsDay\":1,\"maxRevisionsDay\":2,\"targetSkillLevel\":0.6}"))
                .andExpect(status().isOk());
        var interview = interviewRepository.findById(interviewId).orElseThrow();
        interview.setInterviewDate(LocalDate.now(ZoneOffset.UTC).plusDays(100));
        interviewRepository.saveAndFlush(interview);
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyBudgetMinutes", is(45)))
                .andExpect(jsonPath("$.maxNewProblemsDay", is(1))).andExpect(jsonPath("$.maxRevisionsDay", is(2)))
                .andExpect(jsonPath("$.targetSkillLevel", is(0.6))).andExpect(jsonPath("$.simulationDays", is(60)))
                .andExpect(jsonPath("$.horizonCapped", is(true)))
                .andExpect(jsonPath("$.projectedThroughDate", is(LocalDate.now(ZoneOffset.UTC).plusDays(60).toString())));
    }

    @Test
    void pastInterviewDoesNotProduceAFutureForecast() throws Exception {
        var interview = interviewRepository.findById(interviewId).orElseThrow();
        interview.setInterviewDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        interviewRepository.saveAndFlush(interview);
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("INTERVIEW_PASSED")))
                .andExpect(jsonPath("$.simulationDays", is(0))).andExpect(jsonPath("$.overallReadiness", nullValue()));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/forecast/interviews/" + interviewId)).andExpect(status().isUnauthorized());
    }
}
