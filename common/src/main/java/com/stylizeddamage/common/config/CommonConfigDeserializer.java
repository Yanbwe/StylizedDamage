package com.stylizeddamage.common.config;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom Gson deserializer for {@link CommonConfig}.
 * Handles missing fields, invalid values, and nested object parsing
 * with fallback to defaults defined in {@link ConfigDefaults}.
 * <p>
 * Stateless and thread-safe — safe for use with a shared Gson instance.
 */
final class CommonConfigDeserializer implements JsonDeserializer<CommonConfig> {

    @Override
    public CommonConfig deserialize(JsonElement json, Type typeOfT,
                                     JsonDeserializationContext context) {
        JsonObject obj = json.getAsJsonObject();

        // Parse selectors: keep raw JSON for later selector subsystem processing
        Map<String, JsonObject> selectors = parseSelectors(obj);

        // Parse displayFilter nested object with defaults
        DisplayFilterConfig displayFilter = parseDisplayFilter(obj);

        // Parse distance scale from flat fields (validated in constructor)
        DistanceScaleConfig distanceScale = parseDistanceScale(obj);

        // Parse totalDamage nested object
        TotalDamageConfig totalDamage = parseTotalDamage(obj);

        // Parse displayOpacity
        DisplayOpacityConfig displayOpacity = parseDisplayOpacity(obj);

        // Parse simple top-level fields with validation
        double minDamageDisplay = getDouble(obj, "minDamageDisplay",
                ConfigDefaults.DEFAULT_MIN_DAMAGE_DISPLAY);
        if (minDamageDisplay < 0) minDamageDisplay = ConfigDefaults.DEFAULT_MIN_DAMAGE_DISPLAY;

        int maxActiveNumbers = getInt(obj, "maxActiveNumbers",
                ConfigDefaults.DEFAULT_MAX_ACTIVE_NUMBERS);
        if (maxActiveNumbers < 0) maxActiveNumbers = ConfigDefaults.DEFAULT_MAX_ACTIVE_NUMBERS;

        boolean showHealing = getBoolean(obj, "showHealing",
                ConfigDefaults.DEFAULT_SHOW_HEALING);

        boolean showAbsorption = getBoolean(obj, "showAbsorption",
                ConfigDefaults.DEFAULT_SHOW_ABSORPTION);

        double maxDisplayDistance = getDouble(obj, "maxDisplayDistance",
                ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE);
        if (maxDisplayDistance <= 0) {
            maxDisplayDistance = ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE;
        }

        return new CommonConfig(selectors, displayFilter, minDamageDisplay,
                maxActiveNumbers, showHealing, showAbsorption, distanceScale,
                maxDisplayDistance, totalDamage, displayOpacity);
    }

    // ── Selector parsing (raw storage) ─────────────────────────────

    private Map<String, JsonObject> parseSelectors(JsonObject root) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        if (root.has("selectors")) {
            JsonObject selObj = root.getAsJsonObject("selectors");
            for (Map.Entry<String, JsonElement> entry : selObj.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    result.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        }
        if (result.isEmpty()) {
            // Provide default selector structure
            return defaultSelectors();
        }
        return result;
    }

    private static Map<String, JsonObject> defaultSelectors() {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        JsonObject branch = new JsonObject();
        JsonObject common = new JsonObject();
        common.addProperty("match", "*");
        common.addProperty("style", ConfigDefaults.DEFAULT_STYLE_NAME);
        branch.add("common", common);
        map.put("common", branch);
        return map;
    }

    // ── Display filter parsing ─────────────────────────────────────

    private DisplayFilterConfig parseDisplayFilter(JsonObject root) {
        if (!root.has("displayFilter")) {
            return defaultDisplayFilter();
        }

        JsonObject df = root.getAsJsonObject("displayFilter");
        String mode = getString(df, "mode", ConfigDefaults.DEFAULT_FILTER_MODE);
        boolean hideSelfDamage = getBoolean(df, "hideSelfDamage",
                ConfigDefaults.DEFAULT_HIDE_SELF_DAMAGE);

        DisplayFilterConfig.FilterTargetConfig bySource =
                parseFilterTarget(df, "bySource", true);
        DisplayFilterConfig.FilterTargetConfig byTarget =
                parseFilterTarget(df, "byTarget", false);

        return new DisplayFilterConfig(mode, hideSelfDamage, bySource, byTarget);
    }

    private DisplayFilterConfig.FilterTargetConfig parseFilterTarget(
            JsonObject parent, String key, boolean isBySource) {
        if (!parent.has(key)) {
            return isBySource
                    ? DisplayFilterConfig.FilterTargetConfig.defaultBySource()
                    : DisplayFilterConfig.FilterTargetConfig.defaultByTarget();
        }

        JsonObject ft = parent.getAsJsonObject(key);

        DisplayFilterConfig.FilterPlayerConfig player =
                parseFilterPlayer(ft, isBySource);
        DisplayFilterConfig.FilterMobConfig mob =
                parseFilterMob(ft, isBySource);
        boolean other = isBySource
                ? getBoolean(ft, "other", ConfigDefaults.DEFAULT_BYSOURCE_OTHER)
                : getBoolean(ft, "other", ConfigDefaults.DEFAULT_BYTARGET_OTHER);

        return new DisplayFilterConfig.FilterTargetConfig(player, mob, other);
    }

