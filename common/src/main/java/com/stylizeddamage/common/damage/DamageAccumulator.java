package com.stylizeddamage.common.damage;

import com.google.gson.JsonObject;
import com.stylizeddamage.common.config.ConfigDefaults;
import com.stylizeddamage.common.config.TotalDamageConfig;
import com.stylizeddamage.common.selector.IntervalParser;

import java.util.*;

/**
 * An immutable-style accumulator for the total-damage HUD panel.
 *
 * <p>Each call to {@link #accumulate(float, String, int)} returns a
 * <em>new</em> instance with updated state — the original is never mutated.
 * This makes the accumulator safe to share across threads without
 * synchronization.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Create via {@link #create(TotalDamageConfig)} — starts with zero
 *       damage, empty trail, timer at 0.</li>
 *   <li>Call {@link #accumulate(float, String, int)} whenever a new damage
 *       event occurs — resets the timer to {@code config.resetTimeout()}.</li>
 *   <li>Call {@link #tick(int)} every game tick — decrements the internal
 *       timer. When the timer expires, returns a reset accumulator
 *       (zero damage, empty trail).</li>
 * </ol>
 *
 * <h3>Trail ordering</h3>
 * New entries are inserted at the <b>head</b> of the trail list
 * (position 0). The list is truncated to {@code config.maxTrailCount()}
 * by removing the oldest entries from the tail.
 *
 * <h3>Font size formula</h3>
 * <pre>{@code
 *   finalSize = clamp(
 *       baseFontSize + floor(totalDamage / 100) * sizeOffsetPerThousand,
 *       baseFontSize,
 *       sizeOffsetMax
 *   );
 * }</pre>
 *
 * <p>Package: {@code com.stylizeddamage.common.damage}
 */
public final class DamageAccumulator {

    private final double totalDamage;
    private final List<TrailEntry> trailList;
    private final int resetTimer;
    private final TotalDamageConfig config;

    private DamageAccumulator(double totalDamage,
                              List<TrailEntry> trailList,
                              int resetTimer,
                              TotalDamageConfig config) {
        this.totalDamage = totalDamage;
        this.trailList = Collections.unmodifiableList(
                Objects.requireNonNull(trailList, "trailList must not be null"));
        this.resetTimer = resetTimer;
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    // ── Public factory ────────────────────────────────────────────────

    /**
     * Creates a fresh accumulator bound to the given total-damage configuration.
     * Starts with zero total damage, empty trail, and timer at 0
     * (not counting down until the first {@link #accumulate} call).
     *
     * @param config the total-damage panel configuration
     * @return a new, zeroed-out accumulator
     */
    public static DamageAccumulator create(TotalDamageConfig config) {
        return new DamageAccumulator(0.0, List.of(), 0, config);
    }

    /**
     * Returns a reset accumulator — total damage back to 0, trail cleared,
     * timer at 0. Equivalent to {@link #create(TotalDamageConfig)}.
     *
     * @param config the total-damage panel configuration
     * @return a new, zeroed-out accumulator
     */
    public static DamageAccumulator reset(TotalDamageConfig config) {
        return create(config);
    }

    // ── Core operations ───────────────────────────────────────────────

    /**
     * Accumulates a damage event into the total, inserts a trail entry at
     * the head, truncates the trail list to {@code config.maxTrailCount()},
     * and resets the countdown timer to {@code config.resetTimeout()}.
     *
     * @param damage    the damage amount (may be positive, zero, or negative)
     * @param styleName the matched style name for this damage event
     * @param currentTick the game tick when the damage was dealt
     * @return a new accumulator with the updated state
     */
    public DamageAccumulator accumulate(float damage, String styleName, int currentTick) {
        Objects.requireNonNull(styleName, "styleName must not be null");

        double newTotal = totalDamage + damage;

        // Build new trail list: new entry at head, then existing entries
        List<TrailEntry> newTrail = new ArrayList<>(config.maxTrailCount() + 1);
        newTrail.add(new TrailEntry(damage, styleName, currentTick));
        newTrail.addAll(trailList);

        // Truncate to maxTrailCount (remove oldest from tail) — at most one extra
        if (newTrail.size() > config.maxTrailCount()) {
            newTrail = new ArrayList<>(newTrail.subList(0, config.maxTrailCount()));
        }

        return new DamageAccumulator(newTotal, newTrail, config.resetTimeout(), config);
    }

    /**
     * Advances the internal countdown timer by one tick. When the timer
     * reaches zero or below, the accumulator resets (total damage → 0,
     * trail cleared, timer → 0).
     *
     * <p>Callers should replace their reference with the returned instance.
     *
     * @param currentTick the current game tick (for informational purposes)
     * @return the updated accumulator (may be a reset one if timer expired)
     * @throws IllegalArgumentException if currentTick is negative
     */
    public DamageAccumulator tick(int currentTick) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must be non-negative, got: " + currentTick);
        }

