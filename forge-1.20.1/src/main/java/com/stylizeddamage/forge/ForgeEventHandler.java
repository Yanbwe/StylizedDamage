package com.stylizeddamage.forge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Placeholder event handler for Forge bus events not yet delegated to dedicated
 * listener classes.
 *
 * <p>{@link LivingDamageEvent} and {@link LivingHealEvent} are now handled by
 * {@link com.stylizeddamage.forge.event.DamageEventListener} and
 * {@link com.stylizeddamage.forge.event.HealEventListener}, which are registered
 * directly in {@link StylizedDamageForge}. This class remains available for
 * future event hooks.
 *
 * <p>Registered via {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}
 * if re-enabled in {@link StylizedDamageForge}.
 */
public final class ForgeEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Listens for living hurt events (pre-reduction).
     * Used when the raw (pre-armor) damage value is needed.
     *
     * @param event the living hurt event
     */
    @SubscribeEvent
    public void onLivingHurt(final LivingHurtEvent event) {
        // Placeholder for pre-reduction damage handling if needed.
    }
}
