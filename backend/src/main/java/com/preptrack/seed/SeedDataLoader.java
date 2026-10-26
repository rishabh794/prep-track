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
        log.info("Starting optimized seed data sync...");

        try {
            // 1. Ensure patterns exist
            List<Pattern> patterns = loadJson("seed/patterns.json", new TypeReference<List<Pattern>>() {});
            Map<String, Pattern> patternMap = new HashMap<>();
            for (Pattern p : patternRepo.findAll()) {
                patternMap.put(p.getSlug(), p);
            }
            List<Pattern> newPatterns = new ArrayList<>();
            for (Pattern pattern : patterns) {
                if (!patternMap.containsKey(pattern.getSlug())) {
                    newPatterns.add(pattern);
                }
            }
            if (!newPatterns.isEmpty()) {
                List<Pattern> saved = patternRepo.saveAll(newPatterns);
                for (Pattern p : saved) {
                    patternMap.put(p.getSlug(), p);
                }
            }
            log.info("Patterns verified. Total patterns: {}", patternMap.size());

            // 2. Pre-fetch existing problems, sources, and problem-patterns
            Map<String, Problem> problemMap = new HashMap<>();
            for (Problem p : problemRepo.findAll()) {
                problemMap.put(p.getCanonicalTitle(), p);
            }

            Map<String, Problem> sourceIndex = new HashMap<>();
            for (ProblemSource ps : sourceRepo.findAllWithProblem()) {
                sourceIndex.put(ps.getPlatform().name() + ":" + ps.getExternalId(), ps.getProblem());
            }

            Map<Long, Set<String>> existingPatternsByProblemId = new HashMap<>();
            for (ProblemPattern pp : problemPatternRepo.findAllWithPatternAndProblem()) {
                existingPatternsByProblemId
                        .computeIfAbsent(pp.getProblem().getId(), k -> new HashSet<>())
                        .add(pp.getPattern().getSlug());
            }

            List<ProblemSeedDto> problemSeeds = loadJson("seed/problems.json", new TypeReference<List<ProblemSeedDto>>() {});

            // First pass: save any missing problems in batch
            List<Problem> newProblems = new ArrayList<>();
            for (ProblemSeedDto seed : problemSeeds) {
                if (!problemMap.containsKey(seed.canonicalTitle())) {
                    Problem p = Problem.builder()
                            .canonicalTitle(seed.canonicalTitle())
                            .difficulty(seed.difficulty())
                            .estimatedMinutes(seed.estimatedMinutes() != null ? seed.estimatedMinutes() : 30)
                            .descriptionHint(seed.descriptionHint())
                            .referenceApproach(seed.referenceApproach())
                            .optimalComplexity(seed.optimalComplexity())
                            .build();
                    newProblems.add(p);
                }
            }
            if (!newProblems.isEmpty()) {
                List<Problem> saved = problemRepo.saveAll(newProblems);
                for (Problem p : saved) {
                    problemMap.put(p.getCanonicalTitle(), p);
                }
                log.info("Batch saved {} new canonical problems", saved.size());
            }

            // Second pass: save any missing sources and pattern associations in batch
            List<ProblemSource> newSources = new ArrayList<>();
            List<ProblemPattern> newProblemPatterns = new ArrayList<>();

            for (ProblemSeedDto seed : problemSeeds) {
                Problem problem = problemMap.get(seed.canonicalTitle());
                if (problem == null) continue;

                if (seed.sources() != null) {
                    for (SourceSeedDto src : seed.sources()) {
                        String key = src.platform().name() + ":" + src.externalId();
                        if (!sourceIndex.containsKey(key)) {
                            ProblemSource ps = ProblemSource.builder()
                                    .problem(problem)
                                    .platform(src.platform())
                                    .externalId(src.externalId())
                                    .externalUrl(src.url())
                                    .externalTitle(src.title())
                                    .build();
                            newSources.add(ps);
                            sourceIndex.put(key, problem);
                        }
                    }
                }

                if (seed.patterns() != null) {
                    Set<String> attached = existingPatternsByProblemId.computeIfAbsent(problem.getId(), k -> new HashSet<>());
                    for (PatternTagSeedDto tag : seed.patterns()) {
                        if (!attached.contains(tag.slug())) {
                            Pattern pat = patternMap.get(tag.slug());
                            if (pat != null) {
                                ProblemPattern pp = ProblemPattern.builder()
                                        .problem(problem)
                                        .pattern(pat)
                                        .weight(tag.weight() != null ? tag.weight() : 1.0)
                                        .build();
                                newProblemPatterns.add(pp);
                                attached.add(tag.slug());
                            }
                        }
                    }
                }
            }

            if (!newSources.isEmpty()) {
                sourceRepo.saveAll(newSources);
                log.info("Batch saved {} new problem sources", newSources.size());
            }
            if (!newProblemPatterns.isEmpty()) {
                problemPatternRepo.saveAll(newProblemPatterns);
                log.info("Batch saved {} new problem-pattern associations", newProblemPatterns.size());
            }

            // 3. Batch sync sheets
            syncSheet("Blind 75", "Curated list of 75 essential LeetCode questions", "https://leetcode.com/discuss/general-discussion/460599/blind-75-leetcode-questions", "seed/blind75.json", sourceIndex);
            syncSheet("NeetCode 150", "The premier structured 150 DSA practice list", "https://neetcode.io/practice", "seed/neetcode150.json", sourceIndex);
            syncSheet("Striver A-Z", "Comprehensive DSA Course covering basics to advanced topics", "https://takeuforward.org/strivers-a2z-dsa-course/strivers-a2z-dsa-course-sheet-2", "seed/striver_a2z.json", sourceIndex);

            // 4. Batch load missing relations
            loadMissingRelations(sourceIndex);

            // 5. Dynamic overlap logging
            Set<Long> b75 = sheetProblemRepo.findProblemIdsBySheetName("Blind 75");
            Set<Long> nc150 = sheetProblemRepo.findProblemIdsBySheetName("NeetCode 150");
            Set<Long> striver = sheetProblemRepo.findProblemIdsBySheetName("Striver A-Z");

            Set<Long> b75NcOverlap = new HashSet<>(b75);
            b75NcOverlap.retainAll(nc150);

            Set<Long> allUnion = new HashSet<>(b75);
            allUnion.addAll(nc150);
            allUnion.addAll(striver);

            log.info("==================================================");
            log.info("=== PREPTRACK SEED DATA SYNCED SUCCESSFULLY ===");
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
            log.error("Failed to sync seed data", e);
            throw new RuntimeException("Seed data sync failure", e);
        }
    }

    private void syncSheet(String name, String description, String url, String seedFile, Map<String, Problem> sourceIndex) throws Exception {
        Sheet sheet = sheetRepo.findByName(name).orElseGet(() ->
                sheetRepo.save(Sheet.builder()
                        .name(name)
                        .description(description)
                        .sourceUrl(url)
                        .isActive(true)
                        .build())
        );

        Set<Long> existingProblemIds = sheetProblemRepo.findProblemIdsBySheetName(name);
        List<SheetItemSeedDto> items = loadJson(seedFile, new TypeReference<List<SheetItemSeedDto>>() {});
        List<SheetProblem> toAdd = new ArrayList<>();

        for (SheetItemSeedDto item : items) {
            Problem problem = sourceIndex.get(item.sourcePlatform().name() + ":" + item.sourceExternalId());
            if (problem != null) {
                if (!existingProblemIds.contains(problem.getId())) {
                    toAdd.add(SheetProblem.builder()
                            .sheet(sheet)
                            .problem(problem)
                            .category(item.category())
                            .displayOrder(item.displayOrder())
                            .build());
                    existingProblemIds.add(problem.getId());
                }
            } else {
                log.warn("Sheet '{}' referenced missing problem: {}:{}", name, item.sourcePlatform(), item.sourceExternalId());
            }
        }

        if (!toAdd.isEmpty()) {
            sheetProblemRepo.saveAll(toAdd);
        }

        log.info("Sheet '{}' synced ({} newly added, total: {})", name, toAdd.size(), sheetProblemRepo.countBySheetId(sheet.getId()));
    }

    private void loadMissingRelations(Map<String, Problem> sourceIndex) {
        try {
            List<ProblemRelation> existingRelations = relationRepo.findAll();
            Set<String> existingPairs = new HashSet<>();
            for (ProblemRelation pr : existingRelations) {
                existingPairs.add(pr.getSourceProblem().getId() + "->" + pr.getTargetProblem().getId());
            }

            List<RelationSeedDto> relationSeeds = loadJson("seed/relations.json", new TypeReference<List<RelationSeedDto>>() {});
            List<ProblemRelation> newRelations = new ArrayList<>();

            for (RelationSeedDto rel : relationSeeds) {
                Problem src = sourceIndex.get(rel.sourcePlatform().name() + ":" + rel.sourceExternalId());
                Problem tgt = sourceIndex.get(rel.targetPlatform().name() + ":" + rel.targetExternalId());
                if (src != null && tgt != null) {
                    String pairKey = src.getId() + "->" + tgt.getId();
                    if (!existingPairs.contains(pairKey)) {
                        newRelations.add(ProblemRelation.builder()
                                .sourceProblem(src)
                                .targetProblem(tgt)
                                .relationType(rel.relationType())
                                .reviewed(true)
                                .notes(rel.notes())
                                .build());
                        existingPairs.add(pairKey);
                    }
                }
            }
            if (!newRelations.isEmpty()) {
                relationRepo.saveAll(newRelations);
                log.info("Synced {} new relations. Total relations in DB: {}", newRelations.size(), relationRepo.count());
            } else {
                log.info("Relations up to date (Total: {})", relationRepo.count());
            }
        } catch (Exception e) {
            log.warn("Could not sync missing relations: {}", e.getMessage());
        }
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
