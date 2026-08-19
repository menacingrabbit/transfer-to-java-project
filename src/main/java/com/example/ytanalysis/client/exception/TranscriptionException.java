package com.example.ytanalysis.client.exception;

/**
 * A permanent (non-retryable) transcription failure — e.g. a 401 with an invalid API key,
 * a 400 bad request, or a non-JSON response body.
 *
 * <p>It deliberately does <em>not</em> implement {@link RetryableApiException}, so Spring
 * Retry (configured with {@code retryFor = RetryableApiException.class}) rethrows it on the
 * first attempt without wasting retries — exactly like Python's {@code TranscriptionError},
 * which did not subclass {@code RetryableError}.
 */
public class TranscriptionException extends OpenAiApiException {

    public TranscriptionException(String message) {
        super(message);
    }

    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}