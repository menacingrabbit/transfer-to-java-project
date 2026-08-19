package com.example.ytanalysis.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.example.ytanalysis.cli.CliArgumentParser.CliParseException;
import com.example.ytanalysis.cli.CliArgumentParser.HelpRequested;
import com.example.ytanalysis.model.AnalysisResult;
import com.example.ytanalysis.model.PipelineOptions;
import com.example.ytanalysis.service.BatchFileService;
import com.example.ytanalysis.service.PipelineOrchestrator;
import com.example.ytanalysis.service.ProgressReporter;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Turns the parsed CLI arguments into pipeline calls and sets the process exit code — the
 * Java port of {@code main()} in {@code src/cli.py}.
 *
 * <p>Two Spring interfaces make this class the natural CLI entry point:
 *
 * <ul>
 *   <li>{@link CommandLineRunner} — after Spring has created every bean, it invokes the
 *       {@code run(...)} method of any bean implementing it. Our {@code main()} already ran
 *       the container; this is where the app "starts doing work".</li>
 *   <li>{@link ExitCodeGenerator} — lets us feed the JVM exit code (0 = success, 1 = failure)
 *       back through {@code SpringApplication.exit(context)} in {@code main()}. The Python
 *       CLI promised "exit 0 on success, 1 on failure" as a scripting contract; we keep it.</li>
 * </ul>
 */
@Component
public class CliRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(CliRunner.class);

    private final CliArgumentParser parser;
    private final PipelineOrchestrator orchestrator;
    private final BatchFileService batchFileService;
    private final ProgressReporter progress;

    private int exitCode = 0;

    public CliRunner(CliArgumentParser parser,
                     PipelineOrchestrator orchestrator,
                     BatchFileService batchFileService,
                     ProgressReporter progress) {
        this.parser = parser;
        this.orchestrator = orchestrator;
        this.batchFileService = batchFileService;
        this.progress = progress;
    }

    /**
     * Executed by Spring after startup. In web (no-argument) mode we do nothing — the REST
     * controller owns the app then. With any CLI argument we parse and run.
     */
    @Override
    public void run(String... args) {
        if (args.length == 0) {
            return; // REST server mode — nothing to do at startup
        }

        CliArguments cli;
        try {
            cli = parser.parse(args);
        } catch (HelpRequested h) {
            System.out.print(parser.usage());
            exitCode = 0;
            return;
        } catch (CliParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(parser.usage());
            exitCode = 2;   // argparse convention: 2 = usage error
            return;
        }

        applyLogLevel(cli);

        if (cli.batchMode()) {
            runBatch(cli);
        } else {
            runSingle(cli);
        }
    }

    /** Single-URL mode: one pipeline run, success = exit 0, any failure = exit 1. */
    private void runSingle(CliArguments cli) {
        try {
            AnalysisResult result = orchestrator.analyse(
                    cli.url(), cli.outDir().toAbsolutePath(),
                    PipelineOptions.of(cli.noSummary(), cli.force(), cli.split()));
            progress.done("audio " + result.audioPath()
                    + " | transcript " + result.transcriptPath()
                    + (result.summaryPath() != null ? " | summary " + result.summaryPath() : ""));
            exitCode = 0;
        } catch (Exception e) {
            progress.error(cli.url(), e.getMessage());
            log.debug("Stack trace for debugging:", e);
            exitCode = 1;
        }
    }

    /** Batch mode: process every URL, counting successes; exit 0 only if all succeeded. */
    private void runBatch(CliArguments cli) {
        List<String> urls;
        try {
            urls = batchFileService.readUrls(cli.batchFile());
        } catch (Exception e) {
            progress.error(cli.batchFile().toString(), e.getMessage());
            exitCode = 1;
            return;
        }

        Path outDir = cli.outDir().toAbsolutePath();
        int succeeded = 0;
        for (String url : urls) {
            try {
                orchestrator.analyse(url, outDir, PipelineOptions.of(cli.noSummary(), cli.force(), cli.split()));
                succeeded++;
            } catch (Exception e) {
                progress.error(url, e.getMessage());
            }
        }
        progress.batchSummary(succeeded, urls.size());
        exitCode = succeeded == urls.size() ? 0 : 1; // "0 only if all succeeded"
    }

    /**
     * Map {@code --verbose}/{@code --quiet} to the root log level, like the Python
     * {@code configure(level)} call. Uses the logback {@link LoggerContext} directly.
     */
    private void applyLogLevel(CliArguments cli) {
        Level level = cli.quiet() ? Level.WARN : (cli.verbose() ? Level.DEBUG : Level.INFO);
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).setLevel(level);
    }

    /** Read by {@code SpringApplication.exit(...)} in main(). */
    @Override
    public int getExitCode() {
        return exitCode;
    }
}