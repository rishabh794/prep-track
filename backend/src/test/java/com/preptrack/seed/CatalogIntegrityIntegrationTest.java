package com.preptrack.seed;

import com.preptrack.attempt.entity.*;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.catalog.CatalogCountStatus;
import com.preptrack.catalog.service.CatalogHealthService;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.problem.repository.*;
import com.preptrack.seed.repository.CatalogSeedVersionRepository;
import com.preptrack.sheet.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.preptrack.seed.CatalogSeed.*;
import static com.preptrack.seed.SeedDataLoader.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogIntegrityIntegrationTest {
    @Autowired CatalogSeedReader reader;
    @Autowired CatalogSeedSyncService sync;
    @Autowired CatalogSeedVersionRepository versions;
    @Autowired CatalogHealthService health;
    @Autowired ProblemRepository problems;
    @Autowired ProblemSourceRepository sources;
    @Autowired ProblemPatternRepository links;
    @Autowired SheetProblemRepository memberships;
    @Autowired ProblemRelationRepository relations;
    @Autowired UserRepository users;
    @Autowired UserProblemAttemptRepository attempts;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    @Test
    void applyingSameReleaseTwiceKeepsIdsCountsAndVersionStable() {
        var seed = reader.read();
        long problemCount = problems.count();
        long sourceCount = sources.count();
        long memberCount = memberships.count();
        long relationCount = relations.count();
        long versionCount = versions.count();
        Long id = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
        sync.apply(seed);
        sync.apply(seed);
        em.flush();
        assertThat(problems.count()).isEqualTo(problemCount);
        assertThat(sources.count()).isEqualTo(sourceCount);
        assertThat(memberships.count()).isEqualTo(memberCount);
        assertThat(relations.count()).isEqualTo(relationCount);
        assertThat(versions.count()).isEqualTo(versionCount);
        assertThat(problems.findByCanonicalTitle("Two Sum").orElseThrow().getId()).isEqualTo(id);
    }

    @Test
    void newerReleaseUpdatesReferenceMetadataUsingStableSourcesAndPreservesAttempts() {
        var seed = reader.read();
        var original = seed.problems().getFirst();
        var problem = problems.findByCanonicalTitle(original.canonicalTitle()).orElseThrow();
        Long id = problem.getId();
        long count = problems.count();
        User user = users.save(User.builder().email(UUID.randomUUID() + "@catalog.test").displayName("Catalog Tester").passwordHash("test-hash").build());
        var attempt = attempts.save(UserProblemAttempt.builder().user(user).problem(problem).outcome(AttemptOutcome.SOLVED_INDEPENDENTLY).notes("Keep this history").build());
        Long attemptId = attempt.getId();
        var definitions = new ArrayList<>(seed.problems());
        definitions.set(0, new ProblemSeedDto(original.canonicalTitle() + " (updated)", original.difficulty(), original.estimatedMinutes() + 1,
                original.descriptionHint(), "Updated approach", original.optimalComplexity(), original.sources(), original.patterns()));
        var manifest = new Manifest(seed.manifest().version() + "-test-update", seed.manifest().patternsResource(),
                seed.manifest().problemsResource(), seed.manifest().relationsResource(), seed.manifest().sheets());
        sync.apply(new CatalogSeed(manifest, "a".repeat(64), seed.patterns(), definitions, seed.sheets(), seed.relations()));
        em.flush();
        em.clear();
        var updated = problems.findByCanonicalTitle(original.canonicalTitle() + " (updated)").orElseThrow();
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getReferenceApproach()).isEqualTo("Updated approach");
        assertThat(updated.getEstimatedMinutes()).isEqualTo(original.estimatedMinutes() + 1);
        assertThat(problems.count()).isEqualTo(count);
        var history = attempts.findById(attemptId).orElseThrow();
        assertThat(history.getProblem().getId()).isEqualTo(id);
        assertThat(history.getNotes()).isEqualTo("Keep this history");
        assertThat(versions.findById(manifest.version())).isPresent();
    }

    @Test
    void changedContentMustUseANewVersion() {
        var seed = reader.read();
        long count = versions.count();
        assertThatThrownBy(() -> sync.apply(new CatalogSeed(seed.manifest(), "b".repeat(64), seed.patterns(), seed.problems(), seed.sheets(), seed.relations())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version bump");
        assertThat(versions.count()).isEqualTo(count);
    }

    @Test
    void rejectsSplittingAnExistingCanonicalProblemAcrossTwoSeedDefinitions() {
        var seed = reader.read();
        var original = seed.problems().getFirst();
        assertThat(original.sources()).hasSizeGreaterThan(1);
        var definitions = new ArrayList<>(seed.problems());
        definitions.set(0, new ProblemSeedDto(original.canonicalTitle(), original.difficulty(), original.estimatedMinutes(),
                original.descriptionHint(), original.referenceApproach(), original.optimalComplexity(),
                List.of(original.sources().getFirst()), original.patterns()));
        definitions.add(new ProblemSeedDto("Conflicting split alias", original.difficulty(), original.estimatedMinutes(),
                original.descriptionHint(), original.referenceApproach(), original.optimalComplexity(),
                original.sources().subList(1, original.sources().size()), original.patterns()));
        var manifest = new Manifest(seed.manifest().version() + "-conflict", seed.manifest().patternsResource(),
                seed.manifest().problemsResource(), seed.manifest().relationsResource(), seed.manifest().sheets());
        var conflicting = new CatalogSeed(manifest, "c".repeat(64), seed.patterns(), definitions, seed.sheets(), seed.relations());
        CatalogSeedReader.validate(conflicting); // valid references alone cannot detect the persisted alias conflict
        assertThatThrownBy(() -> sync.apply(conflicting)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Canonical identity conflict");
    }

    @Test
    void healthDetectsMissingLinksAndSameReleaseRepairsThem() {
        Long id = problems.findByCanonicalTitle("Two Sum").orElseThrow().getId();
        links.deleteAll(links.findByProblemId(id));
        links.flush();
        assertThat(health.inspect().issues()).anyMatch(i -> i.code().equals("PROBLEM_WITHOUT_PATTERNS"));
        sync.apply(reader.read());
        em.flush();
        assertThat(health.inspect().issues()).noneMatch(i -> i.severity().equals("ERROR"));
    }

    @Test
    void healthReportsActualSizesAndKnownIncompleteCatalogWithoutFabricatingEntries() {
        var report = health.inspect();
        assertThat(report.status()).isEqualTo("WARNING");
        var neetcode = report.sheets().stream().filter(s -> s.name().equals("NeetCode 150")).findFirst().orElseThrow();
        assertThat(neetcode.availableProblems()).isEqualTo(117);
        assertThat(neetcode.bundledProblems()).isEqualTo(117);
        assertThat(neetcode.expectedProblems()).isEqualTo(150);
        assertThat(neetcode.countStatus()).isEqualTo(CatalogCountStatus.INCOMPLETE);
        assertThat(neetcode.missingBundledProblems()).isZero();
        var blind = report.sheets().stream().filter(s -> s.name().equals("Blind 75")).findFirst().orElseThrow();
        assertThat(blind.countStatus()).isEqualTo(CatalogCountStatus.COMPLETE);
        assertThat(report.issues()).noneMatch(i -> i.severity().equals("ERROR"));
    }

    @Test
    void legacySourcesWithoutExternalIdsAreReportedAndDoNotBreakSeedRepair() {
        var problem = problems.findByCanonicalTitle("Two Sum").orElseThrow();
        var legacy = sources.save(com.preptrack.problem.entity.ProblemSource.builder().problem(problem)
                .platform(com.preptrack.problem.entity.Platform.GFG).externalId(null)
                .externalUrl("https://example.test/legacy-source").build());
        sources.flush();
        assertThat(health.inspect().issues()).anyMatch(i -> i.code().equals("INVALID_SOURCE_IDENTITY"));
        sync.apply(reader.read());
        em.flush();
        assertThat(sources.findById(legacy.getId())).isPresent();
        assertThat(health.inspect().issues()).noneMatch(i -> i.severity().equals("ERROR"));
    }

    @Test
    void healthDetectsInvalidStoredWeightsAndSeedRepairRestoresThem() {
        var problem = problems.findByCanonicalTitle("Two Sum").orElseThrow();
        var link = links.findByProblemId(problem.getId()).getFirst();
        link.setWeight(0.0);
        links.saveAndFlush(link);
        assertThat(health.inspect().issues()).anyMatch(i -> i.code().equals("INVALID_PATTERN_WEIGHT"));
        sync.apply(reader.read());
        em.flush();
        assertThat(health.inspect().issues()).noneMatch(i -> i.code().equals("INVALID_PATTERN_WEIGHT"));
        assertThat(links.findById(link.getId()).orElseThrow().getWeight()).isPositive();
    }

    @Test
    @WithMockUser
    void authenticatedHealthAndPublicSheetMetadataAreAvailable() throws Exception {
        mvc.perform(get("/api/catalog/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion", not(emptyString())))
                .andExpect(jsonPath("$.counts.problems", greaterThan(0)))
                .andExpect(jsonPath("$.sheets[?(@.name == 'NeetCode 150')].countStatus", contains("INCOMPLETE")));
        mvc.perform(get("/api/sheets/available")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'NeetCode 150')].totalProblems", contains(117)))
                .andExpect(jsonPath("$[?(@.name == 'NeetCode 150')].expectedProblems", contains(150)));
    }

    @Test
    void healthRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/catalog/health")).andExpect(status().isUnauthorized());
    }
}
