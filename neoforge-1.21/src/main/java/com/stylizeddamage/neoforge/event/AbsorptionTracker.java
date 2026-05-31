package com.stylizeddamage.neoforge.event;

import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.neoforge.NeoForgePlatform;
import com.stylizeddamage.neoforge.StylizedDamageNeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side tracker that detects changes in the local player's absorption
 * amount and generates floating damage numbers with the pseudo damage type
 * {@code "absorption"}.
 *
 * <h3>Why client-side?</h3>
 * <p>Absorption changes are player-local. The player's absorption hearts are
 * already known to the client, and there is no need to send this data over
 * the network. The client monitors {@link LivingEntity#getAbsorptionAmount()}
 * each tick and fires a synthetic damage event when the value increases.
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>On each client tick ({@link ClientTickEvent.Post}), reads the local
 *       player's current absorption amount.</li>
 *   <li>Compares to the previously recorded value; if the new value is higher,
 *       the difference is treated as "absorption gained" and pushed to the
 *       rendering pipeline as a {@link DamageSyncPacket} with
 *       {@code damageTypeId = "absorption"}.</li>
 *   <li>Ignores decreases (absorption wearing off or being consumed).</li>
 *   <li>Honors the {@code showAbsorption} config toggle in {@code common.json}.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * <p>All logic runs on the client render thread via {@code ClientTickEvent.Post}.
 * The packet handler is called synchronously to push data to the rendering queue.
 */
public final class AbsorptionTracker {

    private static final Logger LOG = LoggerFactory.getLogger(StylizedDamageNeoForge.MOD_ID);

    /** The previously observed absorption amount; starts at 0 (no absorption). */
    private static float previousAbsorption = 0f;

    private AbsorptionTracker() {
        // Utility class — no instances
    }

    /**
     * Registers the absorption tracker on the NeoForge game event bus
     * for client tick events. Must only be called from the client
     * distribution.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(AbsorptionTracker::onClientTickPost);
        LOG.debug("AbsorptionTracker registered on game bus (client-side)");
    }

    /**
     * Called at the end of each client tick.
     * Compares the local player's current absorption amount to the previous
     * value and dispatches a synthetic damage event for any increase.
     */
    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            previousAbsorption = 0f;
            return;
        }

        // Respect the showAbsorption config toggle
        CommonConfig config = ConfigManager.getInstance().getConfig();
        if (!config.showAbsorption()) {
            previousAbsorption = player.getAbsorptionAmount();
            return;
        }

        float currentAbsorption = player.getAbsorptionAmount();
        float delta = currentAbsorption - previousAbsorption;

        // Only fire for increases (absorption gained)
        if (delta > 0.001f) {
            long timestamp = System.currentTimeMillis();
            final double hitX = player.getX();
            final double hitY = player.getY() + player.getBbHeight() * 0.5;
            final double hitZ = player.getZ();

            DamageSyncPacket packet = new DamageSyncPacket(
                    -1,              // no source entity for absorption
                    player.getId(),
                    delta,
                    "absorption",    // pseudo damage type for absorption
                    false,           // absorption is never critical
                    timestamp,
                    hitX, hitY, hitZ);

            LOG.trace("Absorption gained: {} (+{})", currentAbsorption, delta);

            @SuppressWarnings("unchecked")
            PacketHandler handler = NeoForgePlatform.getPacketHandler();
            if (handler != null) {
                handler.handleDamageSync(packet);
            } else {
                LOG.debug("PacketHandler not yet set — absorption event queued");
            }
        }

        // Update tracking value
        previousAbsorption = currentAbsorption;
    }

    /**
     * Resets the tracked absorption amount to zero.
     * Should be called when the player respawns or changes dimension
     * to avoid stale tracking state.
     */
    public static void reset() {
        previousAbsorption = 0f;
        LOG.trace("AbsorptionTracker state reset");
    }
}
