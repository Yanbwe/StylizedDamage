package com.stylizeddamage.neoforge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.selector.MatchRule;
import com.stylizeddamage.common.selector.SelectorConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code /stylizeddamage list} — lists loaded styles and selector bindings.
 *
 * <p>Outputs two sections:
 * <ol>
 *   <li><b>Loaded styles</b> — all style names currently registered</li>
 *   <li><b>Selector bindings</b> — intervals, branches, and match-rule lists</li>
 * </ol>
 */
public final class ListCommand {

    private ListCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Registers the {@code list} subcommand under the given root.
     *
     * @param root the parent {@code /stylizeddamage} literal
     */
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));
    }

    /**
     * Gathers and prints the current state of styles and selectors.
     *
     * @param source the command source (for feedback)
     * @return {@code 1} on success
     */
    private static int executeList(CommandSourceStack source) {
        try {
            var api = StylizedDamageAPI.getInstance();
            var styleLoader = api.getStyleLoader();
            var engine = api.getSelectorEngine();

            // ── Section 1: Styles ──────────────────────────────────
            Map<String, ?> styles = styleLoader.getStyles();
            source.sendSuccess(
                    () -> Component.literal("§6=== StylizedDamage Styles (")
                            .append(String.valueOf(styles.size()))
                            .append(") ==="),
                    false);

            if (styles.isEmpty()) {
                source.sendSuccess(
                        () -> Component.literal("  §7(no styles loaded)"),
                        false);
            } else {
                for (String name : styles.keySet()) {
                    source.sendSuccess(
                            () -> Component.literal("  §a" + name),
                            false);
                }
            }

            // ── Section 2: Selectors ───────────────────────────────
            SelectorConfig config = engine.getConfig();
            source.sendSuccess(
                    () -> Component.literal("\n§6=== Selector Bindings (")
                            .append(String.valueOf(config.intervalCount()))
                            .append(" intervals) ==="),
                    false);

            listSelectorBindings(source, config);

            return 1;
        } catch (Exception e) {
            StylizedDamageCommand.LOG.error("List command failed", e);
            source.sendFailure(Component.literal(
                    "Failed to list styles: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Iterates over the selector configuration and prints each binding.
     *
     * <p>The output format per binding is:
     * {@code interval → branch: matchConditions → styleName}
     */
    private static void listSelectorBindings(CommandSourceStack source,
                                              SelectorConfig config) {
        // Gather all known interval keys from branchesFor iteration
        // Since SelectorConfig does not expose a direct iterator over
        // all intervals, we use the "common" interval plus any parsed
        // specific intervals from the raw selectors map.
        Map<String, com.google.gson.JsonObject> rawSelectors =
                com.stylizeddamage.common.config.ConfigManager.getInstance()
                        .getConfig().selectors();

        boolean foundAny = false;
        for (String intervalKey : rawSelectors.keySet()) {
            Map<String, List<MatchRule>> branches = config.branchesFor(intervalKey);
            if (branches.isEmpty()) continue;

            for (Map.Entry<String, List<MatchRule>> branchEntry : branches.entrySet()) {
                String branchKey = branchEntry.getKey();
                List<MatchRule> rules = branchEntry.getValue();

                for (MatchRule rule : rules) {
                    foundAny = true;
                    String matchStr = formatMatchList(rule.match());
                    String msg = String.format("  %s → %s: %s → §e%s",
                            intervalKey, branchKey, matchStr, rule.style());
                    source.sendSuccess(
                            () -> Component.literal(msg),
                            false);
                }
            }
        }

        if (!foundAny) {
            source.sendSuccess(
                    () -> Component.literal("  §7(no selector bindings configured)"),
                    false);
        }
    }

    /**
     * Formats a list of match conditions into a compact string.
     * Example: {@code ["minecraft:in_fire", "*"] → "in_fire, *"}
     */
    private static String formatMatchList(List<String> match) {
        if (match.isEmpty()) return "§7<none>";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < match.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(match.get(i));
        }
        return sb.toString();
    }
}
