package com.stylizeddamage.neoforge;

import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21.1 client-side implementation of {@link PacketHandler}.
 *
 * <p>Bridges incoming network packets to the rendering pipeline. When a
 * {@link DamageSyncPacket} or {@link TotalDamageSyncPacket} arrives
 * (either from the network or from local client components like
 * {@code AbsorptionTracker}), this handler pushes the data into the
 * damage-number rendering queue.
 *
 * <p><b>Note:</b> Full rendering pipeline integration is implemented in
 * a later subtask (HUD rendering). For now, this handler logs packet
 * receipt at trace level so the full networking→handler chain can be
 * verified.
 */
public class NeoForgePacketHandler implements PacketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StylizedDamageNeoForge.MOD_ID);

    @Override
    public void handleDamageSync(DamageSyncPacket packet) {
        LOG.trace("Damage sync received: target={}, amount={}, type={}",
                packet.targetEntityId(), packet.damage(), packet.damageTypeId());
        // TODO (later subtask): push to DamageNumberRenderer queue
    }

    @Override
    public void handleTotalDamageSync(TotalDamageSyncPacket packet) {
        LOG.trace("Total damage sync received: reset={}, damage={}, style={}",
                packet.reset(), packet.damage(), packet.styleName());
        // TODO (later subtask): update TotalDamagePanel state
    }
}