    private DisplayFilterConfig.FilterPlayerConfig parseFilterPlayer(
            JsonObject parent, boolean isBySource) {
        if (!parent.has("player")) {
            return isBySource
                    ? new DisplayFilterConfig.FilterPlayerConfig(
                            ConfigDefaults.DEFAULT_BYSOURCE_PLAYER_SAME_TEAM,
                            ConfigDefaults.DEFAULT_BYSOURCE_PLAYER_OTHER_TEAM)
                    : new DisplayFilterConfig.FilterPlayerConfig(
                            ConfigDefaults.DEFAULT_BYTARGET_PLAYER_SAME_TEAM,
                            ConfigDefaults.DEFAULT_BYTARGET_PLAYER_OTHER_TEAM);
        }
        JsonObject p = parent.getAsJsonObject("player");
        boolean sameTeam = isBySource
                ? getBoolean(p, "sameTeam", ConfigDefaults.DEFAULT_BYSOURCE_PLAYER_SAME_TEAM)
                : getBoolean(p, "sameTeam", ConfigDefaults.DEFAULT_BYTARGET_PLAYER_SAME_TEAM);
        boolean otherTeam = isBySource
                ? getBoolean(p, "otherTeam", ConfigDefaults.DEFAULT_BYSOURCE_PLAYER_OTHER_TEAM)
                : getBoolean(p, "otherTeam", ConfigDefaults.DEFAULT_BYTARGET_PLAYER_OTHER_TEAM);
        return new DisplayFilterConfig.FilterPlayerConfig(sameTeam, otherTeam);
    }

    private DisplayFilterConfig.FilterMobConfig parseFilterMob(
            JsonObject parent, boolean isBySource) {
        if (!parent.has("mob")) {
            return isBySource
                    ? new DisplayFilterConfig.FilterMobConfig(
                            ConfigDefaults.DEFAULT_BYSOURCE_MOB_HOSTILE,
                            ConfigDefaults.DEFAULT_BYSOURCE_MOB_PASSIVE)
                    : new DisplayFilterConfig.FilterMobConfig(
                            ConfigDefaults.DEFAULT_BYTARGET_MOB_HOSTILE,
                            ConfigDefaults.DEFAULT_BYTARGET_MOB_PASSIVE);
        }
        JsonObject m = parent.getAsJsonObject("mob");
        boolean hostile = isBySource
                ? getBoolean(m, "hostile", ConfigDefaults.DEFAULT_BYSOURCE_MOB_HOSTILE)
                : getBoolean(m, "hostile", ConfigDefaults.DEFAULT_BYTARGET_MOB_HOSTILE);
        boolean passive = isBySource
                ? getBoolean(m, "passive", ConfigDefaults.DEFAULT_BYSOURCE_MOB_PASSIVE)
                : getBoolean(m, "passive", ConfigDefaults.DEFAULT_BYTARGET_MOB_PASSIVE);
        return new DisplayFilterConfig.FilterMobConfig(hostile, passive);
    }

    private static DisplayFilterConfig defaultDisplayFilter() {
        return new DisplayFilterConfig(
                ConfigDefaults.DEFAULT_FILTER_MODE,
                ConfigDefaults.DEFAULT_HIDE_SELF_DAMAGE,
                DisplayFilterConfig.FilterTargetConfig.defaultBySource(),
                DisplayFilterConfig.FilterTargetConfig.defaultByTarget());
    }

    // ── Distance scale parsing ─────────────────────────────────────

    private DistanceScaleConfig parseDistanceScale(JsonObject root) {
        double segment = getDouble(root, "distanceScaleSegment",
                ConfigDefaults.DEFAULT_DISTANCE_SCALE_SEGMENT);
        double factor = getDouble(root, "distanceScaleFactor",
                ConfigDefaults.DEFAULT_DISTANCE_SCALE_FACTOR);
        double min = getDouble(root, "distanceScaleMin",
                ConfigDefaults.DEFAULT_DISTANCE_SCALE_MIN);
        // maxDisplayDistance is stored at top-level; use a placeholder here
        double maxDist = getDouble(root, "maxDisplayDistance",
                ConfigDefaults.DEFAULT_MAX_DISPLAY_DISTANCE);
        return new DistanceScaleConfig(segment, factor, min, maxDist);
    }

    // ── Total damage parsing ───────────────────────────────────────

