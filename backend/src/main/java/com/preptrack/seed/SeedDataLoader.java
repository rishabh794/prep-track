package com.preptrack.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.problem.entity.*;
import com.preptrack.problem.repository.ProblemRelationRepository;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.problem.repository.ProblemSourceRepository;
import com.preptrack.sheet.entity.Sheet;
import com.preptrack.sheet.entity.SheetProblem;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.SheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader implements CommandLineRunner {

    private final PatternRepository patternRepo;
    private final ProblemRepository problemRepo;
    private final ProblemSourceRepository sourceRepo;
    private final ProblemPatternRepository problemPatternRepo;
    private final SheetRepository sheetRepo;
    private final SheetProblemRepository sheetProblemRepo;
    private final ProblemRelationRepository relationRepo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        if (patternRepo.count() > 0) {
            log.info("Database already seeded. Skipping seed data loading.");
            return;
        }

        log.info("Starting seed data loading...");

        try {
            // 1. Load patterns
            List<Pattern> patterns = loadJson("seed/patterns.json", new TypeReference<List<Pattern>>() {});
            patternRepo.saveAll(patterns);
            log.info("Loaded {} patterns", patterns.size());

            // 2. Load problems with sources and pattern associations
            List<ProblemSeedDto> problemSeeds = loadJson("seed/problems.json", new TypeReference<List<ProblemSeedDto>>() {});
            Map<String, Problem> sourceIndex = new HashMap<>(); // "PLATFORM:externalId" -> Problem

            for (ProblemSeedDto seed : problemSeeds) {
                Problem problem = Problem.builder()
                        .canonicalTitle(seed.canonicalTitle())
                        .difficulty(seed.difficulty())
                        .estimatedMinutes(seed.estimatedMinutes() != null ? seed.estimatedMinutes() : 30)
                        .descriptionHint(seed.descriptionHint())
                        .referenceApproach(seed.referenceApproach())
                        .optimalComplexity(seed.optimalComplexity())
                        .build();

                Problem savedProblem = problemRepo.save(problem);

                if (seed.sources() != null) {
                    for (SourceSeedDto src : seed.sources()) {
                        ProblemSource problemSource = ProblemSource.builder()
                                .problem(savedProblem)
                                .platform(src.platform())
                                .externalId(src.externalId())
                                .externalUrl(src.url())
                                .externalTitle(src.title())
                                .build();
                        sourceRepo.save(problemSource);
                        sourceIndex.put(src.platform().name() + ":" + src.externalId(), savedProblem);
                    }
                }

                if (seed.patterns() != null) {
                    for (PatternTagSeedDto tag : seed.patterns()) {
                        Pattern pattern = patternRepo.findBySlug(tag.slug())
                                .orElseThrow(() -> new IllegalStateException("Unknown pattern slug: " + tag.slug()));
                        ProblemPattern problemPattern = ProblemPattern.builder()
                                .problem(savedProblem)
                                .pattern(pattern)
                                .weight(tag.weight() != null ? tag.weight() : 1.0)
                                .build();
                        problemPatternRepo.save(problemPattern);
                    }
                }
            }
            log.info("Loaded {} canonical problems with sources and pattern tags", problemSeeds.size());

            // 3. Load sheets
            loadSheet("Blind 75", "Curated list of 75 essential LeetCode questions", "https://leetcode.com/discuss/general-discussion/460599/blind-75-leetcode-questions", "seed/blind75.json", sourceIndex);
            loadSheet("NeetCode 150", "The premier structured 150 DSA practice list", "https://neetcode.io/practice", "seed/neetcode150.json", sourceIndex);
            loadSheet("Striver A-Z", "Comprehensive DSA Course covering basics to advanced topics", "https://takeuforward.org/strivers-a2z-dsa-course/strivers-a2z-dsa-course-sheet-2", "seed/striver_a2z.json", sourceIndex);

            // 4. Load relations
            List<RelationSeedDto> relationSeeds = loadJson("seed/relations.json", new TypeReference<List<RelationSeedDto>>() {});
            int relationCount = 0;
            for (RelationSeedDto rel : relationSeeds) {
                Problem source = sourceIndex.get(rel.sourcePlatform().name() + ":" + rel.sourceExternalId());
                Problem target = sourceIndex.get(rel.targetPlatform().name() + ":" + rel.targetExternalId());
                if (source != null && target != null) {
                    ProblemRelation relation = ProblemRelation.builder()
                            .sourceProblem(source)
                            .targetProblem(target)
                            .relationType(rel.relationType())
                            .reviewed(true)
                            .notes(rel.notes())
                            .build();
                    relationRepo.save(relation);
                    relationCount++;
                } else {
                    log.warn("Could not find problems for relation: {} -> {}", rel.sourceExternalId(), rel.targetExternalId());
                }
            }
            log.info("Loaded {} problem relations", relationCount);

            // 5. Compute and log dynamic overlap
            Set<Long> b75 = sheetProblemRepo.findProblemIdsBySheetName("Blind 75");
            Set<Long> nc150 = sheetProblemRepo.findProblemIdsBySheetName("NeetCode 150");
            Set<Long> striver = sheetProblemRepo.findProblemIdsBySheetName("Striver A-Z");

            Set<Long> b75NcOverlap = new HashSet<>(b75);
            b75NcOverlap.retainAll(nc150);

            Set<Long> allUnion = new HashSet<>(b75);
            allUnion.addAll(nc150);
            allUnion.addAll(striver);

            log.info("==================================================");
            log.info("=== PREPTRACK SEED DATA LOADED SUCCESSFULLY ===");
            log.info("Total Patterns: {}", patternRepo.count());
            log.info("Total Problems: {}", problemRepo.count());
            log.info("Total Problem Sources: {}", sourceRepo.count());
            log.info("Total Relations: {}", relationRepo.count());
            log.info("Blind 75 Sheet: {} problems", b75.size());
            log.info("NeetCode 150 Sheet: {} problems", nc150.size());
            log.info("Striver A-Z Sheet: {} problems", striver.size());
            log.info("Blind 75 & NeetCode 150 Overlap: {} shared problems", b75NcOverlap.size());
            log.info("Total Unique Combined Problems across Sheets: {}", allUnion.size());
            log.info("==================================================");

        } catch (Exception e) {
            log.error("Failed to load seed data", e);
            throw new RuntimeException("Seed data load failure", e);
        }
    }

    private void loadSheet(String name, String description, String url, String seedFile, Map<String, Problem> sourceIndex) throws Exception {
        Sheet sheet = sheetRepo.save(Sheet.builder()
                .name(name)
                .description(description)
                .sourceUrl(url)
                .isActive(true)
                .build());

        List<SheetItemSeedDto> items = loadJson(seedFile, new TypeReference<List<SheetItemSeedDto>>() {});
        List<SheetProblem> sheetProblems = new ArrayList<>();

        for (SheetItemSeedDto item : items) {
            Problem problem = sourceIndex.get(item.sourcePlatform().name() + ":" + item.sourceExternalId());
            if (problem != null) {
                sheetProblems.add(SheetProblem.builder()
                        .sheet(sheet)
                        .problem(problem)
                        .category(item.category())
                        .displayOrder(item.displayOrder())
                        .build());
            } else {
                log.warn("Sheet '{}' referenced missing problem: {}:{}", name, item.sourcePlatform(), item.sourceExternalId());
            }
        }

        sheetProblemRepo.saveAll(sheetProblems);
        log.info("Loaded sheet '{}' with {} problems", name, sheetProblems.size());
    }

    private <T> T loadJson(String path, TypeReference<T> typeRef) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, typeRef);
        }
    }

    public record ProblemSeedDto(
            String canonicalTitle,
            Difficulty difficulty,
            Integer estimatedMinutes,
            String descriptionHint,
            String referenceApproach,
            String optimalComplexity,
            List<SourceSeedDto> sources,
            List<PatternTagSeedDto> patterns
    ) {}

    public record SourceSeedDto(
            Platform platform,
            String externalId,
            String url,
            String title
    ) {}

    public record PatternTagSeedDto(
            String slug,
            Double weight
    ) {}

    public record SheetItemSeedDto(
            Platform sourcePlatform,
            String sourceExternalId,
            String category,
            Integer displayOrder
    ) {}

    public record RelationSeedDto(
            Platform sourcePlatform,
            String sourceExternalId,
            Platform targetPlatform,
            String targetExternalId,
            RelationType relationType,
            String notes
    ) {}
}
