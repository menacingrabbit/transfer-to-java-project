package com.example.ytanalysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * User-facing progress messages — a simple stand-in for the Python {@code tqdm} bars and
 * {@code rich} console output.
 *
 * <p>The original printed live progress bars for downloads and colourful section banners.
 * Reproducing a real progress bar would drag in a dependency and a terminal-formatting
 * library; for a learning project a few friendly log lines ("Downloading audio...",
 * "Transcribing...") convey the same pipeline stages with a fraction of the machinery. The
 * {@code logback-spring.xml} routes this class's logger straight to the console so the
 * messages stand out from the framework logs.
 *
 * <p>Why a {@code @Component} instead of static methods? Because other beans use it via
 * constructor injection, and a logger name that lives on the class is testable — a test can
 * inject a real instance and inspect behaviour, or a mock, if it ever needs to.
 */
@Component
public class ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ProgressReporter.class);

    public void processingUrl(String url) {
        log.info("== Processing: {} ==", url);
    }

    public void downloading(String title) {
        log.info("Downloading audio for '{}'...", title);
    }

    public void transcribing() {
        log.info("Transcribing...");
    }

    public void splitting(long chunkCount) {
        log.info("Splitting audio into {} chunks...", chunkCount);
    }

    public void summarising() {
        log.info("Summarising...");
    }

    public void done(String summary) {
        log.info("Complete: {}", summary);
    }

    public void batchSummary(int succeeded, int total) {
        log.info("Batch complete: {}/{} successful", succeeded, total);
    }

    public void error(String url, String message) {
        log.error("Error for {}: {}", url, message);
    }
}