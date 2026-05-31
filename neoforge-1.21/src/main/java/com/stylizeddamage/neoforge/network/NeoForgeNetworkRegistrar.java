package com.stylizeddamage.neoforge.network;

import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import com.stylizeddamage.neoforge.NeoForgePlatform;
import com.stylizeddamage.neoforge.StylizedDamageNeoForge;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21.1 implementation of {@link NetworkRegistrar} using
 * {@link PayloadRegistrar} and {@link CustomPacketPayload}.
 *
 * <p>Registers two client-bound payloads during the {@link RegisterPayloadHandlersEvent}:
 * <ul>
 *   <li>{@link DamageSyncPayload} — per-damage-event data broadcast to all
 *       players tracking the damaged entity.</li>
 *   <li>{@link TotalDamageSyncPayload} — total-damage panel updates sent
 *       to a specific player.</li>
 * </ul>
 *
 * <p>Payload handlers delegate to the common {@link PacketHandler} interface
 * after converting platform payloads to common records, keeping the common
 * module free of any platform-specific types.
 *
 * <h3>Thread safety</h3>
 * <p>The {@link PayloadRegistrar} defaults to {@code HandlerThread.MAIN},
 * which wraps handlers in {@code MainThreadPayloadHandler}. This means
 * {@code handleDamageSync} and {@code handleTotalDamageSync} are guaranteed
 * to execute on the main client thread.
 *
 * @see RegisterPayloadHandlersEvent
 * @see PayloadRegistrar
 */
public final class NeoForgeNetworkRegistrar implements NetworkRegistrar<Entity, ServerPlayer> {

    private static final Logger LOG = LoggerFactory.getLogger(StylizedDamageNeoForge.MOD_ID);

    private final PacketHandler handler;

    /**
     * Creates the network registrar and subscribes to the
     * {@link RegisterPayloadHandlersEvent} on the given mod event bus.
     *
     * @param modEventBus the mod-specific event bus (from the {@code @Mod} constructor)
     * @param handler     the common packet handler for client-side processing
     */
    public NeoForgeNetworkRegistrar(IEventBus modEventBus, PacketHandler handler) {
        this.handler = handler;

        // Register payload types during network setup (IModBusEvent)
        modEventBus.addListener(this::onRegisterPayloads);
        LOG.debug("NeoForgeNetworkRegistrar — listening for RegisterPayloadHandlersEvent");
    }

    // ── Payload registration ──────────────────────────────────────────

    /**
     * Called when the network registry is being set up.
     * Registers both client-bound payload types with their stream codecs and handlers.
     */
    private void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        LOG.info("Registering StylizedDamage network payloads (version 1)");

        // Damage sync: server → client, broadcast to all tracking players
        registrar.playToClient(
                DamageSyncPayload.TYPE,
                DamageSyncPayload.STREAM_CODEC,
                this::handleDamageSync
        );

        // Total damage sync: server → client, sent to a specific player
        registrar.playToClient(
                TotalDamageSyncPayload.TYPE,
                TotalDamageSyncPayload.STREAM_CODEC,
                this::handleTotalDamageSync
        );

        LOG.info("Network payloads registered successfully");
    }

    // ── Client-side payload handlers ───────────────────────────────────

    /**
     * Handles an incoming damage sync payload on the client.
     * Converts to the common record and delegates to the shared packet handler.
     */
    private void handleDamageSync(DamageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            DamageSyncPacket packet = payload.toCommon();
            LOG.trace("Client received damage sync: target={}, damage={}, type={}",
                    packet.targetEntityId(), packet.damage(), packet.damageTypeId());
            handler.handleDamageSync(packet);
        });
    }

    /**
     * Handles an incoming total damage sync payload on the client.
     * Converts to the common record and delegates to the shared packet handler.
     */
    private void handleTotalDamageSync(TotalDamageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            TotalDamageSyncPacket packet = payload.toCommon();
            LOG.trace("Client received total damage sync: reset={}, damage={}",
                    packet.reset(), packet.damage());
            handler.handleTotalDamageSync(packet);
        });
    }

    // ── Packet sending (server → client) ──────────────────────────────

    @Override
    public void registerPackets(PacketHandler handler) {
        // Registration happens in the constructor via RegisterPayloadHandlersEvent.
        // This method exists for interface compliance; no additional action needed.
    }

    @Override
    public void sendToTracking(DamageSyncPacket packet, Entity entity) {
        DamageSyncPayload payload = DamageSyncPayload.fromCommon(packet);
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    @Override
    public void sendToPlayer(TotalDamageSyncPacket packet, ServerPlayer player) {
        TotalDamageSyncPayload payload = TotalDamageSyncPayload.fromCommon(packet);
        PacketDistributor.sendToPlayer(player, payload);
    }
}
