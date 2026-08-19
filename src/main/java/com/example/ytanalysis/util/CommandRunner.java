package com.example.ytanalysis.util;

import com.example.ytanalysis.model.ProcessResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only place in the app that launches external processes.
 *
 * <p>The Python original called {@code subprocess.run(cmd, check=True)} from both the
 * downloader and the splitter. We funnel every external call here for two reasons:
 *
 * <ul>
 *   <li><b>Testability.</b> {@code java.lang.ProcessBuilder} is a {@code final} class, so
 *       Mockito cannot mock it directly. By hiding it behind our own bean, every service
 *       can instead mock <em>this</em> bean in unit tests — no real subprocess is ever
 *       spawned during tests.</li>
 *   <li><b>One capture point.</b> stdout and stderr are gathered here, where we can also
 *       apply a sensible timeout and a clear error type.</li>
 * </ul>
 */
@Component
public class CommandRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);

    /** How long a subprocess may run before we consider it hung (20 minutes). */
    private static final long TIMEOUT_MINUTES = 20;

    /**
     * Run a command, wait for it to finish, and return its captured output.
     *
     * <p>This mirrors {@code subprocess.run(..., check=False)} — we do <em>not</em> throw
     * on a non-zero exit; callers decide. (Python only checked in the ffmpeg helpers.)
     *
     * @param commandAndArgs the executable followed by its arguments, e.g. {@code [ffmpeg, -y, -i, in, out]}
     * @return a resolved {@link ProcessResult} (never {@code null})
     * @throws ProcessException if the process could not be started or timed out
     */
    public ProcessResult run(List<String> commandAndArgs) {
        ProcessBuilder pb = new ProcessBuilder(commandAndArgs);
        pb.redirectErrorStream(true);   // merge stderr into stdout so ordering is preserved
        log.trace("Running: {}", String.join(" ", commandAndArgs));
        try {
            Process process = pb.start();
            // read the combined output fully BEFORE waiting, otherwise a very chatty child
            // could fill the OS pipe buffer and write-block forever.
            byte[] outBytes = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ProcessException("Command timed out after " + TIMEOUT_MINUTES
                        + " min: " + String.join(" ", commandAndArgs));
            }
            String output = new String(outBytes, StandardCharsets.UTF_8);
            return new ProcessResult(process.exitValue(), output, "");
        } catch (IOException e) {
            // e.g. the executable is not on PATH.
            throw new ProcessException("Could not start command: "
                    + String.join(" ", commandAndArgs) + " — " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessException("Interrupted while waiting for command", e);
        }
    }

    /** Thrown when an external command fails to start or never finishes. */
    public static class ProcessException extends RuntimeException {
        public ProcessException(String message) {
            super(message);
        }

        public ProcessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}