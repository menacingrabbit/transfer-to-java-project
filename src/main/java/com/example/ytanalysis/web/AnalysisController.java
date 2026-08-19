package com.example.ytanalysis.web;

import com.example.ytanalysis.config.OpenRouterProperties;
import com.example.ytanalysis.model.AnalysisResult;
import com.example.ytanalysis.model.PipelineOptions;
import com.example.ytanalysis.service.PipelineOrchestrator;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The REST entry point, sibling to the {@link com.example.ytanalysis.cli.CliRunner}.
 *
 * <p>It exposes the pipeline over HTTP so a browser or another program can trigger it the
 * same way {@code curl} would — and crucially it calls the <em>same</em> orchestration
 * service as the CLI. Spring sees two ways in (Tomcat dispatcher and {@code CommandLineRunner}),
 * but there is only ever one pipeline implementation: {@link PipelineOrchestrator}.
 *
 * <p><strong>Educational note — why this controller stays thin:</strong> everything of interest
 * (download, split, transcribe, summarise) lives behind {@link PipelineOrchestrator#analyse}.
 * The controller is just the HTTP vocabulary (verbs, status codes, JSON shapes) pinned onto an
 * already-existing service. If you read this class and the CLI runner back to back, you will see
 * they are mirrors: the same call reachable two ways. That is the whole point of carving services
 * out of the entry points.
 *
 * <p><strong>Educational note — the synchronous reality.</strong> A real pipeline run blocks a
 * Tomcat thread for the full duration (mins). The CLI never notices because the terminal waits.
 * For a browser user this is a poor experience once the queue exists. The production fix is an
 * {@code @Async} method plus a job table and {@code GET /api/jobs/{id}} polling — a classic
 * exercise. A commented-out sketch of that shape sits below in {@link #analyseAsyncSketch()}.
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final PipelineOrchestrator pipeline;
    private final OpenRouterProperties openRouterProperties;

    // Constructor injection: Spring hands us the composed pipeline and its configuration.
    // There is no 'new' here, no wiring by hand — the container resolves the graph for us.
    public AnalysisController(PipelineOrchestrator pipeline,
                              OpenRouterProperties openRouterProperties) {
        this.pipeline = pipeline;
        this.openRouterProperties = openRouterProperties;
    }

    /**
     * Run the full pipeline for one URL and return everything it produced.
     *
     * <p>Because the request record is marked {@code @Valid}, Spring runs Jakarta Bean
     * Validation before the method body — a blank/invalid body yields {@code 400} without
     * any code here. URL shape is validated again inside {@link PipelineOrchestrator}, which
     * throws {@link IllegalArgumentException} for a non-YouTube URL; the
     * {@link GlobalExceptionMapper} turns that into {@code 400} too.
     *
     * @param request the validated body ({@code url} required; the rest optional)
     * @return {@code 200} with the {@link AnalyseResponse}, or an error mapped by the advice
     */
    @PostMapping("/analyse")
    public AnalyseResponse analyse(@Valid @RequestBody AnalyseRequest request) {
        Path outDir = Path.of(request.outDir());

        PipelineOptions options = PipelineOptions.of(
                request.noSummary(), request.force(), request.split());
        // save comes straight from the request — the only option PipelineOptions.of() doesn't set
        options = options.withSave(request.save());

        AnalysisResult result = pipeline.analyse(request.url(), outDir, options);

        // The *Path fields are converted from Path to plain path strings (Jackson would
        // otherwise render file:/// URIs). toString() keeps the same relative form the CLI
        // logs, e.g. "output/<stem>.mp3".
        return new AnalyseResponse(
                true,
                result.video().videoId(),
                result.video().title(),
                result.transcript(),
                result.summary(),
                pathToString(result.audioPath()),
                pathToString(result.transcriptPath()),
                pathToString(result.summaryPath()));
    }

    /** Null-safe Path → display string (null stays null, so save=false reads as null). */
    private static String pathToString(java.nio.file.Path p) {
        return p == null ? null : p.toString();
    }

    /**
     * A zero-cost health probe. The set of things we can assert without risking work:
     * Spring is up (we're handling the request), and whether an API key is configured.
     * Note we deliberately do <em>not</em> call the remote API here — health checks should
     * be cheap and offline.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(
                "UP",
                openRouterProperties.isApiKeyConfigured(),
                "0.0.1-SNAPSHOT"));
    }

    /** Small record shaped for the health body (registers a nested class is fine in Java 21). */
    public record HealthResponse(String status, boolean apiKeyConfigured, String version) {
    }

    /**
     * Exercise (not wired up): the {@code @Async} + polling sketch for long jobs.
     *
     * <pre>{@code
     * @Async("pipelineExecutor")
     * public CompletableFuture<AnalysisResult> analyseAsync(String url, Path outDir, PipelineOptions options) {
     *     return CompletableFuture.completedFuture(pipeline.analyse(url, outDir, options));
     * }
     * }</pre>
     * A controller route would {@code CompletableFuture<T>} it; a {@code /api/jobs/{id}} GET
     * endpoint would poll {@code Future.isDone()}. Blocking synchronous runs stay the tutorial's
     * simple default.
     */
    private void analyseAsyncSketch() {
        // not reachable; exists as a place to hold the comment above
    }
}