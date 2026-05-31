package com.stylizeddamage.common.animation;

import java.util.Objects;
import java.util.Random;

/**
 * Top‑level animation configuration composed of four independent modules.
 *
 * <p>The animation lifecycle for each module:
 * <ol>
 *   <li><b>Enter</b> — interpolate from start offset to target offset over enter duration</li>
 *   <li><b>Hold</b> — stay at the enter target for {@link #holdTicks()} ticks</li>
 *   <li><b>Exit</b> — interpolate from enter target to exit target over exit duration</li>
 *   <li><b>Complete</b> — the animation is finished</li>
 * </ol>
 *
 * <p>All four modules run their enter phases simultaneously, then hold together,
 * then exit together. A module whose phase type is {@code NONE} instantly skips
 * that phase with effective duration 0.
 *
 * <p>Random values contained in offsets are resolved <em>once per animation</em>
 * via {@link #resolve(Random)} before the engine begins ticking.
 *
 * @param holdTicks  number of ticks to hold at the enter target between phases
 * @param position   position animation module config
 * @param size       size (scale) animation module config
 * @param brightness brightness animation module config
 * @param opacity    opacity animation module config
 */
public record AnimationConfig(
        int holdTicks,
        PositionConfig position,
        SizeConfig size,
        BrightnessConfig brightness,
        OpacityConfig opacity) {

    public AnimationConfig {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(brightness, "brightness must not be null");
        Objects.requireNonNull(opacity, "opacity must not be null");
        if (holdTicks < 0) {
            throw new IllegalArgumentException("holdTicks must be >= 0, got: " + holdTicks);
        }
    }

    /**
     * Resolves all random values in all four modules at once.
     *
     * <p>This should be called exactly once when the animation is created.
     * The returned {@link Resolved} instance is immutable and may be fed
     * to {@link AnimationEngine#update(int, Resolved)} every tick.
     */
    public Resolved resolve(Random random) {
        return new Resolved(
                holdTicks,
                position.resolve(random),
                size.resolve(random),
                brightness.resolve(random),
                opacity.resolve(random)
        );
    }

    /**
     * Fully‑resolved animation config — every random value has been evaluated.
     *
     * <p>This is the input type accepted by {@link AnimationEngine}.
     */
    public record Resolved(
            int holdTicks,
            PositionConfig.Resolved position,
            SizeConfig.Resolved size,
            BrightnessConfig.Resolved brightness,
            OpacityConfig.Resolved opacity) {

        /**
         * Returns the longest enter duration among all four modules.
         * Used by the engine to synchronise exit-phase start times.
         */
        public int maxEnterDuration() {
            int max = 0;
            max = Math.max(max, position.enterPhase().effectiveDuration());
            max = Math.max(max, size.enterPhase().effectiveDuration());
            max = Math.max(max, brightness.enterPhase().effectiveDuration());
            max = Math.max(max, opacity.enterPhase().effectiveDuration());
            return max;
        }
    }
}
