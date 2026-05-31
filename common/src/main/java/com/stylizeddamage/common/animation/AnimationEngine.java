package com.stylizeddamage.common.animation;

/**
 * Stateless pure‑function engine for computing per‑frame animation state.
 *
 * <p>This class is the core of the four‑module animation system. Given a
 * relative tick and a fully‑resolved configuration, it calculates the
 * instantaneous offset, scale, brightness, and opacity values.
 *
 * <p><b>Usage pattern:</b>
 * <pre>{@code
 *   // Resolve random values once at animation creation
 *   AnimationConfig.Resolved resolved = config.resolve(random);
 *   // Each tick, feed the resolved config + relative tick
 *   for (int tick = 0; ; tick++) {
 *       AnimationState state = AnimationEngine.update(tick, resolved);
 *       if (state.isComplete()) break;
 *       render(state);
 *   }
 * }</pre>
 *
 * <p>All methods are pure static functions. This class cannot be instantiated.
 */
public final class AnimationEngine {

    private AnimationEngine() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Computes the animation state for a single tick.
     *
     * @param tick     the relative tick, where 0 is the moment the animation
     *                 was created (enter begins)
     * @param resolved the fully‑resolved animation configuration (all random
     *                 values already evaluated)
     * @return the instantaneous animation state; never null
     */
    public static AnimationState update(double tick, AnimationConfig.Resolved resolved) {
        if (tick < 0) {
            tick = 0;
        }

        // All modules share the same exit start time (= longest enter + hold).
        // This prevents modules with shorter enter phases from starting their
        // exit earlier than others, which would cause visible desync.
        final int holdTicks = resolved.holdTicks();
        final int maxEnter = resolved.maxEnterDuration();

        // Position
        double offsetX = computePosition(tick, holdTicks, maxEnter, resolved.position(), true);
        double offsetY = computePosition(tick, holdTicks, maxEnter, resolved.position(), false);

        // Size
        double scale = computeScalar(
                tick, holdTicks, maxEnter,
                resolved.size().enterPhase(), resolved.size().exitPhase(),
                resolved.size().enterStartOffset(), resolved.size().enterTargetOffset(),
                resolved.size().exitTargetOffset()
        );
        // scale factor = 1.0 + offset (offset additive to baseline)
        scale = 1.0 + scale;

        // Brightness
        double brightnessOffset = computeScalar(
                tick, holdTicks, maxEnter,
                resolved.brightness().enterPhase(), resolved.brightness().exitPhase(),
                0.0, // brightness enter always starts from 0
                resolved.brightness().enterTargetOffset(),
                resolved.brightness().exitTargetOffset()
        );

        // Opacity
        double opacity = computeScalar(
                tick, holdTicks, maxEnter,
                resolved.opacity().enterPhase(), resolved.opacity().exitPhase(),
                resolved.opacity().enterStartOpacity(),
                resolved.opacity().enterTargetOpacity(),
                resolved.opacity().exitTargetOpacity()
        );
        // Clamp opacity to valid range
        opacity = clamp(opacity, 0.0, 1.0);

        // Determine completion (uses maxEnter so all modules sync)
        boolean posComplete = isModuleComplete(
                tick, holdTicks, maxEnter,
                resolved.position().enterPhase(), resolved.position().exitPhase());
        boolean sizeComplete = isModuleComplete(
                tick, holdTicks, maxEnter,
                resolved.size().enterPhase(), resolved.size().exitPhase());
        boolean brightComplete = isModuleComplete(
                tick, holdTicks, maxEnter,
                resolved.brightness().enterPhase(), resolved.brightness().exitPhase());
        boolean opacityComplete = isModuleComplete(
                tick, holdTicks, maxEnter,
                resolved.opacity().enterPhase(), resolved.opacity().exitPhase());
        boolean isComplete = posComplete && sizeComplete && brightComplete && opacityComplete;

        return new AnimationState(offsetX, offsetY, scale, brightnessOffset, opacity, isComplete);
    }

    // ========================================================================
    // Position interpolation (handles X and Y independently)
    // ========================================================================

    private static double computePosition(
            double tick, int holdTicks, int maxEnter,
            PositionConfig.Resolved pos,
            boolean isX) {

        double enterStart = isX ? pos.enterStartX() : pos.enterStartY();
        double enterTarget = isX ? pos.enterTargetX() : pos.enterTargetY();
        double exitTarget = isX ? pos.exitTargetX() : pos.exitTargetY();

        return computeScalar(
                tick, holdTicks, maxEnter,
                pos.enterPhase(), pos.exitPhase(),
                enterStart, enterTarget, exitTarget
        );
    }

    // ========================================================================
    // General scalar interpolation
    // ========================================================================

    /**
     * Computes a scalar value through the enter → hold → exit lifecycle.
     *
     * @param tick        the relative tick
     * @param holdTicks   shared hold duration
     * @param enterPhase  enter phase config
     * @param exitPhase   exit phase config
     * @param enterStart  value at the beginning of enter
     * @param enterTarget value at the end of enter (also the hold value)
     * @param exitTarget  value at the end of exit
     * @return the interpolated value at the given tick
     */
    private static double computeScalar(
            double tick, int holdTicks, int maxEnter,
            PhaseConfig enterPhase, PhaseConfig exitPhase,
            double enterStart, double enterTarget, double exitTarget) {

        int enterDuration = enterPhase.effectiveDuration();
        int exitDuration = exitPhase.effectiveDuration();

        // --- Enter phase ---
        if (tick < enterDuration) {
            if (enterPhase.type() == PhaseType.NONE) {
                return enterTarget;
            }
            double progress = tick / enterDuration;
            double eased = enterPhase.easing().apply(progress);
            return lerp(enterStart, enterTarget, eased);
        }

        // Hold + exit use maxEnter so all modules exit in sync.
        // A module with a shorter enter simply holds longer.
        int holdStart = maxEnter;
        int holdEnd = holdStart + holdTicks;

        // --- Hold phase ---
        if (tick < holdEnd) {
            return enterTarget;
        }

        int exitStart = holdEnd;
        int exitEnd = exitStart + exitDuration;

        // --- Exit phase ---
        if (tick < exitEnd) {
            if (exitPhase.type() == PhaseType.NONE) {
                return enterTarget;
            }
            double exitTick = tick - exitStart;
            double progress = exitTick / exitDuration;
            double eased = exitPhase.easing().apply(progress);
            return lerp(enterTarget, exitTarget, eased);
        }

        // --- Complete ---
        if (exitPhase.type() == PhaseType.NONE) {
            return enterTarget;
        }
        return exitTarget;
    }

    // ========================================================================
    // Module completion check
    // ========================================================================

    private static boolean isModuleComplete(
            double tick, int holdTicks, int maxEnter,
            PhaseConfig enterPhase, PhaseConfig exitPhase) {

        int totalDuration = maxEnter + holdTicks + exitPhase.effectiveDuration();
        return tick >= totalDuration;
    }

    // ========================================================================
    // Math helpers
    // ========================================================================

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
