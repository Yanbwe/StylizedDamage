package com.stylizeddamage.forge.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.animation.AnimationConfig;
import com.stylizeddamage.common.damage.ScreenPosition;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.forge.client.ActiveDamageNumber;
import com.stylizeddamage.forge.client.DamageNumberRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

import static com.stylizeddamage.forge.StylizedDamageForge.NETWORK;

/**
 * The {@code /stylizeddamage test <type> <amount>} subcommand.
 *
 * <p>Generates a test damage number at the commander's position by sending a
 * {@link DamageSyncPacket} to the player. The damage number renders at the
 * player's world position mapped to screen coordinates — approximately the
 * crosshair area when the player is looking at nearby blocks or entities.
 *
 * <h3>Arguments</h3>
 * <ul>
 *   <li>{@code <type>} — damage type identifier (e.g. {@code "minecraft:generic"})</li>
 *   <li>{@code <amount>} — floating-point damage value to display</li>
 * </ul>
 */
public final class TestCommand {

    private TestCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Creates the Brigadier command node for the {@code test} subcommand.
     *
     * @return a literal argument builder that can be attached to the root command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        final RequiredArgumentBuilder<CommandSourceStack, String> typeArg =
                Commands.argument("type", StringArgumentType.word());

        final RequiredArgumentBuilder<CommandSourceStack, Float> amountArg =
                Commands.argument("amount", FloatArgumentType.floatArg(0.1f));

        return Commands.literal("test")
                .then(typeArg.then(amountArg.executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();
                    final ServerPlayer player = source.getPlayerOrException();

                    final String rawType = StringArgumentType.getString(ctx, "type");
                    final String damageType = rawType.contains(":") ? rawType : "minecraft:" + rawType;
                    final float amount = FloatArgumentType.getFloat(ctx, "amount");

                    // Build a test damage sync packet — use the player as both source and target
                    final double hitX = player.getX();
                    final double hitY = player.getY() + player.getBbHeight() * 0.5;
                    final double hitZ = player.getZ();

                    final DamageSyncPacket packet = new DamageSyncPacket(
                            player.getId(),       // sourceEntityId = player
                            player.getId(),       // targetEntityId = player
                            Math.max(0.1f, amount),
                            damageType,
                            false,                // isCritical = false by default
                            System.currentTimeMillis(),
                            hitX, hitY, hitZ
                    );

                    // Send the packet to the player — the client will render it
                    NETWORK.sendDamageToPlayer(packet, player);

                    // Also directly inject into client renderer (bypass network for testing)
                    final DamageNumberRenderer renderer = DamageNumberRenderer.getInstance();
                    source.sendSuccess(() -> Component.literal(
                            "[DEBUG] renderer=" + (renderer != null ? "found" : "NULL")), false);
                    if (renderer != null) {
                        final Style style = StylizedDamageAPI.getInstance().getStyleLoader()
                                .getStyles().getOrDefault("default", null);
                        source.sendSuccess(() -> Component.literal(
                                "[DEBUG] style=" + (style != null ? "found" : "NULL")), false);
                        if (style != null) {
                            final Random random = new Random();
                            final AnimationConfig.Resolved anim = style.animation().resolve(random);
                            final ScreenPosition pos = new ScreenPosition(
                                    renderer.getScreenCenterX(), renderer.getScreenCenterY());
                            source.sendSuccess(() -> Component.literal(
                                    "[DEBUG] enqueuing at (" + pos.x() + "," + pos.y() + ")"), false);
                            final ActiveDamageNumber num = new ActiveDamageNumber(
                                    packet, style, anim, pos,
                                    renderer.getClientTick(), random, player.getId(),
                                    0.0, 0.0,
                                    hitX, hitY, hitZ, 1.0);
                            renderer.enqueue(num);
                        }
                    }

                    source.sendSuccess(
                            () -> Component.literal(
                                    "[StylizedDamage] Test damage: " + amount + " (" + damageType + ")"),
                            false);
                    return 1;
                })));
    }
}
