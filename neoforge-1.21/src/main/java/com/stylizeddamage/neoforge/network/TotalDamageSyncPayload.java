package com.stylizeddamage.neoforge.network;

import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge 1.21.1 CustomPacketPayload for total-damage panel state updates.
 *
 * <p>Sent from server to a specific player to update their running total-damage
 * counter. Two modes exist:
 * <ul>
 *   <li><b>Reset</b> ({@code reset=true}): clears the total damage panel and trail list.</li>
 *   <li><b>Increment</b> ({@code reset=false}): adds a damage value to the running total
 *       with an associated style name for display.</li>
 * </ul>
 *
 * <p>This record implements {@link CustomPacketPayload} to integrate with NeoForge's
 * {@link net.neoforged.neoforge.network.registration.PayloadRegistrar PayloadRegistrar}
 * networking stack.
 *
 * @param reset     if true, the client should reset its total damage panel
 * @param damage    the incremental damage value (may be null when reset)
 * @param styleName the display style name (may be null when reset)
 */
public record TotalDamageSyncPayload(
        boolean reset,
        @Nullable Float damage,
        @Nullable String styleName
) implements CustomPacketPayload {

    /** Unique type identifier for packet registration and dispatch. */
    public static final Type<TotalDamageSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("stylizeddamage", "total_damage_sync"));

    /**
     * Stream codec using manual encode/decode for nullable fields.
     * Writes a boolean reset flag, then optionally the damage float
     * and style string depending on whether this is a reset packet.
     */
    public static final StreamCodec<FriendlyByteBuf, TotalDamageSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.reset);
                        buf.writeBoolean(payload.damage != null);
                        if (payload.damage != null) buf.writeFloat(payload.damage);
                        buf.writeBoolean(payload.styleName != null);
                        if (payload.styleName != null) buf.writeUtf(payload.styleName);
                    },
                    buf -> {
                        boolean reset = buf.readBoolean();
                        boolean hasDamage = buf.readBoolean();
                        Float damage = hasDamage ? buf.readFloat() : null;
                        boolean hasStyle = buf.readBoolean();
                        String styleName = hasStyle ? buf.readUtf() : null;
                        return new TotalDamageSyncPayload(reset, damage, styleName);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Converts this platform payload to the common module's record.
     *
     * @return the equivalent common-module {@link TotalDamageSyncPacket}
     */
    public TotalDamageSyncPacket toCommon() {
        if (reset) {
            return TotalDamageSyncPacket.clear();
        }
        return new TotalDamageSyncPacket(false, damage, styleName);
    }

    /**
     * Creates a platform payload from the common module's record.
     *
     * @param packet the common-module total damage sync packet
     * @return an equivalent platform payload for network serialization
     */
    public static TotalDamageSyncPayload fromCommon(TotalDamageSyncPacket packet) {
        return new TotalDamageSyncPayload(packet.reset(), packet.damage(), packet.styleName());
    }
}
