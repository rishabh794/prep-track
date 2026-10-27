package com.preptrack.company;

import com.preptrack.application.entity.Application;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.company.entity.*;
import com.preptrack.company.repository.*;
import com.preptrack.company.service.CompanyProfileService;
import com.preptrack.config.EngineProperties;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.planner.engine.InterviewRelevanceCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyProfileServiceTest {
    @Test
    void usesExactAliasesAndStableSlugsWithoutPerCandidateReads() {
        var profiles = mock(CompanyProfileRepository.class);
        var aliases = mock(CompanyAliasRepository.class);
        var weights = mock(CompanyPatternWeightRepository.class);
        var patterns = mock(PatternRepository.class);
        var service = new CompanyProfileService(profiles, aliases, weights, patterns);
        var profile = CompanyProfile.builder().id(1L).slug("meta").name("Meta").provenance("HEURISTIC")
                .sourceVersion("test-1").sourceNotes("Manual test profile").seedChecksum("0".repeat(64)).build();
        var trie = Pattern.builder().id(1L).slug("trie-prefix-matching").name("Trie Prefix Matching").build();
        when(profiles.findAll()).thenReturn(List.of(profile));
        when(aliases.findAllWithProfile()).thenReturn(List.of(CompanyAlias.builder().companyProfile(profile).alias("facebook").build()));
        when(weights.findAllWithProfileAndPattern()).thenReturn(List.of(CompanyPatternWeight.builder().companyProfile(profile).pattern(trie).weight(0.4).build()));
        when(patterns.findAll()).thenReturn(List.of(trie));
        service.refresh();
        clearInvocations(profiles, aliases, weights, patterns);

        for (int i = 0; i < 200; i++) {
            assertThat(service.relevance("  FACEBOOK  ", trie)).isEqualTo(0.4);
            assertThat(service.relevance("meta", "trie-prefix-matching")).isEqualTo(0.4);
        }
        trie.setName("Renamed display label");
        assertThat(service.relevance("Meta", trie)).isEqualTo(0.4);
        assertThat(service.relevance("Metaverse Startup", trie)).isEqualTo(1.0);
        assertThat(service.relevance("unknown", trie)).isEqualTo(1.0);
        assertThat(service.relevance("Meta", "Partial Trie")).isEqualTo(1.0);
        assertThat(service.relevance("Meta", Pattern.builder().slug("new-pattern").build())).isEqualTo(1.0);
        assertThat(service.get("meta").provenance()).isEqualTo("HEURISTIC");
        verifyNoInteractions(profiles, aliases, weights, patterns);
    }

    @Test
    void calculatorUsesConfiguredWindowsAndSkipsProfilesWithoutRelevantInterview() {
        var profiles = mock(CompanyProfileService.class);
        var policy = new EngineProperties();
        policy.setInterviewProximityWindowDays(7);
        policy.setInterviewProximityMaxBoost(0.8);
        policy.setInterviewBoostDecayDays(4);
        var calculator = new InterviewRelevanceCalculator(mock(InterviewRepository.class), profiles, policy, mock(com.preptrack.common.service.TimeService.class));
        var pattern = Pattern.builder().id(1L).slug("two-pointer-squeeze").build();
        var today = LocalDate.of(2026, 10, 27);

        assertThat(calculator.calculateWithInterviews(List.of(), pattern, today).getRelevanceMultiplier()).isEqualTo(1.0);
        var tooFar = Interview.builder().interviewDate(today.plusDays(8)).build();
        assertThat(calculator.calculateWithInterviews(List.of(tooFar), pattern, today).getRelevanceMultiplier()).isEqualTo(1.0);
        verifyNoInteractions(profiles);

        when(profiles.relevance("Meta", pattern)).thenReturn(1.2);
        var upcoming = Interview.builder().application(Application.builder().companyName("Meta").build()).interviewDate(today.plusDays(3)).build();
        assertThat(calculator.calculateWithInterviews(List.of(upcoming), pattern, today).getRelevanceMultiplier()).isEqualTo(1.75);
        var state = UserPatternState.builder().interviewBoost(0.4).interviewBoostSetAt(today.minusDays(2).atStartOfDay()).build();
        assertThat(calculator.getEffectiveInterviewBoost(state, today)).isEqualTo(0.2);
        assertThat(calculator.getEffectiveInterviewBoost(state, today.plusDays(2))).isZero();
    }
}
