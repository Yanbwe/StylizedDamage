package com.stylizeddamage.common.style;

import com.stylizeddamage.common.animation.AnimationConfig;
import com.stylizeddamage.common.animation.BrightnessConfig;
import com.stylizeddamage.common.animation.OffsetValue;
import com.stylizeddamage.common.animation.OpacityConfig;
import com.stylizeddamage.common.animation.PhaseConfig;
import com.stylizeddamage.common.animation.PositionConfig;
import com.stylizeddamage.common.animation.SizeConfig;
import com.stylizeddamage.common.style.color.Color;
import com.stylizeddamage.common.style.color.ColorSource;
import com.stylizeddamage.common.util.EasingCurve;
import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;

/**
 * Immutable visual style definition for a damage number.
 *
 * <p>A {@code Style} bundles all visual attributes — colour, font, decorations,
 * animation, and damage scaling — into a single record. Styles are loaded from
 * JSON files in the {@code config/stylizeddamage/styles/} directory, or
 * registered programmatically via {@link StyleLoader#register(String, Style)}.
 *
 * <p>The compact constructor validates invariants (e.g. {@code fontSize > 0})
 * and replaces {@code null} references with safe defaults where appropriate.
 *
 * @param color           the primary colour source (fixed hex or rainbow)
 * @param fontSize        base font size multiplier (must be positive)
 * @param fontStyle       text style variant (normal, bold, italic, bold_italic)
 * @param shadow          whether to render a drop shadow behind the text
 * @param outlineColor    colour of the text outline; {@code null} for no outline
 * @param backgroundColor background colour block behind the text; {@code null} for none
 * @param sound           sound event identifier to play on appearance; {@code null} for none
 * @param prefix          text prepended before the damage number
 * @param suffix          text appended after the damage number
 * @param icon            texture path for an icon; {@code null} for none
 * @param iconPosition    where to place the icon relative to the text ({@code "left"} or {@code "right"})
 * @param iconOffsetX     horizontal pixel offset to fine-tune icon placement
 * @param iconOffsetY     vertical pixel offset to fine-tune icon placement
 * @param killText              text displayed instead of damage value for kill-type damage; {@code null} to show damage
 * @param bypassDisplayOpacity  when {@code true}, ignores the global {@code displayOpacity} config
 * @param animation             animation configuration controlling movement, size, brightness, and opacity
 * @param damageScale     damage-value-based automatic font scaling
 */
public record Style(
        ColorSource color,
        float fontSize,
        FontStyle fontStyle,
        boolean shadow,
        Color outlineColor,
        Color backgroundColor,
        String sound,
        String prefix,
        String suffix,
        String icon,
        String iconPosition,
        double iconOffsetX,
        double iconOffsetY,
        String killText,
        boolean bypassDisplayOpacity,
        AnimationConfig animation,
        DamageScaleConfig damageScale) {

    public Style {
        // ── Required non‑null fields ────────────────────────────────
        Objects.requireNonNull(color, "color must not be null");
        Objects.requireNonNull(fontStyle, "fontStyle must not be null");
        Objects.requireNonNull(prefix, "prefix must not be null");
        Objects.requireNonNull(suffix, "suffix must not be null");
        Objects.requireNonNull(animation, "animation must not be null");
        Objects.requireNonNull(damageScale, "damageScale must not be null");

        // ── Validate iconPosition ────────────────────────────────────
        if (iconPosition == null || (!iconPosition.equals("left") && !iconPosition.equals("right"))) {
            iconPosition = StyleDefaults.DEFAULT_ICON_POSITION;
        }

        // ── Value validation ─────────────────────────────────────────
        if (fontSize <= 0) {
            fontSize = StyleDefaults.DEFAULT_FONT_SIZE;
        }
    }

    /**
     * Creates a {@code Style} populated entirely with default values.
     * The returned style uses a white fixed colour, bold font, and a
     * no‑animation config with zero hold ticks.
     */
    public static Style createDefault() {
        return new Style(
                Color.of(255, 255, 255),          // color = white
                StyleDefaults.DEFAULT_FONT_SIZE,
                StyleDefaults.DEFAULT_FONT_STYLE,
                StyleDefaults.DEFAULT_SHADOW,
                StyleDefaults.DEFAULT_OUTLINE_COLOR,
                StyleDefaults.DEFAULT_BACKGROUND_COLOR,
                StyleDefaults.DEFAULT_SOUND,
                StyleDefaults.DEFAULT_PREFIX,
                StyleDefaults.DEFAULT_SUFFIX,
                StyleDefaults.DEFAULT_ICON,
                StyleDefaults.DEFAULT_ICON_POSITION,
                StyleDefaults.DEFAULT_ICON_OFFSET_X,
                StyleDefaults.DEFAULT_ICON_OFFSET_Y,
                StyleDefaults.DEFAULT_KILL_TEXT,
                StyleDefaults.DEFAULT_BYPASS_DISPLAY_OPACITY,
                defaultAnimation(),
                DamageScaleConfig.defaults());
    }

    /**
     * Default animation matching the built-in default.json style:
     * upward float with random scatter, fade-in/fade-out, size pop and shrink.
     */
    private static AnimationConfig defaultAnimation() {
        var none = PhaseConfig.NONE;
        var zeroXY = OffsetValue.XY.FIXED_ZERO;
        var zeroR = RandomValue.ZERO;
        var one = RandomValue.fixed(1.0);
        var r02 = RandomValue.of(0, -2, 2);
        var r11 = RandomValue.of(0, -1, 1);
        var r22 = RandomValue.of(0, -2, 2);

        // position enter: xy scatter → direction(90°, 20px) float
        var pos = new PositionConfig(
                PhaseConfig.normal(30, EasingCurve.EASE_OUT),
                none,
                new OffsetValue.XY(RandomValue.of(2, -2, 2), RandomValue.of(2, -2, 2)),
                new OffsetValue.Direction(RandomValue.of(90, -1, 1), RandomValue.of(20, -2, 2)),
                zeroXY);

        // size enter: 1.3x → 1.0x over 40t, exit: 1.0x → 0 over 40t
        var size = new SizeConfig(
                PhaseConfig.normal(40, EasingCurve.EASE_IN_OUT),
                PhaseConfig.normal(40, EasingCurve.EASE_IN),
                RandomValue.fixed(0.3), zeroR, RandomValue.fixed(-1));

        var bright = new BrightnessConfig(none, none, zeroR, zeroR);

        // opacity enter: 0 → 1 over 10t, exit: 1 → 0 over 40t
        var opacity = new OpacityConfig(
                PhaseConfig.normal(10, EasingCurve.EASE_IN_OUT),
                PhaseConfig.normal(40, EasingCurve.EASE_IN),
                zeroR, one, zeroR);

        return new AnimationConfig(10, pos, size, bright, opacity);
    }
}
