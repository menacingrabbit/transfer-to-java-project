package com.example.ytanalysis.model;

/**
 * The outcome of running one external command (yt-dlp, ffmpeg, ffprobe) via
 * {@link com.example.ytanalysis.util.CommandRunner}.
 *
 * <p>Python's {@code subprocess.run(cmd, check=True)} captures nothing and raises on a
 * non-zero exit code. We capture stdout/stderr so we can both inspect JSON output
 * (yt-dlp's {@code -J} mode) and show the user a helpful message on failure. That is
 * why this small record exists.
 *
 * @param exitCode the process's own exit code (0 == success)
 * @param stdout   everything the command wrote to standard out
 * @param stderr   everything it wrote to standard error
 */
public record ProcessResult(int exitCode, String stdout, String stderr) {

    /** Convenience mirror of Python's {@code check=True} semantic. */
    public boolean successful() {
        return exitCode == 0;
    }

    /** The stdout trimmed of a trailing newline, handy for ffprobe numbers. */
    public String stdoutTrimmed() {
        return stdout == null ? "" : stdout.strip();
    }
}