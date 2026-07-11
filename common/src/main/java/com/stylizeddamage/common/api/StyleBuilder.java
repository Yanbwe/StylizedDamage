package com.stylizeddamage.common.api;

import com.stylizeddamage.common.animation.AnimationConfig;
import com.stylizeddamage.common.style.DamageScaleConfig;
import com.stylizeddamage.common.style.FontStyle;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.style.StyleDefaults;
import com.stylizeddamage.common.style.StyleLoader;
import com.stylizeddamage.common.style.color.Color;
import com.stylizeddamage.common.style.color.ColorParser;
import com.stylizeddamage.common.style.color.ColorSource;

import java.util.Objects;

/**
 * Chainable builder for programmatically constructing a {@link Style}.
 *
 * <p>All fields default to sensible values (matching {@link StyleDefaults})
 * so that users only need to set properties they wish to override.
 * The builder is not reusable — once {@link #register()} is called the
 * style is committed to the {@link StyleLoader} and the builder should
 * be discarded.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * api.createStyle("my_style")
 *     .color("#FF4444")
 *     .fontSize(1.2f)
 *     .fontStyle("bold")
 *     .shadow(true)
 *     .outlineColor("#000000")
 *     .prefix("!")
 *     .suffix("\u2764")
 *     .register();
 * }</pre>
 */
public final class StyleBuilder {

    // ── StyleLoader reference ──────────────────────────────────────
    private final StyleLoader styleLoader;
    private final String name;

    // ── Mutable fields with defaults ────────────────────────────────
    private ColorSource color;
    private float fontSize;
    private FontStyle fontStyle;
    private boolean shadow;
    private Color outlineColor;
    private Color backgroundColor;
    private String sound;
    private String prefix;
    private String suffix;
    private String icon;
    private String iconPosition;
    private double iconOffsetX;
    private double iconOffsetY;
    private String killText;
    private boolean bypassDisplayOpacity;
    private AnimationConfig animation;
    private DamageScaleConfig damageScale;
    private int decimalPlaces;

    /**
     * Creates a builder bound to a style name and loader.
     * Called internally via {@link StylizedDamageAPI#createStyle(String)}.
     */
    StyleBuilder(String name, StyleLoader styleLoader) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.styleLoader = Objects.requireNonNull(styleLoader, "styleLoader must not be null");

