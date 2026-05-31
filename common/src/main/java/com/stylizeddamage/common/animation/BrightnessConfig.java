package com.stylizeddamage.common.animation;

import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;
import java.util.Random;

/**
 * Configuration for the brightness animation module.
 *
 * <p>Brightness offset is a direct additive value applied to the color:
 * positive values make the text brighter, negative values make it darker.
 * The enter phase interpolates from 0 to {@link #enterTargetOffset()};
 * the exit phase interpolates from that value to {@link #exitTargetOffset()}.
 *
 * @param enterPhase        enter animation phase config
 * @param exitPhase         exit animation phase config
 * @param enterTargetOffset target brightness offset for the enter phase
 * @param exitTargetOffset  target brightness offset for the exit phase
 */
public record BrightnessConfig(
        PhaseConfig enterPhase,
        PhaseConfig exitPhase,
        RandomValue enterTargetOffset,
        RandomValue exitTargetOffset) {

    public BrightnessConfig {
        Objects.requireNonNull(enterPhase, "enterPhase must not be null");
        Objects.requireNonNull(exitPhase, "exitPhase must not be null");
        Objects.requireNonNull(enterTargetOffset, "enterTargetOffset must not be null");
        Objects.requireNonNull(exitTargetOffset, "exitTargetOffset must not be null");
    }

    /**
     * Resolves all random offset values at once.
     */
    public Resolved resolve(Random random) {
        return new Resolved(
                enterPhase, exitPhase,
                enterTargetOffset.resolve(random),
                exitTargetOffset.resolve(random)
        );
    }

    /**
     * Fully‑resolved brightness config — all random values are concrete doubles.
     */
    public record Resolved(
            PhaseConfig enterPhase,
            PhaseConfig exitPhase,
            double enterTargetOffset,
            double exitTargetOffset) {
    }
}
