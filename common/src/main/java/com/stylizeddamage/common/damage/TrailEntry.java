package com.stylizeddamage.common.damage;

import java.util.Objects;

/**
 * An immutable record representing a single trail entry in the total-damage panel.
 * Each entry records the damage amount, the style name that was matched, and the
 * game tick when the damage occurred.
 *
 * <p>Newest entries are inserted at the head of the trail list in
 * {@link DamageAccumulator}. The trail list is truncated to the configured
 * {@code maxTrailCount} on each accumulation.
 *
 * @param damage    the raw damage amount (float, may be positive, zero, or negative)
 * @param styleName the matched style name for rendering this entry
 * @param timestamp the game tick when this damage was dealt
 */
public record TrailEntry(float damage, String styleName, int timestamp) {

    /** Canonical constructor — ensures non-null styleName. */
    public TrailEntry {
        Objects.requireNonNull(styleName, "styleName must not be null");
    }
}
