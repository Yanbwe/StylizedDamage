package com.stylizeddamage.common.config;

/**
 * Hard-coded default values for all configuration fields.
 * Used as fallback when JSON fields are missing or invalid.
 * All constants are public for use by deserializers and tests.
 */
public final class ConfigDefaults {

    private ConfigDefaults() { /* constants only */ }

    // ── Selector defaults ──────────────────────────────────────────
    /** Default selector: matches all damage types, uses "default" style. */
    public static final String DEFAULT_STYLE_NAME = "default";

    // ── Display filter defaults ────────────────────────────────────
    public static final String DEFAULT_FILTER_MODE = "byTarget";
    public static final boolean DEFAULT_HIDE_SELF_DAMAGE = true;

    public static final boolean DEFAULT_BYSOURCE_PLAYER_SAME_TEAM = false;
    public static final boolean DEFAULT_BYSOURCE_PLAYER_OTHER_TEAM = false;
    public static final boolean DEFAULT_BYSOURCE_MOB_HOSTILE = false;
    public static final boolean DEFAULT_BYSOURCE_MOB_PASSIVE = false;
    public static final boolean DEFAULT_BYSOURCE_OTHER = false;

    public static final boolean DEFAULT_BYTARGET_PLAYER_SAME_TEAM = true;
    public static final boolean DEFAULT_BYTARGET_PLAYER_OTHER_TEAM = true;
    public static final boolean DEFAULT_BYTARGET_MOB_HOSTILE = true;
    public static final boolean DEFAULT_BYTARGET_MOB_PASSIVE = true;
    public static final boolean DEFAULT_BYTARGET_OTHER = true;

    // ── Distance scale defaults ────────────────────────────────────
    public static final double DEFAULT_MIN_DAMAGE_DISPLAY = 0.1;
    public static final int DEFAULT_MAX_ACTIVE_NUMBERS = 999;
    public static final boolean DEFAULT_SHOW_HEALING = true;
    public static final boolean DEFAULT_SHOW_ABSORPTION = true;
    public static final double DEFAULT_DISTANCE_SCALE_SEGMENT = 10.0;
    public static final double DEFAULT_DISTANCE_SCALE_FACTOR = 0.2;
    public static final double DEFAULT_DISTANCE_SCALE_MIN = 0.3;
    public static final double DEFAULT_MAX_DISPLAY_DISTANCE = 64.0;

    // ── Total damage panel defaults ────────────────────────────────
    public static final boolean DEFAULT_TOTAL_DAMAGE_ENABLED = true;
    public static final int DEFAULT_RESET_TIMEOUT = 100;
    public static final int DEFAULT_MAX_TRAIL_COUNT = 20;
    public static final double DEFAULT_BASE_FONT_SIZE = 2.0;
    public static final double DEFAULT_SIZE_OFFSET_PER_THOUSAND = 0.5;
    public static final double DEFAULT_SIZE_OFFSET_MAX = 3.0;
    /** Label text rendered above the total-damage number. */
    public static final String DEFAULT_LABEL_TEXT = "Damage";

    // ── Total damage panel position defaults (fraction of window size) ──
    public static final double DEFAULT_TOTAL_DAMAGE_POSITION_X = -0.15;
    public static final double DEFAULT_TOTAL_DAMAGE_POSITION_Y = 0.15;

    // ── Total damage panel animation defaults ──────────────────────
    public static final boolean DEFAULT_ANIM_ENTRY_ENABLED = true;
    public static final boolean DEFAULT_ANIM_EXIT_ENABLED = true;
    public static final boolean DEFAULT_ANIM_BOUNCE_ENABLED = true;
    public static final boolean DEFAULT_ANIM_TRAIL_ENTRY_ENABLED = true;
    public static final boolean DEFAULT_ANIM_TRAIL_EXIT_ENABLED = true;
    public static final double DEFAULT_BOUNCE_SCALE_PEAK = 1.4;
}
