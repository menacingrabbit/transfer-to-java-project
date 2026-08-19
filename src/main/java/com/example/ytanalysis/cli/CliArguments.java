package com.example.ytanalysis.cli;

import java.nio.file.Path;

/**
 * The parsed result of the command line — the Java mirror of Python's
 * {@code argparse.Namespace} + {@code Options} dataclass rolled into one.
 *
 * <p>Records are ideal here because a parsed CLI is immutable by nature: it is computed once
 * at startup and read everywhere afterwards.
 *
 * @param url       the {@code --url} value, or {@code null} in batch mode
 * @param batchFile the {@code --batch-file} value, or {@code null} in single mode
 * @param outDir    output directory ({@code --out-dir}, default {@code output})
 * @param noSummary {@code --no-summary}
 * @param force     {@code --force}
 * @param split     {@code --split}
 * @param quiet     {@code --quiet}
 * @param verbose   {@code --verbose}
 */
public record CliArguments(
        String url,
        Path batchFile,
        Path outDir,
        boolean noSummary,
        boolean force,
        boolean split,
        boolean quiet,
        boolean verbose) {

    /** True when a batch file was given instead of a single URL. */
    public boolean batchMode() {
        return batchFile != null;
    }
}