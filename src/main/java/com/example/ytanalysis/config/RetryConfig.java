package com.example.ytanalysis.config;

import com.example.ytanalysis.client.exception.TransientSummarisationException;
import com.example.ytanalysis.client.exception.TransientTranscriptionException;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Retry setup — the Java answer to the Python project's {@code tenacity} decorator
 * ({@code src/utils/retry.py}).
 *
 * <p>That Python code wrapped the leaf API methods with:
 * <pre>{@code
 * @retry(attempts=3)
 * def transcribe(audio_path): ...
 * }</pre>
 * i.e. "try at most 3 times, back off exponentially, but only retry transient errors".
 *
 * <p>Two equivalent ways exist in Spring. We teach both:
 *
 * <ol>
 *   <li><b>Annotation-driven</b> ({@code @Retryable}) — closest to the Python decorator,
 *       reads beautifully: the marker goes right on the method. Requires {@code @EnableRetry}
 *       (state AOP proxies are set up).</li>
 *   <li><b>Template-driven</b> ({@code RetryTemplate}, a {@code @Bean} here) — the fluent
 *       builder; handy when retry settings must be swapped at runtime without recompiling.</li>
 * </ol>
 *
 * The pipeline uses (1); this class provides both the {@code @EnableRetry} switch and the
 * template (2) for illustration/tests.
 */
@Configuration
@EnableRetry   // <- turned on so @Retryable annotations anywhere in the context take effect
public class RetryConfig {

    /** Read via a hard constant so annotations and template agree. */
    public static final int MAX_ATTEMPTS = 3;
    public static final long BASE_DELAY_MS = 1000;
    public static final double MULTIPLIER = 2.0;
    public static final long MAX_DELAY_MS = 10_000;

    /**
     * An optional {@link RetryTemplate} mirroring the same policy, for callers that prefer
     * programmatic retry. Not used by the pipeline itself (which uses {@code @Retryable}),
     * but exercised in the tests and documented in the README.
     */
    @Bean
    public RetryTemplate openRouterRetryTemplate() {
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(BASE_DELAY_MS);    // 1s, then 2s, then 4s (x2, capped)
        backoff.setMultiplier(MULTIPLIER);
        backoff.setMaxInterval(MAX_DELAY_MS);

        // SimpleRetryPolicy's classifier map is keyed on Class<? extends Throwable>, and the
        // retryable marker interface (RetryableApiException) is NOT a Throwable subtype, so we
        // list the two concrete transient classes. Because the classifier matches subclasses,
        // permanent exceptions (which do not extend these) are never retried.
        SimpleRetryPolicy policy = new SimpleRetryPolicy(
                MAX_ATTEMPTS,
                java.util.Map.of(
                        TransientTranscriptionException.class, true,
                        TransientSummarisationException.class, true));

        RetryTemplate retry = new RetryTemplate();
        retry.setBackOffPolicy(backoff);
        retry.setRetryPolicy(policy);
        return retry;
    }

    /** Helper for tests: how long to sleep between attempts. */
    public static Duration backoffDelayForAttempt(int attemptNumberZeroBased) {
        long delay = (long) (BASE_DELAY_MS * Math.pow(MULTIPLIER, attemptNumberZeroBased));
        return Duration.ofMillis(Math.min(delay, MAX_DELAY_MS));
    }
}