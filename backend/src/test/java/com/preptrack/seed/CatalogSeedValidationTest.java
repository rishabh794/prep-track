package com.preptrack.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.preptrack.seed.CatalogSeed.*;
import static com.preptrack.seed.SeedDataLoader.*;
import static org.assertj.core.api.Assertions.*;

class CatalogSeedValidationTest {
    private final CatalogSeed seed = new CatalogSeedReader(new ObjectMapper()).read();

    @Test
    void releaseFingerprintsIgnoreFormattingLineEndingsAndObjectKeyOrder() throws Exception {
        var mapper = new ObjectMapper();
        byte[] first = "{\n\"a\": 1, \"b\": [2, 3]\n}".getBytes(StandardCharsets.UTF_8);
        byte[] second = "{\r\n\"b\":[2,3],\r\n\"a\":1\r\n}".getBytes(StandardCharsets.UTF_8);
        assertThat(SeedChecksums.canonicalJson(mapper, first)).isEqualTo(SeedChecksums.canonicalJson(mapper, second));
    }

    @Test
    void bundledReferencesAreValidAndFingerprintIsStable() {
        CatalogSeedReader.validate(seed);
        assertThat(seed.checksum()).hasSize(64).isEqualTo(new CatalogSeedReader(new ObjectMapper()).read().checksum());
        assertThat(seed.sheets()).hasSize(3);
        var neetcode = seed.sheets().stream().filter(s -> s.definition().name().equals("NeetCode 150")).findFirst().orElseThrow();
        assertThat(neetcode.items()).hasSize(117);
        assertThat(neetcode.definition().expectedProblems()).isEqualTo(150);
    }

    @Test
    void rejectsMissingPatternLinksAndUnresolvedPatternSlugs() {
        var p = seed.problems().getFirst();
        for (var links : List.of(List.<PatternTagSeedDto>of(), List.of(new PatternTagSeedDto("missing-pattern", 1.0)))) {
            var bad = new ProblemSeedDto(p.canonicalTitle(), p.difficulty(), p.estimatedMinutes(), p.descriptionHint(),
                    p.referenceApproach(), p.optimalComplexity(), p.sources(), links);
            assertThatThrownBy(() -> CatalogSeedReader.validate(replaceProblem(bad))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsDuplicateSourceIdentitiesAndInvalidTimeEstimates() {
        var p = seed.problems().getFirst();
        var duplicate = new ArrayList<>(p.sources());
        duplicate.add(p.sources().getFirst());
        var bad = new ProblemSeedDto(p.canonicalTitle(), p.difficulty(), p.estimatedMinutes(), p.descriptionHint(),
                p.referenceApproach(), p.optimalComplexity(), duplicate, p.patterns());
        assertThatThrownBy(() -> CatalogSeedReader.validate(replaceProblem(bad))).hasMessageContaining("Duplicate source");
        var badTime = new ProblemSeedDto(p.canonicalTitle(), p.difficulty(), 0, p.descriptionHint(),
                p.referenceApproach(), p.optimalComplexity(), p.sources(), p.patterns());
        assertThatThrownBy(() -> CatalogSeedReader.validate(replaceProblem(badTime))).hasMessageContaining("positive time");
    }

    @Test
    void rejectsUnresolvedSheetReferencesAndDuplicateMemberships() {
        var sheet = seed.sheets().getFirst();
        var brokenItems = new ArrayList<>(sheet.items());
        brokenItems.add(new SheetItemSeedDto(com.preptrack.problem.entity.Platform.LEETCODE, "missing", "test", 999));
        assertThatThrownBy(() -> CatalogSeedReader.validate(replaceSheet(new SheetSeed(sheet.definition(), brokenItems))))
                .hasMessageContaining("Unresolved sheet reference");
        var duplicate = new ArrayList<>(sheet.items());
        duplicate.add(sheet.items().getFirst());
        assertThatThrownBy(() -> CatalogSeedReader.validate(replaceSheet(new SheetSeed(sheet.definition(), duplicate))))
                .hasMessageContaining("Duplicate canonical problem");
    }

    @Test
    void rejectsUnresolvedRelationsAndDuplicatePairs() {
        var relation = seed.relations().getFirst();
        var broken = new ArrayList<>(seed.relations());
        broken.set(0, new RelationSeedDto(relation.sourcePlatform(), "missing", relation.targetPlatform(),
                relation.targetExternalId(), relation.relationType(), relation.notes()));
        assertThatThrownBy(() -> CatalogSeedReader.validate(new CatalogSeed(seed.manifest(), seed.checksum(), seed.patterns(), seed.problems(), seed.sheets(), broken)))
                .hasMessageContaining("Unresolved relation");
        broken.set(0, relation);
        broken.add(relation);
        assertThatThrownBy(() -> CatalogSeedReader.validate(new CatalogSeed(seed.manifest(), seed.checksum(), seed.patterns(), seed.problems(), seed.sheets(), broken)))
                .hasMessageContaining("duplicate relation");
    }

    private CatalogSeed replaceProblem(ProblemSeedDto problem) {
        var problems = new ArrayList<>(seed.problems());
        problems.set(0, problem);
        return new CatalogSeed(seed.manifest(), seed.checksum(), seed.patterns(), problems, seed.sheets(), seed.relations());
    }
    private CatalogSeed replaceSheet(SheetSeed sheet) {
        var sheets = new ArrayList<>(seed.sheets());
        sheets.set(0, sheet);
        return new CatalogSeed(seed.manifest(), seed.checksum(), seed.patterns(), seed.problems(), sheets, seed.relations());
    }
}
