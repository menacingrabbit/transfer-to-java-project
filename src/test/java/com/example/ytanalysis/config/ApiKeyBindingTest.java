package com.example.ytanalysis.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-context binding test for the API key, as its own top-level class (Surefire does not
 * discover nested test classes).
 *
 * <p>{@code @SpringBootTest(properties = "openrouter.api-key=...")} supplies the key as a
 * <em>property</em>, the exact path a user's external {@code config/application.yml}
 * exercises. If binding broke (e.g. the record lost its {@code apiKey} component), this
 * test would fail — it is the concrete proof that the external-file workflow lands in
 * {@link OpenRouterProperties#apiKey()}, not just the env-var workflow.
 */
@SpringBootTest(properties = "openrouter.api-key=test-key-123")
class ApiKeyBindingTest {

    @Autowired
    private OpenRouterProperties properties;

    @Test
    void keyFromYamlStylePropertyIsBoundAndReportedConfigured() {
        assertThat(properties.apiKey()).isEqualTo("test-key-123");
        assertThat(properties.isApiKeyConfigured()).isTrue();
    }
}