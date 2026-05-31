package com.stylizeddamage.common.style;

/**
 * Font style variants for damage number rendering.
 *
 * <p>Maps directly to Minecraft text styles; the platform-specific
 * rendering module translates these to the appropriate font renderer calls.
 */
public enum FontStyle {

    /** Standard weight, no slant. */
    NORMAL,

    /** Bold weight, no slant. */
    BOLD,

    /** Standard weight, italic slant. */
    ITALIC,

    /** Bold weight, italic slant. */
    BOLD_ITALIC;

    /**
     * Parses a font style from its lower-case JSON representation.
     * Unrecognised or null values fall back to {@link #NORMAL}.
     */
    public static FontStyle fromString(String value) {
        if (value == null) {
            return NORMAL;
        }
        return switch (value.trim().toLowerCase()) {
            case "bold" -> BOLD;
            case "italic" -> ITALIC;
            case "bold_italic", "bolditalic" -> BOLD_ITALIC;
            default -> NORMAL;
        };
    }
}
