package com.example.ytanalysis.client;

import com.example.ytanalysis.client.exception.OpenAiApiException;
import com.example.ytanalysis.client.exception.RetryableApiException;
import com.example.ytanalysis.client.exception.SummarisationException;
import com.example.ytanalysis.client.exception.TranscriptionException;
import com.example.ytanalysis.client.exception.TransientSummarisationException;
import com.example.ytanalysis.client.exception.TransientTranscriptionException;
import com.example.ytanalysis.client.json.ChatResponse;
import com.example.ytanalysis.client.json.TranscribeResponse;
import com.example.ytanalysis.config.OpenRouterProperties;
import com.example.ytanalysis.config.RetryConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The OpenRouter HTTP client — a port of {@code src/transcription/client.py}. It handles
 * BOTH external calls of the pipeline: transcription and summarisation.
 *
 * <h2>Mapping the Python module</h2>
 * <ul>
 *   <li>{@code _AUDIO_API_URL} → {@link #AUDIO_API_URL}</li>
 *   <li>{@code _CHAT_API_URL} → {@link #CHAT_API_URL}</li>
 *   <li>{@code _post()} + {@code _raise_with_details()} → {@link #postJson(...)} + {@link #classify(...)}</li>
 *   <li>{@code @retry() transcribe()} → {@link #transcribe(Path)} annotated {@code @Retryable}</li>
 *   <li>{@code @retry() summarise()} → {@link #summarise(String)} annotated {@code @Retryable}</li>
 * </ul>
 *
 * <p><b>Retry philosophy (unchanged from the original):</b> only the two <em>leaf</em> calls
 * retry. The orchestration in {@code TranscriptionService} is <em>not</em> annotated, for the
 * same reason the Python authors gave: a late failure should not re-split and re-transcribe
 * chunks that already succeeded. Because {@code TranscriptionService} is a separate bean, its
 * calls into this bean go through the retry proxy — see the class javadoc there.
 *
 * <p><b>Dependency injection:</b> this {@code @Service} asks Spring for a {@code RestClient}
 * (created in {@link com.example.ytanalysis.config.RestClientConfig}), an
 * {@code OpenRouterProperties} (bound from env) — both handed to the constructor. Constructor
 * injection is the recommended Spring style: the fields can be {@code final}, nothing is
 * mutable, and the class is trivially testable with plain constructor args.
 */
@Service
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    /** OpenRouter transcription endpoint (OpenAI Whisper-compatible). */
    public static final String AUDIO_API_URL = "https://openrouter.ai/api/v1/audio/transcriptions";
    /** OpenRouter chat-completion endpoint used for summarisation. */
    public static final String CHAT_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    /** HTTP statuses the Python code treats as transient (worth a retry). */
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

    /** The bullet-point summarisation prompt, copied verbatim from the Python original. */
    private static final String SUMMARY_PROMPT = """
            Summarise the following transcript in bullet points, focusing on the main \
            ideas, arguments, and conclusions. Write detailed but concise summaries. \
            Finally, write 1 sentence summarising the key takeaway. Use concise language.\n\
            \n\
            ```\n%s\n```""";

    // HTML <title> extraction for Cloudflare-style error pages (same idea as the Python
    // "extract the <title> text as the message" branch).
    private static final Pattern HTML_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private final RestClient restClient;
    private final OpenRouterProperties properties;

    /**
     * @param restClient the shared, time-out-configured client (see RestClientConfig)
     * @param properties the bound OpenRouter settings (models, timeout, max tokens)
     */
    public OpenRouterClient(RestClient restClient, OpenRouterProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    // ---------------------------------------------------------------------------------
    // Public API — the two annotated leaf methods.
    // ---------------------------------------------------------------------------------

    /**
     * Transcribe an audio file through OpenRouter.
     *
     * <p>Port of {@code transcribe(audio_path)}: base64-encode the file, POST it to the
     * audio endpoint, return the {@code "text"} field trimmed. An empty or missing text
     * becomes {@code ""} (the Python {@code data.get("text", "").strip()}).
     *
     * <p><b>{@code @Retryable}</b> wraps this method with a Spring AOP proxy: if the call
     * throws something implementing {@link RetryableApiException}, Spring retries up to
     * {@value RetryConfig#MAX_ATTEMPTS} times with exponential backoff (1s → 2s → 4s → cap
     * 10s), then rethrows. Permanent exceptions are rethrown immediately.
     */
    // NOTE: retryFor takes Class<? extends Throwable>[], which an interface reference does
    // not satisfy. Spring matches by "is the thrown exception a subclass of any listed class?",
    // so listing the two CONCRETE transient types is enough: those (or their subclasses) get
    // retried, and the permanent classes are deliberately absent, so they never retry.
    @Retryable(retryFor = {TransientTranscriptionException.class, TransientSummarisationException.class},
               maxAttempts = RetryConfig.MAX_ATTEMPTS,
               backoff = @Backoff(delay = RetryConfig.BASE_DELAY_MS,
                                  multiplier = RetryConfig.MULTIPLIER,
                                  maxDelay = RetryConfig.MAX_DELAY_MS))
    public String transcribe(Path audioPath) {
        String payload = transcribePayload(audioPath);
        TranscribeResponse response = postJson(
                AUDIO_API_URL, payload, TranscribeResponse.class,
                TranscriptionException::new,          // permanent factory
                TransientTranscriptionException::new); // transient factory
        String text = response == null ? "" : response.text();
        return (text == null ? "" : text).strip();
    }

    /**
     * Summarise a transcript through OpenRouter.
     *
     * <p>Port of {@code summarise(transcript)}: send a chat-completion request whose single
     * user message is the bullet-point prompt with the transcript embedded, and read the
     * first choice's message content.
     */
    @Retryable(retryFor = {TransientTranscriptionException.class, TransientSummarisationException.class},
               maxAttempts = RetryConfig.MAX_ATTEMPTS,
               backoff = @Backoff(delay = RetryConfig.BASE_DELAY_MS,
                                  multiplier = RetryConfig.MULTIPLIER,
                                  maxDelay = RetryConfig.MAX_DELAY_MS))
    public String summarise(String transcript) {
        Map<String, Object> body = Map.of(
                "model", properties.summariseModel(),
                "messages", new Object[]{
                        Map.of("role", "user", "content", SUMMARY_PROMPT.formatted(transcript))
                },
                "max_tokens", properties.maxTokens());

        ChatResponse response = postJson(
                CHAT_API_URL, body, ChatResponse.class,
                SummarisationException::new,
                TransientSummarisationException::new);

        return response == null ? "" : response.firstContent().strip();
    }

    // ---------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------

    /** Build the JSON string for the audio endpoint (base64 file inside {@code input_audio}). */
    private String transcribePayload(Path audioPath) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(audioPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read audio file for transcription: " + audioPath, e);
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String format = extensionOf(audioPath);
        // {"model":..., "input_audio":{"data":<base64>,"format":<ext>}}
        return "{\"model\":\"" + properties.transcribeModel()
                + "\",\"input_audio\":{\"data\":\"" + base64
                + "\",\"format\":\"" + format + "\"}}";
    }

    /**
     * The audio format key OpenRouter expects — Python:
     * {@code audio_path.suffix.lstrip(".").lower()}, defaulting to {@code "wav"}.
     */
    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "wav";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /**
     * POST a JSON body, deserialise the response, and translate any HTTP or network failure
     * into our exception hierarchy.
     *
     * <p>This is the Java counterpart of Python's {@code _post()} — but Java cannot pass
     * exception <em>classes</em> around the way Python passes the class itself as an
     * argument ({@code permanent} / {@code transient}), so we pass two factory lambdas.
     *
     * @param <T>         the response type Jackson should map to
     * @param permanent   factory for permanent errors (e.g. {@code TranscriptionException::new})
     * @param transientE  factory for transient errors (e.g. {@code TransientTranscriptionException::new})
     */
    private <T> T postJson(String url, Object body, Class<T> responseType,
                           java.util.function.Function<String, ? extends OpenAiApiException> permanent,
                           java.util.function.Function<String, ? extends OpenAiApiException> transientE) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.apiKey());   // <-- the ONLY call that triggers lazy key check
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            return restClient.post().uri(url).headers(h -> h.addAll(headers)).body(body)
                    .retrieve()
                    .body(responseType);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // 4xx / 5xx with a body — classify transient vs permanent from the status code.
            throw classify(e.getStatusCode().value(), e.getResponseBodyAsString(), permanent, transientE);
        } catch (RestClientException e) {
            // connect/read/DNS errors never got an HTTP status → always transient, like Python.
            throw transientE.apply("Network error contacting OpenRouter: " + e.getMessage());
        }
    }

    /**
     * Port of {@code _raise_with_details()}: pick the most useful message from the response,
     * append a per-status hint, and throw the right exception type.
     */
    private OpenAiApiException classify(int status, String body,
                                        java.util.function.Function<String, ? extends OpenAiApiException> permanent,
                                        java.util.function.Function<String, ? extends OpenAiApiException> transientE) {
        String message = extractMessage(status, body);
        String hint = statusHint(status);
        String full = hint == null ? message : message + " (" + hint + ")";

        // Only RETRYABLE_STATUSES map to the transient marker; everything else is permanent.
        if (RETRYABLE_STATUSES.contains(status)) {
            return transientE.apply(full);
        }
        return permanent.apply(full);
    }

    /** Extract a human message from a JSON {@code {"error": {"message": ...}}} or an HTML page. */
    private String extractMessage(int status, String body) {
        if (body == null || body.isBlank()) {
            return "HTTP " + status;
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html")) {
            Matcher m = HTML_TITLE.matcher(trimmed);
            if (m.find()) {
                return "Error page from OpenRouter/Cloudflare: " + m.group(1).strip();
            }
            return "HTML error page (HTTP " + status + ")";
        }
        try {
            // try to read {"error": {"message": ...}}
            Map<?, ?> root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, Map.class);
            Object err = root.get("error");
            if (err instanceof Map<?, ?> em) {
                Object msg = em.get("message");
                if (msg != null) {
                    return msg.toString();
                }
            }
        } catch (IOException ignored) {
            // not JSON — fall through to raw body
        }
        return body.strip();
    }

    /** Per-status hints, mirroring the Python {@code status_hints} map. */
    private String statusHint(int status) {
        return switch (status) {
            case 400 -> "invalid request";
            case 401 -> "check your key (OPENROUTER_API_KEY or openrouter.api-key in config/application.yml)";
            case 402 -> "add credits to your OpenRouter account";
            case 403 -> "forbidden / content policy";
            case 404 -> "resource not found";
            case 408 -> "timeout";
            case 429 -> "rate limit — retrying shortly";
            case 500 -> "internal server error";
            case 502 -> "bad gateway";
            case 503 -> "service unavailable";
            case 504 -> "gateway timeout";
            default -> null;
        };
    }
}