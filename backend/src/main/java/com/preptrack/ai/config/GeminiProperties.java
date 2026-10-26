package com.preptrack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    private String apiKey;
    private String model = "gemini-3.5-flash";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private int timeoutMs = 10000;
    private int maxTokens = 500;
    private double temperature = 0.2;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equalsIgnoreCase("null");
    }
}
