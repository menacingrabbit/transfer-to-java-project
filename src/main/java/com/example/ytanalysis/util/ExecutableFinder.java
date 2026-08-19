package com.example.ytanalysis.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Finds an executable on the system {@code PATH} — the Java port of Python's
 * {@code shutil.which()}.
 *
 * <p>{@code shutil.which("ffmpeg")} returns the absolute path of the command or {@code None}.
 * We need the same behaviour to decide "did the user install ffmpeg?" and, crucially, to
 * run it with an absolute path (a plain {@code ffmpeg} argument hand to {@code ProcessBuilder}
 * would rely on PATH lookup and give a worse error on Windows).
 *
 * <p>Windows quirk handled here: executables carry a suffix ({@code .exe}, {@code .cmd},
 * {@code .bat}). We try the exact name and, on Windows, the common suffixes in turn.
 */
@Component
public class ExecutableFinder {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    /** The suffixes Windows appends when resolving an unadorned command name. */
    private static final String[] WINDOWS_SUFFIXES = {"", ".exe", ".cmd", ".bat"};

    /**
     * @return the absolute path of {@code name} if it exists on PATH, else empty.
     */
    public Optional<String> find(String name) {
        String pathVar = System.getenv("PATH");
        if (pathVar == null) {
            return Optional.empty();
        }
        String separator = System.getProperty("path.separator");
        for (String dirEntry : pathVar.split(java.util.regex.Pattern.quote(separator))) {
            if (dirEntry == null || dirEntry.isBlank()) {
                continue;
            }
            Path dir = Path.of(dirEntry);
            String[] suffixes = WINDOWS ? WINDOWS_SUFFIXES : new String[]{""};
            for (String suffix : suffixes) {
                Path candidate = dir.resolve(name + suffix);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toString());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve {@code name} to an absolute path, or fall back to the bare name. Callers use
     * this before starting a process so they pass an explicit path.
     */
    public String resolveOrBare(String name) {
        return find(name).orElse(name);
    }
}