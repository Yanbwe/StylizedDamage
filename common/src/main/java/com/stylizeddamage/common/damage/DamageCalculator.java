package com.stylizeddamage.common.damage;

/**
 * Pure static utility class for damage number calculations.
 *
 * <p>Provides two independent scaling systems:
 * <ul>
 *   <li><b>Distance scale</b> — shrinks the font size based on how far the
 *       damaged entity is from the player, creating a depth-of-field effect.</li>
 *   <li><b>Damage value scale</b> — adjusts font size based on the damage
 *       amount, making larger hits visually bigger.</li>
 * </ul>
 *
 * <p>All methods are pure functions: same input always produces the same output,
 * with no side effects. This class cannot be instantiated.
 *
 * <p>Package: {@code com.stylizeddamage.common.damage}
 */
public final class DamageCalculator {

    // --- Curve type constants ---

    /** Linear growth: factor = rawScale - 1. */
    public static final String CURVE_LINEAR = "linear";
    /** Square root growth: factor = sqrt(rawScale - 1). */
    public static final String CURVE_SQRT = "sqrt";
    /** Logarithmic growth: factor = log2(rawScale). */
    public static final String CURVE_LOG = "log";

    // --- Default configuration values ---

    /** Default distance segment (blocks) before each shrink step. */
    public static final double DEFAULT_DISTANCE_SEGMENT = 10.0;
    /** Default shrink factor per segment. */
    public static final double DEFAULT_DISTANCE_FACTOR = 0.2;
    /** Default minimum scale factor. */
    public static final double DEFAULT_DISTANCE_MIN_SCALE = 0.3;
    /** Default maximum display distance in blocks. */
    public static final double DEFAULT_MAX_DISPLAY_DISTANCE = 64.0;

    /** Default base damage for damage scaling. */
    public static final double DEFAULT_BASE_DAMAGE = 10.0;
    /** Default maximum scale for damage-based scaling. */
    public static final double DEFAULT_MAX_DAMAGE_SCALE = 3.0;

    private static final double FONT_SIZE_EPSILON = 1e-9;

    private DamageCalculator() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    // ========================================================================
    // Distance-Based Scaling
    // ========================================================================

    /**
     * Calculates the scaled font size based on the distance between the player
     * and the damaged entity.
     *
     * <p>Formula:
     * <pre>{@code
     *   scaled = fontSize * (1 - (distance / segment) * factor)
     *   result = clamp(scaled, fontSize * minScale, fontSize)
     * }</pre>
     *
     * <p>Returns {@code 0} when the distance exceeds {@code maxDisplayDistance},
     * signaling the caller to skip rendering this damage number entirely.
     *
     * @param distance          distance in blocks between the player and the entity
     * @param fontSize          base font size from the style configuration
     * @param segment           distance in blocks per shrink segment (default 10)
     * @param factor            shrink factor per segment (default 0.2)
     * @param minScale          minimum scale factor relative to fontSize (default 0.3)
     * @param maxDisplayDistance distance beyond which no damage number is displayed (default 64)
     * @return the final scaled font size, or {@code 0} if beyond max display distance
     * @throws IllegalArgumentException if font size is non-positive
     */
    public static double distanceScale(
            double distance,
            double fontSize,
            double segment,
            double factor,
            double minScale,
            double maxDisplayDistance
    ) {
        if (fontSize <= 0) {
            throw new IllegalArgumentException("fontSize must be positive, got: " + fontSize);
        }
        if (distance < 0) {
            distance = 0; // Treat negative distance as zero
        }

        // Beyond max display distance — don't render
        if (maxDisplayDistance > FONT_SIZE_EPSILON && distance > maxDisplayDistance) {
            return 0.0;
        }

        // Distance 0 → full size
        if (segment <= 0 || factor <= 0) {
            return fontSize;
        }

        double shrinkRatio = (distance / segment) * factor;
        double scaled = fontSize * (1.0 - shrinkRatio);
        double minimum = fontSize * minScale;

        return clamp(scaled, minimum, fontSize);
    }

