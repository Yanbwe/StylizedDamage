package com.stylizeddamage.common.animation;

import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;
import java.util.Random;

/**
 * Configuration for the opacity (alpha) animation module.
 *
 * <p>Opacity values range from {@code 0.0} (fully transparent) to {@code 1.0}
 * (fully opaque). The enter phase interpolates from {@link #enterStartOpacity()}
 * to {@link #enterTargetOpacity()}; the exit phase moves to {@link #exitTargetOpacity()}.
 *
 * @param enterPhase         enter animation phase config
 * @param exitPhase          exit animation phase config
 * @param enterStartOpacity  starting opacity for the enter phase
 * @param enterTargetOpacity target opacity for the enter phase (also held during hold)
 * @param exitTargetOpacity  target opacity for the exit phase
 */
public record OpacityConfig(
        PhaseConfig enterPhase,
        PhaseConfig exitPhase,
        RandomValue enterStartOpacity,
        RandomValue enterTargetOpacity,
        RandomValue exitTargetOpacity) {

    public OpacityConfig {
        Objects.requireNonNull(enterPhase, "enterPhase must not be null");
        Objects.requireNonNull(exitPhase, "exitPhase must not be null");
        Objects.requireNonNull(enterStartOpacity, "enterStartOpacity must not be null");
        Objects.requireNonNull(enterTargetOpacity, "enterTargetOpacity must not be null");
        Objects.requireNonNull(exitTargetOpacity, "exitTargetOpacity must not be null");
    }

    /**
     * Resolves all random opacity values at once.
     */
    public Resolved resolve(Random random) {
        return new Resolved(
                enterPhase, exitPhase,
                enterStartOpacity.resolve(random),
                enterTargetOpacity.resolve(random),
                exitTargetOpacity.resolve(random)
        );
    }

    /**
     * Fully‑resolved opacity config — all random values are concrete doubles.
     */
    public record Resolved(
            PhaseConfig enterPhase,
            PhaseConfig exitPhase,
            double enterStartOpacity,
            double enterTargetOpacity,
            double exitTargetOpacity) {
    }
}
