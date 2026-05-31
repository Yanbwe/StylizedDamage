package com.stylizeddamage.common.network;

/**
 * Interface for platform-specific network channel registration and packet sending.
 *
 * <p>Each platform module (Forge 1.20.1, NeoForge 1.21, NeoForge 26) provides
 * its own implementation, binding the generic entity and player types to their
 * respective Minecraft classes. This keeps the common module free of any
 * platform-specific imports.
 *
 * <h3>Example platform binding:</h3>
 * <pre>{@code
 * // In Forge 1.20.1 module:
 * public class ForgeNetworkRegistrar implements NetworkRegistrar<Entity, ServerPlayer> {
 *     public void registerPackets(PacketHandler handler) {
 *         // Use SimpleChannel to register packet codecs
 *     }
 *     public void sendToTracking(DamageSyncPacket packet, Entity entity) {
 *         // Use PacketDistributor.TRACKING_ENTITY.with(entity).send(packet)
 *     }
 *     public void sendToPlayer(TotalDamageSyncPacket packet, ServerPlayer player) {
 *         // Use PacketDistributor.PLAYER.with(player).send(packet)
 *     }
 * }
 * }</pre>
 *
 * @param <E> the platform's entity type (e.g. {@code net.minecraft.world.entity.Entity})
 * @param <P> the platform's player type (e.g. {@code net.minecraft.world.entity.player.Player}
 *            or {@code net.minecraft.server.level.ServerPlayer})
 */
public interface NetworkRegistrar<E, P> {

    /**
     * Registers all damage-related packets with the platform's network channel
     * and binds the given handler to process incoming packets on the client side.
     *
     * <p>Called once during mod initialization.
     *
     * @param handler the packet handler that processes incoming damage data
     */
    void registerPackets(PacketHandler handler);

    /**
     * Sends a damage sync packet to all players currently tracking the given entity.
     * Typically used for {@link DamageSyncPacket} so all nearby players see the
     * floating damage number above the damaged entity.
     *
     * @param packet the damage data to broadcast
     * @param entity the entity being tracked (e.g. the damaged entity)
     */
    void sendToTracking(DamageSyncPacket packet, E entity);

    /**
     * Sends a total damage update packet to a specific player.
     * Used to update or reset the total damage panel for a single player.
     *
     * @param packet the total damage update to send
     * @param player the target player to receive the update
     */
    void sendToPlayer(TotalDamageSyncPacket packet, P player);
}
