package com.stylizeddamage.forge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.selector.SelectorConfig;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.style.StyleLoader;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * The {@code /stylizeddamage reload} subcommand.
 *
 * <p>Hot-reloads {@code common.json} via {@link ConfigManager#hotReload()} and
 * rescans the styles directory. Sends a success message to the command source
 * on completion.
 */
public final class ReloadCommand {

    private ReloadCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Creates the Brigadier command node for the {@code reload} subcommand.
     *
     * @return a literal argument builder that can be attached to the root command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("reload")
                .executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();

                    // Hot-reload the common config
                    ConfigManager.getInstance().hotReload();

                    // Rescan styles directory
                    try {
                        final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
                        final StyleLoader loader = api.getStyleLoader();
                        loader.load();

                        // Rebuild selector engine with new config
                        final CommonConfig config = ConfigManager.getInstance().getConfig();
                        final SelectorConfig selectorConfig = SelectorConfig.from(config.selectors());
                        selectorConfig.expandTags();
                        final SelectorEngine engine = new SelectorEngine(selectorConfig);
                        StylizedDamageAPI.initialize(loader, engine);
                    } catch (final IllegalStateException e) {
                        // API not initialised — will be reloaded on next init
                    }

                    source.sendSuccess(
                            () -> Component.literal("[StylizedDamage] Configuration reloaded successfully."),
                            false);
                    return 1;
                });
    }
}
