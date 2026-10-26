package com.preptrack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {
    private String apiKey;
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private int timeoutMs = 5000;
    private int maxTokens = 300;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equalsIgnoreCase("null");
    }
}
