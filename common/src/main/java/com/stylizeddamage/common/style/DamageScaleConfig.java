package com.stylizeddamage.common.style;

/**
 * Configuration for damage-value-based font size and hold-time scaling.
 *
 * <p>Both size and hold use the same {@code stepSize} for linear growth:
 * <pre>{@code
 *   size = clamp(baseFontSize + (damage / stepSize) * sizeOffsetPerStep,
 *                baseFontSize, maxSize)
 *   hold = clamp(baseHold + (damage / stepSize) * holdOffsetPerStep,
 *                baseHold, holdMax)
 * }</pre>
 *
 * @param enabled           whether damage scaling is active
 * @param baseFontSize      the base font size multiplier
 * @param stepSize          how many damage points per scale step
 * @param sizeOffsetPerStep how much to add to font size per step
 * @param maxSize           the maximum font size
 * @param baseHold          base hold ticks at zero damage (added to animation hold)
 * @param holdOffsetPerStep how many hold ticks to add per step
 * @param holdMax           the maximum hold ticks (capped)
 */
public record DamageScaleConfig(
        boolean enabled,
        double baseFontSize,
        double stepSize,
        double sizeOffsetPerStep,
        double maxSize,
        double baseHold,
        double holdOffsetPerStep,
        double holdMax) {

    public DamageScaleConfig {
        if (baseFontSize <= 0) baseFontSize = StyleDefaults.DEFAULT_DAMAGE_SCALE_BASE;
        if (stepSize <= 0) stepSize = StyleDefaults.DEFAULT_DAMAGE_SCALE_STEP;
        if (sizeOffsetPerStep < 0) sizeOffsetPerStep = StyleDefaults.DEFAULT_DAMAGE_SCALE_OFFSET;
        if (maxSize < baseFontSize) maxSize = StyleDefaults.DEFAULT_DAMAGE_SCALE_MAX;
        if (baseHold < 0) baseHold = StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_BASE;
        if (holdOffsetPerStep < 0) holdOffsetPerStep = StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_OFFSET;
        if (holdMax < baseHold) holdMax = StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_MAX;
    }

    public static DamageScaleConfig defaults() {
        return new DamageScaleConfig(
                StyleDefaults.DEFAULT_DAMAGE_SCALE_ENABLED,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_BASE,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_STEP,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_OFFSET,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_MAX,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_BASE,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_OFFSET,
                StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_MAX);
    }

    /** Computes the font size scale factor. */
    public double computeScale(double damage) {
        if (!enabled || damage <= 0) return baseFontSize;
        double scale = baseFontSize + (damage / stepSize) * sizeOffsetPerStep;
        return clamp(scale, baseFontSize, maxSize);
    }

    /**
     * Computes the effective hold ticks for a given damage value.
     * Result is added to the animation's native hold ticks.
     */
    public int computeHoldExtra(double damage) {
        if (!enabled || damage <= 0) return 0;
        double extra = (damage / stepSize) * holdOffsetPerStep;
        return (int) Math.round(clamp(extra, 0, holdMax - baseHold));
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
