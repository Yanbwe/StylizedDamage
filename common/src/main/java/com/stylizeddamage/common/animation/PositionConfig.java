package com.stylizeddamage.common.animation;

import java.util.Objects;
import java.util.Random;

/**
 * Configuration for the position animation module.
 *
 * <p>The position controls screen‑space pixel offsets from a damage number's
 * origin. During the enter phase the offset interpolates from {@link #enterStartOffset()}
 * to {@link #enterTargetOffset()}; during the exit phase it moves from the enter
 * target to {@link #exitTargetOffset()}.
 *
 * @param enterPhase       enter animation phase config (type + duration + easing)
 * @param exitPhase        exit animation phase config
 * @param enterStartOffset starting position offset for the enter phase
 * @param enterTargetOffset target position offset for the enter phase (also held during hold)
 * @param exitTargetOffset  target position offset for the exit phase
 */
public record PositionConfig(
        PhaseConfig enterPhase,
        PhaseConfig exitPhase,
        OffsetValue enterStartOffset,
        OffsetValue enterTargetOffset,
        OffsetValue exitTargetOffset) {

    public PositionConfig {
        Objects.requireNonNull(enterPhase, "enterPhase must not be null");
        Objects.requireNonNull(exitPhase, "exitPhase must not be null");
        Objects.requireNonNull(enterStartOffset, "enterStartOffset must not be null");
        Objects.requireNonNull(enterTargetOffset, "enterTargetOffset must not be null");
        Objects.requireNonNull(exitTargetOffset, "exitTargetOffset must not be null");
    }

    /**
     * Resolves all random offsets at once using the supplied {@link Random}.
     *
     * <p>This is the only call site that evaluates randomness for this module.
     * The returned {@link Resolved} instance is immutable and deterministic
     * for the remainder of the animation's lifetime.
     */
    public Resolved resolve(Random random) {
        return new Resolved(
                enterPhase, exitPhase,
                enterStartOffset.resolveX(random), enterStartOffset.resolveY(random),
                enterTargetOffset.resolveX(random), enterTargetOffset.resolveY(random),
                exitTargetOffset.resolveX(random), exitTargetOffset.resolveY(random)
        );
    }

    /**
     * Fully‑resolved position config — all random values are concrete doubles.
     */
    public record Resolved(
            PhaseConfig enterPhase,
            PhaseConfig exitPhase,
            double enterStartX, double enterStartY,
            double enterTargetX, double enterTargetY,
            double exitTargetX, double exitTargetY) {
    }
}