        // ── Populate defaults ──────────────────────────────────────
        this.color = parseColorSafe(StyleDefaults.DEFAULT_COLOR_HEX);
        this.fontSize = StyleDefaults.DEFAULT_FONT_SIZE;
        this.fontStyle = StyleDefaults.DEFAULT_FONT_STYLE;
        this.shadow = StyleDefaults.DEFAULT_SHADOW;
        this.outlineColor = StyleDefaults.DEFAULT_OUTLINE_COLOR;
        this.backgroundColor = StyleDefaults.DEFAULT_BACKGROUND_COLOR;
        this.sound = StyleDefaults.DEFAULT_SOUND;
        this.prefix = StyleDefaults.DEFAULT_PREFIX;
        this.suffix = StyleDefaults.DEFAULT_SUFFIX;
        this.icon = StyleDefaults.DEFAULT_ICON;
        this.iconPosition = StyleDefaults.DEFAULT_ICON_POSITION;
        this.iconOffsetX = StyleDefaults.DEFAULT_ICON_OFFSET_X;
        this.iconOffsetY = StyleDefaults.DEFAULT_ICON_OFFSET_Y;
        this.killText = StyleDefaults.DEFAULT_KILL_TEXT;
        this.bypassDisplayOpacity = StyleDefaults.DEFAULT_BYPASS_DISPLAY_OPACITY;
        this.damageScale = DamageScaleConfig.defaults();
        this.decimalPlaces = StyleDefaults.DEFAULT_DECIMAL_PLACES;
    }

    // ── Setters (each returns this for chaining) ───────────────────

    /** Sets the primary colour as a hex string (e.g. {@code "#FF4444"}). */
    public StyleBuilder color(String hex) {
        this.color = ColorParser.parse(Objects.requireNonNull(hex, "color must not be null"));
        return this;
    }

    /** Sets the primary colour from an existing {@link ColorSource}. */
    public StyleBuilder color(ColorSource source) {
        this.color = Objects.requireNonNull(source, "color must not be null");
        return this;
    }

    /** Sets the base font size multiplier (must be positive). */
    public StyleBuilder fontSize(float size) {
        if (size <= 0) {
            throw new IllegalArgumentException("fontSize must be positive, got: " + size);
        }
        this.fontSize = size;
        return this;
    }

    /**
     * Sets the font style from a string.
     * Accepted values: {@code "normal"}, {@code "bold"}, {@code "italic"},
     * {@code "bold_italic"} (or {@code "bolditalic"}).
     */
    public StyleBuilder fontStyle(String style) {
        this.fontStyle = FontStyle.fromString(Objects.requireNonNull(style, "fontStyle must not be null"));
        return this;
    }

    /** Sets the font style from the enum. */
    public StyleBuilder fontStyle(FontStyle style) {
        this.fontStyle = Objects.requireNonNull(style, "fontStyle must not be null");
        return this;
    }

    /** Enables or disables the drop shadow. */
    public StyleBuilder shadow(boolean enabled) {
        this.shadow = enabled;
        return this;
    }

    /** Sets the outline colour as a hex string. {@code null} disables the outline. */
    public StyleBuilder outlineColor(String hex) {
        this.outlineColor = hex == null ? null : parseColorSafe(hex);
        return this;
    }

    /** Sets the outline colour from an existing {@link Color}. */
    public StyleBuilder outlineColor(Color color) {
        this.outlineColor = color;
        return this;
    }

    /** Sets the background colour block. {@code null} disables it. */
    public StyleBuilder backgroundColor(String hex) {
        this.backgroundColor = hex == null ? null : parseColorSafe(hex);
        return this;
    }

    /** Sets the background colour from an existing {@link Color}. */
    public StyleBuilder backgroundColor(Color color) {
        this.backgroundColor = color;
        return this;
    }

    /** Sets the sound resource identifier (e.g. {@code "minecraft:entity.player.hurt"}). */
    public StyleBuilder sound(String soundId) {
        this.sound = soundId;
        return this;
    }

    /** Sets the text prefix displayed before the damage number. */
    public StyleBuilder prefix(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
        return this;
    }

    /** Sets the text suffix displayed after the damage number. */
    public StyleBuilder suffix(String suffix) {
        this.suffix = Objects.requireNonNull(suffix, "suffix must not be null");
        return this;
    }

    /** Sets the icon texture path (e.g. {@code "mymod:textures/gui/my_icon.png"}). */
    public StyleBuilder icon(String iconPath) {
        this.icon = iconPath;
        return this;
    }

    /** Sets the icon position: {@code "left"} (before text) or {@code "right"} (after text). */
    public StyleBuilder iconPosition(String position) {
        this.iconPosition = Objects.requireNonNull(position, "iconPosition must not be null");
        return this;
    }

    /** Sets the icon horizontal offset in pixels (positive = right, negative = left). */
    public StyleBuilder iconOffsetX(double offset) {
        this.iconOffsetX = offset;
        return this;
    }

    /** Sets the icon vertical offset in pixels (positive = down, negative = up). */
    public StyleBuilder iconOffsetY(double offset) {
        this.iconOffsetY = offset;
        return this;
    }

    /** Sets the text displayed instead of damage for kill-type damage numbers. */
    public StyleBuilder killText(String text) {
        this.killText = text;
        return this;
    }

    /** When {@code true}, ignores the global {@code displayOpacity} config. */
    public StyleBuilder bypassDisplayOpacity(boolean bypass) {
        this.bypassDisplayOpacity = bypass;
        return this;
    }

    /**
     * Sets the animation configuration from an {@link AnimationBuilder}.
     * The builder's {@code done()} is called automatically.
     */
    public StyleBuilder animation(AnimationBuilder builder) {
        this.animation = Objects.requireNonNull(builder, "animation builder must not be null").build();
        return this;
    }

    /** Sets the animation configuration directly. */
    public StyleBuilder animation(AnimationConfig config) {
        this.animation = Objects.requireNonNull(config, "animation config must not be null");
        return this;
    }

    /**
     * Configures damage-value-based font scaling (linear-step model).
     * Passing {@code false} disables it entirely.
     */
    public StyleBuilder damageScale(boolean enabled) {
        this.damageScale = enabled
                ? new DamageScaleConfig(true,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_BASE,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_STEP,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_OFFSET,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_MAX,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_BASE,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_OFFSET,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_MAX)
                : new DamageScaleConfig(false,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_BASE,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_STEP,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_OFFSET,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_MAX,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_BASE,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_OFFSET,
                        StyleDefaults.DEFAULT_DAMAGE_SCALE_HOLD_MAX);
        return this;
    }

    /** Sets a custom damage-scale configuration. */
    public StyleBuilder damageScale(DamageScaleConfig config) {
        this.damageScale = Objects.requireNonNull(config, "damageScale must not be null");
        return this;
    }

    /**
     * 设置伤害数值显示保留的小数位数。
     * 整型伤害（如 12.0）始终显示为整数；小数伤害按该位数四舍五入。
     * 取值小于 0 时回退为默认值 1。
     */
    public StyleBuilder decimalPlaces(int places) {
        this.decimalPlaces = places;
        return this;
    }

    // ── Build / Register ───────────────────────────────────────────

    /**
     * Builds the {@link Style} without registering it.
     */
    public Style build() {
        // Ensure animation is never null
        AnimationConfig anim = this.animation;
        if (anim == null) {
            anim = defaultAnimation();
        }
        return new Style(color, fontSize, fontStyle, shadow, outlineColor,
                backgroundColor, sound, prefix, suffix, icon, iconPosition,
                iconOffsetX, iconOffsetY, killText, bypassDisplayOpacity,
                anim, damageScale, decimalPlaces);
    }

    /**
     * Builds the {@link Style} and registers it with the {@link StyleLoader}.
     * API-registered styles override file-loaded styles of the same name.
     *
     * @return the built and registered Style
     */
    public Style register() {
        Style style = build();
        styleLoader.register(name, style);
        return style;
    }

    // ── Safe colour parsing ────────────────────────────────────────

    /**
     * Parses a colour string safely, returning white on failure.
     * Uses {@link ColorParser#parse(String)} which throws on invalid input;
     * this wrapper catches and returns a default.
     */
    private static Color parseColorSafe(String hex) {
        try {
            ColorSource cs = ColorParser.parse(hex);
            if (cs instanceof Color c) {
                return c;
            }
            // If it's rainbow, resolve at tick 0 as fallback
            return cs.resolve(0f, 0);
        } catch (Exception e) {
            return Color.of(255, 255, 255);
        }
    }

    // ── Default animation (zero-animation) ─────────────────────────

    private static AnimationConfig defaultAnimation() {
        return new AnimationConfig(
                0,
                new com.stylizeddamage.common.animation.PositionConfig(
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.animation.OffsetValue.XY.FIXED_ZERO,
                        com.stylizeddamage.common.animation.OffsetValue.XY.FIXED_ZERO,
                        com.stylizeddamage.common.animation.OffsetValue.XY.FIXED_ZERO),
                new com.stylizeddamage.common.animation.SizeConfig(
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.util.RandomValue.ZERO,
                        com.stylizeddamage.common.util.RandomValue.ZERO,
                        com.stylizeddamage.common.util.RandomValue.ZERO),
                new com.stylizeddamage.common.animation.BrightnessConfig(
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.util.RandomValue.ZERO,
                        com.stylizeddamage.common.util.RandomValue.ZERO),
                new com.stylizeddamage.common.animation.OpacityConfig(
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.animation.PhaseConfig.NONE,
                        com.stylizeddamage.common.util.RandomValue.ZERO,
                        com.stylizeddamage.common.util.RandomValue.ZERO,
                        com.stylizeddamage.common.util.RandomValue.ZERO));
    }
}
