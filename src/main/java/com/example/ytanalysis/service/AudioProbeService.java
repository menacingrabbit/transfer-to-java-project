package com.example.ytanalysis.service;

import com.example.ytanalysis.model.ProcessResult;
import com.example.ytanalysis.util.CommandRunner;
import com.example.ytanalysis.util.CommandRunner.ProcessException;
import com.example.ytanalysis.util.ExecutableFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wraps the external {@code ffprobe} command to learn the duration of an audio file — the
 * Java port of {@code _get_audio_duration()} in {@code src/audio/splitter.py}.
 *
 * <p>Python:
 * <pre>{@code
 * ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 <path>
 * }</pre>
 * prints a bare number like {@code 42.5}. We run the same command through
 * {@link CommandRunner} and parse the number. Any failure (missing ffprobe, bad file,
 * non-numeric output) returns {@link Optional#empty()} rather than throwing — the caller
 * (the splitter) treats "duration unknown" as "don't split", exactly like the Python
 * {@code except (SubprocessError, ValueError): return None}.
 */
@Service
public class AudioProbeService {

    private static final Logger log = LoggerFactory.getLogger(AudioProbeService.class);

    private final CommandRunner commandRunner;
    private final ExecutableFinder executables;

    public AudioProbeService(CommandRunner commandRunner, ExecutableFinder executables) {
        this.commandRunner = commandRunner;
        this.executables = executables;
    }

    /**
     * @param audioPath the audio file to probe
     * @return the duration in seconds, or empty if it cannot be determined
     */
    public Optional<Double> durationSeconds(Path audioPath) {
        Optional<String> ffprobe = executables.find("ffprobe");
        if (ffprobe.isEmpty()) {
            log.warn("ffprobe not found on PATH — cannot check duration");
            return Optional.empty();
        }
        List<String> cmd = List.of(
                ffprobe.get(), "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioPath.toString());
        try {
            ProcessResult result = commandRunner.run(cmd);
            if (!result.successful()) {
                return Optional.empty();
            }
            String trimmed = result.stdoutTrimmed();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Double.parseDouble(trimmed));
        } catch (ProcessException | NumberFormatException e) {
            log.warn("Could not read duration of {}: {}", audioPath, e.getMessage());
            return Optional.empty();
        }
    }
}