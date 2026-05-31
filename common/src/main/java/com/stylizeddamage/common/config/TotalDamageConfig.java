package com.stylizeddamage.common.config;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the total-damage HUD panel.
 * <p>
 * Font size formula:
 * <pre>{@code
 *   finalSize = clamp(
 *       baseFontSize + floor(totalDamage / 100) * sizeOffsetPerThousand,
 *       baseFontSize,
 *       sizeOffsetMax
 *   );
 * }</pre>
 * Selectors are stored as raw {@link JsonObject} maps — full deserialization
 * is deferred to the selector subsystem.
 *
 * @param enabled                whether the panel is active
 * @param resetTimeout           ticks after last damage before resetting
 * @param maxTrailCount          maximum recent damage entries shown
 * @param baseFontSize           base font size multiplier
 * @param sizeOffsetPerThousand  size increase per 100 total damage
 * @param sizeOffsetMax          maximum font size multiplier
 * @param selectors              raw interval→style selectors (keys: "common", "[100,...]", etc.)
 * @param positionX              horizontal offset in pixels (positive = right)
 * @param positionY              vertical offset in pixels (positive = down)
 * @param enableEntryAnimation   whether total-damage number plays an entry animation
 * @param enableExitAnimation    whether total-damage number plays an exit animation
 * @param enableBounceAnimation  whether total-damage number bounces on value change
 * @param enableTrailEntryAnimation whether trail entries slide in from the right
 * @param enableTrailExitAnimation  whether trail entries slide out to the left
 * @param bounceScalePeak           peak scale multiplier during bounce (e.g. 1.4 = 140% size at peak)
 */
public record TotalDamageConfig(
        boolean enabled,
        int resetTimeout,
        int maxTrailCount,
        double baseFontSize,
        double sizeOffsetPerThousand,
        double sizeOffsetMax,
        Map<String, JsonObject> selectors,
        double positionX,
        double positionY,
        boolean enableEntryAnimation,
        boolean enableExitAnimation,
        boolean enableBounceAnimation,
        boolean enableTrailEntryAnimation,
        boolean enableTrailExitAnimation,
        double bounceScalePeak) {

    /** Compact constructor — validates and provides defaults. */
    public TotalDamageConfig {
        if (resetTimeout <= 0) resetTimeout = ConfigDefaults.DEFAULT_RESET_TIMEOUT;
        if (maxTrailCount < 0) maxTrailCount = ConfigDefaults.DEFAULT_MAX_TRAIL_COUNT;
        if (baseFontSize <= 0) baseFontSize = ConfigDefaults.DEFAULT_BASE_FONT_SIZE;
        if (sizeOffsetPerThousand < 0) {
            sizeOffsetPerThousand = ConfigDefaults.DEFAULT_SIZE_OFFSET_PER_THOUSAND;
        }
        if (sizeOffsetMax < baseFontSize) {
            sizeOffsetMax = ConfigDefaults.DEFAULT_SIZE_OFFSET_MAX;
        }
        if (selectors == null) {
            selectors = defaultSelectors();
        }
        if (bounceScalePeak <= 1.0) {
            bounceScalePeak = ConfigDefaults.DEFAULT_BOUNCE_SCALE_PEAK;
        }
    }

    /** Returns config with all default values. */
    public static TotalDamageConfig defaults() {
        return new TotalDamageConfig(
                ConfigDefaults.DEFAULT_TOTAL_DAMAGE_ENABLED,
                ConfigDefaults.DEFAULT_RESET_TIMEOUT,
                ConfigDefaults.DEFAULT_MAX_TRAIL_COUNT,
                ConfigDefaults.DEFAULT_BASE_FONT_SIZE,
                ConfigDefaults.DEFAULT_SIZE_OFFSET_PER_THOUSAND,
                ConfigDefaults.DEFAULT_SIZE_OFFSET_MAX,
                defaultSelectors(),
                ConfigDefaults.DEFAULT_TOTAL_DAMAGE_POSITION_X,
                ConfigDefaults.DEFAULT_TOTAL_DAMAGE_POSITION_Y,
                ConfigDefaults.DEFAULT_ANIM_ENTRY_ENABLED,
                ConfigDefaults.DEFAULT_ANIM_EXIT_ENABLED,
                ConfigDefaults.DEFAULT_ANIM_BOUNCE_ENABLED,
                ConfigDefaults.DEFAULT_ANIM_TRAIL_ENTRY_ENABLED,
                ConfigDefaults.DEFAULT_ANIM_TRAIL_EXIT_ENABLED,
                ConfigDefaults.DEFAULT_BOUNCE_SCALE_PEAK);
    }

    private static Map<String, JsonObject> defaultSelectors() {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        JsonObject commonEntry = new JsonObject();
        commonEntry.addProperty("style", ConfigDefaults.DEFAULT_STYLE_NAME);
        map.put("common", commonEntry);
        return Collections.unmodifiableMap(map);
    }
}
