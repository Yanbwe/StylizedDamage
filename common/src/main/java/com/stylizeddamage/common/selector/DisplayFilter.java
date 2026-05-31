package com.stylizeddamage.common.selector;

import com.stylizeddamage.common.config.DisplayFilterConfig;

/**
 * Pure-function display filter that decides whether a damage number
 * should be shown to the local player.
 *
 * <h3>Filtering logic</h3>
 * <ol>
 *   <li>If {@code hideSelfDamage} is on and the damage is self-inflicted → hide.</li>
 *   <li>If mode is {@code bySource}, check the <em>source</em> entity type.</li>
 *   <li>If mode is {@code byTarget}, check the <em>target</em> entity type.</li>
 *   <li>For player entities, use team relationship (same / other).</li>
 *   <li>For mobs, use hostile / passive classification.</li>
 *   <li>For "other" entities, use the simple boolean flag.</li>
 * </ol>
 *
 * <p>This class is stateless beyond the injected config — all logic is pure.
 */
public final class DisplayFilter {

    private final DisplayFilterConfig config;

    /**
     * Creates a display filter backed by the given configuration.
     */
    public DisplayFilter(DisplayFilterConfig config) {
        this.config = config;
    }

    /**
     * Returns the underlying configuration (read-only access).
     */
    public DisplayFilterConfig getConfig() {
        return config;
    }

    /**
     * Decides whether a damage number should be displayed.
     *
     * @param source  information about the damage source entity
     * @param target  information about the entity receiving damage
     * @param isSelf  {@code true} if the local player is both source and target
     * @param damage  the damage amount (reserved for future range filtering)
     * @return {@code true} if the damage number should be rendered
     */
    public boolean shouldDisplay(EntityInfo source, EntityInfo target,
                                  boolean isSelf, float damage) {
        // Rule 1: hide self-damage when configured
        if (isSelf && config.hideSelfDamage()) {
            return false;
        }

        // Determine which entity to inspect based on mode
        String mode = config.mode();
        if ("bySource".equals(mode)) {
            return checkByRules(source, target, config.bySource());
        }
        // Default: byTarget
        return checkByRules(target, source, config.byTarget());
    }

    /**
     * Checks the given entity against the filter rules, using the other entity
     * for team-relationship resolution.
     *
     * @param subject the entity being checked (source or target, depending on mode)
     * @param other   the other entity (used for team comparison)
     * @param rules   the filter rules to apply
     * @return {@code true} if the damage number should be shown
     */
    private boolean checkByRules(EntityInfo subject, EntityInfo other,
                                  DisplayFilterConfig.FilterTargetConfig rules) {
        return switch (subject.entityType()) {
            case PLAYER -> checkPlayer(subject, other, rules.player());
            case MOB_HOSTILE -> rules.mob().hostile();
            case MOB_PASSIVE -> rules.mob().passive();
            case OTHER -> rules.other();
        };
    }

    /**
     * Checks a player entity against team-based rules.
     *
     * @param subject the player entity being checked
     * @param other   the other entity for team comparison
     * @param rules   player-specific filter rules
     * @return {@code true} if the damage number should be shown
     */
    private boolean checkPlayer(EntityInfo subject, EntityInfo other,
                                 DisplayFilterConfig.FilterPlayerConfig rules) {
        if (isSameTeam(subject, other)) {
            return rules.sameTeam();
        }
        return rules.otherTeam();
    }

    /**
     * Returns {@code true} when both entities are players on the same
     * non-{@code null} team.
     */
    static boolean isSameTeam(EntityInfo a, EntityInfo b) {
        return a.teamId() != null
                && b.teamId() != null
                && a.teamId().equals(b.teamId());
    }
}
