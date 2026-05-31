package com.stylizeddamage.common.api;

import com.stylizeddamage.common.selector.MatchRule;

import java.util.*;

/**
 * Chainable builder for registering selector rules via the API.
 *
 * <p>API-registered selectors have the <b>highest priority</b> — they are
 * inserted before all configuration-file selectors. Multiple
 * {@code .match(...).style(...)} chains may be added; each builds a
 * {@link MatchRule} that is stored for later injection into the
 * {@link com.stylizeddamage.common.selector.SelectorEngine}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * api.selectors()
 *     .in("common")
 *     .branch("common")
 *     .match("*").style("my_style")
 *     .match("minecraft:in_fire").style("fire")
 *     .match("critical").style("critical_style")
 *     .register();
 *
 * // With interval + target-type branching
 * api.selectors()
 *     .in("[50,...]")
 *     .branch("player", "yourTeam")
 *     .match("*").style("high_damage")
 *     .register();
 *
 * // Register selectors into a specific interval only
 * api.selectors()
 *     .in("common")
 *     .branch("mob", "hostile")
 *     .match("minecraft:magic").style("magic_mob")
 *     .register();
 * }</pre>
 *
 * <p>Each call to {@link #register()} commits the accumulated rules for the
 * selected interval/branch to the API's internal registry. Subsequent
 * {@code selectors()} calls start fresh.
 */
public final class SelectorBuilder {

    private final StylizedDamageAPI api;
    private String interval = "common";
    private String branch = "common";
    private final List<MatchRule> rules = new ArrayList<>();

    /** Called internally via {@link StylizedDamageAPI#selectors()}. */
    SelectorBuilder(StylizedDamageAPI api) {
        this.api = Objects.requireNonNull(api, "api must not be null");
    }

    // ── Interval ────────────────────────────────────────────────────

    /**
     * Sets the damage interval this selector applies to.
     * Example values: {@code "common"}, {@code "[50,...]"}, {@code "[0,50]"}.
     */
    public SelectorBuilder in(String interval) {
        this.interval = Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isBlank()) {
            throw new IllegalArgumentException("interval must not be blank");
        }
        return this;
    }

    // ── Branch ──────────────────────────────────────────────────────

    /**
     * Sets the target-type branch path.
     *
     * <p>Single-segment branches (e.g. {@code "common"}) map directly.
     * Multi-segment paths (e.g. {@code "player", "yourTeam"}) are joined
     * with {@code ":"} to form {@code "player:yourTeam"}.
     */
    public SelectorBuilder branch(String... segments) {
        Objects.requireNonNull(segments, "segments must not be null");
        if (segments.length == 0) {
            throw new IllegalArgumentException("branch must have at least one segment");
        }
        if (segments.length == 1) {
            this.branch = Objects.requireNonNull(segments[0], "branch segment must not be null");
        } else {
            this.branch = String.join(":", segments);
        }
        return this;
    }

    // ── Match → Style chain ─────────────────────────────────────────

    /**
     * Begins a match-condition entry.
     *
     * @param condition the match condition string (e.g. {@code "*"},
     *                  {@code "minecraft:in_fire"}, {@code "#minecraft:is_fire"},
     *                  {@code "critical"}, {@code "heal"}, {@code "absorption"})
     * @return a {@link MatchEntry} that accepts a {@code .style(...)} call
     */
    public MatchEntry match(String condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        return new MatchEntry(condition);
    }

    /**
     * Begins a match-condition entry for multiple conditions (OR semantics).
     *
     * @param conditions the match conditions
     * @return a {@link MatchEntry} that accepts a {@code .style(...)} call
     */
    public MatchEntry match(String... conditions) {
        Objects.requireNonNull(conditions, "conditions must not be null");
        if (conditions.length == 0) {
            throw new IllegalArgumentException("conditions must not be empty");
        }
        return new MatchEntry(List.of(conditions));
    }

    /**
     * Intermediate builder step: binds a style name to the previously
     * specified match condition(s) and returns to the parent
     * {@link SelectorBuilder} for further chaining or {@link #register()}.
     */
    public final class MatchEntry {
        private final List<String> conditions;

        MatchEntry(String singleCondition) {
            this.conditions = List.of(singleCondition);
        }

        MatchEntry(List<String> conditions) {
            this.conditions = List.copyOf(conditions);
        }

        /**
         * Binds the style name to the match condition(s) and returns to
         * the parent {@link SelectorBuilder}.
         *
         * @param styleName the style name to apply when this match rule fires
         * @return the parent SelectorBuilder for further chaining
         */
        public SelectorBuilder style(String styleName) {
            Objects.requireNonNull(styleName, "styleName must not be null");
            if (styleName.isBlank()) {
                throw new IllegalArgumentException("styleName must not be blank");
            }
            rules.add(new MatchRule(conditions, styleName));
            return SelectorBuilder.this;
        }
    }

    // ── Register ────────────────────────────────────────────────────

    /**
     * Commits all accumulated {@link MatchRule}s for the current interval
     * and branch to the API's internal registry.
     *
     * <p>API-registered rules take precedence over configuration-file
     * selectors: they are inserted at the front of the rule lists so they
     * are evaluated first.
     *
     * <p>This builder should be discarded after calling this method.
     */
    public void register() {
        if (rules.isEmpty()) {
            return;
        }
        api.registerSelectorRules(interval, branch, List.copyOf(rules));
    }

    /**
     * Returns the current number of accumulated rules in this builder.
     */
    public int ruleCount() {
        return rules.size();
    }

    // ── Package-private accessors (for testing) ─────────────────────

    String interval() { return interval; }
    String branch() { return branch; }
    List<MatchRule> getRules() { return Collections.unmodifiableList(rules); }
}
