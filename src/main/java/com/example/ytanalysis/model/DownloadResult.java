package com.example.ytanalysis.model;

import java.nio.file.Path;

/**
 * What {@code AudioDownloadService} returns: the MP3 path <em>and</em> the video metadata
 * learned during download.
 *
 * <p>The Python function returned only a {@code Path} ({@code download_audio(...) -> path})
 * and let the caller lose the title/id. We bundle them because the REST response wants the
 * video's title and id back to the caller — a tiny improvement over the original, made
 * explicit here so the design intent is visible.
 *
 * @param video    title + id from the metadata pass
 * @param audioPath the produced MP3 (existing or newly downloaded)
 */
public record DownloadResult(VideoInfo video, Path audioPath) {
}