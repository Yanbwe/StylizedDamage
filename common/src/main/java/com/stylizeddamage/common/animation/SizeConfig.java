package com.stylizeddamage.common.animation;

import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;
import java.util.Random;

/**
 * Configuration for the size animation module.
 *
 * <p>Size offsets are additive multipliers relative to the base font size:
 * an offset of {@code 0} means 100% size, {@code 0.5} means 150%, and
 * {@code -0.5} means 50%. The final scale factor is {@code 1.0 + offset}.
 *
 * <p>During enter the offset interpolates from {@link #enterStartOffset()}
 * to {@link #enterTargetOffset()}; exit moves to {@link #exitTargetOffset()}.
 *
 * @param enterPhase       enter animation phase config
 * @param exitPhase        exit animation phase config
 * @param enterStartOffset starting size offset for the enter phase
 * @param enterTargetOffset target size offset for the enter phase (also held during hold)
 * @param exitTargetOffset  target size offset for the exit phase
 */
public record SizeConfig(
        PhaseConfig enterPhase,
        PhaseConfig exitPhase,
        RandomValue enterStartOffset,
        RandomValue enterTargetOffset,
        RandomValue exitTargetOffset) {

    public SizeConfig {
        Objects.requireNonNull(enterPhase, "enterPhase must not be null");
        Objects.requireNonNull(exitPhase, "exitPhase must not be null");
        Objects.requireNonNull(enterStartOffset, "enterStartOffset must not be null");
        Objects.requireNonNull(enterTargetOffset, "enterTargetOffset must not be null");
        Objects.requireNonNull(exitTargetOffset, "exitTargetOffset must not be null");
    }

    /**
     * Resolves all random offset values at once.
     */
    public Resolved resolve(Random random) {
        return new Resolved(
                enterPhase, exitPhase,
                enterStartOffset.resolve(random),
                enterTargetOffset.resolve(random),
                exitTargetOffset.resolve(random)
        );
    }

    /**
     * Fully‑resolved size config — all random values are concrete doubles.
     */
    public record Resolved(
            PhaseConfig enterPhase,
            PhaseConfig exitPhase,
            double enterStartOffset,
            double enterTargetOffset,
            double exitTargetOffset) {
    }
}
