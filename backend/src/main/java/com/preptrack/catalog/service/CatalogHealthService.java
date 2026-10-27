package com.preptrack.catalog.service;

import com.preptrack.catalog.CatalogCountStatus;
import com.preptrack.catalog.dto.CatalogHealthResponse;
import com.preptrack.pattern.repository.*;
import com.preptrack.problem.repository.*;
import com.preptrack.seed.*;
import com.preptrack.seed.repository.CatalogSeedVersionRepository;
import com.preptrack.sheet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.preptrack.catalog.dto.CatalogHealthResponse.*;
import static com.preptrack.seed.CatalogSeedReader.sourceKey;

@Service
@RequiredArgsConstructor
public class CatalogHealthService {
    private final CatalogSeedReader reader;
    private final CatalogSeedVersionRepository versions;
    private final PatternRepository patterns;
    private final ProblemRepository problems;
    private final ProblemSourceRepository sources;
    private final ProblemPatternRepository links;
    private final ProblemRelationRepository relations;
    private final SheetRepository sheets;
    private final SheetProblemRepository memberships;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CatalogHealthResponse inspect() {
        CatalogSeed seed = reader.read();
        List<Issue> issues = new ArrayList<>();
        var applied = versions.findFirstByOrderByAppliedAtDesc().orElse(null);
        if (applied == null || !applied.getVersion().equals(seed.manifest().version())) {
            issues.add(new Issue("ERROR", "CATALOG_VERSION_NOT_APPLIED", "The bundled catalog version has not been applied", seed.manifest().version()));
        } else if (!applied.getChecksum().equals(seed.checksum())) {
            issues.add(new Issue("ERROR", "CATALOG_CHECKSUM_MISMATCH", "Catalog files changed without a new manifest version", seed.manifest().version()));
        }
        var actualPatterns = patterns.findAll();
        Set<String> actualSlugs = actualPatterns.stream().map(p -> p.getSlug()).collect(Collectors.toSet());
        for (var pattern : seed.patterns()) {
            if (!actualSlugs.contains(pattern.getSlug())) issues.add(new Issue("ERROR", "MISSING_PATTERN", "Bundled pattern is missing", pattern.getSlug()));
        }
        var actualProblems = problems.findAll();
        var actualSources = sources.findAllWithProblem();
        var actualLinks = links.findAllWithPatternAndProblem();
        for (var link : actualLinks) {
            if (link.getWeight() == null || !Double.isFinite(link.getWeight()) || link.getWeight() <= 0) {
                issues.add(new Issue("ERROR", "INVALID_PATTERN_WEIGHT", "Pattern weight must be positive and finite",
                        link.getProblem().getCanonicalTitle() + ":" + link.getPattern().getSlug()));
            }
        }
        Set<Long> linkedProblems = actualLinks.stream().map(l -> l.getProblem().getId()).collect(Collectors.toSet());
        Set<String> linkKeys = actualLinks.stream().map(l -> l.getProblem().getId() + ":" + l.getPattern().getSlug()).collect(Collectors.toSet());
        for (var problem : actualProblems) {
            if (!linkedProblems.contains(problem.getId())) issues.add(new Issue("ERROR", "PROBLEM_WITHOUT_PATTERNS", "Problem cannot be scored without pattern links", problem.getCanonicalTitle()));
            if (problem.getEstimatedMinutes() == null || problem.getEstimatedMinutes() <= 0) {
                issues.add(new Issue("ERROR", "INVALID_TIME_ESTIMATE", "Problem needs a positive time estimate", problem.getCanonicalTitle()));
            }
            if (problem.getReferenceApproach() == null || problem.getReferenceApproach().isBlank()) {
                issues.add(new Issue("WARNING", "MISSING_REFERENCE_APPROACH", "Recall grading has no reference approach", problem.getCanonicalTitle()));
            }
        }
        for (var source : actualSources) {
            if (!CatalogSeedReader.hasSourceIdentity(source.getPlatform(), source.getExternalId())) {
                issues.add(new Issue("WARNING", "INVALID_SOURCE_IDENTITY", "This source has no usable identity for exact matching", "source:" + source.getId()));
            }
        }
        Map<String, Long> sourceIds = actualSources.stream()
                .filter(s -> CatalogSeedReader.hasSourceIdentity(s.getPlatform(), s.getExternalId()))
                .collect(Collectors.toMap(s -> sourceKey(s.getPlatform(), s.getExternalId()), s -> s.getProblem().getId()));
        for (var definition : seed.problems()) {
            Set<Long> canonicalIds = new HashSet<>();
            for (var source : definition.sources()) {
                String key = sourceKey(source.platform(), source.externalId());
                Long id = sourceIds.get(key);
                if (id == null) issues.add(new Issue("ERROR", "MISSING_SOURCE", "Bundled source identity is missing", key));
                else canonicalIds.add(id);
            }
            if (canonicalIds.size() > 1) issues.add(new Issue("ERROR", "SOURCE_IDENTITY_CONFLICT", "Sources no longer resolve to one canonical problem", definition.canonicalTitle()));
            if (canonicalIds.size() == 1) {
                Long id = canonicalIds.iterator().next();
                for (var link : definition.patterns()) {
                    if (!linkKeys.contains(id + ":" + link.slug())) issues.add(new Issue("ERROR", "MISSING_PATTERN_LINK", "Bundled problem-pattern link is missing", definition.canonicalTitle() + ":" + link.slug()));
                }
            }
        }
        var actualRelations = relations.findAll();
        Set<String> relationKeys = actualRelations.stream().map(r -> r.getSourceProblem().getId() + ":"
                + r.getTargetProblem().getId() + ":" + r.getRelationType()).collect(Collectors.toSet());
        for (var relation : seed.relations()) {
            Long source = sourceIds.get(sourceKey(relation.sourcePlatform(), relation.sourceExternalId()));
            Long target = sourceIds.get(sourceKey(relation.targetPlatform(), relation.targetExternalId()));
            if (source == null || target == null || !relationKeys.contains(source + ":" + target + ":" + relation.relationType())) {
                issues.add(new Issue("ERROR", "MISSING_RELATION", "Bundled relationship is missing or has the wrong type",
                        relation.sourceExternalId() + " -> " + relation.targetExternalId()));
            }
        }
        var actualSheets = sheets.findAll();
        var byName = actualSheets.stream().collect(Collectors.toMap(s -> s.getName(), s -> s));
        Map<Long, Set<Long>> members = new HashMap<>();
        for (var membership : memberships.findAllWithSheetAndProblem()) {
            members.computeIfAbsent(membership.getSheet().getId(), ignored -> new HashSet<>()).add(membership.getProblem().getId());
        }
        List<SheetHealth> sheetHealth = new ArrayList<>();
        for (var bundle : seed.sheets()) {
            var definition = bundle.definition();
            var sheet = byName.get(definition.name());
            Set<Long> available = sheet == null ? Set.of() : members.getOrDefault(sheet.getId(), Set.of());
            int missing = 0;
            for (var item : bundle.items()) {
                Long id = sourceIds.get(sourceKey(item.sourcePlatform(), item.sourceExternalId()));
                if (id == null || !available.contains(id)) missing++;
            }
            var countStatus = CatalogCountStatus.compare(available.size(), definition.expectedProblems());
            if (sheet == null) issues.add(new Issue("ERROR", "MISSING_SHEET", "Bundled sheet is missing", definition.name()));
            if (missing > 0) issues.add(new Issue("ERROR", "MISSING_SHEET_REFERENCES", missing + " bundled problems are unavailable in this sheet", definition.name()));
            if (countStatus == CatalogCountStatus.INCOMPLETE || countStatus == CatalogCountStatus.COUNT_MISMATCH) {
                issues.add(new Issue("WARNING", "SHEET_SIZE_MISMATCH", "Available " + available.size() + "; declared full size " + definition.expectedProblems(), definition.name()));
            } else if (countStatus == CatalogCountStatus.UNVERIFIED) {
                issues.add(new Issue("INFO", "SHEET_SIZE_UNVERIFIED", "Full external curriculum size is not verified", definition.name()));
            }
            sheetHealth.add(new SheetHealth(sheet == null ? null : sheet.getId(), definition.name(), available.size(),
                    bundle.items().size(), definition.expectedProblems(), countStatus, missing, sheet == null ? null : sheet.getSeedVersion()));
        }
        String status = issues.stream().anyMatch(i -> i.severity().equals("ERROR")) ? "ERROR"
                : issues.stream().anyMatch(i -> i.severity().equals("WARNING")) ? "WARNING" : "HEALTHY";
        return new CatalogHealthResponse(Instant.now(clock), status, seed.manifest().version(), applied == null ? null : applied.getVersion(),
                seed.checksum(), applied == null ? null : applied.getAppliedAt(),
                new Counts(actualPatterns.size(), actualProblems.size(), actualSources.size(), actualLinks.size(), actualRelations.size(), actualSheets.size()),
                List.copyOf(sheetHealth), List.copyOf(issues));
    }
}
