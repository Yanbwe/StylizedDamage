package com.stylizeddamage.neoforge.event;

import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.neoforge.NeoForgePlatform;
import com.stylizeddamage.neoforge.StylizedDamageNeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side listener for {@link LivingHealEvent} that treats healing as the
 * pseudo damage type {@code "heal"}.
 *
 * <p>When an entity is healed (via {@link LivingEntity#heal(float)}), this
 * listener captures the heal amount and dispatches it to tracking clients as
 * a {@link DamageSyncPacket} with {@code damageTypeId = "heal"}. The client
 * can then render a healing-style floating number if the selector config
 * matches {@code "heal"} and {@code showHealing} is enabled in {@code common.json}.
 *
 * <h3>Integration</h3>
 * <p>The heal amount is reported as a positive float value. The client-side
 * rendering pipeline may prefix it with a "+" sign or apply a green tint,
 * depending on the matched style.
 */
public final class HealEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(StylizedDamageNeoForge.MOD_ID);

    private HealEventListener() {
        // Utility class — no instances
    }

    /**
     * Registers this listener on the NeoForge game event bus.
     *
     * @param gameBus the NeoForge game event bus ({@code NeoForge.EVENT_BUS})
     */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(HealEventListener::onLivingHeal);
        LOG.debug("HealEventListener registered on game bus");
    }

    /**
     * Handles {@link LivingHealEvent} and dispatches heal data as a pseudo
     * damage packet to all players tracking the healed entity.
     *
     * <p>Skips processing when:
     * <ul>
     *   <li>The event fires on the client side.</li>
     *   <li>The heal amount is zero or negative.</li>
     *   <li>{@code showHealing} is disabled in the common config.</li>
     *   <li>The network registrar has not been initialized yet.</li>
     * </ul>
     */
    private static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) {
            return;
        }

        float healAmount = event.getAmount();
        if (healAmount <= 0) {
            return;
        }

        // Respect the showHealing config toggle
        CommonConfig config = ConfigManager.getInstance().getConfig();
        if (!config.showHealing()) {
            return;
        }

        // When hideFullHealthHeal is enabled, skip healing numbers for
        // entities that are already at (or very near) full health.
        if (config.hideFullHealthHeal()
                && entity.getHealth() >= entity.getMaxHealth() - 0.001) {
            return;
        }

        int targetEntityId = entity.getId();
        long timestamp = System.currentTimeMillis();

        final double hitX = entity.getX();
        final double hitY = entity.getY() + entity.getBbHeight() * 0.5;
        final double hitZ = entity.getZ();

        // Build a damage sync packet with the pseudo-type "heal"
        // sourceEntityId = -1 since healing has no "attacker"
        DamageSyncPacket packet = new DamageSyncPacket(
                -1,           // no source entity for healing
                targetEntityId,
                healAmount,
                "heal",       // pseudo damage type for healing
                false,        // healing is never critical
                timestamp,
                hitX, hitY, hitZ);

        LOG.trace("Dispatching heal sync: target={}, amount={}",
                entity.getName().getString(), healAmount);

        // Send to all players tracking the healed entity
        @SuppressWarnings("unchecked")
        NetworkRegistrar<Entity, ServerPlayer> registrar = NeoForgePlatform.getNetworkRegistrar();
        if (registrar != null) {
            registrar.sendToTracking(packet, entity);
        } else {
            LOG.warn("NetworkRegistrar not initialized — skipping heal packet dispatch");
        }
    }
}
