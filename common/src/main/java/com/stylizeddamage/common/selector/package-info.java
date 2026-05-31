/**
 * Selector system — matches damage events to styles via a three-layer matching engine.
 * Layer 1: damage value range, Layer 2: target entity type/team, Layer 3: damage type ID/tag.
 * Selectors are configured in {@code common.json} and can be extended via the API.
 */
package com.stylizeddamage.common.selector;
