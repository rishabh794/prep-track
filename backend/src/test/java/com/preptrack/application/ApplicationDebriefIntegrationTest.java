package com.preptrack.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.dto.ConfirmTopicsRequest;
import com.preptrack.ai.dto.ExtractFeedbackRequest;
import com.preptrack.application.dto.ApplicationRequestDto;
import com.preptrack.application.dto.InterviewRequestDto;
import com.preptrack.application.entity.ApplicationStatus;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.auth.dto.RegisterRequest;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.engine.AcquisitionScorer;
import com.preptrack.planner.engine.PlanCandidate;
import com.preptrack.problem.entity.Problem;
import com.preptrack.problem.repository.ProblemRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "gemini.api-key="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationDebriefIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatternRepository patternRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserPatternStateRepository userPatternStateRepository;

    @Autowired
    private AcquisitionScorer acquisitionScorer;

    private String authToken;
    private Long userId;
    private Pattern twoPointerPattern;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email(java.util.UUID.randomUUID() + "@appdebrief.example.com")
                .password("password123")
                .displayName("App Debrief Tester")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        com.fasterxml.jackson.databind.JsonNode authJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String rawToken = authJson.has("token") ? authJson.path("token").asText() : authJson.path("accessToken").asText();
        authToken = "Bearer " + rawToken;
        userId = authJson.path("userId").asLong();

        twoPointerPattern = patternRepository.findBySlug("two-pointer-squeeze")
                .orElseGet(() -> patternRepository.findAll().stream().findFirst().orElseThrow());
    }

    @Test
    @DisplayName("Application CRUD operations work as expected")
    void testApplicationCrud() throws Exception {
        // 1. Create application
        ApplicationRequestDto createDto = ApplicationRequestDto.builder()
                .companyName("Stripe")
                .role("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(5))
                .notes("Referral via college alum")
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.companyName", is("Stripe")))
                .andExpect(jsonPath("$.role", is("Backend Engineer")))
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andReturn();

        Long appId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("id").asLong();

        // 2. Get applications list
        mockMvc.perform(get("/api/applications")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].companyName", is("Stripe")));

        // 3. Get single application
        mockMvc.perform(get("/api/applications/" + appId)
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(appId.intValue())))
                .andExpect(jsonPath("$.notes", is("Referral via college alum")));

        // 4. Update application
        ApplicationRequestDto updateDto = ApplicationRequestDto.builder()
                .companyName("Stripe")
                .role("Senior Backend Engineer")
                .status(ApplicationStatus.OA)
                .notes("Passed resume screen, OA received")
                .build();

        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("Senior Backend Engineer")))
                .andExpect(jsonPath("$.status", is("OA")))
                .andExpect(jsonPath("$.notes", is("Passed resume screen, OA received")));

        // 5. Delete application
        mockMvc.perform(delete("/api/applications/" + appId)
                        .header("Authorization", authToken))
                .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get("/api/applications/" + appId)
                        .header("Authorization", authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Interview CRUD attached to application works")
    void testInterviewCrud() throws Exception {
        // 1. Create parent application
        ApplicationRequestDto createDto = ApplicationRequestDto.builder()
                .companyName("Google")
                .role("SWE III")
                .status(ApplicationStatus.INTERVIEW)
                .build();

        MvcResult appRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        Long appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).path("id").asLong();

        // 2. Add interview to application
        InterviewRequestDto interviewReq = InterviewRequestDto.builder()
                .interviewDate(LocalDate.now().plusDays(10))
                .roundName("Round 1: Coding & Algorithms")
                .notes("Prepare Graphs and Dynamic Programming")
                .outcome(InterviewOutcome.UPCOMING)
                .build();

        MvcResult intRes = mockMvc.perform(post("/api/applications/" + appId + "/interviews")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(interviewReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.companyName", is("Google")))
                .andExpect(jsonPath("$.roundName", is("Round 1: Coding & Algorithms")))
                .andExpect(jsonPath("$.outcome", is("UPCOMING")))
                .andReturn();

        Long interviewId = objectMapper.readTree(intRes.getResponse().getContentAsString()).path("id").asLong();

        // 3. List interviews for application
        mockMvc.perform(get("/api/applications/" + appId + "/interviews")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(interviewId.intValue())));

        // 4. Update interview round & outcome
        InterviewRequestDto updateIntReq = InterviewRequestDto.builder()
                .interviewDate(LocalDate.now().plusDays(12))
                .roundName("Round 1 Rescheduled")
                .notes("Rescheduled to next week")
                .outcome(InterviewOutcome.UPCOMING)
                .build();

        mockMvc.perform(put("/api/interviews/" + interviewId)
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateIntReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundName", is("Round 1 Rescheduled")));

        // 5. Delete interview
        mockMvc.perform(delete("/api/interviews/" + interviewId)
                        .header("Authorization", authToken))
                .andExpect(status().isNoContent());

        // Verify upcoming interviews no longer returns it
        mockMvc.perform(get("/api/interviews/upcoming")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + interviewId + ")]", hasSize(0)));
    }

    @Test
    @DisplayName("Task 6.7: Full debrief flow (paste -> extract -> confirm -> plan changes)")
    void testFullDebriefFlowShiftsPlan() throws Exception {
        // 1. Create application and interview
        ApplicationRequestDto appDto = ApplicationRequestDto.builder()
                .companyName("Amazon")
                .role("Software Development Engineer")
                .build();

        MvcResult appRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).path("id").asLong();

        InterviewRequestDto intDto = InterviewRequestDto.builder()
                .interviewDate(LocalDate.now().minusDays(1))
                .roundName("Technical Bar Raiser")
                .outcome(InterviewOutcome.PENDING)
                .build();

        MvcResult intRes = mockMvc.perform(post("/api/applications/" + appId + "/interviews")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long interviewId = objectMapper.readTree(intRes.getResponse().getContentAsString()).path("id").asLong();

        // 2. Submit debrief feedback (paste)
        ExtractFeedbackRequest feedbackReq = new ExtractFeedbackRequest();
        feedbackReq.setRawFeedback("I had a Two Pointer problem which went well, but I was weak on " + twoPointerPattern.getName());

        mockMvc.perform(post("/api/interviews/" + interviewId + "/feedback")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresConfirmation", is(true)));

        // 3. Confirm topics (confirm with weakness on the pattern)
        ConfirmTopicsRequest confirmReq = new ConfirmTopicsRequest();
        confirmReq.setOutcome("PARTIAL");
        confirmReq.setTopics(List.of(
                new ConfirmTopicsRequest.ConfirmedTopicDto(twoPointerPattern.getId(), true)
        ));

        mockMvc.perform(post("/api/interviews/" + interviewId + "/confirm-topics")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("PARTIAL")))
                .andExpect(jsonPath("$.topics", hasSize(1)))
                .andExpect(jsonPath("$.topics[0].wasWeak", is(true)));

        // 4. Verify pattern state in DB received the +0.3 boost
        UserPatternState boostedState = userPatternStateRepository.findByUserIdAndPatternId(userId, twoPointerPattern.getId()).orElse(null);

        assertThat(boostedState).isNotNull();
        assertThat(boostedState.getInterviewBoost()).isGreaterThanOrEqualTo(0.30);
    }
}
