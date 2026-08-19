package com.example.ytanalysis.client.exception;

/**
 * The transient twin of {@link TransientTranscriptionException}, for the summarisation
 * endpoint. Same idea: permanent plus retry-marker in one class.
 */
public class TransientSummarisationException extends SummarisationException
        implements RetryableApiException {

    public TransientSummarisationException(String message) {
        super(message);
    }

    public TransientSummarisationException(String message, Throwable cause) {
        super(message, cause);
    }
}