package com.stylizeddamage.common.selector;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single selector rule from the configuration: a list of match conditions
 * (OR semantics) paired with a style name.
 *
 * <p>Each element in {@link #match()} is tested against the damage context
 * according to these rules:
 * <ul>
 *   <li>{@code "*"} — matches any damage (wildcard)</li>
 *   <li>{@code "#"} prefix — tag match (prefix check against the damage type)</li>
 *   <li>{@code "heal"} — healing pseudo-type</li>
 *   <li>{@code "absorption"} — absorption pseudo-type</li>
 *   <li>{@code "critical"} — critical hit flag</li>
 *   <li>otherwise — exact damage type ID match (e.g. {@code "minecraft:in_fire"})</li>
 * </ul>
 *
 * <p>Conditions are ORed: if any single condition matches, the rule matches.
 *
 * @param match the list of match condition strings (OR relationship), never null or empty
 * @param style the style name to apply when this rule matches
 */
public record MatchRule(List<String> match, String style) {

    /** Canonical constructor — validates and freezes the match list. */
    public MatchRule {
        Objects.requireNonNull(match, "match list must not be null");
        if (match.isEmpty()) {
            throw new IllegalArgumentException("match list must not be empty");
        }
        Objects.requireNonNull(style, "style must not be null");
        if (style.isBlank()) {
            throw new IllegalArgumentException("style must not be blank");
        }
        match = List.copyOf(match); // defensive copy — immutability guarantee
    }

    /**
     * Convenience factory for a single-match condition.
     */
    public static MatchRule of(String matchCondition, String style) {
        Objects.requireNonNull(matchCondition, "matchCondition must not be null");
        return new MatchRule(List.of(matchCondition), style);
    }

    /**
     * Tests whether any condition in this rule matches the given damage context.
     *
     * @param damageType the damage type identifier (e.g. {@code "minecraft:in_fire"})
     * @param isCritical {@code true} if the hit was a critical hit
     * @return {@code true} if this rule matches the damage context
     */
    public boolean matches(String damageType, boolean isCritical) {
        for (String condition : match) {
            if (matchesCondition(condition, damageType, isCritical)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests a single condition string against the damage context.
     */
    static boolean matchesCondition(String condition, String damageType, boolean isCritical) {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(damageType, "damageType must not be null");

        String trimmed = condition.trim();

        // Wildcard — matches anything
        if ("*".equals(trimmed)) {
            return true;
        }

        // Pseudo-type: healing
        if ("heal".equals(trimmed)) {
            return "heal".equals(damageType);
        }

        // Pseudo-type: absorption
        if ("absorption".equals(trimmed)) {
            return "absorption".equals(damageType);
        }

        // Critical hit flag
        if ("critical".equals(trimmed)) {
            return isCritical;
        }

        // Tag match (prefix '#')
        if (trimmed.startsWith("#")) {
            String tag = trimmed.substring(1);
            return damageType.startsWith(tag) || damageType.equals(tag);
        }

        // Exact damage type ID match (e.g. "minecraft:in_fire")
        return trimmed.equals(damageType);
    }
}
