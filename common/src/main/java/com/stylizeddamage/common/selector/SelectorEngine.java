package com.stylizeddamage.common.selector;

import java.util.*;

/**
 * Three-layer chained selector matching engine.
 *
 * <p>Determines which style should be applied to a damage number by
 * evaluating the selector configuration in three ordered layers:
 *
 * <ol>
 *   <li><b>Damage interval</b> — the damage value is tested against
 *       configured intervals ({@code [min,max]}, {@code [min,...]},
 *       {@code [...,max]}, or the fallback {@code common}).</li>
 *   <li><b>Target type branch</b> — within the matched interval, a
 *       branch is selected based on the entity type (player, mob,
 *       other) and team relationship.</li>
 *   <li><b>Match rule evaluation</b> — the ordered list of match rules
 *       in the selected branch is tested sequentially; the first rule
 *       whose conditions match (OR semantics) wins.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * SelectorConfig config = SelectorConfig.from(commonConfig.selectors());
 * SelectorEngine engine = new SelectorEngine(config);
 *
 * Optional<StyleMatchResult> result = engine.match(
 *     5.0f,
 *     "minecraft:in_fire",
 *     EntityClassifier.EntityType.PLAYER,
 *     "yourTeam",
 *     false
 * );
 * result.ifPresent(r -> System.out.println("Style: " + r.styleName()));
 * }</pre>
 *
 * <p>This class is thread-safe once constructed (the underlying
 * {@link SelectorConfig} is immutable).
 */
public final class SelectorEngine {

    private final SelectorConfig config;

    /**
     * Creates a selector engine backed by the given parsed configuration.
     *
     * @param config the parsed selector configuration, never {@code null}
     */
    public SelectorEngine(SelectorConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Returns the underlying configuration (read-only).
     */
    public SelectorConfig getConfig() {
        return config;
    }

    // ── Public matching API ──────────────────────────────────────────

    /**
     * Performs the full three-layer selector matching and returns the
     * chosen style, if any.
     *
     * @param damage       the damage amount (non-negative for normal damage;
     *                     may be 0 or negative for healing/absorption)
     * @param damageType   the damage type identifier (e.g. {@code "minecraft:in_fire"},
     *                     {@code "heal"}, {@code "absorption"})
     * @param entityType   the classification of the entity receiving damage
     * @param teamRelation the team relationship: {@code "yourTeam"},
     *                     {@code "otherTeam"}, or {@code "common"} (for no team)
     * @param isCritical   {@code true} if the hit was a critical hit
     * @return the matching result, or {@link Optional#empty()} if no rule matched
     */
    public Optional<StyleMatchResult> match(
            float damage,
            String damageType,
            EntityClassifier.EntityType entityType,
            String teamRelation,
            boolean isCritical) {

        Objects.requireNonNull(damageType, "damageType must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(teamRelation, "teamRelation must not be null");

        // ── Layer 1: Interval matching ──────────────────────────
        String intervalKey = config.findInterval(damage);

        // ── Layer 2: Branch resolution ───────────────────────────
        List<String> branchCandidates = resolveBranches(entityType, teamRelation);

        // ── Layer 3: Sequential rule evaluation ──────────────────
        for (String branchKey : branchCandidates) {
            List<MatchRule> rules = config.rulesFor(intervalKey, branchKey);
            for (MatchRule rule : rules) {
                if (rule.matches(damageType, isCritical)) {
                    String matchedCondition = findMatchingCondition(rule, damageType, isCritical);
                    return Optional.of(StyleMatchResult.of(
                            rule.style(), matchedCondition, intervalKey, branchKey));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Convenience overload that accepts a float damage type as a string
     * (same as the main method). Kept for clarity in call sites that
     * already have the values unpacked.
     */
    public Optional<StyleMatchResult> match(
            float damage,
            String damageType,
            EntityInfo targetInfo,
            EntityInfo sourceInfo,
            boolean isCritical) {

        Objects.requireNonNull(targetInfo, "targetInfo must not be null");
        Objects.requireNonNull(sourceInfo, "sourceInfo must not be null");

        String teamRelation = resolveTeamRelation(targetInfo, sourceInfo);
        return match(damage, damageType, targetInfo.entityType(), teamRelation, isCritical);
    }

    // ── Branch resolution ────────────────────────────────────────────

    /**
     * Returns the ordered list of branch keys to try for the given entity
     * type and team relationship.
     *
     * <p>For players: {@code "player:yourTeam"} → {@code "player:otherTeam"}
     * → {@code "player:common"} → {@code "common"}.
     *
     * <p>For mobs: {@code "mob:hostile"} / {@code "mob:passive"} →
     * {@code "mob:common"} → {@code "common"}.
     *
     * <p>For other: {@code "common"} only.
     */
    static List<String> resolveBranches(EntityClassifier.EntityType entityType,
                                         String teamRelation) {
        List<String> candidates = new ArrayList<>();

        switch (entityType) {
            case PLAYER -> {
                String playerBranch = "player:" + teamRelation;
                candidates.add(playerBranch);
                // Only add player:common if a specific team branch was already added
                // to avoid duplicates
                if (!"common".equals(teamRelation)) {
                    candidates.add("player:common");
                }
            }
            case MOB_HOSTILE -> {
                candidates.add("mob:hostile");
                candidates.add("mob:common");
            }
            case MOB_PASSIVE -> {
                candidates.add("mob:passive");
                candidates.add("mob:common");
            }
            case OTHER -> {
                // Falls through to "common" below
            }
        }

        candidates.add("common"); // Ultimate fallback
        return Collections.unmodifiableList(candidates);
    }

    /**
     * Determines the team relationship between two entities.
     *
     * @return {@code "yourTeam"} if same team, {@code "otherTeam"} if
     *         different teams, or {@code "common"} if team info is missing
     */
    static String resolveTeamRelation(EntityInfo target, EntityInfo source) {
        if (target.entityType() != EntityClassifier.EntityType.PLAYER) {
            return "common";
        }
        if (DisplayFilter.isSameTeam(target, source)) {
            return "yourTeam";
        }
        // If either has no team, treat as "common" (no team info)
        if (target.teamId() == null || source.teamId() == null) {
            return "common";
        }
        return "otherTeam";
    }

    // ── Internal helpers ──────────────────────────────────────────────

    /**
     * Finds which specific condition in a rule matched, for reporting.
     */
    private static String findMatchingCondition(MatchRule rule,
                                                 String damageType,
                                                 boolean isCritical) {
        for (String condition : rule.match()) {
            if (MatchRule.matchesCondition(condition, damageType, isCritical)) {
                return condition;
            }
        }
        return "unknown"; // Should never reach here if rule.matches() returned true
    }
}
