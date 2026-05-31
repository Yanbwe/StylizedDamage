package com.stylizeddamage.common.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level configuration record parsed from {@code common.json}.
 * Immutable — all fields are final. Missing/invalid JSON fields
 * fall back to the defaults defined in {@link ConfigDefaults}.
 * <p>
 * The {@code selectors} field stores raw JSON for later processing
 * by the selector subsystem (subtask in a later step).
 *
 * @param selectors           raw interval→branch selectors map
 * @param displayFilter       rules for which damage events produce floating numbers
 * @param minDamageDisplay    minimum damage value that produces a number
 * @param maxActiveNumbers    maximum concurrent floating numbers on screen
 * @param showHealing         whether healing events produce numbers
 * @param showAbsorption      whether absorption changes produce numbers
 * @param distanceScale       distance-based font scaling parameters
 * @param maxDisplayDistance  maximum distance (blocks) for any display
 * @param totalDamage         total-damage HUD panel configuration
 */
public record CommonConfig(
        Map<String, JsonObject> selectors,
        DisplayFilterConfig displayFilter,
        double minDamageDisplay,
        int maxActiveNumbers,
        boolean showHealing,
        boolean showAbsorption,
        DistanceScaleConfig distanceScale,
        double maxDisplayDistance,
        TotalDamageConfig totalDamage,
        DisplayOpacityConfig displayOpacity) {

    /** Compact constructor — validates and provides defaults. */
    public CommonConfig {
        if (selectors == null) {
            selectors = defaultSelectors();
        }
        if (displayFilter == null) {
            displayFilter = defaultDisplayFilter();
        }
        if (minDamageDisplay < 0) minDamageDisplay = ConfigDefaults.DEFAULT_MIN_DAMAGE_DISPLAY;
        if (maxActiveNumbers < 0) maxActiveNumbers = ConfigDefaults.DEFAULT_MAX_ACTIVE_NUMBERS;
        if (distanceScale == null) {
            distanceScale = DistanceScaleConfig.defaults();
        }
        if (maxDisplayDistance <= 0) {
            maxDisplayDistance = ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE;
        }
        if (totalDamage == null) {
            totalDamage = TotalDamageConfig.defaults();
        }
        if (displayOpacity == null) {
            displayOpacity = DisplayOpacityConfig.defaults();
        }
    }

    /** Creates a fully-default configuration. */
    static CommonConfig createDefault() {
        return new CommonConfig(
                defaultSelectors(),
                defaultDisplayFilter(),
                ConfigDefaults.DEFAULT_MIN_DAMAGE_DISPLAY,
                ConfigDefaults.DEFAULT_MAX_ACTIVE_NUMBERS,
                ConfigDefaults.DEFAULT_SHOW_HEALING,
                ConfigDefaults.DEFAULT_SHOW_ABSORPTION,
                DistanceScaleConfig.defaults(),
                ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE,
                TotalDamageConfig.defaults(),
                DisplayOpacityConfig.defaults());
    }

    private static Map<String, JsonObject> defaultSelectors() {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        JsonObject commonBranch = new JsonObject();
        JsonArray rules = new JsonArray();

        addSelectorRule(rules, "absorption", "absorption");
        addSelectorRule(rules, "heal", "heal");
        addSelectorRule(rules, "#minecraft:is_fire", "fire");
        addSelectorRule(rules, "kill", "kill");
        addSelectorRule(rules, "#minecraft:bypasses_armor", "magic");
        addSelectorRule(rules, "*", ConfigDefaults.DEFAULT_STYLE_NAME);

        commonBranch.add("common", rules);
        map.put("common", commonBranch);
        return Collections.unmodifiableMap(map);
    }

    private static void addSelectorRule(JsonArray array, String match, String style) {
        JsonObject rule = new JsonObject();
        rule.addProperty("match", match);
        rule.addProperty("style", style);
        array.add(rule);
    }

    private static DisplayFilterConfig defaultDisplayFilter() {
        return new DisplayFilterConfig(
                ConfigDefaults.DEFAULT_FILTER_MODE,
                ConfigDefaults.DEFAULT_HIDE_SELF_DAMAGE,
                DisplayFilterConfig.FilterTargetConfig.defaultBySource(),
                DisplayFilterConfig.FilterTargetConfig.defaultByTarget());
    }
}
