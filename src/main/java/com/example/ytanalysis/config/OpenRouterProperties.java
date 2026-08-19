package com.example.ytanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds every non-secret OpenRouter setting from the environment — the Spring version of
 * {@code src/config.py}.
 *
 * <p>In Python the settings were plain functions ({@code config.transcribe_model()},
 * {@code config.api_timeout()}, ...) that read {@code os.getenv} with a default. Spring
 * provides a more declarative mechanism: {@code @ConfigurationProperties}. We declare a
 * POJO/record, and Spring fills its fields from any property source — the
 * {@code OPENROUTER_*} environment variables map automatically to the camelCase fields.
 *
 * <p><b>Relaxed binding</b> is why this works without any glue: the env var
 * {@code OPENROUTER_TIMEOUT_SECONDS} maps to the field {@code timeoutSeconds} (the
 * {@code openrouter} prefix from the annotation is the {@code OPENROUTER_} env family).
 * Kebab-case and snake_case map to camelCase for free.
 *
 * <p><b>Why the API key is NOT a field here: laziness.</b> The Python project deliberately
 * deferred key validation until an API call, so {@code --help} (and startup) work without
 * a key. Spring's {@code @ConfigurationProperties} binding happens at startup — if the key
 * were a required field here, the app would fail to boot without one. So the key lives
 * behind the separate {@link #apiKey()} method, which reads the env var only when called.
 */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String transcribeModel,
        String summariseModel,
        double timeoutSeconds,
        int maxTokens,
        int chunkSeconds) {

    /** The defaults mirror the Python {@code config.py} defaults. */
    public OpenRouterProperties {
        if (transcribeModel == null) {
            transcribeModel = "mistralai/voxtral-mini-transcribe";
        }
        if (summariseModel == null) {
            summariseModel = "anthropic/claude-3.5-sonnet";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 60.0;
        }
        if (maxTokens <= 0) {
            maxTokens = 1024;
        }
        if (chunkSeconds <= 0) {
            chunkSeconds = 590;
        }
    }

    /**
     * As documented on the class: returns the API key by reading the environment directly,
     * <em>but only when called</em> — so the app boots and shows {@code --help} without one.
     *
     * @throws IllegalStateException if {@code OPENROUTER_API_KEY} is not set
     */
    public String apiKey() {
        String key = System.getenv("OPENROUTER_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: OPENROUTER_API_KEY");
        }
        return key;
    }

    /** True if a key is currently available in the environment (used by /api/health). */
    public boolean isApiKeyConfigured() {
        String key = System.getenv("OPENROUTER_API_KEY");
        return key != null && !key.isBlank();
    }
}