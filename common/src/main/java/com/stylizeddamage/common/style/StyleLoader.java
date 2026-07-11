package com.stylizeddamage.common.style;

import com.google.gson.*;
import com.stylizeddamage.common.animation.*;
import com.stylizeddamage.common.style.color.Color;
import com.stylizeddamage.common.style.color.ColorParser;
import com.stylizeddamage.common.style.color.ColorSource;
import com.stylizeddamage.common.util.EasingCurve;
import com.stylizeddamage.common.util.RandomValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Loads, holds, and hot‑reloads style definitions from {@code .json} files.
 *
 * <h3>Directory layout</h3>
 * <pre>{@code
 *   config/stylizeddamage/styles/
 *     default.json      → style name "default"
 *     fire.json         → style name "fire"
 *     critical.json     → style name "critical"
 * }</pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   StyleLoader loader = new StyleLoader(stylesDir);
 *   Map<String, Style> styles = loader.load();
 *   Style def = styles.get("default");
 *
 *   // Programmatic registration (API use)
 *   loader.register("my_style", myStyle);
 * }</pre>
 *
 * <p>Thread‑safety: the returned {@code Map} from {@link #load()} is an
 * unmodifiable snapshot. Call {@link #load()} again to pick up file changes.
 * The {@link #register(String, Style)} method is synchronized and creates
 * a fresh immutable map internally.
 */
public final class StyleLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("StylizedDamage");

    private final Path stylesDir;
    private final Gson gson;
    private volatile Map<String, Style> styles;

    /**
     * Creates a loader bound to a styles directory.
     *
     * @param stylesDir the directory containing {@code .json} style files
     * @throws NullPointerException if {@code stylesDir} is null
     */
    public StyleLoader(Path stylesDir) {
        this.stylesDir = Objects.requireNonNull(stylesDir, "stylesDir must not be null");
        this.gson = new GsonBuilder()

                .create();
        this.styles = Collections.emptyMap();
    }

    // ── Loading ─────────────────────────────────────────────────────

    /**
     * Scans the styles directory for {@code .json} files and parses them
     * into an immutable {@code name → Style} map.
     *
     * <p>File names serve as style names (the {@code .json} extension is
     * stripped). If the styles directory does not exist or contains no
     * valid files, an empty map is returned.
     *
     * <p>Individual file parse failures are logged and skipped — they do
     * not prevent other valid styles from loading.
     *
     * @return an unmodifiable snapshot of all loaded styles; never null
     */
    public Map<String, Style> load() {
        if (!Files.isDirectory(stylesDir)) {
            LOGGER.warn("Styles directory does not exist: {} — no styles loaded.", stylesDir);
            Map<String, Style> empty = Collections.emptyMap();
            this.styles = empty;
            return empty;
        }

        Map<String, Style> map = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(stylesDir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        name = name.substring(0, name.length() - 5); // strip ".json"
                        if (name.isEmpty()) {
                            return;
                        }
                        try {
                            Style style = parseStyleFile(path);
                            map.put(name, style);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse style \"{}\": {} — skipping.", name, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to list styles directory: {}", e.getMessage());
        }

        Map<String, Style> immutable = Collections.unmodifiableMap(new LinkedHashMap<>(map));
        this.styles = immutable;
        return immutable;
    }

    /**
     * Returns the most recently loaded styles snapshot.
     * Call {@link #load()} first; otherwise returns an empty map.
     */
    public Map<String, Style> getStyles() {
        return styles;
    }

    // ── Programmatic registration ───────────────────────────────────

    /**
     * Registers (or overwrites) a style programmatically.
     *
     * <p>This is the primary entry point for the API system. Registered
     * styles take precedence over file‑loaded styles when names collide
     * (as required by the API priority rules).
     *
     * @param name  the style name (must not be null or blank)
     * @param style the style definition (must not be null)
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if name is blank
     */
    public synchronized void register(String name, Style style) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(style, "style must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Style name must not be blank");
        }

        Map<String, Style> mutable = new LinkedHashMap<>(this.styles);
        mutable.put(name, style);
        this.styles = Collections.unmodifiableMap(mutable);
    }

    /**
     * Returns the styles directory path (for use by other subsystems).
     */
    public Path getStylesDir() {
        return stylesDir;
    }

    // ── Single‑file parsing ─────────────────────────────────────────

    private Style parseStyleFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json == null) {
                throw new IOException("File contains no valid JSON: " + path);
            }
            return parseStyle(json);
        }
    }

    // ── Style JSON → Style record ───────────────────────────────────

    /**
     * Parses a style {@link JsonObject} into a {@link Style} record.
     * Missing fields fall back to defaults defined in {@link StyleDefaults}.
     */
    static Style parseStyle(JsonObject json) {
        ColorSource color = parseColor(json, "color", StyleDefaults.DEFAULT_COLOR_HEX);
        float fontSize = getFloat(json, "fontSize", StyleDefaults.DEFAULT_FONT_SIZE);
        FontStyle fontStyle = parseFontStyle(json);
        boolean shadow = getBoolean(json, "shadow", StyleDefaults.DEFAULT_SHADOW);
        Color outlineColor = parseNullableColor(json, "outlineColor");
        Color backgroundColor = parseNullableColor(json, "backgroundColor");
        String sound = getNullableString(json, "sound");
        String prefix = getString(json, "prefix", StyleDefaults.DEFAULT_PREFIX);
        String suffix = getString(json, "suffix", StyleDefaults.DEFAULT_SUFFIX);
        String icon = getNullableString(json, "icon");
        String iconPosition = getString(json, "iconPosition", StyleDefaults.DEFAULT_ICON_POSITION);
        double iconOffsetX = getDouble(json, "iconOffsetX", StyleDefaults.DEFAULT_ICON_OFFSET_X);
        double iconOffsetY = getDouble(json, "iconOffsetY", StyleDefaults.DEFAULT_ICON_OFFSET_Y);
        String killText = getNullableString(json, "killText");
        boolean bypassDisplayOpacity = getBoolean(json, "bypassDisplayOpacity",
                StyleDefaults.DEFAULT_BYPASS_DISPLAY_OPACITY);
        AnimationConfig animation = parseAnimation(json);
        DamageScaleConfig damageScale = parseDamageScale(json);
        int decimalPlaces = getInt(json, "decimalPlaces", StyleDefaults.DEFAULT_DECIMAL_PLACES);

        return new Style(color, fontSize, fontStyle, shadow, outlineColor,
                backgroundColor, sound, prefix, suffix, icon, iconPosition,
                iconOffsetX, iconOffsetY, killText, bypassDisplayOpacity,
                animation, damageScale, decimalPlaces);
    }

    // ── Field helpers ───────────────────────────────────────────────

    private static ColorSource parseColor(JsonObject json, String key, String defaultHex) {
        if (!json.has(key)) {
            return ColorParser.parse(defaultHex);
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull()) {
            return ColorParser.parse(defaultHex);
        }
        try {
            return ColorParser.parse(elem.getAsString());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid color value for \"{}\": {} — using default.", key, e.getMessage());
            return ColorParser.parse(defaultHex);
        }
    }

    /**
     * Parses a nullable colour field (outlineColor / backgroundColor).
     * Returns null when the JSON field is absent or explicitly null.
     * For outlineColor, falls back to the default outline colour on parse failure.
     */
    private static Color parseNullableColor(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull()) {
            return null;
        }
        try {
            ColorSource cs = ColorParser.parse(elem.getAsString());
            // Resolve immediately — outline/background don't support rainbow
            if (cs instanceof Color c) {
                return c;
            }
            // If it's rainbow, resolve at tick 0 as a fallback
            return cs.resolve(0f, 0);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid colour for \"{}\": {} — skipping.", key, e.getMessage());
            return null;
        }
    }

    private static FontStyle parseFontStyle(JsonObject json) {
        if (!json.has("fontStyle")) {
            return StyleDefaults.DEFAULT_FONT_STYLE;
        }
        JsonElement elem = json.get("fontStyle");
        if (elem.isJsonNull()) {
            return StyleDefaults.DEFAULT_FONT_STYLE;
        }
        return FontStyle.fromString(elem.getAsString());
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull()) {
            return defaultValue;
        }
        return elem.getAsString();
    }

    private static String getNullableString(JsonObject json, String key) {
        return getString(json, key, null);
    }

    private static float getFloat(JsonObject json, String key, float defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return elem.getAsFloat();
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return elem.getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return elem.getAsInt();
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ── Animation parsing ───────────────────────────────────────────

    private static AnimationConfig parseAnimation(JsonObject json) {
        if (!json.has("animation")) {
            return Style.createDefault().animation();
        }
        JsonElement elem = json.get("animation");
        if (!elem.isJsonObject()) {
            return Style.createDefault().animation();
        }
        JsonObject anim = elem.getAsJsonObject();

        int hold = anim.has("hold") ? anim.get("hold").getAsInt() : 0;

        PositionConfig position = parsePosition(anim.has("position")
                ? anim.getAsJsonObject("position") : new JsonObject());
        SizeConfig size = parseSize(anim.has("size")
                ? anim.getAsJsonObject("size") : new JsonObject());
        BrightnessConfig brightness = parseBrightness(anim.has("brightness")
                ? anim.getAsJsonObject("brightness") : new JsonObject());
        OpacityConfig opacity = parseOpacity(anim.has("opacity")
                ? anim.getAsJsonObject("opacity") : new JsonObject());

        return new AnimationConfig(hold, position, size, brightness, opacity);
    }

    // ── Position parsing ────────────────────────────────────────────

    private static PositionConfig parsePosition(JsonObject json) {
        // Enter phase (with embedded startOffset / targetOffset)
        JsonObject enterJson = json.has("enter") && json.get("enter").isJsonObject()
                ? json.getAsJsonObject("enter") : new JsonObject();
        PhaseConfig enterPhase = parsePhase(enterJson);
        OffsetValue enterStartOffset = parseOffsetValue(enterJson.has("startOffset")
                ? enterJson.get("startOffset") : null, OffsetValue.XY.FIXED_ZERO);
        OffsetValue enterTargetOffset = parseOffsetValue(enterJson.has("targetOffset")
                ? enterJson.get("targetOffset") : null, OffsetValue.XY.FIXED_ZERO);

        // Exit phase (with embedded targetOffset)
        JsonObject exitJson = json.has("exit") && json.get("exit").isJsonObject()
                ? json.getAsJsonObject("exit") : new JsonObject();
        PhaseConfig exitPhase = parsePhase(exitJson);
        OffsetValue exitTargetOffset = parseOffsetValue(exitJson.has("targetOffset")
                ? exitJson.get("targetOffset") : null, OffsetValue.XY.FIXED_ZERO);

        return new PositionConfig(enterPhase, exitPhase,
                enterStartOffset, enterTargetOffset, exitTargetOffset);
    }

    // ── Size parsing ────────────────────────────────────────────────

    private static SizeConfig parseSize(JsonObject json) {
        JsonObject enterJson = json.has("enter") && json.get("enter").isJsonObject()
                ? json.getAsJsonObject("enter") : new JsonObject();
        PhaseConfig enterPhase = parsePhase(enterJson);
        RandomValue enterStartOffset = parseRandomValue(
                enterJson.has("startOffset") ? enterJson.get("startOffset") : null);
        RandomValue enterTargetOffset = parseRandomValue(
                enterJson.has("targetOffset") ? enterJson.get("targetOffset") : null);

        JsonObject exitJson = json.has("exit") && json.get("exit").isJsonObject()
                ? json.getAsJsonObject("exit") : new JsonObject();
        PhaseConfig exitPhase = parsePhase(exitJson);
        RandomValue exitTargetOffset = parseRandomValue(
                exitJson.has("targetOffset") ? exitJson.get("targetOffset") : null);

        return new SizeConfig(enterPhase, exitPhase,
                enterStartOffset, enterTargetOffset, exitTargetOffset);
    }

    // ── Brightness parsing ──────────────────────────────────────────

    private static BrightnessConfig parseBrightness(JsonObject json) {
        JsonObject enterJson = json.has("enter") && json.get("enter").isJsonObject()
                ? json.getAsJsonObject("enter") : new JsonObject();
        PhaseConfig enterPhase = parsePhase(enterJson);
        RandomValue enterStartOffset = parseRandomValue(
                enterJson.has("startOffset") ? enterJson.get("startOffset") : null);

        JsonObject exitJson = json.has("exit") && json.get("exit").isJsonObject()
                ? json.getAsJsonObject("exit") : new JsonObject();
        PhaseConfig exitPhase = parsePhase(exitJson);
        RandomValue exitTargetOffset = parseRandomValue(
                exitJson.has("targetOffset") ? exitJson.get("targetOffset") : null);

        return new BrightnessConfig(enterPhase, exitPhase,
                enterStartOffset, exitTargetOffset);
    }

    // ── Opacity parsing ─────────────────────────────────────────────

    private static OpacityConfig parseOpacity(JsonObject json) {
        JsonObject enterJson = json.has("enter") && json.get("enter").isJsonObject()
                ? json.getAsJsonObject("enter") : new JsonObject();
        PhaseConfig enterPhase = parsePhase(enterJson);
        RandomValue enterStartOpacity = parseOpacityValue(
                enterJson.has("startOpacity") ? enterJson.get("startOpacity") : null);
        RandomValue enterTargetOpacity = parseOpacityValue(
                enterJson.has("targetOpacity") ? enterJson.get("targetOpacity") : null);

        JsonObject exitJson = json.has("exit") && json.get("exit").isJsonObject()
                ? json.getAsJsonObject("exit") : new JsonObject();
        PhaseConfig exitPhase = parsePhase(exitJson);
        RandomValue exitTargetOpacity = parseOpacityValue(
                exitJson.has("targetOpacity") ? exitJson.get("targetOpacity") : null);

        return new OpacityConfig(enterPhase, exitPhase,
                enterStartOpacity, enterTargetOpacity, exitTargetOpacity);
    }

    // ── PhaseConfig parsing ─────────────────────────────────────────

    private static PhaseConfig parsePhase(JsonObject json) {
        if (json == null || json.entrySet().isEmpty()) {
            return PhaseConfig.NONE;
        }

        PhaseType type = parsePhaseType(json);
        if (type == PhaseType.NONE) {
            return PhaseConfig.NONE;
        }

        int duration = json.has("duration") ? json.get("duration").getAsInt() : 0;
        EasingCurve easing = json.has("easing")
                ? EasingCurve.fromJSON(json.getAsJsonObject("easing"))
                : EasingCurve.EASE_OUT;

        return new PhaseConfig(type, duration, easing);
    }

    private static PhaseType parsePhaseType(JsonObject json) {
        if (!json.has("type")) {
            return PhaseType.NONE;
        }
        JsonElement typeElem = json.get("type");
        if (typeElem.isJsonNull()) {
            return PhaseType.NONE;
        }
        String typeStr = typeElem.getAsString();
        if ("normal".equalsIgnoreCase(typeStr)) {
            return PhaseType.NORMAL;
        }
        return PhaseType.NONE;
    }

    // ── OffsetValue parsing ─────────────────────────────────────────

    /**
     * Parses an {@link OffsetValue} from its JSON representation.
     *
     * <p>Accepted shapes:
     * <ul>
     *   <li>Absent / null → returns {@code defaultValue}</li>
     *   <li>{@code {"type": "xy", "x": <RandomValue>, "y": <RandomValue>}}</li>
     *   <li>{@code {"type": "direction", "angle": <RandomValue>, "distance": <RandomValue>}}</li>
     * </ul>
     */
    private static OffsetValue parseOffsetValue(JsonElement elem, OffsetValue defaultValue) {
        if (elem == null || elem.isJsonNull() || !elem.isJsonObject()) {
            return defaultValue;
        }
        JsonObject obj = elem.getAsJsonObject();
        if (!obj.has("type")) {
            return defaultValue;
        }
        String type = obj.get("type").getAsString();
        if ("xy".equalsIgnoreCase(type)) {
            RandomValue x = parseRandomValue(obj.has("x") ? obj.get("x") : null);
            RandomValue y = parseRandomValue(obj.has("y") ? obj.get("y") : null);
            return new OffsetValue.XY(x, y);
        }
        if ("direction".equalsIgnoreCase(type)) {
            RandomValue angle = parseRandomValue(obj.has("angle") ? obj.get("angle") : null);
            RandomValue distance = parseRandomValue(obj.has("distance") ? obj.get("distance") : null);
            return new OffsetValue.Direction(angle, distance);
        }
        return defaultValue;
    }

    // ── RandomValue parsing ─────────────────────────────────────────

    private static RandomValue parseRandomValue(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return RandomValue.ZERO;
        }
        try {
            return RandomValue.fromJSON(elem);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid RandomValue: {} — using zero.", e.getMessage());
            return RandomValue.ZERO;
        }
    }

    /**
     * Parses an opacity value (expected in [0, 1]) from JSON.
     * Uses {@link RandomValue#fromJSON(JsonElement)} but clamps the base.
     */
    private static RandomValue parseOpacityValue(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return RandomValue.ZERO;
        }
        try {
            RandomValue rv = RandomValue.fromJSON(elem);
            // Clamp base to valid opacity range, but preserve random deltas
            double clampedBase = clamp(rv.base(), 0.0, 1.0);
            if (clampedBase == rv.base()) {
                return rv;
            }
            return new RandomValue(clampedBase, rv.randomMin(), rv.randomMax());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid opacity value: {} — using zero.", e.getMessage());
            return RandomValue.ZERO;
        }
    }

    // ── DamageScale parsing ─────────────────────────────────────────

    private static DamageScaleConfig parseDamageScale(JsonObject json) {
        if (!json.has("damageScale")) {
            return DamageScaleConfig.defaults();
        }
        JsonElement elem = json.get("damageScale");
        if (!elem.isJsonObject()) {
            return DamageScaleConfig.defaults();
        }
        JsonObject ds = elem.getAsJsonObject();

        boolean enabled = getBoolean(ds, "enabled", StyleDefaults.DEFAULT_DAMAGE_SCALE_ENABLED);
        double baseFontSize = getDouble(ds, "baseFontSize", StyleDefaults.DEFAULT_DAMAGE_SCALE_BASE);
        double stepSize = getDouble(ds, "stepSize", StyleDefaults.DEFAULT_DAMAGE_SCALE_STEP);
        double sizeOffset = getDouble(ds, "sizeOffsetPerStep", StyleDefaults.DEFAULT_DAMAGE_SCALE_OFFSET);
        double maxSize = getDouble(ds, "maxSize", StyleDefaults.DEFAULT_DAMAGE_SCALE_MAX);
        double holdBase = getDouble(ds, "holdBase", StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_BASE);
        double holdOffset = getDouble(ds, "holdOffsetPerStep", StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_OFFSET);
        double holdMax = getDouble(ds, "holdMax", StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_MAX);

        return new DamageScaleConfig(enabled, baseFontSize, stepSize, sizeOffset, maxSize,
                holdBase, holdOffset, holdMax);
    }

    private static double getDouble(JsonObject json, String key, double defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return elem.getAsDouble();
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ── Math helpers ────────────────────────────────────────────────

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
