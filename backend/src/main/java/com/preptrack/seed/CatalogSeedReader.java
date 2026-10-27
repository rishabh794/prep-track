package com.preptrack.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.problem.entity.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.preptrack.seed.CatalogSeed.*;
import static com.preptrack.seed.SeedDataLoader.*;

@Component
@RequiredArgsConstructor
public class CatalogSeedReader {
    private final ObjectMapper mapper;
    @Value("${preptrack.seed.manifest:seed/catalog-manifest.json}")
    private String manifestResource = "seed/catalog-manifest.json";

    public CatalogSeed read() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Manifest manifest = mapper.readValue(bytes(manifestResource, digest), Manifest.class);
            require(manifest.version() != null && !manifest.version().isBlank() && manifest.version().length() <= 100,
                    "Manifest needs a version of at most 100 characters");
            var patterns = mapper.readValue(bytes(manifest.patternsResource(), digest), new TypeReference<List<Pattern>>() {});
            var problems = mapper.readValue(bytes(manifest.problemsResource(), digest), new TypeReference<List<ProblemSeedDto>>() {});
            var relations = mapper.readValue(bytes(manifest.relationsResource(), digest), new TypeReference<List<RelationSeedDto>>() {});
            require(manifest.sheets() != null && !manifest.sheets().isEmpty(), "Manifest needs at least one sheet");
            List<SheetSeed> sheets = new ArrayList<>();
            for (var definition : manifest.sheets()) {
                List<SheetItemSeedDto> items = mapper.readValue(bytes(definition.resource(), digest), new TypeReference<List<SheetItemSeedDto>>() {});
                sheets.add(new SheetSeed(definition, items));
            }
            var seed = new CatalogSeed(manifest, HexFormat.of().formatHex(digest.digest()), patterns, problems, List.copyOf(sheets), relations);
            validate(seed);
            return seed;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid reference catalog: " + e.getMessage(), e);
        }
    }

    private byte[] bytes(String resource, MessageDigest digest) throws Exception {
        require(resource != null && resource.startsWith("seed/") && !resource.contains("..") && resource.endsWith(".json"),
                "Catalog resources must be JSON files inside seed/");
        try (var input = new ClassPathResource(resource).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            digest.update(resource.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(SeedChecksums.canonicalJson(mapper, bytes));
            return bytes;
        }
    }

    public static void validate(CatalogSeed seed) {
        require(seed != null && seed.manifest() != null && text(seed.manifest().version()) && seed.manifest().version().length() <= 100,
                "Catalog needs a manifest version of at most 100 characters");
        require(seed.checksum() != null && seed.checksum().matches("[0-9a-f]{64}"), "Catalog needs a SHA-256 checksum");
        require(seed.patterns() != null && !seed.patterns().isEmpty(), "Catalog needs patterns");
        require(seed.problems() != null && !seed.problems().isEmpty(), "Catalog needs problems");
        require(seed.sheets() != null && !seed.sheets().isEmpty() && seed.relations() != null, "Catalog needs sheet and relation definitions");
        Set<String> slugs = new HashSet<>();
        Set<String> patternNames = new HashSet<>();
        for (var pattern : seed.patterns()) {
            require(text(pattern.getSlug()) && slugs.add(pattern.getSlug()), "Missing or duplicate pattern slug: " + pattern.getSlug());
            require(text(pattern.getName()) && patternNames.add(pattern.getName()), "Missing or duplicate pattern name: " + pattern.getName());
        }
        Set<String> titles = new HashSet<>();
        Map<String, String> sourceOwners = new HashMap<>();
        for (var problem : seed.problems()) {
            require(text(problem.canonicalTitle()) && titles.add(problem.canonicalTitle()), "Missing or duplicate problem title: " + problem.canonicalTitle());
            require(problem.difficulty() != null && problem.estimatedMinutes() != null && problem.estimatedMinutes() > 0,
                    "Problem needs a difficulty and positive time estimate: " + problem.canonicalTitle());
            require(problem.sources() != null && !problem.sources().isEmpty(), "Problem needs a source: " + problem.canonicalTitle());
            for (var source : problem.sources()) {
                String key = sourceKey(source.platform(), source.externalId());
                require(sourceOwners.putIfAbsent(key, problem.canonicalTitle()) == null, "Duplicate source identity: " + key);
                require(webUrl(source.url()), "Invalid source URL: " + key);
            }
            require(problem.patterns() != null && !problem.patterns().isEmpty(), "Problem has no pattern links: " + problem.canonicalTitle());
            Set<String> linked = new HashSet<>();
            for (var link : problem.patterns()) {
                require(slugs.contains(link.slug()), "Unresolved pattern " + link.slug() + " on " + problem.canonicalTitle());
                require(linked.add(link.slug()), "Duplicate pattern link on " + problem.canonicalTitle());
                require(link.weight() != null && Double.isFinite(link.weight()) && link.weight() > 0,
                        "Invalid pattern weight on " + problem.canonicalTitle());
            }
        }
        Set<String> sheetNames = new HashSet<>();
        Set<String> sheetResources = new HashSet<>();
        for (var sheet : seed.sheets()) {
            var definition = sheet.definition();
            require(text(definition.name()) && sheetNames.add(definition.name()), "Missing or duplicate sheet name");
            require(sheetResources.add(definition.resource()), "Duplicate sheet resource: " + definition.resource());
            require(definition.expectedProblems() == null || definition.expectedProblems() > 0, "Invalid expected sheet size");
            require(webUrl(definition.sourceUrl()), "Invalid sheet URL: " + definition.name());
            Set<String> canonicalProblems = new HashSet<>();
            Set<Integer> orders = new HashSet<>();
            for (var item : sheet.items()) {
                String key = sourceKey(item.sourcePlatform(), item.sourceExternalId());
                require(sourceOwners.containsKey(key), "Unresolved sheet reference " + key + " in " + definition.name());
                require(canonicalProblems.add(sourceOwners.get(key)), "Duplicate canonical problem in " + definition.name() + ": " + key);
                require(item.displayOrder() != null && item.displayOrder() >= 0 && orders.add(item.displayOrder()),
                        "Invalid or duplicate display order in " + definition.name());
            }
        }
        Set<String> pairs = new HashSet<>();
        for (var relation : seed.relations()) {
            String source = sourceKey(relation.sourcePlatform(), relation.sourceExternalId());
            String target = sourceKey(relation.targetPlatform(), relation.targetExternalId());
            require(sourceOwners.containsKey(source) && sourceOwners.containsKey(target), "Unresolved relation: " + source + " -> " + target);
            require(!sourceOwners.get(source).equals(sourceOwners.get(target)), "Self relation: " + source);
            require(relation.relationType() != null && pairs.add(sourceOwners.get(source) + "->" + sourceOwners.get(target)),
                    "Missing type or duplicate relation: " + source + " -> " + target);
        }
    }

    public static String sourceKey(Platform platform, String id) {
        require(hasSourceIdentity(platform, id), "Source identity needs a platform and external ID");
        return platform.name() + ":" + id;
    }
    public static boolean hasSourceIdentity(Platform platform, String id) { return platform != null && text(id); }
    private static boolean webUrl(String value) {
        try { var uri = URI.create(value); return Set.of("https", "http").contains(uri.getScheme()) && uri.getHost() != null; }
        catch (Exception e) { return false; }
    }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
