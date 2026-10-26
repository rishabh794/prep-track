package com.preptrack.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    private synchronized RestClient getRestClient() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getTimeoutMs());
            factory.setReadTimeout(properties.getTimeoutMs());

            restClient = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(factory)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return restClient;
    }

    public boolean isAvailable() {
        return properties.isConfigured();
    }

    public String chat(String systemPrompt, String userPrompt, String modelOverride) {
        if (!isAvailable()) {
            throw new AiClientException("Gemini API key is not configured.");
        }

        String model = (modelOverride != null && !modelOverride.isBlank())
                ? modelOverride
                : properties.getModel();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))
        ));

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBody.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }

        requestBody.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", properties.getTemperature(),
                "maxOutputTokens", properties.getMaxTokens()
        ));

        int maxRetries = 2;
        long backoffMs = 500;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String uri = "/models/" + model + ":generateContent";

                String responseStr = getRestClient().post()
                        .uri(uri)
                        .header("x-goog-api-key", properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        String text = parts.get(0).path("text").asText();
                        return sanitizeJson(text);
                    }
                }
                throw new AiClientException("Invalid response structure from Gemini: " + responseStr);
            } catch (RestClientResponseException ex) {
                if (attempt < maxRetries && isRetryable(ex)) {
                    log.warn("Gemini returned retryable HTTP {} on attempt {}. Retrying in {}ms...",
                            ex.getStatusCode(), attempt + 1, backoffMs * (attempt + 1));
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiClientException("Interrupted during Gemini retry backoff", ie);
                    }
                    continue;
                }
                log.warn("Gemini request failed with HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                throw new AiClientException("Gemini request failed: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString(), ex);
            } catch (ResourceAccessException ex) {
                if (attempt < maxRetries) {
                    log.warn("Gemini network/timeout error on attempt {}. Retrying in {}ms...",
                            attempt + 1, backoffMs * (attempt + 1));
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiClientException("Interrupted during Gemini retry backoff", ie);
                    }
                    continue;
                }
                log.warn("Gemini network error after retries: {}", ex.getMessage());
                throw new AiClientException("Gemini network error: " + ex.getMessage(), ex);
            } catch (AiClientException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Gemini generateContent call failed: {}", e.getMessage());
                throw new AiClientException("Gemini request failed: " + e.getMessage(), e);
            }
        }

        throw new AiClientException("Gemini request failed after retries.");
    }

    private boolean isRetryable(RestClientResponseException ex) {
        if (ex.getStatusCode().is5xxServerError()) {
            return true;
        }
        if (ex.getStatusCode().value() == 429) {
            return true;
        }
        return false;
    }

    private String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
