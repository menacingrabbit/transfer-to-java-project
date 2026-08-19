package com.example.ytanalysis.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Produces the single {@link RestClient} bean used for every OpenRouter HTTP call.
 *
 * <p>The Python client used {@code httpx.post(url, ..., timeout=api_timeout())}. Spring's
 * {@code RestClient} (added in Spring 6.1) is the modern, synchronous, fluent client that
 * replaces the older {@code RestTemplate}. It is the natural Java mirror of {@code httpx}.
 *
 * <p><b>{@code @Configuration}</b> tells Spring "this class only defines beans — no
 * business logic". Each {@code @Bean} method's return value becomes one object in the
 * container that other beans can ask for by type.
 *
 * <p>We build the client with a {@code JdkClientHttpRequestFactory} (the JDK's own
 * {@code java.net.http.HttpClient} under the hood). Using the {@code OpenRouterProperties}
 * timeout also demonstrates constructor injection at the {@code @Bean} level: Spring sees
 * the argument, finds the matching property bean, and passes it in.
 */
@Configuration
public class RestClientConfig {

    /**
     * The one {@code RestClient} the app shares.
     *
     * @param openRouter the bound settings (needed for the timeout)
     * @return a pre-configured, thread-safe client
     */
    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties openRouter) {
        // A request factory lets us set connect + read timeouts. The Python default of 60s
        // (OPENROUTER_TIMEOUT) is the read timeout here — the time we wait for a response.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis((long) (openRouter.timeoutSeconds() * 1000)));

        // We leave baseUrl() out on purpose: both endpoints share one host but different
        // paths, and OpenRouterClient builds full URLs. See that class's comments.
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}