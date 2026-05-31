package com.stylizeddamage.forge.client;

import com.stylizeddamage.common.style.color.Color;
import com.stylizeddamage.common.style.color.ColorSource;

/**
 * Converts a {@link ColorSource} into the packed ARGB integer format
 * expected by Minecraft's font rendering.
 *
 * <p>Minecraft uses an ABGR-like integer format where alpha is in the
 * upper 8 bits (matching Java's standard ARGB). This class provides a
 * bridge between the common module's {@link Color} and the
 * Minecraft-compatible integer.
 *
 * <p>Optionally applies a brightness offset (positive = lighter,
 * negative = darker) from the animation system.
 *
 * <p>All methods are static utilities. This class cannot be instantiated.
 */
public final class ColorRenderer {

    private ColorRenderer() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Resolves a {@link ColorSource} and returns the packed ARGB integer
     * suitable for {@code GuiGraphics#drawString}.
     *
     * @param source   the color source (fixed, gradient, or rainbow)
     * @param progress interpolation progress in [0, 1] for gradients
     * @param tick     the current client tick (drives rainbow hue)
     * @return packed ARGB integer (e.g., {@code 0xFFFFFFFF} for opaque white)
     */
    public static int toArgb(final ColorSource source, final float progress, final int tick) {
        final Color color = source.resolve(progress, tick);
        return color.argb();
    }

    /**
     * Resolves a color source and applies a brightness offset.
     *
     * <p>Positive brightness makes the color lighter (approaches white);
     * negative makes it darker (approaches black). The offset is clamped
     * to {@code [-1, 1]}.
     *
     * @param source           the color source to resolve
     * @param progress         interpolation progress in [0, 1]
     * @param tick             the current client tick
     * @param brightnessOffset brightness delta in [-1, 1]
     * @param opacity          alpha multiplier in [0, 1]
     * @return packed ARGB integer with brightness and alpha applied
     */
    public static int toArgb(
            final ColorSource source,
            final float progress,
            final int tick,
            final double brightnessOffset,
            final double opacity) {
        final Color color = source.resolve(progress, tick);
        return applyBrightnessAndOpacity(color, brightnessOffset, opacity);
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Applies a brightness offset and alpha multiplier to a color.
     *
     * @param color            the base color
     * @param brightnessOffset in [-1, 1]; 0 = no change, +1 = white, -1 = black
     * @param opacity          alpha multiplier in [0, 1]; 1 = fully opaque
     * @return the modified packed ARGB integer
     */
    private static int applyBrightnessAndOpacity(
            final Color color,
            final double brightnessOffset,
            final double opacity) {
        double b = Math.max(-1.0, Math.min(1.0, brightnessOffset));
        double o = Math.max(0.0, Math.min(1.0, opacity));

        int r = applyBrightnessChannel(color.red(), b);
        int g = applyBrightnessChannel(color.green(), b);
        int blu = applyBrightnessChannel(color.blue(), b);
        int a = (int) Math.round(color.alpha() * o);

        return Color.of(r, g, blu, a).argb();
    }

    /**
     * Adjusts a single color channel by the brightness offset.
     * Negative offset darkens (moves toward 0), positive offset lightens
     * (moves toward 255), using linear interpolation.
     */
    private static int applyBrightnessChannel(final int channel, final double offset) {
        if (offset >= 0) {
            // Lighten: interpolate from channel to 255
            return (int) Math.round(channel + (255 - channel) * offset);
        } else {
            // Darken: interpolate from channel to 0
            return (int) Math.round(channel * (1.0 + offset));
        }
    }
}
