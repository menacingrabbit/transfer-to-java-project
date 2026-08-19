package com.example.ytanalysis.client.exception;

/**
 * The interface that marks an exception as <em>worth retrying</em>.
 *
 * <p>This is the Java answer to a Python <b>mix-in</b>. In Python an exception could inherit
 * from <i>two</i> bases at once:
 * <pre>{@code
 * class TransientTranscriptionError(TranscriptionError, RetryableError): ...
 * }</pre>
 * Java has single inheritance, so instead of a base class we use an <b>interface as a
 * marker</b>. Any exception that broad implements this interface tells the retry machinery
 * "transient: please try again". The permanent exception classes intentionally do not
 * implement it.
 *
 * <p>Spring Retry reads it as {@code retryFor = RetryableApiException.class} — see
 * {@link com.example.ytanalysis.config.RetryConfig} and the {@code @Retryable} annotations
 * on the client methods.
 */
public interface RetryableApiException {
}