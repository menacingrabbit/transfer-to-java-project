package com.example.ytanalysis.client.exception;

/**
 * A permanent (non-retryable) summarisation failure — the {@code SummarisationError} of the
 * Python original. See {@link TranscriptionException} for the retry rationale, which applies
 * identically here.
 */
public class SummarisationException extends OpenAiApiException {

    public SummarisationException(String message) {
        super(message);
    }

    public SummarisationException(String message, Throwable cause) {
        super(message, cause);
    }
}