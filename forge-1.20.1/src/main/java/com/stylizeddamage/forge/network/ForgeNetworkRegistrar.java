package com.stylizeddamage.forge.network;

import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 implementation of {@link NetworkRegistrar} using Forge's
 * {@link SimpleChannel} networking API.
 *
 * <p>This registrar creates a versioned {@code "stylizeddamage:main"} channel
 * and registers two packet types:
 * <ul>
 *   <li>ID 0 — {@link DamageSyncPacket} (server → tracking clients)</li>
 *   <li>ID 1 — {@link TotalDamageSyncPacket} (server → specific player)</li>
 * </ul>
 *
 * <p>The {@link PacketHandler} reference is set during {@link #registerPackets(PacketHandler)}
 * and may be {@code null} on the dedicated server (where no rendering pipeline exists).
 */
public final class ForgeNetworkRegistrar implements NetworkRegistrar<Entity, ServerPlayer> {

    private static final Logger LOGGER = LoggerFactory.getLogger("StylizedDamage");

    private static final ResourceLocation CHANNEL_NAME =
            new ResourceLocation("stylizeddamage", "main");

    /** Protocol version string exchanged during handshake. */
    private static final String PROTOCOL_VERSION = "1";

    private final SimpleChannel channel;

    /**
     * The client-side packet handler injected by the client setup phase.
     * {@code null} on a dedicated server.
     */
    private volatile PacketHandler handler;

    /**
     * Creates and initialises the versioned SimpleChannel.
     * Channel construction must happen during mod construction (before registry freeze).
     */
    public ForgeNetworkRegistrar() {
        this.channel = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
    }

    /**
     * Registers all damage-related packet codecs and binds the {@link PacketHandler}
     * for client-side processing.
     *
     * @param handler the handler for incoming packets (may be {@code null} on server)
     */
    @Override
    public void registerPackets(final PacketHandler handler) {
        this.handler = handler;

        // ── DamageSyncPacket (ID 0) — server → tracking clients ──
        channel.messageBuilder(DamageSyncPacket.class, 0)
                .encoder(DamageSyncPacketCodec::encode)
                .decoder(DamageSyncPacketCodec::decode)
                .consumerMainThread((packet, ctx) -> {
                    final PacketHandler h = this.handler;
                    if (h != null) {
                        h.handleDamageSync(packet);
                    } else {
                        LOGGER.warn("Received DamageSyncPacket but handler is null!");
                    }
                })
                .add();

        // ── TotalDamageSyncPacket (ID 1) — server → specific player ──
        channel.messageBuilder(TotalDamageSyncPacket.class, 1)
                .encoder(TotalDamageSyncPacketCodec::encode)
                .decoder(TotalDamageSyncPacketCodec::decode)
                .consumerMainThread((packet, ctx) -> {
                    if (this.handler != null) {
                        this.handler.handleTotalDamageSync(packet);
                    }
                })
                .add();
    }

    /**
     * Broadcasts a damage sync packet to all players currently tracking {@code entity}.
     * Typically the entity is the one that took damage, so all nearby players see
     * the floating damage number.
     *
     * @param packet the damage data to broadcast
     * @param entity the tracked entity (usually the damage target)
     */
    @Override
    public void sendToTracking(final DamageSyncPacket packet, final Entity entity) {
        channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /**
     * Sends a damage sync packet to a specific player (e.g. for the test command).
     *
     * @param packet the damage data to send
     * @param player the target player
     */
    public void sendDamageToPlayer(final DamageSyncPacket packet, final ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Sends a total-damage update to a specific player.
     *
     * @param packet the total-damage update (increment or reset)
     * @param player the target player
     */
    @Override
    public void sendToPlayer(final TotalDamageSyncPacket packet, final ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Returns the raw {@link SimpleChannel} for advanced use cases
     * (e.g. custom packet types registered by add-ons).
     *
     * @return the underlying Forge networking channel
     */
    public SimpleChannel getChannel() {
        return channel;
    }

    /**
     * Sets or replaces the client-side {@link PacketHandler} without re-registering
     * packet codecs. Use this on the client side after the rendering pipeline
     * has been initialised.
     *
     * @param handler the new handler (must not be {@code null} on the client)
     */
    public void setHandler(final PacketHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns the currently registered {@link PacketHandler}, or {@code null}
     * on a dedicated server.
     *
     * @return the handler, or {@code null}
     */
    public PacketHandler getHandler() {
        return handler;
    }
}
