package com.stylizeddamage.common.config;

/**
 * Distance-based font size scaling parameters.
 * Derived from the flat fields in common.json.
 * <p>
 * Scaling formula:
 * <pre>{@code
 *   finalScale = clamp(
 *       fontSize * (1 - (distance / segment) * factor),
 *       min,
 *       fontSize
 *   );
 * }</pre>
 *
 * @param segment            distance interval per scaling step (blocks), default 10.0
 * @param factor             scale reduction per segment, default 0.2
 * @param min                minimum scale factor, default 0.3
 * @param maxDisplayDistance maximum distance for any display (blocks), default 64.0
 */
public record DistanceScaleConfig(
        double segment,
        double factor,
        double min,
        double maxDisplayDistance) {

    /** Compact constructor — validates and clamps to defaults. */
    public DistanceScaleConfig {
        if (segment <= 0) segment = ConfigDefaults.DEFAULT_DISTANCE_SCALE_SEGMENT;
        if (factor < 0) factor = ConfigDefaults.DEFAULT_DISTANCE_SCALE_FACTOR;
        if (min < 0) min = ConfigDefaults.DEFAULT_DISTANCE_SCALE_MIN;
        if (maxDisplayDistance <= 0) {
            maxDisplayDistance = ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE;
        }
    }

    /** Creates config populated with all default values. */
    public static DistanceScaleConfig defaults() {
        return new DistanceScaleConfig(
                ConfigDefaults.DEFAULT_DISTANCE_SCALE_SEGMENT,
                ConfigDefaults.DEFAULT_DISTANCE_SCALE_FACTOR,
                ConfigDefaults.DEFAULT_DISTANCE_SCALE_MIN,
                ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE);
    }
}
