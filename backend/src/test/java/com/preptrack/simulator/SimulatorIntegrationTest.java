package com.preptrack.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.planner.repository.DailyPlanRepository;
import com.preptrack.simulator.dto.SimulationConfig;
import com.preptrack.simulator.service.*;
import com.preptrack.simulator.engine.SimulationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulatorIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserProblemAttemptRepository attempts;
    @Autowired UserRepository users;
    @Autowired DemoDataService demo;
    @Autowired DailyPlanRepository plans;
    @Autowired SimulatorService simulator;
    @Autowired SimulationEngine engine;
    @Autowired SimulationCatalogService catalogs;

    private String register() throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@sim.test", "password", "password123", "displayName", "Simulator Tester"));
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + mapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void runsPersistsAndRetrievesReportsWithoutChangingStudyHistory() throws Exception {
        String owner = register();
        long before = attempts.count();
        var response = mvc.perform(post("/api/simulator/run").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":3,\"learnerCount\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.results", hasSize(8)))
                .andExpect(jsonPath("$.comparisons", hasSize(6)))
                .andExpect(jsonPath("$.config.dailyBudgetMinutes", is(90))).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        String id = json.get("runId").asText();
        mvc.perform(get("/api/simulator/results/" + id).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(content().json(json.toString()));
        mvc.perform(get("/api/simulator/results/" + id + "/summary").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Caveat:")));
        mvc.perform(get("/api/simulator/results/" + id).header("Authorization", register()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/simulator/results/" + id)).andExpect(status().isUnauthorized());
        assertThat(attempts.count()).isEqualTo(before);
    }

    @Test
    void validatesConfigAndMissingResults() throws Exception {
        String token = register();
        for (String config : List.of("{\"days\":0}", "{\"learnerCount\":101}", "{\"maxNewProblems\":0,\"maxRevisions\":0}",
                "{\"days\":60,\"learnerCount\":100}", "{\"dailyBudgetMinutes\":500}", "{\"targetSkill\":2}")) {
            mvc.perform(post("/api/simulator/run").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(config))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/simulator/run").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"sheetId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/simulator/results/" + UUID.randomUUID()).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/simulator/results/invalid").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/simulator/run").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void defaultExperimentProducesReportFromRealSeedCatalog() throws Exception {
        var result = engine.run(new SimulationConfig(null, null, null, null, null, null, null, null), catalogs.load(null));
        assertThat(result.results()).hasSize(8);
        assertThat(result.comparisons()).hasSize(6);
        assertThat(result.config().learnerCount()).isEqualTo(50);
        Path directory = Files.createDirectories(Path.of("target", "simulator-proof"));
        Files.writeString(directory.resolve("default-run.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        Files.writeString(directory.resolve("default-run.txt"), result.textSummary());
    }

    @Test
    @Transactional
    void demoCreatesTenBackdatedDaysAndNeverOverwritesAnAccount() {
        String email = UUID.randomUUID() + "@demo.test";
        Long id = demo.generate(email, "demo-password-123");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        var history = plans.findAll().stream().filter(p -> p.getUser().getId().equals(id)).toList();
        assertThat(history).hasSize(10);
        assertThat(history).allSatisfy(p -> {
            assertThat(p.getPlanDate()).isBetween(today.minusDays(10), today.minusDays(1));
            assertThat(p.getGeneratedAt().toLocalDate()).isEqualTo(p.getPlanDate());
            assertThat(p.getTotalEstimatedMin()).isLessThanOrEqualTo(90);
        });
        var logs = attempts.findAll().stream().filter(a -> a.getUser().getId().equals(id)).toList();
        assertThat(logs).isNotEmpty().allSatisfy(a -> assertThat(a.getAttemptedAt().toLocalDate()).isBefore(today));
        long count = attempts.count();
        assertThatThrownBy(() -> demo.generate(email, "different-password")).isInstanceOf(BadRequestException.class);
        assertThat(attempts.count()).isEqualTo(count);
    }
}
