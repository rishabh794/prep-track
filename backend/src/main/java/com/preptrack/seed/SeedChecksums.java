package com.preptrack.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** Ignore formatting and platform line endings when fingerprinting reference-data releases. */
public final class SeedChecksums {
    private SeedChecksums() {}
    public static byte[] canonicalJson(ObjectMapper mapper, byte[] bytes) throws IOException {
        return mapper.writeValueAsBytes(canonicalValue(mapper.readTree(bytes)));
    }
    private static Object canonicalValue(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), canonicalValue(entry.getValue())));
            return fields;
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(canonicalValue(item)));
            return items;
        }
        return node;
    }
}
