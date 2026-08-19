package com.example.ytanalysis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ytanalysis.client.exception.TransientSummarisationException;
import com.example.ytanalysis.client.exception.TranscriptionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.retry.RetryContext;

/**
 * Verifies the retry policy behind the {@code @Retryable} annotations — here driven through
 * the {@link RetryTemplate} bean, which shares the same constants and classifier as the
 * annotation path.
 *
 * <p>Both entry points use the identical building blocks ({@link RetryConfig#MAX_ATTEMPTS},
 * the transient marker), so proving the template honours "transient → up to 3 tries,
 * permanent → exactly 1" tests the policy that {@code @Retryable} relies on.
 */
class RetryPolicyTest {

    /** The actual configured template from our production bean method — no test-only copy. */
    private final org.springframework.retry.support.RetryTemplate retry =
            new RetryConfig().openRouterRetryTemplate();

    @Test
    void retriesATransientFailureUpToThreeTimes() {
        AtomicInteger attempts = new AtomicInteger();
        int out = retry.execute((RetryContext ctx) -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                throw new TransientSummarisationException("rate limited, attempt " + n);
            }
            return n;   // succeeds on the third and final try
        });
        assertThat(out).isEqualTo(3);
        assertThat(attempts).hasValue(3); // one per try, no more
    }

    @Test
    void givesUpAfterThreeAttemptsWhenAlwaysTransient() {
        AtomicInteger attempts = new AtomicInteger();
        // With no recovery callback, RetryTemplate 2.x rethrows the LAST transient
        // exception after exhausting its attempts (it does not wrap it).
        assertThatThrownBy(() -> retry.execute((RetryContext ctx) -> {
            throw new TransientSummarisationException("always transient " + attempts.incrementAndGet());
        })).isInstanceOf(TransientSummarisationException.class).hasMessage("always transient 3");

        assertThat(attempts).hasValue(RetryConfig.MAX_ATTEMPTS); // 3, never 4
    }

    @Test
    void doesNotRetryAPermanentFailure() {
        AtomicInteger attempts = new AtomicInteger();
        // TranscriptionException is the PERMANENT type — it never implements the retryable
        // marker, so the classifier must not retry it.
        assertThatThrownBy(() -> retry.execute((RetryContext ctx) -> {
            throw new TranscriptionException("bad request " + attempts.incrementAndGet());
        })).isInstanceOf(TranscriptionException.class);

        assertThat(attempts).hasValue(1); // only the original attempt
    }

    @Test
    void backoffGrowsAndCaps() {
        // delays: 1000, 2000, 4000, 8000, then capped at 10000
        assertThat(RetryConfig.backoffDelayForAttempt(0)).hasMillis(1000);
        assertThat(RetryConfig.backoffDelayForAttempt(1)).hasMillis(2000);
        assertThat(RetryConfig.backoffDelayForAttempt(2)).hasMillis(4000);
        assertThat(RetryConfig.backoffDelayForAttempt(3)).hasMillis(8000);
        assertThat(RetryConfig.backoffDelayForAttempt(10)).hasMillis(10000); // capped
    }

    }