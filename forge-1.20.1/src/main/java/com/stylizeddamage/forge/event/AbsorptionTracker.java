package com.stylizeddamage.forge.event;

import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side tracker that monitors changes to the local player's
 * absorption amount and fires pseudo-damage events with the type
 * {@code "absorption"}.
 *
 * <p>Unlike damage and healing events (which are server-authoritative),
 * absorption changes are detected entirely on the client by comparing
 * the current tick's {@link Player#getAbsorptionAmount()} against the
 * previously recorded value. When a change is detected, a
 * {@link DamageSyncPacket} is pushed directly into the rendering
 * pipeline via the {@link PacketHandler} — no network round-trip needed.
 *
 * <p>Only registered on the physical client side.
 */
@OnlyIn(Dist.CLIENT)
public final class AbsorptionTracker {

    /** Pseudo damage-type identifier for absorption events. */
    static final String ABSORPTION_DAMAGE_TYPE = "absorption";

    /** Sentinel value indicating absorption has not yet been initialised. */
    private static final float UNINITIALISED = -1.0F;

    private final PacketHandler handler;
    private float lastAbsorption = UNINITIALISED;

    /**
     * @param handler the client-side packet handler that pushes damage
     *                data into the rendering queue
     */
    public AbsorptionTracker(final PacketHandler handler) {
        this.handler = handler;
    }

    /**
     * Fires at the end of every client tick. Compares the current
     * absorption amount against the cached value and dispatches a
     * damage packet if a change is detected.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        // Only process at phase END to avoid double-firing
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        final float currentAbsorption = mc.player.getAbsorptionAmount();

        // Initialise the tracker on first tick
        if (lastAbsorption == UNINITIALISED) {
            lastAbsorption = currentAbsorption;
            return;
        }

        final float diff = currentAbsorption - lastAbsorption;
        lastAbsorption = currentAbsorption;

        // Only fire for increases (absorption gained), ignore decreases
        if (diff <= 0.0F) {
            return;
        }

        // Build a local damage packet for the absorption delta
        final double hitX = mc.player.getX();
        final double hitY = mc.player.getY() + mc.player.getBbHeight() * 0.5;
        final double hitZ = mc.player.getZ();

        final DamageSyncPacket packet = new DamageSyncPacket(
                -1,                       // no source entity
                mc.player.getId(),
                diff,                     // positive = gained, negative = lost
                ABSORPTION_DAMAGE_TYPE,
                false,                    // absorption is never critical
                System.currentTimeMillis(),
                hitX, hitY, hitZ
        );

        handler.handleDamageSync(packet);
    }
}
