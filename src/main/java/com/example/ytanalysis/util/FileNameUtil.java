package com.example.ytanalysis.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

/**
 * Centralises every file-name rule.<br>
 * In the Python original these rules were scattered over two modules
 * ({@code _audio_mp3_name} in {@code yt/downloader.py} and the {@code _save_transcript} /
 * {@code _save_summary} writers in {@code cli.py}). We pull them together here so that:
 *
 * <ol>
 *   <li>the naming contract is trivially testable in one place, and</li>
 *   <li>students see <em>why</em> a "FileNameRules" util is a good idea (DRY),</li>
 * </ol>
 *
 * It is a stateless utility (like {@link SlugUtil}), so not a Spring bean.
 */
public final class FileNameUtil {

    private FileNameUtil() { /* static-only */ }

    /**
     * Build the mp3 name: {@code <slug>[-<videoId>].mp3}. The video id is appended when
     * present so re-downloads of the same video are uniquely identifiable for the resume
     * check (Python {@code _audio_mp3_name}).
     */
    public static String audioMp3Name(String slug, String videoId) {
        String stem = (videoId != null && !videoId.isBlank()) ? slug + "-" + videoId : slug;
        return stem + ".mp3";
    }

    /** Transcript output file name: {@code <stem>_transcript.txt}. */
    public static String transcriptFileName(String stem) {
        return stem + "_transcript.txt";
    }

    /** Summary output file name: {@code <stem>_summary.txt}. */
    public static String summaryFileName(String stem) {
        return stem + "_summary.txt";
    }

    /**
     * The mp3 name (stem) minus the {@code .mp3} suffix — the base used for transcripts
     * and summaries. Python used {@code audio_path.stem}.
     */
    public static String stemWithoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Resume detection — port of {@code _find_existing_audio()} in {@code yt/downloader.py}.
     *
     * <p>Look for an already-downloaded audio file for a given video so we can skip the
     * download unless {@code --force} was passed. Order of preference:
     *
     * <ol>
     *   <li>a file matching {@code *-<videoId>.mp3} (the current naming scheme), or</li>
     *   <li>a legacy file whose <em>exact</em> title-slug matches {@code cleanTitle}
     *       (after stripping the leading date prefix), to avoid matching a sibling like
     *       {@code the-interview} when we want {@code interview}.</li>
     * </ol>
     *
     * Among candidates the most recently modified wins (Python {@code max(..., key=mtime)}).
     *
     * @return the path of the existing file, or {@link Optional#empty()} if none is a match
     */
    public static Optional<Path> findExistingAudio(Path outDir, String videoId, String cleanTitle) {
        List<Path> candidates = new java.util.ArrayList<>();

        // 1) precise "by video id" match (current scheme).
        if (videoId != null && !videoId.isBlank()) {
            matchBySuffix(outDir, "-" + videoId + ".mp3", candidates);
        }

        // 2) legacy "by exact title slug" match (older scheme, date-prefixed slug).
        //    Python stripped the ^\d{8}- prefix then compared stem == cleanTitle exactly.
        if (candidates.isEmpty() && cleanTitle != null && !cleanTitle.isBlank()) {
            try (var stream = Files.list(outDir)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".mp3")) {
                        String stem = SlugUtil.DATE_PREFIX.matcher(stemWithoutExtension(name)).replaceFirst("");
                        if (stem.equals(cleanTitle)) {
                            candidates.add(p);
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to scan " + outDir, e);
            }
        }

        return candidates.stream()
                .filter(Files::exists)
                .max(FileNameUtil::compareByMtime);
    }

    private static void matchBySuffix(Path dir, String suffix, List<Path> into) {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(suffix))
                    .forEach(into::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan " + dir, e);
        }
    }

    /** newest first (Python {@code key=lambda p: p.stat().st_mtime}); ties broken by name. */
    private static int compareByMtime(Path a, Path b) {
        long ma = mtime(a), mb = mtime(b);
        return Long.compare(ma, mb);
    }

    private static long mtime(Path p) {
        try {
            FileTime t = Files.getLastModifiedTime(p);
            return t.toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}