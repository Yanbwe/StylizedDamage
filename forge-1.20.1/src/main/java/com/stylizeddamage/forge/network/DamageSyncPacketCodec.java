package com.stylizeddamage.forge.network;

import com.stylizeddamage.common.network.DamageSyncPacket;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Encodes and decodes {@link DamageSyncPacket} to/from a {@link FriendlyByteBuf}
 * for the Forge 1.20.1 SimpleChannel network layer.
 *
 * <p>All methods are static and side-effect-free. The encoded format is:
 * <ol>
 *   <li>sourceEntityId — VarInt</li>
 *   <li>targetEntityId — VarInt</li>
 *   <li>damage — float</li>
 *   <li>damageTypeId — UTF string</li>
 *   <li>isCritical — boolean</li>
 *   <li>timestamp — long</li>
 * </ol>
 */
public final class DamageSyncPacketCodec {

    private DamageSyncPacketCodec() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Writes the fields of {@code packet} into {@code buf}.
     *
     * @param packet the damage sync data to encode (never null)
     * @param buf    the target buffer (never null)
     */
    public static void encode(final DamageSyncPacket packet, final FriendlyByteBuf buf) {
        buf.writeVarInt(packet.sourceEntityId());
        buf.writeVarInt(packet.targetEntityId());
        buf.writeFloat(packet.damage());
        buf.writeUtf(packet.damageTypeId());
        buf.writeBoolean(packet.isCritical());
        buf.writeLong(packet.timestamp());
        buf.writeDouble(packet.hitX());
        buf.writeDouble(packet.hitY());
        buf.writeDouble(packet.hitZ());
    }

    /**
     * Reads and constructs a {@link DamageSyncPacket} from {@code buf}.
     *
     * @param buf the source buffer containing encoded packet data (never null)
     * @return a fully populated {@link DamageSyncPacket}
     */
    public static DamageSyncPacket decode(final FriendlyByteBuf buf) {
        final int sourceEntityId = buf.readVarInt();
        final int targetEntityId = buf.readVarInt();
        final float damage = buf.readFloat();
        final String damageTypeId = buf.readUtf();
        final boolean isCritical = buf.readBoolean();
        final long timestamp = buf.readLong();
        final double hitX = buf.readDouble();
        final double hitY = buf.readDouble();
        final double hitZ = buf.readDouble();
        return new DamageSyncPacket(sourceEntityId, targetEntityId, damage, damageTypeId, isCritical, timestamp,
                hitX, hitY, hitZ);
    }
}
