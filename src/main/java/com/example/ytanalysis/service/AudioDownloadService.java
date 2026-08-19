package com.example.ytanalysis.service;

import com.example.ytanalysis.model.DownloadResult;
import com.example.ytanalysis.model.ProcessResult;
import com.example.ytanalysis.model.VideoInfo;
import com.example.ytanalysis.util.CommandRunner;
import com.example.ytanalysis.util.CommandRunner.ProcessException;
import com.example.ytanalysis.util.ExecutableFinder;
import com.example.ytanalysis.util.FileNameUtil;
import com.example.ytanalysis.util.SlugUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Downloads the audio track of a YouTube video and converts it to MP3 — a port of
 * {@code src/yt/downloader.py}.
 *
 * <p>Python called the <em>yt-dlp library</em> in-process. A Java port has two options:
 * <ol>
 *   <li>call the {@code yt-dlp} <em>executable</em> via {@code ProcessBuilder} (what we do —
 *       the same "shell out to a tool" idea the Python code already used for ffmpeg), or</li>
 *   <li>add a third-party wrapper library for yt-dlp.</li>
 * </ol>
 * We chose (1): it needs no extra dependency, it is exactly how {@code ffmpeg} is invoked
 * elsewhere in this project, and the executed command is visible and debuggable. It does
 * require {@code yt-dlp} to be installed on PATH (documented in the README).
 *
 * <p>The flow mirrors the Python "two-pass" approach:
 * <pre>
 *  pass 1  yt-dlp -J            → metadata only (title, id) — no download
 *  pass 2  yt-dlp -f bestaudio  → download the audio file
 *  ffmpeg                       → convert to MP3 (or copy if ffmpeg unavailable)
 * </pre>
 *
 * <p><b>Resume support</b> is ported too: if a matching {@code *-<videoId>.mp3} (or legacy
 * slug) already exists in the output dir, we skip the download unless {@code --force} — see
 * {@link FileNameUtil#findExistingAudio}.
 */
@Service
public class AudioDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AudioDownloadService.class);

    private final CommandRunner commandRunner;
    private final ExecutableFinder executables;
    private final ObjectMapper objectMapper;

    public AudioDownloadService(CommandRunner commandRunner,
                                ExecutableFinder executables,
                                ObjectMapper objectMapper) {
        this.commandRunner = commandRunner;
        this.executables = executables;
        // ObjectMapper is auto-configured by Spring Boot — one shared instance, preloaded
        // with JavaTime/record support. We inject it rather than `new ObjectMapper()`.
        this.objectMapper = objectMapper;
    }

    /**
     * Ensure an MP3 of the video is present in {@code outDir} and return the download
     * result (video metadata + MP3 path).
     *
     * @throws IllegalStateException when yt-dlp cannot be found or the metadata fetch fails
     */
    public DownloadResult downloadAudio(String url, Path outDir, boolean force) {
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new ProcessException("Cannot create output dir " + outDir, e);
        }

        // ---- pass 1: metadata only (mirrors extract_info(download=False)) ----
        VideoInfo info = fetchMetadata(url);
        String slug = SlugUtil.slugify(info.title());
        String mp3Name = FileNameUtil.audioMp3Name(slug, info.videoId());
        Path mp3Path = outDir.resolve(mp3Name);

        // ---- resume: already downloaded? ----
        Optional<Path> existing = FileNameUtil.findExistingAudio(outDir, info.videoId(), SlugUtil.cleanTitle(info.title()));
        if (!force && existing.isPresent()) {
            log.info("Audio already exists at {} — skipping download (use --force to re-download)", existing.get());
            return new DownloadResult(info, existing.get());
        }

        // ---- pass 2: download the best audio track ----
        log.info("Downloading audio for '{}'...", info.title());
        Path original = downloadBestAudio(url, slug, outDir);

        // ---- convert to mp3 (or fall back to a copy when ffmpeg is unavailable) ----
        convertToMp3(original, mp3Path);

        // delete the intermediate file (Python did the same after conversion)
        try {
            if (!Files.isSameFile(original, mp3Path)) {
                Files.deleteIfExists(original);
            }
        } catch (IOException e) {
            log.warn("Could not delete intermediate file {}: {}", original, e.getMessage());
        }

        return new DownloadResult(info, mp3Path);
    }

    /**
     * Pass 1 — fetch just the metadata. Runs {@code yt-dlp -J <url>} which prints the full
     * info-JSON to stdout, then extracts {@code title} and {@code id}.
     */
    private VideoInfo fetchMetadata(String url) {
        String ytdlp = executable("yt-dlp");
        List<String> cmd = List.of(
                ytdlp, "-J",
                "--no-warnings",
                "--extractor-args", "youtube:player_client=ANDROID", // avoids YouTube HTTP 403
                url);
        ProcessResult result = commandRunner.run(cmd);
        if (!result.successful()) {
            throw new ProcessException("yt-dlp metadata failed (exit " + result.exitCode() + "): "
                    + result.stderr());
        }
        try {
            JsonNode root = objectMapper.readTree(result.stdout());
            String title = root.path("title").asText("");
            String id = root.path("id").asText("");
            if (title.isBlank()) {
                title = "audio";                     // Python: info.get("title") or "audio"
            }
            return new VideoInfo(title, id);
        } catch (IOException e) {
            throw new ProcessException("Could not parse yt-dlp metadata JSON", e);
        }
    }

    /**
     * Pass 2 — download the best audio. The {@code --print after_move:filepath} makes yt-dlp
     * echo the final written file path, so we don't have to guess the extension ourselves
     * (Python used {@code ydl.prepare_filename(info)} for the same job).
     */
    private Path downloadBestAudio(String url, String slug, Path outDir) {
        String ytdlp = executable("yt-dlp");
        List<String> cmd = List.of(
                ytdlp,
                "--no-warnings",
                "--quiet",
                "-f", "bestaudio/best",
                "--extractor-args", "youtube:player_client=ANDROID",
                "-o", outDir.resolve(slug + ".%(ext)s").toString(),
                "--print", "after_move:filepath",
                url);
        ProcessResult result = commandRunner.run(cmd);
        if (!result.successful()) {
            throw new ProcessException("yt-dlp download failed (exit " + result.exitCode() + "): "
                    + result.stderr());
        }
        String firstLine = result.stdout().lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElseThrow(() -> new ProcessException("yt-dlp printed no output file path"));
        return Path.of(firstLine);
    }

    /**
     * Convert to MP3 with {@code ffmpeg -y -i <in> -vn -acodec mp3 <out>}. If ffmpeg is not
     * on PATH or fails, copy the original file to the {@code .mp3} target instead — matching
     * Python's graceful degradation (the name still ends in {@code .mp3}).
     */
    private void convertToMp3(Path original, Path mp3Path) {
        Optional<String> ffmpeg = executables.find("ffmpeg");
        if (ffmpeg.isEmpty()) {
            log.warn("ffmpeg not found — copying audio file as MP3 instead of converting");
            copyFallback(original, mp3Path);
            return;
        }
        List<String> cmd = List.of(
                ffmpeg.get(), "-y", "-i", original.toString(),
                "-vn", "-acodec", "mp3",
                mp3Path.toString());
        try {
            ProcessResult result = commandRunner.run(cmd);
            if (result.successful()) {
                log.info("Converted to MP3 at {}", mp3Path);
                return;
            }
            log.warn("ffmpeg conversion failed (exit {}) — copying instead", result.exitCode());
        } catch (ProcessException e) {
            log.warn("ffmpeg conversion failed: {} — copying instead", e.getMessage());
        }
        copyFallback(original, mp3Path);
    }

    private void copyFallback(Path original, Path mp3Path) {
        try {
            Files.copy(original, mp3Path);
        } catch (IOException e) {
            throw new ProcessException("Failed to copy audio to " + mp3Path, e);
        }
    }

    /** Resolve an executable path once, failing with a clear message if it is missing. */
    private String executable(String name) {
        return executables.find(name)
                .orElseThrow(() -> new IllegalStateException(
                        name + " not found on PATH — install it or add it to your PATH"));
    }
}