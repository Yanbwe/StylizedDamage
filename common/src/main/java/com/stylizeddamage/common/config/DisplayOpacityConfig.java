package com.stylizeddamage.common.config;

/**
 * Global opacity modifier for damage numbers, mirroring the structure of
 * {@link DisplayFilterConfig} but with 0.0–1.0 float values instead of booleans.
 *
 * <p>The final rendered opacity is multiplied by the value returned from
 * {@link #getOpacity(boolean, boolean, boolean)}.
 */
public record DisplayOpacityConfig(
        double player,
        double mobHostile,
        double mobPassive,
        double other) {

    public DisplayOpacityConfig {
        player = clamp(player);
        mobHostile = clamp(mobHostile);
        mobPassive = clamp(mobPassive);
        other = clamp(other);
    }

    public static DisplayOpacityConfig defaults() {
        return new DisplayOpacityConfig(1.0, 0.5, 0.75, 1.0);
    }

    /**
     * Returns the opacity multiplier for the given entity classification.
     */
    public double getOpacity(boolean isPlayer, boolean isMob, boolean isHostile) {
        if (isPlayer) return player;
        if (isMob) return isHostile ? mobHostile : mobPassive;
        return other;
    }

    private static double clamp(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
