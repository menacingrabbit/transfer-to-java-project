package com.example.ytanalysis.model;

/**
 * A small immutable value object holding what we learn about a YouTube video
 * before downloading it.
 *
 * <p>In the Python original this was just a {@code dict} grabbed from
 * {@code yt_dlp.YoutubeDL.extract_info(url, download=False)}:
 * <pre>{@code
 * title    = info.get("title") or "audio"
 * video_id = str(info.get("id") or "")
 * }</pre>
 *
 * <p>Java's equivalent of a plain data holder is a <b>record</b>. A record
 * auto-generates the constructor, {@code equals}/{@code hashCode}, {@code toString}
 * and accessors — so it is the idiomatic way to say "just carry these values".
 * Records are the modern replacement for hand-written POJOs/bean classes.
 *
 * @param title   the video title, or {@code "audio"} if the extractor gave none
 * @param videoId the 11-character YouTube id (empty string, not null, if absent)
 */
public record VideoInfo(String title, String videoId) {

    /** Convenience: whether a usable video id is present (the Python "or ''" guard). */
    public boolean hasVideoId() {
        return videoId != null && !videoId.isBlank();
    }
}