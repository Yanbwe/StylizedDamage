package com.stylizeddamage.neoforge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.neoforge.client.DisplaySettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /stylizeddamage toggle} — toggles damage-number display on or off.
 *
 * <p>When display is toggled off, the renderer skips all damage numbers.
 * The toggle state is stored in {@link StylizedDamageCommand#setDisplayEnabled(boolean)}.
 */
public final class ToggleCommand {

    private ToggleCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Registers the {@code toggle} subcommand under the given root.
     *
     * @param root the parent {@code /stylizeddamage} literal
     */
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("toggle")
                .executes(ctx -> executeToggle(ctx.getSource())));
    }

    /**
     * Flips the display toggle and reports the new state.
     *
     * @param source the command source (for feedback)
     * @return {@code 1} on success
     */
    private static int executeToggle(CommandSourceStack source) {
        boolean current = StylizedDamageCommand.isDisplayEnabled();
        boolean newState = !current;
        StylizedDamageCommand.setDisplayEnabled(newState);
        DisplaySettings.setDisplayEnabled(newState);

        String stateMsg = newState ? "§aON" : "§cOFF";
        source.sendSuccess(
                () -> Component.literal("StylizedDamage display: " + stateMsg),
                true); // broadcast to admins so all ops see the state change
        return 1;
    }
}
