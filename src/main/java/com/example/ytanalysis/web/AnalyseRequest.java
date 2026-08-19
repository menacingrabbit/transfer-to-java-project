package com.example.ytanalysis.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * The HTTP request body for {@code POST /api/analyse}.
 *
 * <p>Validation: the {@code @NotBlank} from Jakarta Bean Validation runs automatically when
 * the controller parameter is annotated {@code @Valid}; a blank {@code url} becomes a 400
 * without any manual check. This is a pure record — Spring parses the JSON body straight into
 * it and validates it before our controller method is even called.
 *
 * <p>The copy-constructor (= the compact constructor) maps optional fields to booleans so
 * {@code null} in JSON means "use the default": {@code split}/{@code noSummary}/{@code force}
 * default to {@code false} and {@code save} to {@code true}.
 *
 * @param url       the YouTube URL (required, not blank)
 * @param split     split long audio into &lt;10min chunks
 * @param noSummary skip summarisation
 * @param force     re-download even if audio exists
 * @param save      write transcript/summary files (default true)
 * @param outDir    output directory override (default {@code output})
 */
public record AnalyseRequest(
        @NotBlank(message = "url is required") String url,
        @JsonProperty("split") Boolean split,
        @JsonProperty("noSummary") Boolean noSummary,
        @JsonProperty("force") Boolean force,
        @JsonProperty("save") Boolean save,
        @JsonProperty("outDir") String outDir) {

    public AnalyseRequest {
        split = (split == null) ? false : split;
        noSummary = (noSummary == null) ? false : noSummary;
        force = (force == null) ? false : force;
        save = (save == null) ? true : save;
        outDir = (outDir == null || outDir.isBlank()) ? "output" : outDir;
    }
}