package com.stylizeddamage.neoforge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.selector.SelectorConfig;
import com.stylizeddamage.common.selector.SelectorEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /stylizeddamage reload} — hot-reloads configuration and styles.
 *
 * <p>Reloads {@code common.json}, rescans the {@code styles/} directory,
 * and rebuilds the selector engine so that file changes take effect
 * without a server restart.
 */
public final class ReloadCommand {

    private ReloadCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Registers the {@code reload} subcommand under the given root.
     *
     * @param root the parent {@code /stylizeddamage} literal
     */
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("reload")
                .executes(ctx -> executeReload(ctx.getSource())));
    }

    /**
     * Executes the full reload sequence:
     * <ol>
     *   <li>Hot-reload {@code common.json} via {@link ConfigManager#hotReload()}</li>
     *   <li>Rescan the styles directory via {@link StyleLoader#load()}</li>
     *   <li>Rebuild the selector engine from the (possibly updated) selectors</li>
     * </ol>
     *
     * @param source the command source (for feedback)
     * @return {@code 1} on success
     */
    private static int executeReload(CommandSourceStack source) {
        try {
            // 1. Reload config from disk
            ConfigManager.getInstance().hotReload();
            StylizedDamageCommand.LOG.info("common.json reloaded");

            // 2. Reload styles
            var api = StylizedDamageAPI.getInstance();
            var styleLoader = api.getStyleLoader();
            styleLoader.load();
            StylizedDamageCommand.LOG.info("Styles reloaded from {}", styleLoader.getStylesDir());

            // 3. Rebuild selector engine with merged config
            var fileSelectors = ConfigManager.getInstance().getConfig().selectors();
            SelectorConfig mergedConfig = api.buildFinalSelectorConfig(fileSelectors);
            mergedConfig.expandTags();
            var newEngine = new SelectorEngine(mergedConfig);
            // Re-initialize the API so the engine reference is updated
            StylizedDamageAPI.initialize(styleLoader, newEngine);
            StylizedDamageCommand.LOG.info("Selector engine rebuilt ({} intervals)",
                    mergedConfig.intervalCount());

            source.sendSuccess(
                    () -> Component.literal("StylizedDamage configs and styles reloaded."),
                    false);
            return 1;
        } catch (Exception e) {
            StylizedDamageCommand.LOG.error("Reload failed", e);
            source.sendFailure(Component.literal(
                    "Reload failed: " + e.getMessage()));
            return 0;
        }
    }
}
