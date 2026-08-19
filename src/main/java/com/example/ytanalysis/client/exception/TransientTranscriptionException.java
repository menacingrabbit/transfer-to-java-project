package com.example.ytanalysis.client.exception;

/**
 * A <b>transient</b> transcription failure — one that might well succeed on a retry:
 * rate limits (429), server hiccups (5xx), timeouts (408) or network drops.
 *
 * <p>Both a {@link TranscriptionException} (so callers can treat it as a transcription
 * problem) <em>and</em> a {@link RetryableApiException} (so the retry proxy will retry it).
 */
public class TransientTranscriptionException extends TranscriptionException
        implements RetryableApiException {

    public TransientTranscriptionException(String message) {
        super(message);
    }

    public TransientTranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}