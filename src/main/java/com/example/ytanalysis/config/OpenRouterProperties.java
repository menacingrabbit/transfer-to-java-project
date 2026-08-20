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
 * <p><b>The API key IS a field now — and that is exactly what makes the external
 * {@code config/application.yml} useful.</b> Because the environment and the YAML file are
 * both property sources, relaxed binding fills {@code apiKey} from <em>either</em> an
 * {@code openrouter.api-key:} line in {@code config/application.yml} <em>or</em> the
 * {@code OPENROUTER_API_KEY} env var — no code chooses. A missing key simply leaves the
 * field {@code null}, which keeps startup and {@code --help} working without one (the
 * Python original's laziness); validation happens only inside {@link #apiKey()}, when a
 * real API call actually needs the key. Two consequences worth spelling out:
 *
 * <ul>
 *   <li>We deliberately do <b>not</b> default {@code apiKey} in the compact constructor and
 *       do <b>not</b> annotate it {@code @NotBlank}: both would make a missing key a boot
 *       error, destroying the lazy behaviour.</li>
 *   <li>The generated record {@code toString()} would print the secret, so it is overridden
 *       below to mask the key — never let a key end up in logs or traces.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String transcribeModel,
        String summariseModel,
        double timeoutSeconds,
        int maxTokens,
        int chunkSeconds,
        /** The OpenRouter API key. null when configured neither in config/ nor the env. */
        String apiKey) {

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
            maxTokens = 4096;
        }
        if (chunkSeconds <= 0) {
            chunkSeconds = 590;
        }
        // The ONE field we never default: apiKey may stay null so the app boots and shows
        // --help without one. We only canonicalise a present-but-blank value to null, so
        // the getters below can rely on "null or a non-blank key".
        if (apiKey != null && apiKey.isBlank()) {
            apiKey = null;
        }
    }

    /**
     * The API key to actually send, failing loudly only when a call needs it.
     *
     * <p>In normal Spring startup the key arrives in the {@code apiKey} field via relaxed
     * binding (from either {@code config/application.yml} or {@code OPENROUTER_API_KEY}).
     * The {@code System.getenv} fallback is a belt-and-braces second chance for anyone who
     * constructs this record directly (e.g. unit tests) — under Spring it is a no-op.
     *
     * @throws IllegalStateException if no key is configured anywhere
     */
    public String apiKey() {
        String key = apiKey;
        if (key == null) {
            key = System.getenv("OPENROUTER_API_KEY");   // relaxed binding usually already did this
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Missing OpenRouter API key. Set 'openrouter.api-key' in "
                    + "config/application.yml or the OPENROUTER_API_KEY environment variable. "
                    + "Startup and --help do not need it; only API calls do.");
        }
        return key;
    }

    /**
     * True if a key is currently available from either source (used by /api/health).
     * The env check mirrors {@link #apiKey()} but never throws.
     */
    public boolean isApiKeyConfigured() {
        return (apiKey != null && !apiKey.isBlank())
                || System.getenv("OPENROUTER_API_KEY") != null;
    }

    /**
     * Mask the secret. The record's auto-generated {@code toString()} would include the raw
     * {@code apiKey}; if anything logs this bean, the key would leak into the log file.
     * Never let a secret reach a log, an exception message kept on purpose is different.
     */
    @Override
    public String toString() {
        String keyMask = isApiKeyConfigured() ? "***configured***" : "null";
        return "OpenRouterProperties[transcribeModel=" + transcribeModel
                + ", summariseModel=" + summariseModel
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxTokens=" + maxTokens
                + ", chunkSeconds=" + chunkSeconds
                + ", apiKey=" + keyMask + "]";
    }
}