package com.stylizeddamage.neoforge.network;

import com.stylizeddamage.common.network.DamageSyncPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * NeoForge 1.21.1 CustomPacketPayload for per-damage-event sync from server to client.
 *
 * <p>This record mirrors {@link DamageSyncPacket} from the common module and adds
 * the NeoForge networking contract: a {@link Type} constant for registration and
 * a {@link StreamCodec} for binary serialization over {@link RegistryFriendlyByteBuf}.
 *
 * <p>The server constructs this payload after capturing a {@code LivingDamageEvent}
 * and sends it to all players tracking the damaged entity via
 * {@link net.neoforged.neoforge.network.PacketDistributor#sendToPlayersTrackingEntity
 * PacketDistributor.sendToPlayersTrackingEntity}.
 *
 * @param sourceEntityId the network ID of the damage source entity
 * @param targetEntityId the network ID of the damaged entity
 * @param damage         the final damage amount after all reductions
 * @param damageTypeId   the damage type identifier (e.g. "minecraft:in_fire")
 * @param isCritical     whether this was a critical hit
 * @param timestamp      the server-side millisecond timestamp
 * @param hitX           world X coordinate where the damage landed
 * @param hitY           world Y coordinate where the damage landed
 * @param hitZ           world Z coordinate where the damage landed
 */
public record DamageSyncPayload(
        int sourceEntityId,
        int targetEntityId,
        float damage,
        String damageTypeId,
        boolean isCritical,
        long timestamp,
        double hitX,
        double hitY,
        double hitZ
) implements CustomPacketPayload {

    /** Unique type identifier for packet registration and dispatch. */
    public static final Type<DamageSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("stylizeddamage", "damage_sync"));

    /**
     * Stream codec for all nine fields.
     *
     * <p>Implemented manually because {@code StreamCodec.composite} only
     * supports up to 6 fields. Nine fields require an anonymous class.
     */
    public static final StreamCodec<FriendlyByteBuf, DamageSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public DamageSyncPayload decode(final FriendlyByteBuf buf) {
                    return new DamageSyncPayload(
                            buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                            buf.readUtf(), buf.readBoolean(), buf.readVarLong(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble());
                }

                @Override
                public void encode(final FriendlyByteBuf buf, final DamageSyncPayload p) {
                    buf.writeVarInt(p.sourceEntityId());
                    buf.writeVarInt(p.targetEntityId());
                    buf.writeFloat(p.damage());
                    buf.writeUtf(p.damageTypeId());
                    buf.writeBoolean(p.isCritical());
                    buf.writeVarLong(p.timestamp());
                    buf.writeDouble(p.hitX());
                    buf.writeDouble(p.hitY());
                    buf.writeDouble(p.hitZ());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Converts this platform payload to the common module's record for
     * passing into shared logic that must not depend on platform types.
     *
     * @return the equivalent common-module {@link DamageSyncPacket}
     */
    public DamageSyncPacket toCommon() {
        return new DamageSyncPacket(
                sourceEntityId, targetEntityId, damage,
                damageTypeId, isCritical, timestamp,
                hitX, hitY, hitZ);
    }

    /**
     * Creates a platform payload from the common module's record.
     * Used by the network registrar when sending outgoing packets.
     *
     * @param packet the common-module damage sync packet
     * @return an equivalent platform payload for network serialization
     */
    public static DamageSyncPayload fromCommon(DamageSyncPacket packet) {
        return new DamageSyncPayload(
                packet.sourceEntityId(),
                packet.targetEntityId(),
                packet.damage(),
                packet.damageTypeId(),
                packet.isCritical(),
                packet.timestamp(),
                packet.hitX(),
                packet.hitY(),
                packet.hitZ());
    }
}
