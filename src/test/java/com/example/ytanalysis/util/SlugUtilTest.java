package com.example.ytanalysis.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SlugUtil}, the port of {@code src/yt/utils.py}.
 *
 * <p>These are pure function tests — no Spring context, no files, no network. They pin the
 * exact filename rules so a future change cannot silently break the resume/artifact naming
 * that everything downstream depends on.
 */
class SlugUtilTest {

    @Test
    void cleanTitleCollapsesWhitespaceAndTrims() {
        // "  Hello   World  " -> collapse runs -> "Hello   World" -> trim -> then the
        // whitespace-to-hyphen step (step 3) turns the inner double space into a single
        // hyphen. The same in Python:
        //   re.sub(r"\s+", " ", t).strip()   -> "Hello World"
        //   re.sub(r"[\s_]+", "-", t)        -> "Hello-World"
        //   t.lower()                        -> "hello-world"
        assertThat(SlugUtil.cleanTitle("  Hello   World  ")).isEqualTo("hello-world");
    }

    @Test
    void cleanTitleDropsUnusualCharacters() {
        // '&' and '?' are not [\w\- ] so they are removed; the surrounding spaces collapse
        // with the whitespace-to-hyphen step: "Cats & Dogs?" -> "Cats-Dogs".
        assertThat(SlugUtil.cleanTitle("Cats & Dogs?")).isEqualTo("cats-dogs");
    }

    @Test
    void cleanTitleTurnsWhitespaceAndUnderscoresIntoHyphens() {
        assertThat(SlugUtil.cleanTitle("hello_world title")).isEqualTo("hello-world-title");
    }

    @Test
    void cleanTitleLowercases() {
        assertThat(SlugUtil.cleanTitle("MiXeD CaSe")).isEqualTo("mixed-case");
    }

    @Test
    void slugifyPrefixesTodayDateAndKeepsUnder80() {
        // Never assert the date (it changes daily) — only the shape and length.
        String slug = SlugUtil.slugify("A fairly long title that keeps going and going");
        assertThat(slug).startsWith("2");
        assertThat(slug).contains("-");
        assertThat(slug).hasSizeLessThanOrEqualTo(SlugUtil.MAX_SLUG_LEN);
        // slug == "YYYYMMDD-" + cleaned title; after the 8-digit date and the dash,
        // the remainder must be the hyphenated title.
        assertThat(slug.substring(9)).isEqualTo(
                SlugUtil.cleanTitle("A fairly long title that keeps going and going"));
    }

    @Test
    void slugifyTruncatesOverlongTitles() {
        String longTitle = "word ".repeat(50).trim();
        String slug = SlugUtil.slugify(longTitle);
        assertThat(slug).hasSize(SlugUtil.MAX_SLUG_LEN);
        // the long title should have been cut to exactly 71 chars
        assertThat(slug.substring(9)).hasSize(71);
    }
}