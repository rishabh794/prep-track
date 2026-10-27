package com.preptrack.seed;

import com.preptrack.pattern.entity.Pattern;
import java.util.List;
import static com.preptrack.seed.SeedDataLoader.*;

/** A validated release of the bundled reference catalog. */
public record CatalogSeed(Manifest manifest, String checksum, List<Pattern> patterns,
                          List<ProblemSeedDto> problems, List<SheetSeed> sheets, List<RelationSeedDto> relations) {
    public record Manifest(String version, String patternsResource, String problemsResource,
                           String relationsResource, List<SheetDefinition> sheets) {}
    public record SheetDefinition(String name, String description, String sourceUrl,
                                  String resource, Integer expectedProblems) {}
    public record SheetSeed(SheetDefinition definition, List<SheetItemSeedDto> items) {}
}
