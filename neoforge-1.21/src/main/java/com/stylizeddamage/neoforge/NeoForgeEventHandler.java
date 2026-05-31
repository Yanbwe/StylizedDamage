package com.stylizeddamage.neoforge;

import com.stylizeddamage.neoforge.command.StylizedDamageCommand;
import com.stylizeddamage.neoforge.event.AbsorptionTracker;
import com.stylizeddamage.neoforge.event.DamageEventListener;
import com.stylizeddamage.neoforge.event.HealEventListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central coordinator for all StylizedDamage event listeners on NeoForge 1.21.1.
 *
 * <p>This class serves as a single registration point for all platform event
 * listeners, delegating to specialized handler classes:
 * <ul>
 *   <li>{@link DamageEventListener} — damage events (Pre + Post)</li>
 *   <li>{@link HealEventListener} — healing events</li>
 *   <li>{@link AbsorptionTracker} — client-side absorption tracking</li>
 * </ul>
 *
 * <p>Each specialized handler is responsible for its own logic, keeping
 * this coordinator class clean and focused on registration order.
 */
public final class NeoForgeEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StylizedDamageNeoForge.MOD_ID);

    private NeoForgeEventHandler() {
        // Utility class — no instances
    }

    /**
     * Registers all event listeners on the NeoForge game event bus.
     * Called once during mod construction.
     *
     * @param gameBus the NeoForge event bus ({@code NeoForge.EVENT_BUS})
     */
    public static void register(IEventBus gameBus) {
        LOG.debug("Registering NeoForge event handlers");

        // Damage events (server-side): LivingDamageEvent.Pre + .Post
        DamageEventListener.register(gameBus);

        // Heal events (server-side): LivingHealEvent
        HealEventListener.register(gameBus);

        // Command registration (server-side): /stylizeddamage [reload|list|toggle]
        gameBus.addListener(RegisterCommandsEvent.class,
                StylizedDamageCommand::register);

        LOG.debug("NeoForge event handlers registered");
    }

    /**
     * Registers client-only event listeners.
     * Must be called via {@code FMLClientSetupEvent} or similar
     * to ensure the client distribution is active.
     *
     * <p>Currently registers:
     * <ul>
     *   <li>{@link AbsorptionTracker} — client-tick absorption monitoring</li>
     * </ul>
     */
    public static void registerClient() {
        LOG.debug("Registering NeoForge client event handlers");
        AbsorptionTracker.register();

        // Client-side command registration: /stylizeddamage test
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                .addListener(RegisterClientCommandsEvent.class,
                        StylizedDamageCommand::registerClient);

        LOG.debug("NeoForge client event handlers registered");
    }
}
