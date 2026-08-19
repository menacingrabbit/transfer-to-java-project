package com.example.ytanalysis.service;

import com.example.ytanalysis.client.OpenRouterClient;
import com.example.ytanalysis.util.FileNameUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates and saves the bullet-point summary — a port of {@code summariser.py}
 * {@code summarise_and_save()}.
 *
 * <p>That Python module had zero business logic beyond "call the client, write the file,
 * log it". Keeping that as its own tiny service is still worthwhile: it is the only place
 * that knows <em>where</em> summaries live ({@code <stem>_summary.txt}), which is exactly
 * the kind of responsibility the original stashed in the summarisation package.
 *
 * <p>Unlike the Python original (which returned only the path), we return the text as well,
 * so the REST layer can echo the summary in its JSON body without re-reading the file.
 */
@Service
public class SummarisationService {

    private static final Logger log = LoggerFactory.getLogger(SummarisationService.class);

    private final OpenRouterClient openRouterClient;

    public SummarisationService(OpenRouterClient openRouterClient) {
        this.openRouterClient = openRouterClient;
    }

    /**
     * Result of {@link #summariseAndSave(Path)}: both the summary text and where it was
     * written. A small nested record beats two parallel out-parameters.
     *
     * @param path the file the summary was written to
     * @param text the summary text
     */
    public record Result(Path path, String text) {
    }

    /**
     * Summarise a transcript and write the result to {@code outDir / <stem>_summary.txt} —
     * the naming rule lives in {@link FileNameUtil}.
     */
    public Result summariseAndSave(String transcript, Path outDir, String stem) {
        String summary = openRouterClient.summarise(transcript);
        Path target = outDir.resolve(FileNameUtil.summaryFileName(stem));
        writeUtf8(target, summary);
        log.info("Summary written to {}", target);
        return new Result(target, summary);
    }

    private void writeUtf8(Path target, String content) {
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        }
    }
}