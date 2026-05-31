package com.stylizeddamage.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Root command for StylizedDamage: {@code /stylizeddamage}.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code reload} — hot-reloads configuration from disk</li>
 *   <li>{@code test <type> <amount>} — spawns a test damage number</li>
 *   <li>{@code list} — lists all loaded styles and selector bindings</li>
 *   <li>{@code toggle} — toggles damage number display on/off</li>
 * </ul>
 *
 * <h3>Registration</h3>
 * <p>Commands are registered on the {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}
 * via {@link RegisterCommandsEvent}. The root command requires permission level 2 (op).
 *
 * <p>Usage example:
 * <pre>{@code
 *   MinecraftForge.EVENT_BUS.register(new StylizedDamageCommand());
 * }</pre>
 */
public final class StylizedDamageCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Handles the {@link RegisterCommandsEvent} fired on the Forge event bus.
     * Builds the full command tree and registers it with the dispatcher.
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    public void onRegisterCommands(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("stylizeddamage")
                        .requires(source -> source.hasPermission(2))
                        .then(ReloadCommand.create())
                        .then(TestCommand.create())
                        .then(ListCommand.create())
                        .then(ToggleCommand.create())
        );

        LOGGER.info("Registered /stylizeddamage command with subcommands: reload, test, list, toggle");
    }
}
