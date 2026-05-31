package com.stylizeddamage.common.style.color;

/**
 * A rainbow color source that cycles through the HSV hue wheel over time.
 *
 * <p>Each tick advances the hue by {@link #speed()} degrees (modulo 360).
 * Saturation and value are fixed at 1.0 for full vibrancy.
 *
 * @param speed hue shift in degrees per tick (positive cycles forward, negative backward)
 */
public record RainbowColor(int speed) implements ColorSource {

    /**
     * Validates that speed is non-zero.
     */
    public RainbowColor {
        if (speed == 0) {
            throw new IllegalArgumentException("speed must not be zero");
        }
    }

    /**
     * Returns the color at the given tick by shifting the hue wheel.
     *
     * @param tick the current tick count (can be positive, negative, or zero)
     * @return the color for this tick at full saturation and value
     */
    public Color getColor(int tick) {
        float hue = Math.floorMod(tick * speed, 360);
        return hsvToRgb(hue, 1.0f, 1.0f);
    }

    @Override
    public Color resolve(float progress, int tick) {
        return getColor(tick);
    }

    /**
     * Converts HSV (hue, saturation, value) to an opaque RGB {@link Color}.
     * Standard algorithm: maps the hue sector to RGB primaries, scales by
     * saturation, and shifts by value.
     *
     * @param hue        hue in degrees [0, 360)
     * @param saturation saturation in [0, 1]
     * @param value      value/brightness in [0, 1]
     * @return the corresponding opaque RGB color
     */
    static Color hsvToRgb(float hue, float saturation, float value) {
        float c = value * saturation;
        float x = c * (1 - Math.abs((hue / 60) % 2 - 1));
        float m = value - c;

        float r, g, b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return Color.of(
                Math.round((r + m) * 255),
                Math.round((g + m) * 255),
                Math.round((b + m) * 255)
        );
    }
}
