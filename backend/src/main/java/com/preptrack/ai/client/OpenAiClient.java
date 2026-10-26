package com.preptrack.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final OpenAiProperties properties;
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
            throw new AiClientException("OpenAI API key is not configured.");
        }

        String model = (modelOverride != null && !modelOverride.isBlank())
                ? modelOverride
                : properties.getModel();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2,
                "max_tokens", properties.getMaxTokens()
        );

        int maxRetries = 2;
        long backoffMs = 500;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String responseStr = getRestClient().post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).path("message").path("content").asText();
                }
                throw new AiClientException("Invalid response structure from OpenAI: " + responseStr);
            } catch (RestClientResponseException ex) {
                if (attempt < maxRetries && isRetryable(ex)) {
                    log.warn("OpenAI returned retryable HTTP {} on attempt {}. Retrying in {}ms...",
                            ex.getStatusCode(), attempt + 1, backoffMs * (attempt + 1));
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiClientException("Interrupted during OpenAI retry backoff", ie);
                    }
                    continue;
                }
                log.warn("OpenAI request failed with HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                throw new AiClientException("OpenAI request failed: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString(), ex);
            } catch (ResourceAccessException ex) {
                if (attempt < maxRetries) {
                    log.warn("OpenAI network/timeout error on attempt {}. Retrying in {}ms...",
                            attempt + 1, backoffMs * (attempt + 1));
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiClientException("Interrupted during OpenAI retry backoff", ie);
                    }
                    continue;
                }
                log.warn("OpenAI network error after retries: {}", ex.getMessage());
                throw new AiClientException("OpenAI network error: " + ex.getMessage(), ex);
            } catch (AiClientException e) {
                throw e;
            } catch (Exception e) {
                log.warn("OpenAI chat completion failed: {}", e.getMessage());
                throw new AiClientException("OpenAI request failed: " + e.getMessage(), e);
            }
        }

        throw new AiClientException("OpenAI request failed after retries.");
    }

    private boolean isRetryable(RestClientResponseException ex) {
        if (ex.getStatusCode().is5xxServerError()) {
            return true;
        }
        if (ex.getStatusCode().value() == 429) {
            String body = ex.getResponseBodyAsString();
            return body == null || !body.contains("insufficient_quota");
        }
        return false;
    }
}
