package com.stylizeddamage.forge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.forge.client.DisplaySettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * The {@code /stylizeddamage toggle} subcommand.
 *
 * <p>Toggles the global damage-number display flag on or off.
 * When disabled, no damage numbers are rendered on the HUD (neither the
 * floating damage numbers nor the total-damage panel).
 *
 * <p>The toggle state is held in {@link DisplaySettings} on the client side.
 * In single-player / integrated-server environments the toggle takes effect
 * immediately because the client and server share the same JVM.
 */
public final class ToggleCommand {

    private ToggleCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Creates the Brigadier command node for the {@code toggle} subcommand.
     *
     * @return a literal argument builder that can be attached to the root command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("toggle")
                .executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();

                    final boolean newState = DisplaySettings.toggle();
                    final String status = newState ? "ENABLED" : "DISABLED";

                    source.sendSuccess(
                            () -> Component.literal("[StylizedDamage] Damage display " + status + "."),
                            false);
                    return 1;
                });
    }
}
