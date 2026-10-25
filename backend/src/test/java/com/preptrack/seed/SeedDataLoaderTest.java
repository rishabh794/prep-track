package com.preptrack.seed;

import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.problem.entity.Problem;
import com.preptrack.problem.entity.ProblemRelation;
import com.preptrack.problem.entity.RelationType;
import com.preptrack.problem.repository.ProblemRelationRepository;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.problem.repository.ProblemSourceRepository;
import com.preptrack.sheet.entity.SheetProblem;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.SheetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeedDataLoaderTest {

    @Autowired
    private PatternRepository patternRepo;

    @Autowired
    private ProblemRepository problemRepo;

    @Autowired
    private ProblemSourceRepository sourceRepo;

    @Autowired
    private SheetRepository sheetRepo;

    @Autowired
    private SheetProblemRepository sheetProblemRepo;

    @Autowired
    private ProblemRelationRepository relationRepo;

    @Test
    void shouldLoadAllPatternsAndProblems() {
        assertTrue(patternRepo.count() >= 30, "Should have loaded patterns");
        assertTrue(problemRepo.count() >= 30, "Should have loaded canonical problems");
        assertTrue(sourceRepo.count() >= 30, "Should have loaded problem sources");
    }

    @Test
    void shouldLoadAllThreeSheets() {
        assertTrue(sheetRepo.findByName("Blind 75").isPresent(), "Blind 75 sheet must exist");
        assertTrue(sheetRepo.findByName("NeetCode 150").isPresent(), "NeetCode 150 sheet must exist");
        assertTrue(sheetRepo.findByName("Striver A-Z").isPresent(), "Striver A-Z sheet must exist");
    }

    @Test
    void shouldDynamicallyComputeOverlapBetweenSheets() {
        Set<Long> b75 = sheetProblemRepo.findProblemIdsBySheetName("Blind 75");
        Set<Long> nc150 = sheetProblemRepo.findProblemIdsBySheetName("NeetCode 150");
        Set<Long> striver = sheetProblemRepo.findProblemIdsBySheetName("Striver A-Z");

        assertFalse(b75.isEmpty(), "Blind 75 should have problems");
        assertFalse(nc150.isEmpty(), "NeetCode 150 should have problems");
        assertFalse(striver.isEmpty(), "Striver A-Z should have problems");

        Set<Long> overlapB75Nc = new HashSet<>(b75);
        overlapB75Nc.retainAll(nc150);
        assertTrue(overlapB75Nc.size() > 20, "Blind 75 and NeetCode 150 must have significant overlap");

        Set<Long> overlapNcStriver = new HashSet<>(nc150);
        overlapNcStriver.retainAll(striver);
        assertTrue(overlapNcStriver.size() > 20, "NeetCode 150 and Striver A-Z must have significant overlap");
    }

    @Test
    void shouldDeduplicateSharedProblemsAcrossSheets() {
        // Find problem "Two Sum" in Blind 75 and NeetCode 150
        Problem twoSum = problemRepo.findByCanonicalTitle("Two Sum")
                .orElseThrow(() -> new AssertionError("Two Sum problem not found"));

        List<SheetProblem> b75TwoSum = sheetProblemRepo.findBySheetIdOrderByDisplayOrderAsc(
                sheetRepo.findByName("Blind 75").orElseThrow().getId()
        ).stream().filter(sp -> sp.getProblem().getId().equals(twoSum.getId())).toList();

        List<SheetProblem> ncTwoSum = sheetProblemRepo.findBySheetIdOrderByDisplayOrderAsc(
                sheetRepo.findByName("NeetCode 150").orElseThrow().getId()
        ).stream().filter(sp -> sp.getProblem().getId().equals(twoSum.getId())).toList();

        assertEquals(1, b75TwoSum.size(), "Two Sum should appear once in Blind 75");
        assertEquals(1, ncTwoSum.size(), "Two Sum should appear once in NeetCode 150");
        assertEquals(b75TwoSum.get(0).getProblem().getId(), ncTwoSum.get(0).getProblem().getId(),
                "Two Sum must share the EXACT SAME problem entity ID across different sheets (canonical deduplication)");
    }

    @Test
    void shouldLoadEasierPrerequisitesForPatternGap() {
        Problem trappingRainWater = problemRepo.findByCanonicalTitle("Trapping Rain Water")
                .orElseThrow(() -> new AssertionError("Trapping Rain Water not found"));

        List<ProblemRelation> prerequisites = relationRepo.findBySourceProblemIdAndRelationType(
                trappingRainWater.getId(), RelationType.EASIER_PREREQUISITE
        );

        assertFalse(prerequisites.isEmpty(), "Trapping Rain Water must have an EASIER_PREREQUISITE relation");
        assertEquals("Next Greater Element I", prerequisites.get(0).getTargetProblem().getCanonicalTitle(),
                "Next Greater Element I must be the easier prerequisite for Trapping Rain Water");
    }
}
