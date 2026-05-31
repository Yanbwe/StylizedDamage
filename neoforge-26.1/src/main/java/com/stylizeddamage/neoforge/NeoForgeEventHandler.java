package com.stylizeddamage.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeleton event handler registered on {@code NeoForge.EVENT_BUS}.
 *
 * <p>Listens for Minecraft lifecycle events such as entity damage,
 * healing, and absorption changes. The actual damage processing logic
 * will be wired in a later subtask (23: {@code DamageEventHandler}).
 *
 * <p>In NeoForge 26.1, the damage event hierarchy is:
 * <ul>
 *   <li>{@link LivingDamageEvent.Pre} — before armor/protection reduction</li>
 *   <li>{@link LivingDamageEvent.Post} — after all reductions applied</li>
 * </ul>
 *
 * <p>Using {@code LivingDamageEvent.Post} to capture the final damage value
 * displayed to the player.
 */
public final class NeoForgeEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeEventHandler.class);

    /**
     * Handles post-damage events to capture the final damage value.
     * Full implementation deferred to subtask 23.
     *
     * @param event the post-damage event carrying the final damage amount
     */
    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        // TODO (subtask 23): Extract damage info, build DamageSyncPacket,
        //      apply display filters, and send to tracking players.
        LOGGER.debug("LivingDamageEvent.Post: entity={}, damage={}",
                event.getEntity().getName().getString(),
                event.getNewDamage());
    }
}
