package com.example.ytanalysis.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CliArgumentParser} — the hand-rolled {@code argparse} mirror.
 *
 * <p>We instantiate the parser directly (it is a stateless {@code @Component}). Verifies the
 * rules the Python original encoded: defaults, store-true flags, and the mutual exclusion
 * of {@code --url} / {@code --batch-file}.
 */
class CliArgumentParserTest {

    private final CliArgumentParser parser = new CliArgumentParser();

    @Test
    void parsesASingleUrlAndDefaults() {
        CliArguments a = parser.parse(new String[]{"--url", "https://youtu.be/dQw4w9WgXcQ"});
        assertThat(a.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(a.batchFile()).isNull();
        assertThat(a.outDir().toString()).isEqualTo("output"); // default
        assertThat(a.noSummary()).isFalse();                  // store_true default
        assertThat(a.force()).isFalse();
        assertThat(a.split()).isFalse();
        assertThat(a.quiet()).isFalse();
        assertThat(a.verbose()).isFalse();
        assertThat(a.batchMode()).isFalse();
    }

    @Test
    void parsesFlagsAndOutDir() {
        CliArguments a = parser.parse(new String[]{
                "--url", "https://youtu.be/dQw4w9WgXcQ",
                "--out-dir", "outx", "--no-summary", "--split", "--force", "--verbose"});
        assertThat(a.outDir().toString()).isEqualTo("outx");
        assertThat(a.noSummary()).isTrue();
        assertThat(a.split()).isTrue();
        assertThat(a.force()).isTrue();
        assertThat(a.verbose()).isTrue();
    }

    @Test
    void parsesBatchFile() {
        CliArguments a = parser.parse(new String[]{"--batch-file", "links.txt", "--quiet"});
        assertThat(a.url()).isNull();
        assertThat(a.batchFile().toString()).isEqualTo("links.txt");
        assertThat(a.batchMode()).isTrue();
        assertThat(a.quiet()).isTrue();
    }

    @Test
    void rejectsUrlAndBatchFileTogether() {
        assertThatThrownBy(() -> parser.parse(new String[]{
                "--url", "https://youtu.be/dQw4w9WgXcQ", "--batch-file", "links.txt"}))
                .isInstanceOf(CliArgumentParser.CliParseException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void requiresEitherUrlOrBatchFile() {
        assertThatThrownBy(() -> parser.parse(new String[]{"--verbose"}))
                .isInstanceOf(CliArgumentParser.CliParseException.class)
                .hasMessageContaining("required");
    }

    @Test
    void throwsOnUnknownFlag() {
        assertThatThrownBy(() -> parser.parse(new String[]{"--bogus", "x"}))
                .isInstanceOf(CliArgumentParser.CliParseException.class)
                .hasMessageContaining("Unknown argument");
    }

    @Test
    void helpThrowsHelpRequested() {
        assertThatThrownBy(() -> parser.parse(new String[]{"--help"}))
                .isInstanceOf(CliArgumentParser.HelpRequested.class);
    }

    @Test
    void missingValueThrows() {
        assertThatThrownBy(() -> parser.parse(new String[]{"--url"}))
                .isInstanceOf(CliArgumentParser.CliParseException.class);
    }
}