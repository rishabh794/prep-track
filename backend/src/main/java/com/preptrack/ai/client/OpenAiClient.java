package com.preptrack.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private WebClient webClient;

    private synchronized WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return webClient;
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
                "temperature", 0.2
        );

        try {
            String responseStr = getWebClient().post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(throwable -> !(throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized)))
                    .block();

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new AiClientException("Invalid response structure from OpenAI: " + responseStr);
        } catch (Exception e) {
            log.warn("OpenAI chat completion failed: {}", e.getMessage());
            throw new AiClientException("OpenAI request failed: " + e.getMessage(), e);
        }
    }
}
