package com.stylizeddamage.common.style.color;

/**
 * Pure-function parser for color strings used in style configuration.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>{@code #RRGGBB} — fixed hex color (alpha = 255)</li>
 *   <li>{@code #AARRGGBB} — fixed hex color with alpha</li>
 *   <li>{@code rainbow:speed:N} — rainbow cycling with N degrees per tick</li>
 * </ul>
 */
public final class ColorParser {

    private ColorParser() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Parses a color string and returns the corresponding {@link ColorSource}.
     *
     * @param input the color string (e.g. {@code "#FF4444"}, {@code "rainbow:speed:10"})
     * @return a {@link Color} or {@link RainbowColor}
     * @throws IllegalArgumentException if the input is null, blank, or malformed
     */
    public static ColorSource parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Color string must not be null or blank");
        }

        String trimmed = input.trim();

        if (trimmed.startsWith("#")) {
            return parseFixed(trimmed);
        }
        if (trimmed.startsWith("rainbow:")) {
            return parseRainbow(trimmed);
        }

        throw new IllegalArgumentException(
                "Unknown color format: \"" + trimmed + "\". " +
                "Expected #RRGGBB or rainbow:speed:N");
    }

    // ── fixed hex ─────────────────────────────────────────────────────

    private static Color parseFixed(String hex) {
        if (hex.length() == 7) {
            int r = parseHexByte(hex, 1);
            int g = parseHexByte(hex, 3);
            int b = parseHexByte(hex, 5);
            return Color.of(r, g, b);
        }
        if (hex.length() == 9) {
            int a = parseHexByte(hex, 1);
            int r = parseHexByte(hex, 3);
            int g = parseHexByte(hex, 5);
            int b = parseHexByte(hex, 7);
            return Color.of(r, g, b, a);
        }
        throw new IllegalArgumentException(
                "Invalid hex color: \"" + hex + "\". " +
                "Expected #RRGGBB (6 digits) or #AARRGGBB (8 digits)");
    }

    private static int parseHexByte(String hex, int offset) {
        try {
            return Integer.parseInt(hex.substring(offset, offset + 2), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid hex digits in \"" + hex + "\" at position " + offset);
        }
    }

    // ── rainbow ────────────────────────────────────────────────────────

    private static RainbowColor parseRainbow(String input) {
        String[] parts = input.split(":");
        if (parts.length != 3 || !"speed".equals(parts[1])) {
            throw new IllegalArgumentException(
                    "Invalid rainbow format: \"" + input + "\". " +
                    "Expected rainbow:speed:N (e.g. rainbow:speed:10)");
        }
        int speed;
        try {
            speed = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid rainbow speed: \"" + parts[2] + "\". Expected an integer");
        }
        return new RainbowColor(speed);
    }
}
