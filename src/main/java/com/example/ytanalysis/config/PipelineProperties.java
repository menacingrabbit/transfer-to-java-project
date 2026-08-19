package com.example.ytanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App-level defaults for our own pipeline — bound under the {@code yt} prefix.
 *
 * <p>This has no direct Python counterpart: in Python the output directory defaulted to a
 * literal {@code "output"} in the argparse definition. Modelling it as configuration is a
 * (small) teaching upgrade — instead of hard-coding {@code "output"} in the CLI parser, the
 * default can now be changed in {@code application.yml} or by {@code YT_DEFAULT_OUT_DIR}.
 */
@ConfigurationProperties(prefix = "yt")
public record PipelineProperties(String defaultOutDir) {

    public PipelineProperties {
        if (defaultOutDir == null || defaultOutDir.isBlank()) {
            defaultOutDir = "output";
        }
    }
}