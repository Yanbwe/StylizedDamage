package com.stylizeddamage.forge.event;

import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.NetworkRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side listener that captures entity healing events and broadcasts
 * them as pseudo-damage packets with the damage type {@code "heal"}.
 *
 * <p>Registered on the {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}
 * Forge bus. Healing is treated as a "damage" event with a special type
 * identifier so the client-side style selector can match it against
 * {@code "heal"} rules in the selector configuration.
 */
public final class HealEventListener {

    /** Pseudo damage-type identifier for healing events. */
    static final String HEAL_DAMAGE_TYPE = "heal";

    private final NetworkRegistrar<Entity, ServerPlayer> network;

    /**
     * @param network the platform network registrar used to send packets
     */
    public HealEventListener(final NetworkRegistrar<Entity, ServerPlayer> network) {
        this.network = network;
    }

    /**
     * Handles {@link LivingHealEvent} on the Forge event bus.
     *
     * <p>Builds a {@link DamageSyncPacket} with the {@code "heal"} pseudo
     * type and broadcasts it to all players tracking the healed entity.
     *
     * @param event the living heal event
     */
    @SubscribeEvent
    public void onLivingHeal(final LivingHealEvent event) {
        final LivingEntity entity = event.getEntity();

        // LivingHealEvent can fire on the client side (e.g. iron golem repair).
        // We must only process on the logical server to avoid ClassCastException
        // when PacketDistributor.TRACKING_ENTITY tries to access ServerChunkCache.
        if (entity.level().isClientSide()) {
            return;
        }

        final float healAmount = event.getAmount();

        // Ignore zero or negative heals (should not happen, but guard)
        if (healAmount <= 0.0F) {
            return;
        }

        final double hitX = entity.getX();
        final double hitY = entity.getY() + entity.getBbHeight() * 0.5;
        final double hitZ = entity.getZ();

        final DamageSyncPacket packet = new DamageSyncPacket(
                -1,              // no source entity for healing
                entity.getId(),
                healAmount,
                HEAL_DAMAGE_TYPE,
                false,           // healing is never critical
                System.currentTimeMillis(),
                hitX, hitY, hitZ
        );

        network.sendToTracking(packet, entity);
    }
}
