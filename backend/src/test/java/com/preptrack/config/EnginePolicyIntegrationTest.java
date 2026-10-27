package com.preptrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"preptrack.engine.default-budget-minutes=60", "preptrack.engine.default-max-new-problems=4",
        "preptrack.engine.default-max-revisions=8", "preptrack.engine.default-target-skill=0.7", "preptrack.engine.recall-minutes=7"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnginePolicyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registrationUsesConfiguredDefaultsAndPolicyEndpointRequiresSignIn() throws Exception {
        mvc.perform(get("/api/planner/policy")).andExpect(status().isUnauthorized());
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@policy.test", "password", "password123", "displayName", "Policy Tester"))))
                .andExpect(status().isCreated()).andReturn();
        String token = "Bearer " + mapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.dailyTimeBudgetMin").value(60))
                .andExpect(jsonPath("$.settings.maxNewProblemsDay").value(4))
                .andExpect(jsonPath("$.settings.maxRevisionsDay").value(8))
                .andExpect(jsonPath("$.settings.targetSkillLevel").value(0.7));
        mvc.perform(get("/api/planner/policy").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recallMinutes").value(7)).andExpect(jsonPath("$.policyVersion").value("1"));
    }
}
