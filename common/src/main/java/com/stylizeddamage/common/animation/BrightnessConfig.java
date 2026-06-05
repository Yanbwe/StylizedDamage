package com.stylizeddamage.common.animation;

import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;
import java.util.Random;

/**
 * Configuration for the brightness animation module.
 *
 * <p>Brightness offset is a direct additive value applied to the color:
 * positive values make the text brighter, negative values make it darker.
 * The enter phase interpolates from {@link #enterStartOffset()} to 0 (original brightness);
 * the exit phase interpolates from 0 to {@link #exitTargetOffset()}.
 *
 * @param enterPhase        enter animation phase config
 * @param exitPhase         exit animation phase config
 * @param enterStartOffset  starting brightness offset for the enter phase
 * @param exitTargetOffset  target brightness offset for the exit phase
 */
public record BrightnessConfig(
        PhaseConfig enterPhase,
        PhaseConfig exitPhase,
        RandomValue enterStartOffset,
        RandomValue exitTargetOffset) {

    public BrightnessConfig {
        Objects.requireNonNull(enterPhase, "enterPhase must not be null");
        Objects.requireNonNull(exitPhase, "exitPhase must not be null");
        Objects.requireNonNull(enterStartOffset, "enterStartOffset must not be null");
        Objects.requireNonNull(exitTargetOffset, "exitTargetOffset must not be null");
    }

    /**
     * Resolves all random offset values at once.
     */
    public Resolved resolve(Random random) {
        return new Resolved(
                enterPhase, exitPhase,
                enterStartOffset.resolve(random),
                exitTargetOffset.resolve(random)
        );
    }

    /**
     * Fully‑resolved brightness config — all random values are concrete doubles.
     */
    public record Resolved(
            PhaseConfig enterPhase,
            PhaseConfig exitPhase,
            double enterStartOffset,
            double exitTargetOffset) {
    }
}
