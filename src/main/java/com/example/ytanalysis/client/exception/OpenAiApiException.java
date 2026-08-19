package com.example.ytanalysis.client.exception;

/**
 * The abstract base of every OpenRouter-related error thrown by this app — the Java
 * counterpart of Python's {@code RuntimeError} subclasses in {@code src/transcription/client.py}.
 *
 * <p>Making it abstract forces callers to use one of the specific subtypes, which in turn
 * lets the global error mapper and the CLI decide the right handling (bad request, bad
 * key, rate limit...) from the exception's concrete type.
 */
public abstract class OpenAiApiException extends RuntimeException {

    protected OpenAiApiException(String message) {
        super(message);
    }

    protected OpenAiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}