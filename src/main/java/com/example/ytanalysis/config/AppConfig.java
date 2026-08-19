package com.example.ytanalysis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables our {@code @ConfigurationProperties} records.
 *
 * <p>{@code @ConfigurationProperties} alone does <em>not</em> register a bean; something must
 * tell Spring "bind and expose these objects". Two options:
 *
 * <ul>
 *   <li>{@code @ConfigurationPropertiesScan} (on {@code YtAnalysisApplication} or here) scans
 *       packages for any class annotated with {@code @ConfigurationProperties};</li>
 *   <li>{@code @EnableConfigurationProperties(...)} (used here) lists them explicitly.</li>
 * </ul>
 *
 * <p>We choose the explicit list so a reader can see at a glance exactly which property
 * holders exist — more instructive for a learning project than a package-wide scan.
 */
@Configuration
@EnableConfigurationProperties({
        OpenRouterProperties.class,
        PipelineProperties.class,
})
public class AppConfig {
    // Beans OpenRouterProperties and PipelineProperties now exist in the container and can
    // be injected anywhere by type.
}