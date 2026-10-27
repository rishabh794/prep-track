package com.preptrack.seed;

import com.preptrack.problem.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Order(0)
@ConditionalOnProperty(name = "preptrack.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SeedDataLoader implements CommandLineRunner {
    private final CatalogSeedReader reader;
    private final CatalogSeedSyncService sync;

    public void run(String... args) { sync.apply(reader.read()); }

    public record ProblemSeedDto(String canonicalTitle, Difficulty difficulty, Integer estimatedMinutes,
            String descriptionHint, String referenceApproach, String optimalComplexity,
            List<SourceSeedDto> sources, List<PatternTagSeedDto> patterns) {}
    public record SourceSeedDto(Platform platform, String externalId, String url, String title) {}
    public record PatternTagSeedDto(String slug, Double weight) {}
    public record SheetItemSeedDto(Platform sourcePlatform, String sourceExternalId, String category, Integer displayOrder) {}
    public record RelationSeedDto(Platform sourcePlatform, String sourceExternalId, Platform targetPlatform,
            String targetExternalId, RelationType relationType, String notes) {}
}
