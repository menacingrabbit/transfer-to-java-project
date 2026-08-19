package com.example.ytanalysis.web;

/**
 * The response body for {@code POST /api/analyse}.
 *
 * <p>Jackson serialises records by their component names, so the JSON will look like:
 * <pre>{@code
 * {
 *   "success": true,
 *   "videoId": "dQw4w9WgXcQ",
 *   "title": "Rick Astley - Never Gonna Give You Up",
 *   "transcript": "…the transcript text…",
 *   "summary": "…the summary text…",      // or null when noSummary was true
 *   "audioPath": "output/…mp3",
 *   "transcriptPath": "output/…transcript.txt", // or null when save=false
 *   "summaryPath": "output/…summary.txt"        // or null when save=false / noSummary
 * }
 * }</pre>
 *
 * <p>The three {@code *Path} fields are {@code String}, not {@link java.nio.file.Path},
 * on purpose: Jackson renders a {@code Path} as a {@code file:///…} URI in JSON, which is
 * not what a caller wants to read. The controller converts the domain's {@code Path} to a
 * plain path string (e.g. {@code output/…mp3}) before responding.
 *
 * @param success        whether the pipeline completed
 * @param videoId        the 11-char YouTube id
 * @param title          the video title
 * @param transcript     the full transcript text (always present)
 * @param summary        the summary text — {@code null} when summarisation was skipped
 * @param audioPath      where the MP3 was saved (path as a string)
 * @param transcriptPath file path, or {@code null} if not saved
 * @param summaryPath    file path, or {@code null} if not saved / summarisation skipped
 */
public record AnalyseResponse(
        boolean success,
        String videoId,
        String title,
        String transcript,
        String summary,
        String audioPath,
        String transcriptPath,
        String summaryPath) {
}