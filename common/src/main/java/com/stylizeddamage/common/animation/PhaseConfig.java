package com.stylizeddamage.common.animation;

import com.stylizeddamage.common.util.EasingCurve;

import java.util.Objects;

/**
 * Configuration for a single animation phase (enter or exit).
 *
 * <p>Defines how a value transitions over time. The {@link #type()} controls whether
 * interpolation is active; {@link #duration()} sets the tick count for the phase;
 * {@link #easing()} provides the curve shape.
 *
 * @param type     whether this phase interpolates or snaps
 * @param duration animation duration in ticks (non‑negative; ignored when type is {@code NONE})
 * @param easing   the easing curve to apply during interpolation
 */
public record PhaseConfig(PhaseType type, int duration, EasingCurve easing) {

    /** Convenience constant for a no‑animation phase. */
    public static final PhaseConfig NONE = new PhaseConfig(PhaseType.NONE, 0, EasingCurve.LINEAR);

    public PhaseConfig {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(easing, "easing must not be null");
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be >= 0, got: " + duration);
        }
    }

    /**
     * Returns the effective duration of this phase.
     * A {@code NONE} phase always has effective duration 0.
     */
    public int effectiveDuration() {
        return type == PhaseType.NONE ? 0 : duration;
    }

    /**
     * Creates a phase config for a normal (interpolated) phase.
     */
    public static PhaseConfig normal(int duration, EasingCurve easing) {
        return new PhaseConfig(PhaseType.NORMAL, duration, easing);
    }
}