    /**
     * Convenience overload using default segment, factor, minScale, and maxDisplayDistance.
     *
     * @param distance distance in blocks between the player and the entity
     * @param fontSize base font size from the style configuration
     * @return the final scaled font size, or {@code 0} if beyond default max distance (64)
     */
    public static double distanceScale(double distance, double fontSize) {
        return distanceScale(
                distance,
                fontSize,
                DEFAULT_DISTANCE_SEGMENT,
                DEFAULT_DISTANCE_FACTOR,
                DEFAULT_DISTANCE_MIN_SCALE,
                DEFAULT_MAX_DISPLAY_DISTANCE
        );
    }

    // ========================================================================
    // Damage-Value-Based Scaling
    // ========================================================================

    /**
     * Calculates the font size multiplier based on the damage amount.
     *
     * <p>The scaling makes larger hits appear visually bigger, using a
     * configurable curve to control the growth rate.
     *
     * <p>Formula:
     * <pre>{@code
     *   rawScale = damage / baseDamage
     *   factor   = rawScale > 1
     *              ? curveFunc(rawScale - 1)
     *              : -(curveFunc(1 - rawScale))
     *   scale    = clamp(1.0 + factor, 1.0, maxScale)
     * }</pre>
     *
     * <p>Supported curves:
     * <ul>
     *   <li>{@code "linear"} — linear growth</li>
     *   <li>{@code "sqrt"}   — square root growth (recommended)</li>
     *   <li>{@code "log"}    — logarithmic (base-2) growth</li>
     * </ul>
     *
     * @param damage     the damage amount (non-negative)
     * @param baseDamage the damage value that yields scale 1.0
     * @param maxScale   the maximum allowed scale multiplier
     * @param curve      the growth curve identifier ("linear", "sqrt", or "log")
     * @return a font size multiplier in [1.0, maxScale]
     * @throws IllegalArgumentException if damage is negative, baseDamage non-positive, or curve unknown
     */
    public static double damageValueScale(
            double damage,
            double baseDamage,
            double maxScale,
            String curve
    ) {
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be non-negative, got: " + damage);
        }
        if (baseDamage <= 0) {
            throw new IllegalArgumentException("baseDamage must be positive, got: " + baseDamage);
        }
        if (maxScale < 1.0) {
            throw new IllegalArgumentException("maxScale must be >= 1.0, got: " + maxScale);
        }

        double rawScale = damage / baseDamage;
        double factor;

        if (rawScale > 1.0) {
            factor = applyCurve(rawScale - 1.0, curve);
        } else {
            // For small damage values, shrink proportionally (but clamped to 1.0)
            factor = -applyCurve(1.0 - rawScale, curve);
        }

        return clamp(1.0 + factor, 1.0, maxScale);
    }

    /**
     * Convenience overload with enabled flag. Returns 1.0 when scaling is disabled
     * or damage is zero.
     */
    public static double damageValueScale(
            double damage,
            boolean enabled,
            double baseDamage,
            double maxScale,
            String curve
    ) {
        if (!enabled || damage <= FONT_SIZE_EPSILON) {
            return 1.0;
        }
        return damageValueScale(damage, baseDamage, maxScale, curve);
    }

    // ========================================================================
    // Internal Helpers
    // ========================================================================

    /**
     * Clamps a value to fall within [min, max].
     */
    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Applies the named curve function to a non-negative input.
     */
    private static double applyCurve(double x, String curve) {
        switch (curve) {
            case CURVE_LINEAR:
                return x;
            case CURVE_SQRT:
                return Math.sqrt(x);
            case CURVE_LOG:
                // log2(x + 1): ln(x+1) / ln(2)
                if (x <= 0) return 0;
                return Math.log(x + 1.0) / Math.log(2.0);
            default:
                throw new IllegalArgumentException(
                        "Unknown curve: " + curve + ". Supported: linear, sqrt, log"
                );
        }
    }
}
