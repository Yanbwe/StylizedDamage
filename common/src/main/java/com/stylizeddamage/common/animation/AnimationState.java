package com.stylizeddamage.common.animation;

/**
 * The output of {@link AnimationEngine#update(int, AnimationConfig.Resolved)} for a
 * single tick — the resolved state of all four animation modules.
 *
 * <p>This record is immutable and carries no mutable rendering state. Callers
 * apply the returned values to their damage‑number rendering each frame.
 *
 * @param offsetX         horizontal pixel offset from the damage number's origin
 * @param offsetY         vertical pixel offset from the damage number's origin
 * @param scale           size scale factor relative to the base {@code fontSize}
 *                        (1.0 = 100% size, 1.5 = 150%, 0.5 = 50%)
 * @param brightnessOffset additive brightness value (0 = no change, positive = brighter,
 *                         negative = darker)
 * @param opacity         alpha value in [0, 1] (0 = fully transparent, 1 = fully opaque)
 * @param isComplete      {@code true} when the entire animation has finished and the
 *                        damage number should be removed
 */
public record AnimationState(
        double offsetX,
        double offsetY,
        double scale,
        double brightnessOffset,
        double opacity,
        boolean isComplete) {

    /** A completed animation with neutral default values. */
    public static final AnimationState COMPLETED =
            new AnimationState(0, 0, 1.0, 0, 0, true);

    /**
     * Creates a neutral (identity) state that is not yet complete.
     * Useful as an initial value before the first tick.
     */
    public static AnimationState identity() {
        return new AnimationState(0, 0, 1.0, 0, 1.0, false);
    }
}
