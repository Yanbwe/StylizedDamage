package com.stylizeddamage.common.config;

import java.util.Objects;

/**
 * Configuration controlling which damage sources/targets produce floating numbers.
 * Immutable — all fields are final via Java records.
 *
 * @param mode          {@code "bySource"} or {@code "byTarget"}
 * @param hideSelfDamage if true, self-inflicted damage is never displayed
 * @param bySource      filter rules when mode is bySource
 * @param byTarget      filter rules when mode is byTarget
 */
public record DisplayFilterConfig(
        String mode,
        boolean hideSelfDamage,
        FilterTargetConfig bySource,
        FilterTargetConfig byTarget) {

    /** Compact constructor — provides defaults for null/missing fields. */
    public DisplayFilterConfig {
        if (mode == null || mode.isBlank()) {
            mode = ConfigDefaults.DEFAULT_FILTER_MODE;
        }
        if (bySource == null) {
            bySource = FilterTargetConfig.defaultBySource();
        }
        if (byTarget == null) {
            byTarget = FilterTargetConfig.defaultByTarget();
        }
    }

    // ── Nested records ─────────────────────────────────────────────

    /**
     * Per-entity-type filter rules (player / mob / other).
     */
    public record FilterTargetConfig(
            FilterPlayerConfig player,
            FilterMobConfig mob,
            boolean other) {

        public FilterTargetConfig {
            Objects.requireNonNull(player, "player config must not be null");
            Objects.requireNonNull(mob, "mob config must not be null");
        }

        static FilterTargetConfig defaultBySource() {
            return new FilterTargetConfig(
                    new FilterPlayerConfig(
                            ConfigDefaults.DEFAULT_BYSOURCE_PLAYER_SAME_TEAM,
                            ConfigDefaults.DEFAULT_BYSOURCE_PLAYER_OTHER_TEAM),
                    new FilterMobConfig(
                            ConfigDefaults.DEFAULT_BYSOURCE_MOB_HOSTILE,
                            ConfigDefaults.DEFAULT_BYSOURCE_MOB_PASSIVE),
                    ConfigDefaults.DEFAULT_BYSOURCE_OTHER);
        }

        static FilterTargetConfig defaultByTarget() {
            return new FilterTargetConfig(
                    new FilterPlayerConfig(
                            ConfigDefaults.DEFAULT_BYTARGET_PLAYER_SAME_TEAM,
                            ConfigDefaults.DEFAULT_BYTARGET_PLAYER_OTHER_TEAM),
                    new FilterMobConfig(
                            ConfigDefaults.DEFAULT_BYTARGET_MOB_HOSTILE,
                            ConfigDefaults.DEFAULT_BYTARGET_MOB_PASSIVE),
                    ConfigDefaults.DEFAULT_BYTARGET_OTHER);
        }
    }

    /**
     * Per-team-type player filter.
     */
    public record FilterPlayerConfig(boolean sameTeam, boolean otherTeam) {}

    /**
     * Per-mob-type mob filter.
     */
    public record FilterMobConfig(boolean hostile, boolean passive) {}
}
