package com.stylizeddamage.common.style;

import com.stylizeddamage.common.style.color.Color;

/**
 * Hard-coded default values for all style fields.
 * Used as fallback when JSON fields are missing or invalid.
 * All constants are public for use by deserializers and tests.
 */
public final class StyleDefaults {

    private StyleDefaults() { /* constants only */ }

    // ── Color defaults ──────────────────────────────────────────────
    /** Default text color: white (#FFFFFF). */
    public static final String DEFAULT_COLOR_HEX = "#FFFFFF";
    public static final Color DEFAULT_OUTLINE_COLOR = Color.of(0x33, 0x33, 0x33);
    public static final Color DEFAULT_BACKGROUND_COLOR = null;

    // ── Text defaults ───────────────────────────────────────────────
    public static final float DEFAULT_FONT_SIZE = 1.0f;
    public static final FontStyle DEFAULT_FONT_STYLE = FontStyle.BOLD;
    public static final boolean DEFAULT_SHADOW = true;

    // ── String defaults ─────────────────────────────────────────────
    public static final String DEFAULT_PREFIX = "";
    public static final String DEFAULT_SUFFIX = "";

    // ── Nullable defaults ───────────────────────────────────────────
    public static final String DEFAULT_SOUND = null;
    public static final String DEFAULT_ICON = null;
    public static final String DEFAULT_ICON_POSITION = "left";
    public static final double DEFAULT_ICON_OFFSET_X = 0.0;
    public static final double DEFAULT_ICON_OFFSET_Y = 0.0;
    public static final String DEFAULT_KILL_TEXT = null;
    public static final boolean DEFAULT_BYPASS_DISPLAY_OPACITY = false;

    // ── Damage scale defaults ───────────────────────────────────────
    public static final boolean DEFAULT_DAMAGE_SCALE_ENABLED = true;
    public static final double DEFAULT_DAMAGE_SCALE_BASE = 1.0;
    public static final double DEFAULT_DAMAGE_SCALE_STEP = 10.0;
    public static final double DEFAULT_DAMAGE_SCALE_OFFSET = 1.0;
    public static final double DEFAULT_DAMAGE_SCALE_MAX = 2.5;
    public static final double DEFAULT_DAMAGE_SCALE_HOLD_BASE = 0.0;
    public static final double DEFAULT_DAMAGE_SCALE_HOLD_OFFSET = 10.0;
    public static final double DEFAULT_DAMAGE_SCALE_HOLD_MAX = 20.0;
}
