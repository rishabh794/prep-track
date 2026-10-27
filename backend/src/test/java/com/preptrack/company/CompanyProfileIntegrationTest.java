package com.preptrack.company;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.company.repository.*;
import com.preptrack.company.service.*;
import com.preptrack.pattern.repository.PatternRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CompanyProfileService profiles;
    @Autowired CompanyProfileSeedService seeds;
    @Autowired CompanyProfileRepository profileRepository;
    @Autowired CompanyAliasRepository aliasRepository;
    @Autowired CompanyPatternWeightRepository weightRepository;
    @Autowired PatternRepository patterns;

    private CompanyProfileSeedService.SeedFile seedFile() throws Exception {
        try (var stream = new ClassPathResource("seed/company-profiles.json").getInputStream()) {
            return mapper.readValue(stream, CompanyProfileSeedService.SeedFile.class);
        }
    }

    @Test
    void exposesAuthenticatedReadOnlyProfilesAndHonestProvenance() throws Exception {
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@profiles.test", "password", "password123", "displayName", "Profile Tester"))))
                .andExpect(status().isCreated()).andReturn();
        String token = "Bearer " + mapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/companies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/companies").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.slug == 'google')].provenance", contains("HEURISTIC")));
        mvc.perform(get("/api/companies/meta").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aliases", hasItem("facebook")))
                .andExpect(jsonPath("$.sourceNotes", containsString("not measured")));
        mvc.perform(get("/api/companies/resolve").param("name", " Facebook ").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matched", is(true))).andExpect(jsonPath("$.profile.slug", is("meta")));
        mvc.perform(get("/api/companies/resolve").param("name", "Google-ish Startup").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matched", is(false))).andExpect(jsonPath("$.defaultWeight", is(1.0)));
        mvc.perform(get("/api/companies/missing").header("Authorization", token)).andExpect(status().isNotFound());
        var trie = patterns.findBySlug("trie-prefix-matching").orElseThrow();
        assertThat(profiles.relevance("Unknown", trie)).isEqualTo(1.0);
        assertThat(profiles.relevance("Google", trie)).isEqualTo(0.4);
    }

    @Test
    @Transactional
    void seedSyncIsIdempotentAndRepairsMissingWeights() throws Exception {
        var file = seedFile();
        long profileCount = profileRepository.count();
        long aliasCount = aliasRepository.count();
        long weightCount = weightRepository.count();
        String checksum = profileRepository.findBySlug("google").orElseThrow().getSeedChecksum();
        seeds.synchronize(file, checksum);
        seeds.synchronize(file, checksum);
        assertThat(profileRepository.count()).isEqualTo(profileCount);
        assertThat(aliasRepository.count()).isEqualTo(aliasCount);
        assertThat(weightRepository.count()).isEqualTo(weightCount);
        var weight = weightRepository.findAllWithProfileAndPattern().getFirst();
        weightRepository.delete(weight);
        weightRepository.flush();
        seeds.synchronize(file, checksum);
        assertThat(weightRepository.count()).isEqualTo(weightCount);
    }

    @Test
    @Transactional
    void seedUpdatesWeightsByVersionAndRejectsUnknownPatternBeforeWriting() throws Exception {
        var original = seedFile();
        var google = original.profiles().stream().filter(p -> p.slug().equals("google")).findFirst().orElseThrow();
        var modified = new CompanyProfileSeedService.ProfileSeed(google.slug(), google.name(), google.aliases(), google.provenance(), google.sourceNotes(), Map.of("trie-prefix-matching", 0.9));
        seeds.synchronize(new CompanyProfileSeedService.SeedFile("test-2", List.of(modified)), "1".repeat(64));
        var updated = profileRepository.findBySlug("google").orElseThrow();
        assertThat(updated.getSourceVersion()).isEqualTo("test-2");
        var stored = weightRepository.findAllWithProfileAndPattern().stream().filter(w -> w.getCompanyProfile().getId().equals(updated.getId())).toList();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getWeight()).isEqualTo(0.9);
        var invalid = new CompanyProfileSeedService.ProfileSeed("newco", "New Co", List.of("newco"), "HEURISTIC", "Test", Map.of("missing-pattern", 0.5));
        long count = profileRepository.count();
        assertThatThrownBy(() -> seeds.synchronize(new CompanyProfileSeedService.SeedFile("test-3", List.of(invalid)), "2".repeat(64)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid company pattern weight");
        assertThat(profileRepository.count()).isEqualTo(count);
    }

    @Test
    @Transactional
    void changedCompanyContentRequiresANewSourceVersion() throws Exception {
        assertThatThrownBy(() -> seeds.synchronize(seedFile(), "f".repeat(64)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version bump");
    }
}
