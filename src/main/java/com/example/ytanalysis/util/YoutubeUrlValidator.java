package com.example.ytanalysis.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Validates a YouTube URL and extracts the 11-character video id — the Java port of
 * {@code validate_youtube_url()} + the {@code _YOUTUBE_URL_PATTERN} regex in
 * {@code src/cli.py}.
 *
 * <p>This <em>is</em> a Spring bean (a component), because two collaborator classes use it
 * through injection: the CLI ({@link com.example.ytanalysis.cli.CliRunner}) and the REST
 * controller ({@link com.example.ytanalysis.web.AnalysisController}). It carries no state,
 * but being a bean lets both callers share the exact same rule and lets tests replace it
 * if they ever need to. Component scanning in {@code YtAnalysisApplication} turns this
 * into a singleton bean automatically.
 *
 * <p>The original regex (Python):
 * <pre>{@code
 * r"^https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/)([a-zA-Z0-9_-]{11})"
 * }</pre>
 * It requires the {@code http(s)://} scheme, an optional {@code www.}, then either
 * {@code youtube.com/watch?v=} or {@code youtu.be/}, then an 11-char id of letters,
 * digits, {@code -} or {@code _}.
 */
@Component
public class YoutubeUrlValidator {

    /** The single source of truth for "is this a valid link?" — keep in sync with Python. */
    private static final Pattern YOUTUBE_URL =
            Pattern.compile("^https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})");

    /**
     * Extract the video id if the URL is a valid YouTube link, or
     * {@link Optional#empty()} if it is not (Python returned a match or failure).
     */
    public Optional<String> extractVideoId(String url) {
        if (url == null) {
            return Optional.empty();
        }
        Matcher m = YOUTUBE_URL.matcher(url);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(m.group(1));
    }

    /**
     * Throws {@link IllegalArgumentException} if the URL is not a valid YouTube link.
     * This is the exact Java mirror of {@code raise ValueError("Invalid YouTube URL...")}.
     */
    public void validate(String url) {
        if (!isValid(url)) {
            throw new IllegalArgumentException(
                    "Invalid YouTube URL: " + url
                    + ". Expected http(s)://www.youtube.com/watch?v=VIDEOID or http(s)://youtu.be/VIDEOID");
        }
    }

    /** True if the URL matches and yields an 11-char id. */
    public boolean isValid(String url) {
        return extractVideoId(url).isPresent();
    }
}