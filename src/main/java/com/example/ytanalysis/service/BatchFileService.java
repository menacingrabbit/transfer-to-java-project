package com.example.ytanalysis.service;

import com.example.ytanalysis.util.YoutubeUrlValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Reads a batch file (one YouTube URL per line) and returns the valid URLs — a port of
 * {@code read_urls_from_file()} in {@code src/cli.py}.
 *
 * <p>Rules inherited verbatim: lines starting with {@code #} are comments, blank lines are
 * skipped, and every remaining line must be a valid YouTube URL — the whole batch is rejected
 * (here: {@link IllegalArgumentException}) if any line is malformed, matching the Python
 * behaviour of raising inside the loop.
 *
 * <p>This is a small service rather than a method in the CLI runner so both the CLI (batch
 * mode) and tests can reuse it without touching the arg parser.
 */
@Service
public class BatchFileService {

    private final YoutubeUrlValidator urlValidator;

    public BatchFileService(YoutubeUrlValidator urlValidator) {
        this.urlValidator = urlValidator;
    }

    /**
     * @param file the batch file
     * @return the list of valid URLs, in file order
     * @throws IllegalArgumentException if the file yields zero URLs or any line is invalid
     */
    public List<String> readUrls(Path file) {
        List<String> urls = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                urlValidator.validate(trimmed);           // throws → rejects the whole batch
                urls.add(trimmed);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read batch file: " + file, e);
        }
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("No valid YouTube URLs found in " + file);
        }
        return urls;
    }
}