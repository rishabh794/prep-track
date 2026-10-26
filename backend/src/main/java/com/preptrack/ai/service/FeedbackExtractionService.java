package com.preptrack.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.client.AiClientException;
import com.preptrack.ai.client.OpenAiClient;
import com.preptrack.ai.dto.ExtractedPatternDto;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.repository.PatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackExtractionService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final PatternRepository patternRepository;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        You are an expert interview coach analyzing candidate debriefs.
        Given a student's description of their interview experience, extract:
        1. DSA patterns mentioned. Match against this available list:
        %s
        2. For each pattern, whether the student struggled (wasWeak: true/false).
        3. Suggested overall outcome: PASSED, FAILED, or PARTIAL.

        Respond with JSON only in this exact format:
        {
          "extractedPatterns": [
            { "patternSlug": "two-pointer-squeeze", "wasWeak": false, "confidence": 0.90 }
          ],
          "suggestedOutcome": "PASSED|FAILED|PARTIAL",
          "requiresConfirmation": true
        }
        """;

    public FeedbackExtractionResult extract(String rawFeedback) {
        List<Pattern> allPatterns = patternRepository.findAll();

        if (openAiClient.isAvailable()) {
            try {
                String patternListStr = allPatterns.stream()
                        .map(p -> String.format("- %s (slug: %s)", p.getName(), p.getSlug()))
                        .collect(Collectors.joining("\n"));

                String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, patternListStr);
                String response = openAiClient.chat(systemPrompt, rawFeedback, null);
                return parseExtractionResult(response, rawFeedback, allPatterns);
            } catch (AiClientException e) {
                log.warn("OpenAI interview feedback extraction failed, falling back to rule-based parser: {}", e.getMessage());
            }
        }

        return extractFallback(rawFeedback, allPatterns);
    }

    private FeedbackExtractionResult parseExtractionResult(String json, String rawFeedback, List<Pattern> allPatterns) {
        try {
            Map<String, Pattern> slugMap = allPatterns.stream()
                    .collect(Collectors.toMap(Pattern::getSlug, p -> p, (a, b) -> a));

            JsonNode root = objectMapper.readTree(json);
            List<ExtractedPatternDto> extractedList = new ArrayList<>();
            Set<Long> seenPatternIds = new HashSet<>();

            JsonNode patternsNode = root.path("extractedPatterns");
            if (patternsNode.isArray()) {
                for (JsonNode item : patternsNode) {
                    String slug = item.path("patternSlug").asText("");
                    boolean wasWeak = item.path("wasWeak").asBoolean(false);
                    double confidence = item.path("confidence").asDouble(0.85);

                    Pattern matched = slugMap.get(slug);
                    if (matched != null && seenPatternIds.add(matched.getId())) {
                        extractedList.add(ExtractedPatternDto.builder()
                                .patternId(matched.getId())
                                .patternSlug(matched.getSlug())
                                .patternName(matched.getName())
                                .wasWeak(wasWeak)
                                .confidence(confidence)
                                .build());
                    }
                }
            }

            String suggestedOutcome = root.path("suggestedOutcome").asText("PARTIAL");

            return FeedbackExtractionResult.builder()
                    .extractedPatterns(extractedList)
                    .suggestedOutcome(suggestedOutcome)
                    .requiresConfirmation(true)
                    .aiUsed(true)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI feedback extraction JSON: {}", json);
            return extractFallback(rawFeedback, allPatterns);
        }
    }

    public FeedbackExtractionResult extractFallback(String rawFeedback, List<Pattern> allPatterns) {
        String lower = rawFeedback != null ? rawFeedback.toLowerCase() : "";
        List<ExtractedPatternDto> extracted = new ArrayList<>();

        // Exclude "hard" so hard difficulty problems aren't flagged as weakness
        Set<String> weaknessWords = Set.of(
                "struggled", "weak", "failed", "couldn't", "could not", "trouble", "missed",
                "stuck", "blanked", "difficult", "confused", "bug", "too slow", "bad"
        );

        for (Pattern pattern : allPatterns) {
            String nameLower = pattern.getName().toLowerCase();
            String slugLower = pattern.getSlug().replace("-", " ").toLowerCase();

            int matchIdx = -1;
            if (lower.contains(nameLower)) {
                matchIdx = lower.indexOf(nameLower);
            } else if (lower.contains(slugLower)) {
                matchIdx = lower.indexOf(slugLower);
            } else {
                for (String part : nameLower.split("[\\s/]+")) {
                    if (part.length() > 4 && lower.contains(part) && !isGenericPart(part)) {
                        matchIdx = lower.indexOf(part);
                        break;
                    }
                }
            }

            if (matchIdx != -1) {
                // Determine if weakness words appear in a local window (within 60 chars) around this pattern match
                int start = Math.max(0, matchIdx - 60);
                int end = Math.min(lower.length(), matchIdx + 60);
                String window = lower.substring(start, end);

                boolean wasWeak = false;
                for (String w : weaknessWords) {
                    if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(w) + "\\b").matcher(window).find()) {
                        wasWeak = true;
                        break;
                    }
                }

                extracted.add(ExtractedPatternDto.builder()
                        .patternId(pattern.getId())
                        .patternSlug(pattern.getSlug())
                        .patternName(pattern.getName())
                        .wasWeak(wasWeak)
                        .confidence(0.80)
                        .build());
            }
        }

        String suggestedOutcome = "PARTIAL";
        if (lower.contains("passed") || lower.contains("offer") || lower.contains("next round") || lower.contains("nailed")) {
            suggestedOutcome = "PASSED";
        } else if (lower.contains("rejected") || lower.contains("failed") || lower.contains("bombed")) {
            suggestedOutcome = "FAILED";
        }

        return FeedbackExtractionResult.builder()
                .extractedPatterns(extracted)
                .suggestedOutcome(suggestedOutcome)
                .requiresConfirmation(true)
                .aiUsed(false)
                .build();
    }

    private boolean isGenericPart(String part) {
        return part.equals("pattern") || part.equals("based") || part.equals("search") || part.equals("array");
    }
}
