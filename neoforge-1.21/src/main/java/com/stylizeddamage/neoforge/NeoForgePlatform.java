package com.stylizeddamage.neoforge;

import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.common.network.PacketHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-specific implementation for NeoForge 1.21.1.
 *
 * <p>Provides access to platform APIs that the common module cannot
 * directly reference. Serves as a facade over NeoForge-specific
 * systems such as the network registrar, config paths, and logger.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Wrap platform-specific entity/player types for the common module</li>
 *   <li>Provide config directory resolution</li>
 *   <li>Expose a SLF4J logger scoped to the mod</li>
 *   <li>Hold the {@link NetworkRegistrar} implementation (to be wired in a later subtask)</li>
 * </ul>
 */
public final class NeoForgePlatform {

    static final String MOD_ID = StylizedDamageNeoForge.MOD_ID;
    static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    // ── Network Registrar (wired in network subtask) ────────────────
    //
    // The NetworkRegistrar will be initialized during network setup.
    // It is stored here so other platform components can access it.
    //
    // Example usage:
    //   NeoForgePlatform.getNetworkRegistrar().sendToTracking(packet, entity);

    @SuppressWarnings("rawtypes")
    private static volatile NetworkRegistrar networkRegistrar;

    /**
     * Returns the platform's {@link NetworkRegistrar} implementation
     * for sending packets to clients.
     *
     * @param <E> the platform entity type
     * @param <P> the platform player type
     * @return the network registrar, or null if not yet initialized
     */
    @SuppressWarnings("unchecked")
    public static <E, P> NetworkRegistrar<E, P> getNetworkRegistrar() {
        return networkRegistrar;
    }

    /**
     * Sets the platform's network registrar.
     * Called during network initialization by the network subtask.
     *
     * @param registrar the network registrar implementation
     */
    @SuppressWarnings("rawtypes")
    public static void setNetworkRegistrar(NetworkRegistrar registrar) {
        networkRegistrar = registrar;
    }

    // ── Packet Handler (client-side processing) ──────────────────────
    //
    // The PacketHandler is used by client-side components (e.g. AbsorptionTracker)
    // to push damage data directly into the rendering pipeline without going
    // through network serialization.

    private static volatile PacketHandler packetHandler;

    /**
     * Returns the platform's {@link PacketHandler} for client-side processing.
     *
     * @return the packet handler, or null if not yet initialized
     */
    public static PacketHandler getPacketHandler() {
        return packetHandler;
    }

    /**
     * Sets the platform's packet handler.
     * Called during initialization by the main mod class.
     *
     * @param handler the packet handler implementation
     */
    public static void setPacketHandler(PacketHandler handler) {
        packetHandler = handler;
    }

    // ── Logger ──────────────────────────────────────────────────────

    /**
     * Returns the SLF4J logger for this platform module.
     *
     * @return the mod-scoped logger
     */
    public static Logger getLogger() {
        return LOG;
    }

    // ── Config directory ────────────────────────────────────────────

    /**
     * Resolves the configuration directory for this mod.
     * Uses NeoForge's {@code FMLPaths.CONFIGDIR} when available.
     *
     * @return the absolute path to the mod's config directory
     */
    public static java.nio.file.Path getConfigDir() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
    }

    // ── Private constructor ─────────────────────────────────────────

    private NeoForgePlatform() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }
}
