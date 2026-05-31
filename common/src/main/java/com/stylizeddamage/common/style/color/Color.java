package com.stylizeddamage.common.style.color;

/**
 * Immutable ARGB color value stored as a packed 32-bit integer.
 *
 * <p>Bit layout: {@code (alpha << 24) | (red << 16) | (green << 8) | blue}.
 * Each channel occupies 8 bits, so values are clamped to [0, 255].
 *
 * <p>Use the static factory {@link #of(int, int, int, int)} to create instances
 * with automatic clamping. Direct construction via the canonical constructor is
 * also supported for when the packed value is already known to be valid.
 *
 * @param argb the packed ARGB color value
 */
public record Color(int argb) implements ColorSource {

    /**
     * Creates a fully opaque color from RGB channels.
     * All values are clamped to [0, 255].
     */
    public static Color of(int r, int g, int b) {
        return of(r, g, b, 255);
    }

    /**
     * Creates a color from ARGB channels.
     * All values are clamped to [0, 255].
     */
    public static Color of(int r, int g, int b, int a) {
        r = clamp(r, 0, 255);
        g = clamp(g, 0, 255);
        b = clamp(b, 0, 255);
        a = clamp(a, 0, 255);
        return new Color((a << 24) | (r << 16) | (g << 8) | b);
    }

    /** Extracts the red channel [0, 255]. */
    public int red() {
        return (argb >>> 16) & 0xFF;
    }

    /** Extracts the green channel [0, 255]. */
    public int green() {
        return (argb >>> 8) & 0xFF;
    }

    /** Extracts the blue channel [0, 255]. */
    public int blue() {
        return argb & 0xFF;
    }

    /** Extracts the alpha channel [0, 255]. */
    public int alpha() {
        return (argb >>> 24) & 0xFF;
    }

    /**
     * Fixed colors always resolve to themselves, ignoring progress and tick.
     */
    @Override
    public Color resolve(float progress, int tick) {
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
