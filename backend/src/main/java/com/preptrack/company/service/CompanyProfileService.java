package com.preptrack.company.service;

import com.preptrack.company.dto.CompanyProfileDto;
import com.preptrack.company.repository.*;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.repository.PatternRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyProfileService {
    private final CompanyProfileRepository profiles;
    private final CompanyAliasRepository aliases;
    private final CompanyPatternWeightRepository weights;
    private final PatternRepository patterns;

    private record Snapshot(Map<String, CompanyProfileDto> profiles, Map<String, String> aliasToSlug,
                            Map<String, Map<String, Double>> weights, Map<String, String> patternNames) {}
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());

    /** Bulk refresh after startup seed updates. No per-candidate repository reads occur during planning. */
    @Transactional(readOnly = true)
    public void refresh() {
        var allProfiles = profiles.findAll();
        var allAliases = aliases.findAllWithProfile();
        var allWeights = weights.findAllWithProfileAndPattern();
        Map<String, String> aliasToSlug = new HashMap<>();
        Map<String, Map<String, Double>> weightsBySlug = new HashMap<>();
        Map<String, CompanyProfileDto> profileDtos = new TreeMap<>();
        for (var profile : allProfiles) {
            var profileAliases = allAliases.stream().filter(a -> a.getCompanyProfile().getId().equals(profile.getId()))
                    .map(a -> a.getAlias()).sorted().toList();
            aliasToSlug.put(normalize(profile.getSlug()), profile.getSlug());
            aliasToSlug.put(normalize(profile.getName()), profile.getSlug());
            profileAliases.forEach(alias -> aliasToSlug.put(normalize(alias), profile.getSlug()));
            var profileWeights = allWeights.stream().filter(w -> w.getCompanyProfile().getId().equals(profile.getId()))
                    .sorted(Comparator.comparing(w -> w.getPattern().getSlug()))
                    .map(w -> new CompanyProfileDto.PatternWeight(w.getPattern().getId(), w.getPattern().getSlug(), w.getPattern().getName(), w.getWeight())).toList();
            weightsBySlug.put(profile.getSlug(), profileWeights.stream()
                    .collect(Collectors.toUnmodifiableMap(CompanyProfileDto.PatternWeight::patternSlug, CompanyProfileDto.PatternWeight::weight)));
            profileDtos.put(profile.getSlug(), new CompanyProfileDto(profile.getId(), profile.getSlug(), profile.getName(), profileAliases,
                    profile.getProvenance(), profile.getSourceVersion(), profile.getSourceNotes(), 1.0, profileWeights));
        }
        Map<String, String> namesToSlugs = new HashMap<>();
        for (var pattern : patterns.findAll()) {
            namesToSlugs.put(normalize(pattern.getName()), pattern.getSlug());
            namesToSlugs.put(normalize(pattern.getSlug()), pattern.getSlug());
        }
        snapshot = new Snapshot(Map.copyOf(profileDtos), Map.copyOf(aliasToSlug), Map.copyOf(weightsBySlug), Map.copyOf(namesToSlugs));
    }

    public List<CompanyProfileDto> list() {
        return snapshot.profiles().values().stream().sorted(Comparator.comparing(CompanyProfileDto::name)).toList();
    }

    public CompanyProfileDto get(String slug) {
        CompanyProfileDto profile = snapshot.profiles().get(normalize(slug));
        if (profile == null) throw new ResourceNotFoundException("Company profile not found");
        return profile;
    }

    public Optional<CompanyProfileDto> resolve(String companyName) {
        Snapshot current = snapshot;
        String slug = current.aliasToSlug().get(normalize(companyName));
        return Optional.ofNullable(slug == null ? null : current.profiles().get(slug));
    }

    public double relevance(String companyName, Pattern pattern) {
        return pattern == null ? 1.0 : relevanceBySlug(companyName, pattern.getSlug());
    }

    /** Compatibility for exact pattern names or slugs, never substring matching. */
    public double relevance(String companyName, String patternNameOrSlug) {
        Snapshot current = snapshot;
        String slug = current.patternNames().get(normalize(patternNameOrSlug));
        return relevanceBySlug(companyName, slug);
    }

    private double relevanceBySlug(String companyName, String patternSlug) {
        Snapshot current = snapshot;
        String companySlug = current.aliasToSlug().get(normalize(companyName));
        if (companySlug == null || patternSlug == null) return 1.0;
        return current.weights().getOrDefault(companySlug, Map.of()).getOrDefault(patternSlug, 1.0);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
