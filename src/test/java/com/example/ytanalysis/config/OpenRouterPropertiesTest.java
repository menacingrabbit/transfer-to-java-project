package com.example.ytanalysis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/**
 * Plain unit tests for {@link OpenRouterProperties} (no Spring context) — lazy failure of
 * a missing key and the {@code toString()} secret mask. The full-context boot/binding
 * test lives in its own class, {@link ApiKeyBindingTest}, because Surefire only discovers
 * top-level test classes, not nested ones.
 *
 * <p>{@link Object#toString()} output can end up in logs, exception messages and stack
 * traces, so a record carrying a secret must never print it verbatim.
 */
class OpenRouterPropertiesTest {

    /**
     * No Spring context — construct the record by hand and verify the secret never leaks
     * through {@code toString()}. Independent of any environment, so always deterministic.
     */
    @Test
    void toStringMasksTheApiKey() {
        OpenRouterProperties properties = new OpenRouterProperties(
                "mistralai/voxtral-mini-transcribe", "anthropic/claude-3.5-sonnet",
                60.0, 1024, 590, "sk-or-v1-TOPSECRET-UNIQUE");

        assertThat(properties.toString())
                .doesNotContain("sk-or-v1-TOPSECRET-UNIQUE")
                .contains("***");
    }

    /**
     * A keyless record fails lazily: {@code apiKey()} throws only when called, mirroring
     * the "help/startup work without a key" contract. Guarded with {@code assumeTrue} so
     * the test is skipped on a machine that happens to have {@code OPENROUTER_API_KEY}
     * set in its environment (which would bind into the record and make this moot).
     */
    @Test
    void missingKeyFailsLazilyOnCallNotOnConstruction() {
        assumeTrue(System.getenv("OPENROUTER_API_KEY") == null,
                "skip: a real OPENROUTER_API_KEY is set in the environment");

        OpenRouterProperties withoutKey = new OpenRouterProperties(
                "mistralai/voxtral-mini-transcribe", "anthropic/claude-3.5-sonnet",
                60.0, 1024, 590, null);

        // Constructing and inspecting is fine — only apiKey() demands a real key.
        assertThat(withoutKey.isApiKeyConfigured()).isFalse();
        assertThatThrownBy(withoutKey::apiKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("config/application.yml");
    }
}