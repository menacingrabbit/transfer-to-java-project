package com.example.ytanalysis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link YoutubeUrlValidator}, the port of {@code validate_youtube_url()} +
 * the {@code _YOUTUBE_URL_PATTERN} in {@code src/cli.py}.
 *
 * <p>Mockito could mock the component, but since it is stateless a real instance is simpler
 * and just as isolated — the class talks to nothing else.
 */
class YoutubeUrlValidatorTest {

    private final YoutubeUrlValidator validator = new YoutubeUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=AbC-9_xYQ1a", // 11 chars: A b C - 9 _ x Y Q 1 a
    })
    void acceptsValidYouTubeUrls(String url) {
        assertThat(validator.isValid(url)).isTrue();
        assertThat(validator.extractVideoId(url)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                       // empty
            "not a url",              // garbage
            "https://youtube.com/",   // no id
            "https://youtu.be/",      // no id
            "https://example.com/watch?v=dQw4w9WgXcQ", // wrong host
            "ftp://youtube.com/watch?v=dQw4w9WgXcQ",   // unsupported scheme
            "https://youtube.com/watch?v=shortId",     // id not 11 chars
    })
    void rejectsInvalidUrls(String url) {
        assertThat(validator.isValid(url)).isFalse();
        assertThat(validator.extractVideoId(url)).isEmpty();
    }

    @Test
    void nullUrlIsInvalid() {
        assertThat(validator.isValid(null)).isFalse();
    }

    @Test
    void extractsTheElevenCharacterId() {
        Optional<String> id = validator.extractVideoId("https://youtu.be/dQw4w9WgXcQ");
        assertThat(id).contains("dQw4w9WgXcQ");
    }

    @Test
    void validateThrowsIllegalArgumentOnBadUrl() {
        assertThatThrownBy(() -> validator.validate("https://example.com/nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid YouTube URL");
    }
}