package com.stylizeddamage.forge.network;

import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Encodes and decodes {@link TotalDamageSyncPacket} to/from a {@link FriendlyByteBuf}
 * for the Forge 1.20.1 SimpleChannel network layer.
 *
 * <p>The on-wire format distinguishes reset and increment packets via a leading
 * boolean flag:
 * <ul>
 *   <li><b>Reset packet:</b> {@code true} (= reset) and no further data</li>
 *   <li><b>Increment packet:</b> {@code false} + float damage + UTF styleName</li>
 * </ul>
 */
public final class TotalDamageSyncPacketCodec {

    private TotalDamageSyncPacketCodec() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Writes the packet fields into {@code buf}.
     *
     * @param packet the total-damage data to encode (never null)
     * @param buf    the target buffer (never null)
     */
    public static void encode(final TotalDamageSyncPacket packet, final FriendlyByteBuf buf) {
        buf.writeBoolean(packet.reset());
        if (!packet.reset()) {
            buf.writeFloat(packet.damage());
            buf.writeUtf(packet.styleName());
        }
    }

    /**
     * Reads and constructs a {@link TotalDamageSyncPacket} from {@code buf}.
     *
     * @param buf the source buffer containing encoded packet data (never null)
     * @return a {@link TotalDamageSyncPacket}, either reset or increment
     */
    public static TotalDamageSyncPacket decode(final FriendlyByteBuf buf) {
        final boolean reset = buf.readBoolean();
        if (reset) {
            return TotalDamageSyncPacket.clear();
        }
        final float damage = buf.readFloat();
        final String styleName = buf.readUtf();
        return TotalDamageSyncPacket.increment(damage, styleName);
    }
}
