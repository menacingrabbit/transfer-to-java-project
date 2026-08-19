package com.example.ytanalysis.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ytanalysis.client.exception.TranscriptionException;
import com.example.ytanalysis.config.OpenRouterProperties;
import com.example.ytanalysis.model.AnalysisResult;
import com.example.ytanalysis.model.VideoInfo;
import com.example.ytanalysis.service.PipelineOrchestrator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A sliced web test: {@code @WebMvcTest} boots only the web layer (controller + advice +
 * validation) without the whole app, and {@code @MockBean} stubs the pipeline so no real
 * downloading/transcribing happens.
 *
 * <p>This is the Spring-native way to verify HTTP behaviour cheaply:
 * <ul>
 *   <li>the {@code /api/analyse} happy-path JSON shape,</li>
 *   <li>the {@code @Valid} → 400 path for a blank {@code url},</li>
 *   <li>the {@link GlobalExceptionMapper} mapping an upstream failure to 502.</li>
 * </ul>
 */
@WebMvcTest(AnalysisController.class)
class WebLayerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PipelineOrchestrator pipeline;

    // OpenRouterProperties is a @ConfigurationProperties bean normally enabled by AppConfig,
    // which the @WebMvcTest slice does not load — so we stub it like any other collaborator.
    @MockBean
    private OpenRouterProperties openRouterProperties;

    @Test
    void healthReportsUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void analyseReturnsThePipelineResultJson() throws Exception {
        AnalysisResult result = new AnalysisResult(
                new VideoInfo("Some Video", "dQw4w9WgXcQ"),
                java.nio.file.Path.of("output/some-video.mp3"),
                "full transcript text...",
                "bullet-point summary",
                java.nio.file.Path.of("output/some-video_transcript.txt"),
                java.nio.file.Path.of("output/some-video_summary.txt"));
        when(pipeline.analyse(any(), any(), any())).thenReturn(result);

        mvc.perform(post("/api/analyse")
                        .contentType("application/json")
                        .content("""
                                {"url":"https://youtu.be/dQw4w9WgXcQ","split":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videoId").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.title").value("Some Video"))
                .andExpect(jsonPath("$.transcript").value("full transcript text..."))
                .andExpect(jsonPath("$.summary").value("bullet-point summary"))
                // Path separator differs per OS (backslash on Windows, slash on *nix), so
                // derive the expected strings from Path rather than hardcoding slashes.
                .andExpect(jsonPath("$.audioPath").value(Path.of("output", "some-video.mp3").toString()))
                .andExpect(jsonPath("$.transcriptPath").value(Path.of("output", "some-video_transcript.txt").toString()))
                .andExpect(jsonPath("$.summaryPath").value(Path.of("output", "some-video_summary.txt").toString()));
    }

    @Test
    void blankUrlIsAValidation400() throws Exception {
        mvc.perform(post("/api/analyse")
                        .contentType("application/json")
                        .content("""
                                {"url":" "}
                                """))
                .andExpect(status().isBadRequest());   // @NotBlank on url
    }

    @Test
    void upstreamFailureIsMappedTo502() throws Exception {
        when(pipeline.analyse(any(), any(), any()))
                .thenThrow(new TranscriptionException("invalid request from OpenRouter"));

        mvc.perform(post("/api/analyse")
                        .contentType("application/json")
                        .content("""
                                {"url":"https://youtu.be/dQw4w9WgXcQ"}
                                """))
                .andExpect(status().isBadGateway())     // OpenAiApiException → 502
                .andExpect(jsonPath("$.status").value(502));
    }
}