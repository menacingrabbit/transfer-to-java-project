package com.example.ytanalysis.client.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The response shape of {@code POST /api/v1/audio/transcriptions} — OpenAI's Whisper-style
 * schema:
 *
 * <pre>{@code { "text": "the transcribed text" }}</pre>
 *
 * <p>Jackson maps JSON straight into records: a field named {@code text} is filled from the
 * {@code text} key. {@code @JsonIgnoreProperties(ignoreUnknown = true)} makes us tolerant of
 * extra keys OpenRouter may add later — the Python code simply did {@code data.get("text")}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscribeResponse(String text) {
}