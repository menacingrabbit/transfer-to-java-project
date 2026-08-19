package com.example.ytanalysis.service;

import com.example.ytanalysis.model.AnalysisResult;
import com.example.ytanalysis.model.DownloadResult;
import com.example.ytanalysis.model.PipelineOptions;
import com.example.ytanalysis.util.FileNameUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The pipeline itself — a port of {@code process_single_url()} in {@code src/cli.py}.
 *
 * <p>This is the class that ties the whole story together and is the best place to read the
 * pipeline end to end:
 *
 * <pre>
 *  download → (optional split) transcribe → save transcript → summarise → save summary
 * </pre>
 *
 * <p>Both entry points (the CLI runner and the REST controller) call exactly this one method,
 * so the pipeline behaves identically no matter how it is triggered — that is the practical
 * benefit of carving the orchestration out as its own service.
 */
@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final AudioDownloadService audioDownloadService;
    private final TranscriptionService transcriptionService;
    private final SummarisationService summarisationService;
    private final ProgressReporter progress;

    public PipelineOrchestrator(AudioDownloadService audioDownloadService,
                                TranscriptionService transcriptionService,
                                SummarisationService summarisationService,
                                ProgressReporter progress) {
        this.audioDownloadService = audioDownloadService;
        this.transcriptionService = transcriptionService;
        this.summarisationService = summarisationService;
        this.progress = progress;
    }

    /**
     * Run the full pipeline for one URL.
     *
     * @param url      a validated YouTube URL
     * @param outDir   directory for artifacts (created if missing)
     * @param options  the run switches (noSummary / force / split / save)
     * @return the produced audio path, transcript text and (if saved) file paths
     * @throws RuntimeException on any stage failure — callers map this to exit codes / HTTP status
     */
    public AnalysisResult analyse(String url, Path outDir, PipelineOptions options) {
        progress.processingUrl(url);
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create output directory " + outDir, e);
        }

        // 1. download (or resume) the MP3; the DownloadResult keeps the video metadata too
        DownloadResult download = audioDownloadService.downloadAudio(url, outDir, options.force());
        Path audioPath = download.audioPath();

        // 2. the base name of the mp3 is the stem used for every artifact name
        String stem = FileNameUtil.stemWithoutExtension(audioPath.getFileName().toString());

        // 3. transcribe — the split flag is handled inside TranscriptionService
        progress.transcribing();
        String transcript = transcriptionService.transcribeOrDefault(audioPath);

        // 4. save the transcript (unless the caller only wants text back)
        Path transcriptPath = null;
        if (options.save()) {
            transcriptPath = outDir.resolve(FileNameUtil.transcriptFileName(stem));
            writeUtf8(transcriptPath, transcript);
            log.info("Transcript written to {}", transcriptPath);
        }

        // 5. summarise (unless skipped) and save it — the service returns both the file path
        //    and the text, so we can populate both the summaryPath and summary fields
        String summary = null;
        Path summaryPath = null;
        if (!options.noSummary()) {
            progress.summarising();
            SummarisationService.Result result = summarisationService.summariseAndSave(transcript, outDir, stem);
            summaryPath = result.path();
            summary = result.text();
        }

        return new AnalysisResult(download.video(), audioPath, transcript, summary, transcriptPath, summaryPath);
    }

    private void writeUtf8(Path target, String content) {
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        }
    }
}