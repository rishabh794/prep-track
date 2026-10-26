package com.preptrack.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.client.GeminiClient;
import com.preptrack.ai.dto.RecallGradingRequest;
import com.preptrack.ai.dto.RecallGradingResult;
import com.preptrack.ai.entity.RecallGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecallGradingServiceTest {

    @Mock
    private GeminiClient geminiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RecallGradingService gradingService;

    @BeforeEach
    void setUp() {
        gradingService = new RecallGradingService(geminiClient, objectMapper);
    }

    @Test
    @DisplayName("Gemini active: parses valid PASS response from model")
    void testGeminiGradingSuccess() {
        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.chat(anyString(), anyString(), eq(null)))
                .thenReturn("{\"grade\": \"PASS\", \"reasoning\": \"Correctly identified monotonic stack.\"}");

        RecallGradingRequest request = RecallGradingRequest.builder()
                .problemTitle("Trapping Rain Water")
                .referenceApproach("Maintain monotonic decreasing stack")
                .userApproach("Use a monotonic stack to store indices and calculate bounded water")
                .build();

        RecallGradingResult result = gradingService.grade(request);
        assertThat(result.getGrade()).isEqualTo(RecallGrade.PASS);
        assertThat(result.getReasoning()).contains("monotonic stack");
    }

    @Test
    @DisplayName("Fallback grading: very short or vague input returns FAIL")
    void testFallbackVagueInputReturnsFail() {
        when(geminiClient.isAvailable()).thenReturn(false);

        RecallGradingRequest request = RecallGradingRequest.builder()
                .problemTitle("3Sum")
                .referenceApproach("Sort array, fix first element and use two pointers")
                .userApproach("i don't know")
                .build();

        RecallGradingResult result = gradingService.grade(request);
        assertThat(result.getGrade()).isEqualTo(RecallGrade.FAIL);
    }

    @Test
    @DisplayName("Fallback grading: high keyword overlap with reference approach returns PASS")
    void testFallbackHighOverlapReturnsPass() {
        when(geminiClient.isAvailable()).thenReturn(false);

        RecallGradingRequest request = RecallGradingRequest.builder()
                .problemTitle("Trapping Rain Water")
                .referenceApproach("Maintain monotonic decreasing stack of indices. Pop when current bar is higher and calculate trapped width and height.")
                .patterns("Monotonic Stack")
                .optimalComplexity("O(N) time, O(N) space")
                .userApproach("Maintain a monotonic decreasing stack of bar indices. When encountering a taller bar, pop elements and compute width times height.")
                .build();

        RecallGradingResult result = gradingService.grade(request);
        assertThat(result.getGrade()).isEqualTo(RecallGrade.PASS);
    }

    @Test
    @DisplayName("Fallback grading: partial keyword alignment returns PARTIAL")
    void testFallbackPartialOverlapReturnsPartial() {
        when(geminiClient.isAvailable()).thenReturn(false);

        RecallGradingRequest request = RecallGradingRequest.builder()
                .problemTitle("Binary Search")
                .referenceApproach("Keep left and right pointers, check mid element against target and adjust search space")
                .patterns("Binary Search")
                .userApproach("You calculate mid and then split into left and right sides")
                .build();

        RecallGradingResult result = gradingService.grade(request);
        assertThat(result.getGrade()).isEqualTo(RecallGrade.PARTIAL);
    }
}
