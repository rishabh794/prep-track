package com.preptrack.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.ai.config.GeminiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    private GeminiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        client = new GeminiClient(properties, objectMapper);
    }

    @Test
    @DisplayName("isAvailable returns false when API key is null or blank")
    void testIsAvailableFalseWhenUnset() {
        properties.setApiKey(null);
        assertThat(client.isAvailable()).isFalse();

        properties.setApiKey("");
        assertThat(client.isAvailable()).isFalse();

        properties.setApiKey("   ");
        assertThat(client.isAvailable()).isFalse();

        properties.setApiKey("null");
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("isAvailable returns true when API key is set")
    void testIsAvailableTrueWhenSet() {
        properties.setApiKey("AQ.test-key-123");
        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("chat throws AiClientException when not configured")
    void testChatThrowsWhenUnconfigured() {
        properties.setApiKey(null);
        assertThatThrownBy(() -> client.chat("system", "user", null))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("Gemini API key is not configured");
    }
}
