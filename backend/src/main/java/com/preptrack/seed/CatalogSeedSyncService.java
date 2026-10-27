package com.preptrack.seed;

import com.preptrack.pattern.entity.*;
import com.preptrack.pattern.repository.*;
import com.preptrack.problem.entity.*;
import com.preptrack.problem.repository.*;
import com.preptrack.seed.entity.CatalogSeedVersion;
import com.preptrack.seed.repository.CatalogSeedVersionRepository;
import com.preptrack.sheet.entity.*;
import com.preptrack.sheet.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.preptrack.seed.CatalogSeedReader.sourceKey;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogSeedSyncService {
    private final PatternRepository patterns;
    private final ProblemRepository problems;
    private final ProblemSourceRepository sources;
    private final ProblemPatternRepository links;
    private final SheetRepository sheets;
    private final SheetProblemRepository memberships;
    private final ProblemRelationRepository relations;
    private final CatalogSeedVersionRepository versions;
    private final Clock clock;

    /** Upsert seed-owned reference metadata atomically; retain canonical identities and all user history. */
    @Transactional
    public void apply(CatalogSeed seed) {
        CatalogSeedReader.validate(seed);
        String version = seed.manifest().version();
        var applied = versions.findById(version);
        if (applied.isPresent() && !applied.get().getChecksum().equals(seed.checksum())) {
            throw new IllegalStateException("Catalog content changed without a version bump: " + version);
        }
        var latest = versions.findFirstByOrderByAppliedAtDesc();
        if (applied.isPresent() && latest.isPresent() && !latest.get().getVersion().equals(version)) {
            throw new IllegalStateException("Refusing to restore older catalog version " + version + " over " + latest.get().getVersion());
        }

        Map<String, Pattern> bySlug = patterns.findAll().stream().collect(Collectors.toMap(Pattern::getSlug, p -> p));
        List<Pattern> addedPatterns = new ArrayList<>();
        for (var definition : seed.patterns()) {
            Pattern pattern = bySlug.get(definition.getSlug());
            if (pattern == null) {
                pattern = Pattern.builder().slug(definition.getSlug()).build();
                bySlug.put(pattern.getSlug(), pattern);
                addedPatterns.add(pattern);
            }
            pattern.setName(definition.getName());
            pattern.setParentTopic(definition.getParentTopic());
            pattern.setDescription(definition.getDescription());
            pattern.setDifficultyTier(definition.getDifficultyTier());
        }
        patterns.saveAll(addedPatterns);

        Map<String, Problem> byTitle = problems.findAll().stream().collect(Collectors.toMap(Problem::getCanonicalTitle, p -> p));
        Map<String, ProblemSource> bySource = sources.findAllWithProblem().stream()
                .filter(s -> CatalogSeedReader.hasSourceIdentity(s.getPlatform(), s.getExternalId()))
                .collect(Collectors.toMap(s -> sourceKey(s.getPlatform(), s.getExternalId()), s -> s));
        Map<String, Problem> seededProblems = new HashMap<>();
        Map<Long, String> resolvedOwners = new HashMap<>();
        List<Problem> addedProblems = new ArrayList<>();
        for (var definition : seed.problems()) {
            Map<Long, Problem> matches = new HashMap<>();
            for (var source : definition.sources()) {
                ProblemSource existing = bySource.get(sourceKey(source.platform(), source.externalId()));
                if (existing != null) matches.put(existing.getProblem().getId(), existing.getProblem());
            }
            if (matches.size() > 1) throw new IllegalStateException("Source identities resolve to different canonical problems: " + definition.canonicalTitle());
            Problem matched = matches.values().stream().findFirst().orElse(null);
            Problem titleMatch = byTitle.get(definition.canonicalTitle());
            if (matched != null && titleMatch != null && !matched.getId().equals(titleMatch.getId())) {
                throw new IllegalStateException("Problem title conflicts with existing source identity: " + definition.canonicalTitle());
            }
            Problem problem = matched != null ? matched : titleMatch;
            if (problem == null) { problem = new Problem(); addedProblems.add(problem); }
            if (problem.getId() != null) {
                String previousOwner = resolvedOwners.putIfAbsent(problem.getId(), definition.canonicalTitle());
                if (previousOwner != null) {
                    throw new IllegalStateException("Canonical identity conflict: " + previousOwner + " and "
                            + definition.canonicalTitle() + " resolve to the same existing problem");
                }
            }
            problem.setCanonicalTitle(definition.canonicalTitle());
            problem.setDifficulty(definition.difficulty());
            problem.setEstimatedMinutes(definition.estimatedMinutes());
            problem.setDescriptionHint(definition.descriptionHint());
            problem.setReferenceApproach(definition.referenceApproach());
            problem.setOptimalComplexity(definition.optimalComplexity());
            seededProblems.put(definition.canonicalTitle(), problem);
        }
        problems.saveAll(addedProblems);

        Map<String, ProblemPattern> byLink = links.findAllWithPatternAndProblem().stream()
                .collect(Collectors.toMap(l -> l.getProblem().getId() + ":" + l.getPattern().getSlug(), l -> l));
        List<ProblemSource> addedSources = new ArrayList<>();
        List<ProblemPattern> addedLinks = new ArrayList<>();
        for (var definition : seed.problems()) {
            Problem problem = seededProblems.get(definition.canonicalTitle());
            for (var source : definition.sources()) {
                String key = sourceKey(source.platform(), source.externalId());
                ProblemSource row = bySource.get(key);
                if (row == null) {
                    row = ProblemSource.builder().problem(problem).platform(source.platform()).externalId(source.externalId()).build();
                    bySource.put(key, row);
                    addedSources.add(row);
                }
                row.setExternalUrl(source.url());
                row.setExternalTitle(source.title());
            }
            for (var link : definition.patterns()) {
                String key = problem.getId() + ":" + link.slug();
                ProblemPattern row = byLink.get(key);
                if (row == null) {
                    row = ProblemPattern.builder().problem(problem).pattern(bySlug.get(link.slug())).build();
                    byLink.put(key, row);
                    addedLinks.add(row);
                }
                row.setWeight(link.weight());
            }
        }
        sources.saveAll(addedSources);
        links.saveAll(addedLinks);

        Map<String, Sheet> bySheet = sheets.findAll().stream().collect(Collectors.toMap(Sheet::getName, s -> s));
        Map<String, SheetProblem> byMembership = memberships.findAllWithSheetAndProblem().stream()
                .collect(Collectors.toMap(m -> m.getSheet().getId() + ":" + m.getProblem().getId(), m -> m));
        List<SheetProblem> addedMemberships = new ArrayList<>();
        for (var sheetSeed : seed.sheets()) {
            var definition = sheetSeed.definition();
            Sheet sheet = bySheet.get(definition.name());
            if (sheet == null) {
                sheet = sheets.save(Sheet.builder().name(definition.name()).build());
                bySheet.put(definition.name(), sheet);
            }
            sheet.setDescription(definition.description());
            sheet.setSourceUrl(definition.sourceUrl());
            sheet.setExpectedProblemCount(definition.expectedProblems());
            sheet.setSeedVersion(version);
            for (var item : sheetSeed.items()) {
                Problem problem = bySource.get(sourceKey(item.sourcePlatform(), item.sourceExternalId())).getProblem();
                String key = sheet.getId() + ":" + problem.getId();
                SheetProblem row = byMembership.get(key);
                if (row == null) {
                    row = SheetProblem.builder().sheet(sheet).problem(problem).build();
                    byMembership.put(key, row);
                    addedMemberships.add(row);
                }
                row.setCategory(item.category());
                row.setDisplayOrder(item.displayOrder());
            }
        }
        memberships.saveAll(addedMemberships);

        Map<String, ProblemRelation> byPair = relations.findAll().stream().collect(Collectors.toMap(
                r -> r.getSourceProblem().getId() + ":" + r.getTargetProblem().getId(), r -> r));
        List<ProblemRelation> addedRelations = new ArrayList<>();
        for (var definition : seed.relations()) {
            Problem source = bySource.get(sourceKey(definition.sourcePlatform(), definition.sourceExternalId())).getProblem();
            Problem target = bySource.get(sourceKey(definition.targetPlatform(), definition.targetExternalId())).getProblem();
            String key = source.getId() + ":" + target.getId();
            ProblemRelation row = byPair.get(key);
            if (row == null) {
                row = ProblemRelation.builder().sourceProblem(source).targetProblem(target).reviewed(true).build();
                byPair.put(key, row);
                addedRelations.add(row);
            }
            row.setRelationType(definition.relationType());
            row.setNotes(definition.notes());
        }
        relations.saveAll(addedRelations);
        if (applied.isEmpty()) versions.save(CatalogSeedVersion.builder().version(version).checksum(seed.checksum()).appliedAt(Instant.now(clock)).build());
        log.info("Reference catalog {} synchronized: {} patterns, {} canonical problems, {} sheets; SHA-256 {}",
                version, seed.patterns().size(), seed.problems().size(), seed.sheets().size(), seed.checksum());
    }
}
