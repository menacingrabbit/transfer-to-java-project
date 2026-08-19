package com.example.ytanalysis.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A faithful port of {@code src/yt/utils.py} — the helpers that turn a video title into
 * a filesystem-safe "slug" used as the file stem.
 *
 * <p>This is a <b>stateless utility</b>: none of its methods need shared mutable state,
 * so it is intentionally <em>not</em> a Spring bean. Every caller invokes it as a static
 * helper, exactly like the Python functions {@code clean_title()} / {@code slugify()}.
 *
 * <p>Why not a bean? A Spring bean is needed when a class has dependencies or state that
 * must be injected/configured. A pure function needs neither, and making it a bean would
 * only add ceremony. We comment this deliberately — an educational project should also
 * teach <em>when not</em> to use Spring.
 */
public final class SlugUtil {

    private SlugUtil() { /* static-only utility */ }

    /** Titles are truncated so the whole stem ("20260819-<title>") stays under 80 chars. */
    public static final int MAX_SLUG_LEN = 80;

    /** Match a leading 8-digit date prefix ("20260819-...") to strip it when resuming. */
    public static final Pattern DATE_PREFIX = Pattern.compile("^\\d{8}-");

    // The three regex passes of clean_title, kept as compiled Patterns (faster than
    // String.replaceAll, which recompiles every call).
    /** Python {@code re.sub(r"\s+", " ", text)} — collapse runs of whitespace. */
    private static final Pattern WS = Pattern.compile("\\s+");
    /** Python {@code re.sub(r"[^\w\- ]+", "", text)} — drop "unusual" characters. */
    private static final Pattern NON_WORD = Pattern.compile("[^\\w\\- ]+");
    /** Python {@code re.sub(r"[\s_]+", "-", text)} — turn whitespace/underscores into hyphens. */
    private static final Pattern WS_OR_UNDER = Pattern.compile("[\\s_]+");

    /** today's date as {@code yyyyMMdd} — the prefix that makes filenames sort by day. */
    // Small cheat for testability: LocalDate.now() is the real clock. Unit tests call
    // slugify() and only assert the *suffix*, never the exact date, so we need no
    // clock injection. (A comment is worth more here than a Clock bean.)
    private static String todayStamp() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * Port of {@code clean_title(text)}:
     *
     * <ol>
     *   <li>collapse any run of whitespace into a single space, then {@code trim()}
     *       (Python {@code " ".join(text.split()).strip()}),</li>
     *   <li>remove any character that is not a word char, dash or space
     *       ({@code [^\w\- ]}),</li>
     *   <li>replace whitespace or underscores with a single hyphen,</li>
     *   <li>lower-case the whole title ({@code text.lower()}).</li>
     * </ol>
     */
    public static String cleanTitle(String text) {
        String t = WS.matcher(text).replaceAll(" ").trim();   // step 1
        t = NON_WORD.matcher(t).replaceAll("");                // step 2
        t = WS_OR_UNDER.matcher(t).replaceAll("-");            // step 3
        return t.toLowerCase(Locale.ROOT);                     // step 4 (ROOT: no locale surprises)
    }

    /**
     * Port of {@code slugify(text)}: {@code "YYYYMMDD-" + clean_title(text)}.
     *
     * <p>The title portion is truncated to {@code MAX_SLUG_LEN - 8 - 1 == 71} characters so
     * the whole slug is at most 80 (Python truncates with the same arithmetic
     * {@code clean_title(text)[: _MAX_LEN - _DATE_LEN - 1]}).
     */
    public static String slugify(String text) {
        String date = todayStamp();
        String cleaned = cleanTitle(text);
        int limit = MAX_SLUG_LEN - date.length() - 1;   // 80 - 8 - 1 = 71
        if (cleaned.length() > limit) {
            cleaned = cleaned.substring(0, limit);
        }
        return date + "-" + cleaned;
    }
}