        // If already at 0 (idle / reset state), stay at 0
        if (resetTimer <= 0) {
            return this;
        }

        int newTimer = resetTimer - 1;
        if (newTimer <= 0) {
            return create(config);
        }

        return new DamageAccumulator(totalDamage, trailList, newTimer, config);
    }

    // ── Style matching ────────────────────────────────────────────────

    /**
     * Determines the style name to apply to the total-damage number based on
     * the accumulated total and the configured interval→style selectors.
     *
     * <p>Non-{@code "common"} intervals are tested in map insertion order.
     * When no specific interval matches, the {@code "common"} fallback is used.
     * If even {@code "common"} is missing, returns {@link ConfigDefaults#DEFAULT_STYLE_NAME}.
     *
     * @return the style name for the total-damage number
     */
    public String getTotalStyleName() {
        Map<String, JsonObject> selectors = config.selectors();

        // Test all non-"common" intervals in insertion order
        for (Map.Entry<String, JsonObject> entry : selectors.entrySet()) {
            String key = entry.getKey();
            if ("common".equals(key)) {
                continue;
            }
            try {
                IntervalParser.Interval interval = IntervalParser.parse(key);
                if (interval.contains(totalDamage)) {
                    return extractStyle(entry.getValue());
                }
            } catch (IllegalArgumentException ignored) {
                // Skip malformed interval keys silently — they can't match
            }
        }

        // Fallback: "common" interval
        JsonObject commonSelector = selectors.get("common");
        if (commonSelector != null) {
            return extractStyle(commonSelector);
        }

        return ConfigDefaults.DEFAULT_STYLE_NAME;
    }

    /**
     * Calculates the font size for the total-damage number using the formula:
     * <pre>{@code
     *   clamp(baseFontSize + floor(totalDamage / 100) * sizeOffsetPerThousand,
     *         baseFontSize, sizeOffsetMax)
     * }</pre>
     *
     * @return the font size multiplier (always &ge; baseFontSize and &le; sizeOffsetMax)
     */
    public double calculateFontSize() {
        double baseFontSize = config.baseFontSize();
        double sizeOffsetPerThousand = config.sizeOffsetPerThousand();
        double sizeOffsetMax = config.sizeOffsetMax();

        // continuous scaling for smooth size growth
        double steps = totalDamage / 100.0;
        double rawSize = baseFontSize + steps * sizeOffsetPerThousand;

        return clamp(rawSize, baseFontSize, sizeOffsetMax);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** The accumulated total damage. */
    public float totalDamage() {
        return (float) totalDamage;
    }

    /** An unmodifiable view of the trail list (newest entry at index 0). */
    public List<TrailEntry> trailList() {
        return trailList; // already unmodifiable from constructor
    }

    /** The current value of the countdown timer (ticks until reset). */
    public int resetTimer() {
        return resetTimer;
    }

    /** The configuration this accumulator is bound to. */
    public TotalDamageConfig config() {
        return config;
    }

    // ── Internal helpers ──────────────────────────────────────────────

    /**
     * Extracts the {@code "style"} property from a selector JSON object.
     *
     * @param selector the JsonObject from the selectors map (e.g. {@code {"style": "total"}})
     * @return the style name, or {@link ConfigDefaults#DEFAULT_STYLE_NAME} if missing
     */
    private static String extractStyle(JsonObject selector) {
        if (selector.has("style")) {
            String style = selector.get("style").getAsString();
            if (style != null && !style.isBlank()) {
                return style;
            }
        }
        return ConfigDefaults.DEFAULT_STYLE_NAME;
    }

    /**
     * Clamps a value to fall within [min, max].
     */
    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    // ── Object overrides ──────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DamageAccumulator other)) return false;
        return Double.compare(other.totalDamage, totalDamage) == 0
                && resetTimer == other.resetTimer
                && trailList.equals(other.trailList)
                && config.equals(other.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalDamage, trailList, resetTimer, config);
    }

    @Override
    public String toString() {
        return "DamageAccumulator{total=" + totalDamage
                + ", trailSize=" + trailList.size()
                + ", timer=" + resetTimer + "}";
    }
}
