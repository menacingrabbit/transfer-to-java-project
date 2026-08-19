package com.example.ytanalysis.model;

/**
 * The switches that change how the pipeline runs — the Java mirror of the Python
 * {@code @dataclass Options} built from the CLI flags.
 *
 * <pre>{@code
 * # Python
 * @dataclass
 * class Options:
 *     no_summary: bool
 *     force:       bool
 *     split:       bool
 * }</pre>
 *
 * We add {@code save} so the REST layer can request "return me the text, do not write
 * files", which the original had no equivalent for (it always wrote files).
 * The record is immutable, so the only way to build it is via its constructor.
 *
 * @param noSummary skip the summarisation step (transcript only)
 * @param force     re-download even if a matching audio file already exists
 * @param split     split long audio into &lt;10-minute chunks before transcribing
 * @param save      write the transcript/summary to disk (default true)
 */
public record PipelineOptions(boolean noSummary, boolean force, boolean split, boolean save) {

    /** Build with the conventional default of writing files. */
    public static PipelineOptions of(boolean noSummary, boolean force, boolean split) {
        return new PipelineOptions(noSummary, force, split, true);
    }

    /**
     * A copy of these options with a different {@code save} flag. Records are immutable,
     * so callers that read one field from the request (like the controller does) get a fresh
     * instance rather than mutating the original. The {@code of} factory covers the CLI case
     * where save is always true.
     */
    public PipelineOptions withSave(boolean newSave) {
        return new PipelineOptions(noSummary, force, split, newSave);
    }
}