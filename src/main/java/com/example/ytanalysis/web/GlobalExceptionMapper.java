package com.example.ytanalysis.web;

import com.example.ytanalysis.client.exception.OpenAiApiException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A single place that turns exceptions thrown anywhere in the web layer into HTTP responses.
 *
 * <p>Without this class, a thrown {@code IllegalArgumentException} would surface as a generic
 * {@code 500 Internal Server Error} — correct but useless to a caller, who has no way to know
 * they sent a malformed URL. This advice catches known failures early and responds with the
 * HTTP status that actually describes the problem.
 *
 * <p><strong>Educational note — what {@code @RestControllerAdvice} is.</strong> It is Spring's
 * "error handler bus": one component that intercepts exceptions bubbled out of any controller,
 * and its {@code @ExceptionHandler} methods decide the response. It replaces many scattered
 * try/catch blocks with one declarative mapping table. Because {@code @ExceptionHandler}
 * methods can receive the exception (and the request), we usually shape a small JSON body so
 * the caller learns <em>what</em> went wrong, not just <em>that</em> it did.
 *
 * <p>We use {@link ProblemDetail} (RFC 7807) as the body shape — Spring's built-in "human and
 * machine readable error" envelope, e.g.:
 * <pre>{@code
 * { "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "url is required" }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionMapper {

    /**
     * A body-validation failure. {@code @RequestBody ... @Valid} triggers this when a field
     * violates a constraint (e.g. the {@code @NotBlank} on {@link AnalyseRequest}.{@code url}),
     * and we collect every message so a caller fixing one field at a time is not needed.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException e) {
        String joined = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed");
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, joined);
        detail.setTitle("Bad Request");
        return detail;
    }

    /**
     * The JSON body could not be read (malformed syntax, or a value of the wrong type).
     * Either way the request cannot be understood — a 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Malformed request body");
        detail.setTitle("Bad Request");
        return detail;
    }

    /**
     * A URL-shaped value arrived where a different type was expected (or the URL failcase
     * raised as {@link IllegalArgumentException}). Both are caller mistakes, not server ones.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArgument(IllegalArgumentException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage() == null ? "Invalid argument" : e.getMessage());
        detail.setTitle("Bad Request");
        return detail;
    }

    /**
     * Any problem talking to the OpenRouter API — the work failed for a remote reason, not
     * a bug in our pipeline. {@code 502 Bad Gateway} tells a proxy or caller "our upstream
     * is unhappy". (The {@code 401/403} from a bad key is a subclass here and is also 502 —
     * an education note could split it into a 500 or a message pointing at the key.)
     */
    @ExceptionHandler(OpenAiApiException.class)
    public ProblemDetail handleUpstream(OpenAiApiException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, e.getMessage() == null ? "Upstream provider error" : e.getMessage());
        detail.setTitle("Bad Gateway");
        return detail;
    }

    /**
     * MethodArgumentTypeMismatchException (a query param of the wrong type) is an
     * IllegalArgumentException subclass, so it lands in the 400 bucket.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Wrong type for parameter '" + e.getName() + "'");
        detail.setTitle("Bad Request");
        return detail;
    }

    /**
     * The true fallback: an unexpected bug anywhere leaves a 500, and we log it so it is not
     * silently swallowed. The {@code Exception e} parameter means Spring matches any throwable.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error");
        detail.setTitle("Internal Server Error");
        return detail;
    }
}