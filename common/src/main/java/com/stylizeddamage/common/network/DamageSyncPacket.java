package com.stylizeddamage.common.network;

/**
 * Immutable record carrying damage event data from server to client.
 *
 * <p>Sent by the server to all players tracking the damaged entity via
 * {@link NetworkRegistrar#sendToTracking(DamageSyncPacket, Object)}.
 * The client receives this through {@link PacketHandler#handleDamageSync(DamageSyncPacket)}
 * and pushes the data into the rendering queue.
 *
 * @param sourceEntityId the network ID of the damage source entity
 * @param targetEntityId the network ID of the damaged entity
 * @param damage         the final damage amount after all reductions
 * @param damageTypeId   the damage type identifier (e.g. "minecraft:in_fire")
 * @param isCritical     whether this was a critical hit
 * @param timestamp      the server-side timestamp when the damage occurred
 * @param hitX           world X coordinate where the damage landed
 * @param hitY           world Y coordinate where the damage landed
 * @param hitZ           world Z coordinate where the damage landed
 */
public record DamageSyncPacket(
        int sourceEntityId,
        int targetEntityId,
        float damage,
        String damageTypeId,
        boolean isCritical,
        long timestamp,
        double hitX,
        double hitY,
        double hitZ
) {
    /**
     * Validates that this packet has a non-negative damage value
     * and a non-null damage type identifier.
     */
    public DamageSyncPacket {
        if (damageTypeId == null) {
            throw new IllegalArgumentException("damageTypeId must not be null");
        }
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be non-negative, got: " + damage);
        }
    }
}
