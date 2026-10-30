package com.applypilot.backend.resume.parsing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Validated
@ConfigurationProperties(prefix = "app.ollama")
public record OllamaProperties(@NotBlank String baseUrl, @NotBlank String model,
                              @Min(1) @Max(600) int timeoutSeconds,
                              @Min(1024) @Max(50000) int maxSourceCharacters,
                              @Min(4096) @Max(32768) int contextTokens) {
    @Configuration
    @EnableConfigurationProperties(OllamaProperties.class)
    static class ConfigurationRegistration {}
}
