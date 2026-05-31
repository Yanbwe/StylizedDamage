package com.stylizeddamage.common.selector;

import java.util.Objects;

/**
 * The result of a successful selector match, carrying the chosen style name
 * and a human-readable description of which rule matched.
 *
 * <p>An empty result (no match) is represented by {@link java.util.Optional#empty()}
 * in the calling code; this record only exists when a match has been found.
 *
 * @param styleName   the name of the style to apply (corresponds to a file in {@code styles/})
 * @param matchedRule a description of the matching rule for debugging/logging
 */
public record StyleMatchResult(String styleName, String matchedRule) {

    /** Canonical constructor with null-safety. */
    public StyleMatchResult {
        Objects.requireNonNull(styleName, "styleName must not be null");
        Objects.requireNonNull(matchedRule, "matchedRule must not be null");
    }

    /**
     * Factory for a style match with a formatted rule description.
     *
     * @param styleName  the matched style name
     * @param condition  the specific condition that matched
     * @param interval   the interval key that was matched
     * @param branch     the branch that was used
     * @return a new result with a descriptive matchedRule
     */
    public static StyleMatchResult of(String styleName, String condition,
                                       String interval, String branch) {
        return new StyleMatchResult(
                styleName,
                String.format("interval=%s, branch=%s, condition=%s", interval, branch, condition));
    }

    /**
     * Factory for a match result with a free-form description.
     */
    public static StyleMatchResult of(String styleName, String matchedRule) {
        return new StyleMatchResult(styleName, matchedRule);
    }
}
