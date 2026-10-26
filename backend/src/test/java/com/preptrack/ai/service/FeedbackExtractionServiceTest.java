package com.preptrack.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.client.OpenAiClient;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.repository.PatternRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackExtractionServiceTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private PatternRepository patternRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FeedbackExtractionService extractionService;

    private List<Pattern> patterns;

    @BeforeEach
    void setUp() {
        patterns = List.of(
                Pattern.builder().id(1L).name("Two Pointer Squeeze").slug("two-pointer-squeeze").build(),
                Pattern.builder().id(2L).name("DP Memoization on Subsets").slug("dp-memoization-on-subsets").build(),
                Pattern.builder().id(3L).name("BFS Level-Order").slug("bfs-level-order").build()
        );
        when(patternRepository.findAll()).thenReturn(patterns);
        extractionService = new FeedbackExtractionService(openAiClient, objectMapper, patternRepository);
    }

    @Test
    @DisplayName("OpenAI active: extracts patterns with weakness and outcome")
    void testOpenAiExtraction() {
        when(openAiClient.isAvailable()).thenReturn(true);
        String mockJson = """
            {
              "extractedPatterns": [
                { "patternSlug": "dp-memoization-on-subsets", "wasWeak": true, "confidence": 0.95 }
              ],
              "suggestedOutcome": "PARTIAL",
              "requiresConfirmation": true
            }
            """;
        when(openAiClient.chat(anyString(), anyString(), eq(null))).thenReturn(mockJson);

        FeedbackExtractionResult result = extractionService.extract("I had a hard time with the subset DP problem.");

        assertThat(result.getExtractedPatterns()).hasSize(1);
        assertThat(result.getExtractedPatterns().get(0).getPatternSlug()).isEqualTo("dp-memoization-on-subsets");
        assertThat(result.getExtractedPatterns().get(0).isWasWeak()).isTrue();
        assertThat(result.getSuggestedOutcome()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("Fallback extraction: recognizes keywords, weakness cues, and suggested outcome")
    void testFallbackExtraction() {
        when(openAiClient.isAvailable()).thenReturn(false);

        String feedback = "The interviewer asked a Two Pointer problem which went well and passed, but I struggled with DP Memoization.";
        FeedbackExtractionResult result = extractionService.extract(feedback);

        assertThat(result.getExtractedPatterns()).isNotEmpty();
        boolean foundDp = result.getExtractedPatterns().stream()
                .anyMatch(p -> p.getPatternSlug().equals("dp-memoization-on-subsets") && p.isWasWeak());
        assertThat(foundDp).isTrue();
    }
}
