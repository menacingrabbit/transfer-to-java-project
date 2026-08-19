package com.example.ytanalysis.cli;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Hand-rolled command-line parser reproducing the behaviour of Python's {@code argparse}
 * in {@code src/cli.py}.
 *
 * <p>It is a {@code @Component} (stateless singleton) so that {@link CliRunner} can have it
 * constructor-injected rather than instantiate it with {@code new} — consistency with the
 * rest of the app, and trivially mockable in tests.
 *
 * <p>Why not a library like <em>picocli</em>? Because the goal of this project is to teach
 * Spring, and an explicit 60-line parser shows every rule (mutual exclusion, defaults,
 * {@code store_true}) plainly instead of hiding it behind annotations. If you later want to
 * grow the CLI, picocli's {@code @Command} is the natural upgrade — the switch only touches
 * this class and {@link CliRunner}.
 *
 * <p>Supported flags (identical semantics to the Python original):
 * <pre>
 *   --url <url>        single video to process
 *   --batch-file <f>   text file with one URL per line (mutually exclusive with --url)
 *   --out-dir <dir>    output directory            (default: output)
 *   --no-summary       skip summarisation
 *   --force            re-download even if the audio exists
 *   --split            split long audio before transcribing
 *   --verbose | --quiet
 *   --help
 * </pre>
 */
@Component
public class CliArgumentParser {

    /** Thrown for any invalid command line. */
    public static class CliParseException extends RuntimeException {
        public CliParseException(String message) {
            super(message);
        }
    }

    /** Thrown when {@code --help} was requested; the runner prints usage and exits 0. */
    public static class HelpRequested extends RuntimeException {
        public HelpRequested() {
            super("help requested");
        }
    }

    /**
     * Parse raw JVM arguments into a {@link CliArguments}.
     *
     * @throws CliParseException on unknown flags, missing values, or both modes together
     * @throws HelpRequested     when {@code --help} is present
     */
    public CliArguments parse(String[] args) {
        String url = null;
        Path batchFile = null;
        Path outDir = Path.of("output");        // argparse default
        boolean noSummary = false;
        boolean force = false;
        boolean split = false;
        boolean quiet = false;
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> url = value(args, ++i, "--url");
                case "--batch-file" -> batchFile = Path.of(value(args, ++i, "--batch-file"));
                case "--out-dir" -> outDir = Path.of(value(args, ++i, "--out-dir"));
                case "--no-summary" -> noSummary = true;
                case "--force" -> force = true;
                case "--split" -> split = true;
                case "--verbose" -> verbose = true;
                case "--quiet" -> quiet = true;
                case "--help", "-h" -> throw new HelpRequested();
                default -> throw new CliParseException("Unknown argument: " + args[i]);
            }
        }

        // argparse's mutually-exclusive group: exactly one of --url / --batch-file.
        if (url != null && batchFile != null) {
            throw new CliParseException("--url and --batch-file are mutually exclusive");
        }
        if (url == null && batchFile == null) {
            throw new CliParseException("One of --url or --batch-file is required");
        }
        if (verbose && quiet) {
            throw new CliParseException("--verbose and --quiet are mutually exclusive");
        }

        return new CliArguments(url, batchFile, outDir, noSummary, force, split, quiet, verbose);
    }

    private String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new CliParseException("Missing value for " + flag);
        }
        return args[index];
    }

    /** A short usage text printed by the runner for {@code --help}. */
    public String usage() {
        return """
                yt-analysis — download, transcribe and summarise a YouTube video

                Usage:
                  java -jar yt-analysis.jar --url <youtube-url> [options]
                  java -jar yt-analysis.jar --batch-file <file> [options]

                Options:
                  --url <url>        single YouTube URL
                  --batch-file <f>   text file with one URL per line
                  --out-dir <dir>    output directory (default: output)
                  --no-summary       skip summarisation
                  --force            re-download even if audio exists
                  --split            split long audio into <10min chunks
                  --verbose          debug logging
                  --quiet            warnings/errors only
                  --help             show this help
                """;
    }
}