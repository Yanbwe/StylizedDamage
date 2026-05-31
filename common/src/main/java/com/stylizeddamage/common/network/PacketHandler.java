package com.stylizeddamage.common.network;

/**
 * Handler interface for processing incoming damage packets on the client side.
 *
 * <p>Platform modules implement this interface to bridge network data into the
 * rendering pipeline. Each platform's network registrar calls the appropriate
 * handler method when a packet arrives.
 *
 * <p>Implementations must be thread-safe, as packet handling may occur on the
 * network thread while rendering runs on the client thread.
 *
 * @see NetworkRegistrar#registerPackets(PacketHandler)
 */
public interface PacketHandler {

    /**
     * Handles an incoming {@link DamageSyncPacket} from the server.
     * The implementation should push the damage data into the rendering queue
     * to create a new floating damage number on the HUD.
     *
     * @param packet the damage synchronization data from the server
     */
    void handleDamageSync(DamageSyncPacket packet);

    /**
     * Handles an incoming {@link TotalDamageSyncPacket} from the server.
     * The implementation should update the total damage panel state:
     * resetting it when {@code packet.reset()} is true (see {@link TotalDamageSyncPacket#clear()}),
     * or adding to the running total otherwise.
     *
     * @param packet the total damage update data from the server
     */
    void handleTotalDamageSync(TotalDamageSyncPacket packet);
}
