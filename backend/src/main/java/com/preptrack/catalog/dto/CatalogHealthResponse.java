package com.preptrack.catalog.dto;

import com.preptrack.catalog.CatalogCountStatus;
import java.time.Instant;
import java.util.List;

public record CatalogHealthResponse(Instant checkedAt, String status, String manifestVersion,
        String appliedVersion, String checksum, Instant appliedAt, Counts counts,
        List<SheetHealth> sheets, List<Issue> issues) {
    public record Counts(long patterns, long problems, long sources, long patternLinks, long relations, long sheets) {}
    public record SheetHealth(Long sheetId, String name, long availableProblems, int bundledProblems,
            Integer expectedProblems, CatalogCountStatus countStatus, int missingBundledProblems, String seedVersion) {}
    public record Issue(String severity, String code, String message, String reference) {}
}
