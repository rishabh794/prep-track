package com.preptrack.company.service;

import com.preptrack.company.entity.*;
import com.preptrack.company.repository.*;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.repository.PatternRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyProfileSeedService {
    public record SeedFile(String version, List<ProfileSeed> profiles) {}
    public record ProfileSeed(String slug, String name, List<String> aliases, String provenance,
                              String sourceNotes, Map<String, Double> patternWeights) {}

    private final CompanyProfileRepository profiles;
    private final CompanyAliasRepository aliases;
    private final CompanyPatternWeightRepository weights;
    private final PatternRepository patterns;

    @Transactional
    public void synchronize(SeedFile seed, String checksum) {
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Company profile seed checksum must be SHA-256");
        }
        Map<String, Pattern> patternBySlug = patterns.findAll().stream().collect(Collectors.toMap(Pattern::getSlug, p -> p));
        validate(seed, patternBySlug.keySet());
        var existingProfiles = profiles.findAll().stream().collect(Collectors.toMap(CompanyProfile::getSlug, p -> p));
        for (var item : seed.profiles()) {
            CompanyProfile existing = existingProfiles.get(item.slug());
            if (existing != null && seed.version().equals(existing.getSourceVersion()) && !checksum.equals(existing.getSeedChecksum())) {
                throw new IllegalStateException("Company profile content changed without a version bump: " + item.slug());
            }
        }
        var allAliases = aliases.findAllWithProfile();
        var allWeights = weights.findAllWithProfileAndPattern();
        Map<String, CompanyAlias> aliasByName = allAliases.stream().collect(Collectors.toMap(CompanyAlias::getAlias, a -> a));
        // Remove obsolete aliases first so a version may move an alias between seeded profiles safely.
        Map<String, Set<String>> desiredAliasesBySlug = new HashMap<>();
        for (var item : seed.profiles()) {
            Set<String> desired = new HashSet<>(item.aliases().stream().map(CompanyProfileService::normalize).toList());
            desired.add(CompanyProfileService.normalize(item.name()));
            desired.add(CompanyProfileService.normalize(item.slug()));
            desiredAliasesBySlug.put(item.slug(), desired);
        }
        var obsoleteAliases = allAliases.stream().filter(a -> {
            CompanyProfile profile = a.getCompanyProfile();
            Set<String> desired = desiredAliasesBySlug.get(profile.getSlug());
            return desired != null && !checksum.equals(profile.getSeedChecksum()) && !desired.contains(a.getAlias());
        }).toList();
        aliases.deleteAll(obsoleteAliases);
        aliases.flush();
        obsoleteAliases.forEach(a -> aliasByName.remove(a.getAlias()));
        for (ProfileSeed item : seed.profiles()) {
            CompanyProfile profile = existingProfiles.get(item.slug());
            boolean changed = profile == null || !checksum.equals(profile.getSeedChecksum());
            if (profile == null) profile = new CompanyProfile();
            profile.setSlug(item.slug());
            profile.setName(item.name());
            profile.setProvenance(item.provenance());
            profile.setSourceVersion(seed.version());
            profile.setSourceNotes(item.sourceNotes());
            profile.setSeedChecksum(checksum);
            profile = profiles.save(profile);

            Set<String> desiredAliases = desiredAliasesBySlug.get(item.slug());
            final Long profileId = profile.getId();
            for (String alias : desiredAliases) {
                CompanyAlias existing = aliasByName.get(alias);
                if (existing != null && !existing.getCompanyProfile().getId().equals(profileId)) {
                    throw new IllegalStateException("Company alias belongs to multiple profiles: " + alias);
                }
                if (existing == null) {
                    var saved = aliases.save(CompanyAlias.builder().companyProfile(profile).alias(alias).build());
                    aliasByName.put(alias, saved);
                }
            }
            var existingWeights = allWeights.stream().filter(w -> w.getCompanyProfile().getId().equals(profileId))
                    .collect(Collectors.toMap(w -> w.getPattern().getSlug(), w -> w));
            if (changed) {
                weights.deleteAll(existingWeights.values().stream().filter(w -> !item.patternWeights().containsKey(w.getPattern().getSlug())).toList());
            }
            for (var entry : item.patternWeights().entrySet()) {
                var weight = existingWeights.get(entry.getKey());
                if (weight == null) {
                    weights.save(CompanyPatternWeight.builder().companyProfile(profile).pattern(patternBySlug.get(entry.getKey())).weight(entry.getValue()).build());
                } else if (changed) {
                    weight.setWeight(entry.getValue());
                    weights.save(weight);
                }
            }
        }
    }

    private void validate(SeedFile seed, Set<String> patternSlugs) {
        if (seed == null || seed.version() == null || seed.version().isBlank() || seed.profiles() == null) {
            throw new IllegalStateException("Company profile seed requires a version and profile list");
        }
        Set<String> slugs = new HashSet<>();
        Map<String, String> aliasOwners = new HashMap<>();
        for (ProfileSeed profile : seed.profiles()) {
            if (profile.slug() == null || !profile.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !slugs.add(profile.slug())
                    || profile.name() == null || profile.name().isBlank() || profile.aliases() == null
                    || profile.provenance() == null || !Set.of("HEURISTIC", "CURATED", "OBSERVED").contains(profile.provenance())
                    || profile.sourceNotes() == null || profile.sourceNotes().isBlank() || profile.patternWeights() == null) {
                throw new IllegalStateException("Invalid or duplicate company profile seed: " + profile.slug());
            }
            List<String> names = new ArrayList<>(profile.aliases());
            names.add(profile.name()); names.add(profile.slug());
            for (String name : names) {
                String alias = CompanyProfileService.normalize(name);
                String previous = aliasOwners.putIfAbsent(alias, profile.slug());
                if (alias.isBlank() || previous != null && !previous.equals(profile.slug())) {
                    throw new IllegalStateException("Company alias must identify exactly one profile: " + alias);
                }
            }
            for (var weight : profile.patternWeights().entrySet()) {
                if (!patternSlugs.contains(weight.getKey()) || weight.getValue() == null || !Double.isFinite(weight.getValue())
                        || weight.getValue() <= 0 || weight.getValue() > 5) {
                    throw new IllegalStateException("Invalid company pattern weight: " + profile.slug() + "/" + weight.getKey());
                }
            }
        }
    }
}