    private TotalDamageConfig parseTotalDamage(JsonObject root) {
        if (!root.has("totalDamage")) {
            return TotalDamageConfig.defaults();
        }

        JsonObject td = root.getAsJsonObject("totalDamage");
        boolean enabled = getBoolean(td, "enabled",
                ConfigDefaults.DEFAULT_TOTAL_DAMAGE_ENABLED);
        int resetTimeout = getInt(td, "resetTimeout",
                ConfigDefaults.DEFAULT_RESET_TIMEOUT);
        int maxTrailCount = getInt(td, "maxTrailCount",
                ConfigDefaults.DEFAULT_MAX_TRAIL_COUNT);
        double baseFontSize = getDouble(td, "baseFontSize",
                ConfigDefaults.DEFAULT_BASE_FONT_SIZE);
        double sizeOffsetPerThousand = getDouble(td, "sizeOffsetPerThousand",
                ConfigDefaults.DEFAULT_SIZE_OFFSET_PER_THOUSAND);
        double sizeOffsetMax = getDouble(td, "sizeOffsetMax",
                ConfigDefaults.DEFAULT_SIZE_OFFSET_MAX);

        // Parse position
        double positionX = getDouble(td, "positionX",
                ConfigDefaults.DEFAULT_TOTAL_DAMAGE_POSITION_X);
        double positionY = getDouble(td, "positionY",
                ConfigDefaults.DEFAULT_TOTAL_DAMAGE_POSITION_Y);

        // Parse animation toggles
        boolean enableEntryAnimation = getBoolean(td, "enableEntryAnimation",
                ConfigDefaults.DEFAULT_ANIM_ENTRY_ENABLED);
        boolean enableExitAnimation = getBoolean(td, "enableExitAnimation",
                ConfigDefaults.DEFAULT_ANIM_EXIT_ENABLED);
        boolean enableBounceAnimation = getBoolean(td, "enableBounceAnimation",
                ConfigDefaults.DEFAULT_ANIM_BOUNCE_ENABLED);
        boolean enableTrailEntryAnimation = getBoolean(td, "enableTrailEntryAnimation",
                ConfigDefaults.DEFAULT_ANIM_TRAIL_ENTRY_ENABLED);
        boolean enableTrailExitAnimation = getBoolean(td, "enableTrailExitAnimation",
                ConfigDefaults.DEFAULT_ANIM_TRAIL_EXIT_ENABLED);
        double bounceScalePeak = getDouble(td, "bounceScalePeak",
                ConfigDefaults.DEFAULT_BOUNCE_SCALE_PEAK);

        // Parse totalDamage selectors (raw storage)
        Map<String, JsonObject> tdSelectors = new LinkedHashMap<>();
        if (td.has("selectors")) {
            JsonObject tdSel = td.getAsJsonObject("selectors");
            for (Map.Entry<String, JsonElement> entry : tdSel.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    tdSelectors.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        }
        if (tdSelectors.isEmpty()) {
            tdSelectors = TotalDamageConfig.defaults().selectors();
        }

        return new TotalDamageConfig(enabled, resetTimeout, maxTrailCount,
                baseFontSize, sizeOffsetPerThousand, sizeOffsetMax, tdSelectors,
                positionX, positionY,
                enableEntryAnimation, enableExitAnimation, enableBounceAnimation,
                enableTrailEntryAnimation, enableTrailExitAnimation,
                bounceScalePeak);
    }

    // ── Safe JSON value extraction helpers ─────────────────────────

    private static double getDouble(JsonObject obj, String key, double defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement elem = obj.get(key);
        if (elem.isJsonNull()) return defaultValue;
        try {
            return elem.getAsDouble();
        } catch (NumberFormatException | IllegalStateException e) {
            return defaultValue;
        }
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement elem = obj.get(key);
        if (elem.isJsonNull()) return defaultValue;
        try {
            return elem.getAsInt();
        } catch (NumberFormatException | IllegalStateException e) {
            return defaultValue;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement elem = obj.get(key);
        if (elem.isJsonNull()) return defaultValue;
        try {
            return elem.getAsBoolean();
        } catch (IllegalStateException e) {
            return defaultValue;
        }
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement elem = obj.get(key);
        if (elem.isJsonNull()) return defaultValue;
        try {
            String s = elem.getAsString();
            return (s == null || s.isBlank()) ? defaultValue : s;
        } catch (IllegalStateException e) {
            return defaultValue;
        }
    }

    // ── DisplayOpacity parsing ──────────────────────────────────────

    private DisplayOpacityConfig parseDisplayOpacity(JsonObject root) {
        if (!root.has("displayOpacity")) {
            return DisplayOpacityConfig.defaults();
        }
        JsonObject op = root.getAsJsonObject("displayOpacity");

        double player = getDouble(op, "player", 1.0);
        double mobHostile = getDouble(op, "mobHostile", 1.0);
        double mobPassive = getDouble(op, "mobPassive", 1.0);
        double other = getDouble(op, "other", 1.0);

        return new DisplayOpacityConfig(player, mobHostile, mobPassive, other);
    }
}
