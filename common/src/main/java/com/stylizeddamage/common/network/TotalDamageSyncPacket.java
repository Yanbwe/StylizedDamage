package com.stylizeddamage.common.network;

/**
 * Immutable record for total damage panel state updates.
 *
 * <p>Sent by the server to the player whose total damage panel needs updating.
 * When {@code reset} is true, the total damage counter and trail list are cleared.
 * When {@code reset} is false, {@code damage} carries the incremental damage value
 * to add to the running total, with {@code styleName} identifying which display
 * style to use for this damage entry.
 *
 * <p>The client receives this through
 * {@link PacketHandler#handleTotalDamageSync(TotalDamageSyncPacket)}.
 *
 * @param reset     if true, resets the total damage panel to zero
 * @param damage    the incremental damage value (null when reset is true)
 * @param styleName the display style name for this damage entry (null when reset is true)
 */
public record TotalDamageSyncPacket(
        boolean reset,
        Float damage,
        String styleName
) {
    /**
     * Creates a reset packet that instructs the client to clear the total damage panel.
     */
    public static TotalDamageSyncPacket clear() {
        return new TotalDamageSyncPacket(true, null, null);
    }

    /**
     * Creates an increment packet that adds the given damage amount to the running total.
     *
     * @param damage    the incremental damage to add (must be positive)
     * @param styleName the style name for displaying this damage entry
     * @return a new increment packet
     */
    public static TotalDamageSyncPacket increment(float damage, String styleName) {
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be non-negative, got: " + damage);
        }
        if (styleName == null || styleName.isEmpty()) {
            throw new IllegalArgumentException("styleName must not be null or empty");
        }
        return new TotalDamageSyncPacket(false, damage, styleName);
    }
}
