package com.preptrack.company.dto;

import java.util.List;

public record CompanyProfileDto(Long id, String slug, String name, List<String> aliases,
                                String provenance, String sourceVersion, String sourceNotes,
                                double defaultWeight, List<PatternWeight> patternWeights) {
    public record PatternWeight(Long patternId, String patternSlug, String patternName, double weight) {}
}
