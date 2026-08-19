package com.example.ytanalysis.model;

import java.nio.file.Path;

/**
 * A record of everything a single "analyse this video" run produced, returned by
 * {@link com.example.ytanalysis.service.PipelineOrchestrator}.
 *
 * <p>This is the Java home of the Python per-video staging in {@code cli.py:
 * process_single_url}. There, intermediate values were plain local variables and files were
 * always written. Here we bundle the results into one object so both the CLI and the REST
 * layer can shape them into their own output.
 *
 * <p>Several fields are deliberately nullable / context-dependent:
 * <ul>
 *   <li>{@code transcript} text is always set (a run always transcribes),</li>
 *   <li>{@code summary} is {@code null} when summarisation was skipped
 *       ({@code --no-summary / noSummary:true}),</li>
 *   <li>the {@code *Path} fields are {@code null} when the caller asked us not to write
 *       files (the REST {@code save:false} mode, where text is returned in the body).</li>
 * </ul>
 *
 * @param video          the metadata (title + id) known from the download step
 * @param audioPath      where the downloaded {@code .mp3} lives (kept on disk)
 * @param transcript     the full transcript text
 * @param summary        the bullet-point summary text, or {@code null} if skipped
 * @param transcriptPath the file the transcript was saved to, or {@code null} if not saved
 * @param summaryPath    the file the summary was saved to, or {@code null} if not saved
 */
public record AnalysisResult(
        VideoInfo video,
        Path audioPath,
        String transcript,
        String summary,
        Path transcriptPath,
        Path summaryPath) {

    /** Whether summarisation actually ran (a short-cut for callers, mirrors no-summary). */
    public boolean hasSummary() {
        return summary != null;
    }
}