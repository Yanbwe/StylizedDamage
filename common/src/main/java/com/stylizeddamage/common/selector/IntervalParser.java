package com.stylizeddamage.common.selector;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and represents damage-value interval strings used as keys in the
 * selector configuration.
 *
 * <h3>Supported interval formats</h3>
 * <table>
 *   <tr><th>Format</th><th>Meaning</th><th>Example</th></tr>
 *   <tr><td>{@code common}</td><td>Fallback — always matches</td><td>{@code "common"}</td></tr>
 *   <tr><td>{@code [min,max]}</td><td>Closed interval (inclusive both ends)</td><td>{@code "[0,100]"}</td></tr>
 *   <tr><td>{@code [min,...]}</td><td>Left-closed, right-open (min &le; x)</td><td>{@code "[50,...]"}</td></tr>
 *   <tr><td>{@code [...,max]}</td><td>Left-open, right-closed (x &le; max)</td><td>{@code "[...,30]"}</td></tr>
 * </table>
 *
 * <p>This class is a pure utility — all methods are static and stateless.
 * Interval instances are immutable sealed types.
 */
public final class IntervalParser {

    private IntervalParser() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /** Regex for {@code [min,max]} */
    private static final Pattern CLOSED_PATTERN =
            Pattern.compile("^\\[(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\]$");
    /** Regex for {@code [min,...]} */
    private static final Pattern LEFT_BOUNDED_PATTERN =
            Pattern.compile("^\\[(-?\\d+(?:\\.\\d+)?)\\s*,\\s*\\.{3}\\]$");
    /** Regex for {@code [...,max]} */
    private static final Pattern RIGHT_BOUNDED_PATTERN =
            Pattern.compile("^\\[\\.{3}\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\]$");

    // ── Sealed interval types ────────────────────────────────────────

    /** Base type for all parsed interval representations. */
    public sealed interface Interval {

        /**
         * Tests whether the given damage value falls within this interval.
         * {@link Common} always returns {@code true}.
         */
        boolean contains(double value);

        /** A closed interval {@code [min, max]} — both ends inclusive. */
        record Closed(double min, double max) implements Interval {
            public Closed {
                if (Double.isNaN(min) || Double.isInfinite(min)) {
                    throw new IllegalArgumentException("min must be finite, got: " + min);
                }
                if (Double.isNaN(max) || Double.isInfinite(max)) {
                    throw new IllegalArgumentException("max must be finite, got: " + max);
                }
                if (min > max) {
                    throw new IllegalArgumentException(
                            "min (" + min + ") must be <= max (" + max + ")");
                }
            }

            @Override
            public boolean contains(double value) {
                return value >= min && value <= max;
            }

            @Override
            public String toString() {
                return "[" + min + "," + max + "]";
            }
        }

        /** A left-bounded interval {@code [min, ...]} — min inclusive, no upper bound. */
        record LeftBounded(double min) implements Interval {
            public LeftBounded {
                if (Double.isNaN(min) || Double.isInfinite(min)) {
                    throw new IllegalArgumentException("min must be finite, got: " + min);
                }
            }

            @Override
            public boolean contains(double value) {
                return value >= min;
            }

            @Override
            public String toString() {
                return "[" + min + ",...]";
            }
        }

        /** A right-bounded interval {@code [..., max]} — no lower bound, max inclusive. */
        record RightBounded(double max) implements Interval {
            public RightBounded {
                if (Double.isNaN(max) || Double.isInfinite(max)) {
                    throw new IllegalArgumentException("max must be finite, got: " + max);
                }
            }

            @Override
            public boolean contains(double value) {
                return value <= max;
            }

            @Override
            public String toString() {
                return "[...," + max + "]";
            }
        }

        /** The fallback interval — always matches. */
        record Common() implements Interval {
            @Override
            public boolean contains(double value) {
                return true;
            }

            @Override
            public String toString() {
                return "common";
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Parses a string interval key into an {@link Interval}.
     *
     * @param key the interval string (e.g. {@code "[50,...]"}, {@code "common"})
     * @return the parsed interval, never {@code null}
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if the format is unrecognised
     */
    public static Interval parse(String key) {
        Objects.requireNonNull(key, "key must not be null");

        String trimmed = key.trim();

        if ("common".equals(trimmed)) {
            return new Interval.Common();
        }

        Matcher closedMatcher = CLOSED_PATTERN.matcher(trimmed);
        if (closedMatcher.matches()) {
            double min = Double.parseDouble(closedMatcher.group(1));
            double max = Double.parseDouble(closedMatcher.group(2));
            return new Interval.Closed(min, max);
        }

        Matcher leftMatcher = LEFT_BOUNDED_PATTERN.matcher(trimmed);
        if (leftMatcher.matches()) {
            double min = Double.parseDouble(leftMatcher.group(1));
            return new Interval.LeftBounded(min);
        }

        Matcher rightMatcher = RIGHT_BOUNDED_PATTERN.matcher(trimmed);
        if (rightMatcher.matches()) {
            double max = Double.parseDouble(rightMatcher.group(1));
            return new Interval.RightBounded(max);
        }

        throw new IllegalArgumentException(
                "Unrecognised interval key format: \"" + key + "\". "
                + "Expected one of: common, [min,max], [min,...], [...,max]");
    }

    /**
     * Returns {@code true} if the key is the fallback {@code "common"} interval.
     */
    public static boolean isCommon(String key) {
        return key != null && "common".equals(key.trim());
    }
}
