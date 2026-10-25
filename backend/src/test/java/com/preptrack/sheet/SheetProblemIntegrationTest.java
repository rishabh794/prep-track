package com.preptrack.sheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.dto.RegisterRequest;
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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SheetProblemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProblemRepository problemRepository;

    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("sheetuser@example.com")
                .password("password123")
                .displayName("Sheet Tester")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        authToken = objectMapper.readTree(responseJson).get("token").asText();
    }

    @Test
    @DisplayName("GET /api/sheets/available should be public and return seeded sheets")
    void getAvailableSheetsPublic() throws Exception {
        mockMvc.perform(get("/api/sheets/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[*].name", hasItems("Blind 75", "NeetCode 150", "Striver A-Z")))
                .andExpect(jsonPath("$[?(@.name == 'Blind 75')].totalProblems").value(hasItem(29)))
                .andExpect(jsonPath("$[?(@.name == 'NeetCode 150')].totalProblems").value(hasItem(36)));
    }

    @Test
    @DisplayName("GET /api/problems/{id} should return canonical problem details with sources and patterns")
    void getProblemDetail() throws Exception {
        Problem twoSum = problemRepository.findByCanonicalTitle("Two Sum")
                .orElseThrow(() -> new AssertionError("Two Sum not found in seed"));

        mockMvc.perform(get("/api/problems/" + twoSum.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalTitle", is("Two Sum")))
                .andExpect(jsonPath("$.difficulty", is("EASY")))
                .andExpect(jsonPath("$.referenceApproach", containsString("hash map")))
                .andExpect(jsonPath("$.optimalComplexity", containsString("O(n)")))
                .andExpect(jsonPath("$.patterns", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.sources", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/problems/{id}/sources should return all platform sources")
    void getProblemSources() throws Exception {
        Problem trappingRainWater = problemRepository.findByCanonicalTitle("Trapping Rain Water")
                .orElseThrow(() -> new AssertionError("Trapping Rain Water not found"));

        mockMvc.perform(get("/api/problems/" + trappingRainWater.getId() + "/sources")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].platform", hasItems("LEETCODE", "GFG")));
    }

    @Test
    @DisplayName("Test: EASIER_PREREQUISITE relation exists for Trapping Rain Water -> Next Greater Element I")
    void getProblemRelationsEasierPrerequisite() throws Exception {
        Problem trappingRainWater = problemRepository.findByCanonicalTitle("Trapping Rain Water")
                .orElseThrow(() -> new AssertionError("Trapping Rain Water not found"));

        mockMvc.perform(get("/api/problems/" + trappingRainWater.getId() + "/relations")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.relatedProblemTitle == 'Next Greater Element I')].relationType")
                        .value(hasItem("EASIER_PREREQUISITE")));
    }
}
