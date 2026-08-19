package com.example.ytanalysis.service;

import com.example.ytanalysis.model.ProcessResult;
import com.example.ytanalysis.util.CommandRunner;
import com.example.ytanalysis.util.CommandRunner.ProcessException;
import com.example.ytanalysis.util.ExecutableFinder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Splits long audio files into &lt;10-minute chunks using ffmpeg's segment muxer — a port of
 * {@code split_audio()} + {@code cleanup_chunks()} in {@code src/audio/splitter.py}.
 *
 * <p>The whole point is to stay under the OpenRouter transcription input limit (~10 minutes).
 * Design decisions inherited from the Python original, deliberately:
 *
 * <ul>
 *   <li><b>Graceful fallback.</b> If ffprobe is missing, ffmpeg is missing, the split fails,
 *       or the file is already short enough — we return the original, <em>unsplit</em> file.
 *       A missing optional tool should degrade, not crash.</li>
 *   <li><b>Safety-first cleanup.</b> {@link #cleanupChunks(List)} only ever deletes files
 *       whose parent directory starts with our {@code yt-split-} prefix. Your original audio
 *       (or any user file elsewhere) is never touched.</li>
 *   <li><b>Stream copy, no re-encode</b> ({@code -c copy}): the chunks are cut from the same
 *       bitstream — fast and lossless.</li>
 * </ul>
 *
 * <p>Testability note: this bean depends on {@link CommandRunner} (mocked in tests) and never
 * spawns a real subprocess when unit-tested.
 */
@Service
public class AudioSplitter {

    private static final Logger log = LoggerFactory.getLogger(AudioSplitter.class);

    /** Seconds per chunk (just under 10 minutes). Mirrors the Python constant. */
    public static final int DEFAULT_CHUNK_DURATION = 590;

    /** Temp-directory prefix: we only clean up dirs whose name starts with this. */
    public static final String TEMP_DIR_PREFIX = "yt-split-";

    private final CommandRunner commandRunner;
    private final ExecutableFinder executables;
    private final AudioProbeService probe;

    public AudioSplitter(CommandRunner commandRunner, ExecutableFinder executables, AudioProbeService probe) {
        this.commandRunner = commandRunner;
        this.executables = executables;
        this.probe = probe;
    }

    /**
     * Split {@code audioPath} into ordered chunks of {@code chunkDuration} seconds each, or
     * return the original file unsplit if splitting is not needed/possible.
     *
     * @return list with one entry (the unsplit file) or several chunk paths in order
     */
    public List<Path> splitIfNeeded(Path audioPath, int chunkDuration) {
        // 1) If we can measure the duration and it already fits, do nothing.
        Optional<Double> duration = probe.durationSeconds(audioPath);
        if (duration.isPresent() && duration.get() <= chunkDuration) {
            return List.of(audioPath);
        }

        // 2) No ffmpeg → fall back to unsplit (Python logged a warning and returned [audio]).
        Optional<String> ffmpeg = executables.find("ffmpeg");
        if (ffmpeg.isEmpty()) {
            log.warn("ffmpeg not found on PATH — cannot split; using unsplit audio");
            return List.of(audioPath);
        }

        // 3) Create a temp dir and cut the file into part_000.mp3, part_001.mp3, ...
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
        } catch (IOException e) {
            log.warn("Cannot create temp dir for splitting: {}", e.getMessage());
            return List.of(audioPath);
        }

        String pattern = tempDir.resolve("part_%03d.mp3").toString();
        List<String> cmd = List.of(
                ffmpeg.get(), "-y", "-i", audioPath.toString(),
                "-f", "segment",
                "-segment_time", String.valueOf(chunkDuration),
                "-c", "copy",
                "-reset_timestamps", "1",
                pattern);

        try {
            ProcessResult result = commandRunner.run(cmd);
            if (!result.successful()) {
                log.warn("ffmpeg segmentation failed (exit {}): {}", result.exitCode(), result.stderr());
                cleanupDir(tempDir);
                return List.of(audioPath);
            }
        } catch (ProcessException e) {
            log.warn("ffmpeg segmentation failed: {}", e.getMessage());
            cleanupDir(tempDir);
            return List.of(audioPath);
        }

        // 4) Collect the produced chunks in filename order (part_000 < part_001 < ...).
        List<Path> chunks;
        try (var stream = Files.list(tempDir)) {
            chunks = stream.filter(p -> p.getFileName().toString().startsWith("part_"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Cannot list chunk files: {}", e.getMessage());
            cleanupDir(tempDir);
            return List.of(audioPath);
        }

        if (chunks.isEmpty()) {
            log.warn("ffmpeg produced no chunks — using unsplit audio");
            cleanupDir(tempDir);
            return List.of(audioPath);
        }
        return chunks;
    }

    /**
     * Delete chunk files and their temp directories — but ONLY those in a
     * {@code yt-split-*} directory. The original audio file is never a target.
     */
    public void cleanupChunks(List<Path> chunks) {
        if (chunks == null) {
            return;
        }
        java.util.Set<Path> cleanedDirs = new java.util.HashSet<>();
        for (Path chunk : chunks) {
            Path parent = chunk.toAbsolutePath().getParent();
            if (parent != null && parent.getFileName().toString().startsWith(TEMP_DIR_PREFIX)) {
                try {
                    Files.deleteIfExists(chunk);
                } catch (IOException ignored) {
                    // best-effort, mirroring Python's `except OSError: pass`
                }
                cleanedDirs.add(parent);
            }
        }
        for (Path dir : cleanedDirs) {
            cleanupDir(dir);
        }
    }

    /** Delete a directory tree, ignoring errors (Python {@code shutil.rmtree(ignore_errors=True)}). */
    private void cleanupDir(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}