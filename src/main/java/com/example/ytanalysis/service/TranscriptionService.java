package com.example.ytanalysis.service;

import com.example.ytanalysis.client.OpenRouterClient;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the split-then-transcribe workflow — the Java port of
 * {@code transcribe_split()} in {@code src/transcription/client.py}.
 *
 * <p><b>Why this is a separate bean (and why it is NOT annotated {@code @Retryable}):</b>
 * the Python authors deliberately retried only the <em>leaf</em> network calls (each single
 * chunk transcription), not this orchestration — "a late failure does not re-split and
 * re-transcribe already-finished chunks". In Spring the same principle needs one extra line
 * of thinking: the retry behaviour lives on the {@code OpenRouterClient} bean as an AOP
 * proxy. For this class's calls to {@code openRouter.transcribe(chunk)} to go through that
 * proxy, the calls must cross a <em>bean boundary</em> — i.e. the caller here must be a
 * different bean. That is exactly why {@code TranscriptionService} is a separate
 * {@code @Service} and not a method inside the client. {@code @Retryable} never works on a
 * method called from inside its own class (self-invocation bypasses the proxy) — a classic
 * exam-question gotcha we avoid by design.
 *
 * <p>Behaviour ported 1:1:
 * <ul>
 *   <li>short files are transcribed directly (no split),</li>
 *   <li>long files are split, every chunk transcribed, and combined as
 *       {@code "--- Part {i} ---\n{text}"},</li>
 *   <li>chunks whose transcription came back empty produce no section,</li>
 *   <li>the temp chunk files are cleaned up in a {@code finally} when more than one chunk
 *       was produced.</li>
 * </ul>
 */
@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final OpenRouterClient openRouterClient;
    private final AudioSplitter audioSplitter;
    private final int chunkSeconds;

    /**
     * Constructor injection: Spring provides all three collaborators.
     *
     * @param chunkSeconds bound from {@code openrouter.chunk-seconds} (default 590) — the
     *                     split duration. (The Python project left this setting effectively
     *                     unused; here we really honour it.)
     */
    public TranscriptionService(OpenRouterClient openRouterClient,
                                AudioSplitter audioSplitter,
                                @Value("${openrouter.chunk-seconds:590}") int chunkSeconds) {
        this.openRouterClient = openRouterClient;
        this.audioSplitter = audioSplitter;
        this.chunkSeconds = chunkSeconds;
    }

    /**
     * Transcribe one audio file, splitting it if needed.
     *
     * @return the full transcript text (concatenated parts when split)
     */
    public String transcribeOrDefault(Path audioPath) {
        List<Path> chunks = audioSplitter.splitIfNeeded(audioPath, chunkSeconds);

        boolean wasSplit = chunks.size() > 1;
        try {
            if (!wasSplit) {
                return openRouterClient.transcribe(audioPath);
            }

            List<String> parts = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String text = openRouterClient.transcribe(chunks.get(i));
                if (text != null && !text.isBlank()) {
                    parts.add("--- Part " + (i + 1) + " ---\n" + text);
                }
            }
            String joined = String.join("\n", parts).strip();
            return joined;
        } finally {
            // Runs even if one chunk's transcription threw (Python used try/finally too).
            if (wasSplit) {
                audioSplitter.cleanupChunks(chunks);
            }
        }
    }
}