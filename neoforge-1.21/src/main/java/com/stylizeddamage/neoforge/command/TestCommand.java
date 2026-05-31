package com.stylizeddamage.neoforge.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.DamageSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /stylizeddamage test <type> <value>} — client-side test command.
 *
 * <p>Spawns a damage number at the player's position using the specified
 * damage type and value. Registered on the client event bus via
 * {@link net.neoforged.neoforge.client.event.RegisterClientCommandsEvent}.
 *
 * <p>Requires permission level 1 (cheats enabled in single-player, or
 * operator privileges on a server). Uses the local player's entity ID
 * as the damage target so the number appears at the player's screen position.
 */
public final class TestCommand {

    private static final String ARG_TYPE = "type";
    private static final String ARG_VALUE = "value";

    private TestCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Registers the {@code test} subcommand under the given root.
     *
     * @param root the parent {@code /stylizeddamage} literal
     */
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("test")
                .then(Commands.argument(ARG_TYPE, StringArgumentType.string())
                        .then(Commands.argument(ARG_VALUE, FloatArgumentType.floatArg(0.0F))
                                .executes(ctx -> executeTest(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, ARG_TYPE),
                                        FloatArgumentType.getFloat(ctx, ARG_VALUE))))));
    }

    /**
     * Creates and enqueues a test damage number.
     *
     * <p>Uses the local player's entity ID as the target so the number
     * appears at the player's position on screen (near the crosshair).
     *
     * @param source     the command source (client-side)
     * @param damageType the damage type identifier (e.g. {@code "minecraft:in_fire"})
     * @param value      the damage amount to display
     * @return {@code 1} on success
     */
    private static int executeTest(CommandSourceStack source,
                                    String damageType, float value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            source.sendFailure(Component.literal(
                    "Cannot run test command without a player."));
            return 0;
        }

        long timestamp = mc.level != null
                ? mc.level.getGameTime()
                : System.currentTimeMillis();

        // Use the player as both source and target so the number renders
        // at the player's screen position (near the crosshair).
        int playerId = mc.player.getId();

        final double hitX = mc.player.getX();
        final double hitY = mc.player.getY() + mc.player.getBbHeight() * 0.5;
        final double hitZ = mc.player.getZ();

        DamageSyncPacket packet = new DamageSyncPacket(
                playerId,           // sourceEntityId
                playerId,           // targetEntityId
                value,              // damage
                damageType,         // damageTypeId
                false,              // isCritical
                timestamp,          // timestamp
                hitX, hitY, hitZ
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Test damage spawned: " + damageType + " → " + value),
                false);
        return 1;
    }
}
