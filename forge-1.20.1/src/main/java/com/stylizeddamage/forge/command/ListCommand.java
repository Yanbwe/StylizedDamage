package com.stylizeddamage.forge.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.style.StyleLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * The {@code /stylizeddamage list} subcommand.
 *
 * <p>Lists all loaded style names along with their bound damage-type selectors.
 * Output is sent as a series of chat messages with formatting.
 */
public final class ListCommand {

    private ListCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Creates the Brigadier command node for the {@code list} subcommand.
     *
     * @return a literal argument builder that can be attached to the root command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("list")
                .executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();

                    try {
                        final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
                        final StyleLoader loader = api.getStyleLoader();

                        // ── Header ──
                        source.sendSuccess(
                                () -> Component.literal("")
                                        .append(Component.literal("=== StylizedDamage Styles ===")
                                                .withStyle(ChatFormatting.GOLD)),
                                false);

                        // ── Loaded styles ──
                        final Map<String, Style> styles = loader.getStyles();
                        if (styles.isEmpty()) {
                            source.sendSuccess(
                                    () -> Component.literal("  (no styles loaded)")
                                            .withStyle(ChatFormatting.GRAY),
                                    false);
                        } else {
                            source.sendSuccess(
                                    () -> Component.literal("  Loaded styles:")
                                            .withStyle(ChatFormatting.YELLOW),
                                    false);
                            for (final Map.Entry<String, Style> entry : styles.entrySet()) {
                                final String name = entry.getKey();
                                final Style style = entry.getValue();
                                final String desc = String.format("    - %s (fontSize=%.1f, shadow=%s)",
                                        name, style.fontSize(), style.shadow());
                                source.sendSuccess(
                                        () -> Component.literal(desc)
                                                .withStyle(ChatFormatting.WHITE),
                                        false);
                            }
                        }

                        // ── Selector bindings ──
                        source.sendSuccess(
                                () -> Component.literal("")
                                        .append(Component.literal("  Selector bindings (config):")
                                                .withStyle(ChatFormatting.YELLOW)),
                                false);
                        try {
                            final CommonConfig config =
                                    ConfigManager.getInstance().getConfig();
                            final Map<String, JsonObject> selectors = config.selectors();

                            if (selectors.isEmpty()) {
                                source.sendSuccess(
                                        () -> Component.literal("    (no selectors configured)")
                                                .withStyle(ChatFormatting.GRAY),
                                        false);
                            } else {
                                for (final Map.Entry<String, JsonObject> entry : selectors.entrySet()) {
                                    final String interval = entry.getKey();
                                    final JsonObject branches = entry.getValue();
                                    final String desc = String.format("    Interval [%s]: %s",
                                            interval, summarizeBranches(branches));
                                    source.sendSuccess(
                                            () -> Component.literal(desc)
                                                    .withStyle(ChatFormatting.WHITE),
                                            false);
                                }
                            }
                        } catch (final Exception e) {
                            source.sendSuccess(
                                    () -> Component.literal("    (config not available)")
                                            .withStyle(ChatFormatting.GRAY),
                                    false);
                        }

                        // ── Footer ──
                        source.sendSuccess(
                                () -> Component.literal("")
                                        .append(Component.literal("==============================")
                                                .withStyle(ChatFormatting.GOLD)),
                                false);
                    } catch (final IllegalStateException e) {
                        source.sendSuccess(
                                () -> Component.literal("[StylizedDamage] API not initialised — no styles loaded.")
                                        .withStyle(ChatFormatting.RED),
                                false);
                    }

                    return 1;
                });
    }

    /**
     * Summarises the branches inside a selector interval JSON for display.
     */
    private static String summarizeBranches(final JsonObject branches) {
        if (branches == null || branches.entrySet().isEmpty()) {
            return "empty";
        }
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final Map.Entry<String, JsonElement> entry : branches.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            final String key = entry.getKey();
            final JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                // Nested (player/mob) — show sub-branch count
                final int subCount = value.getAsJsonObject().size();
                sb.append(key).append("(").append(subCount).append(" sub)");
            } else if (value.isJsonArray()) {
                sb.append(key).append("(").append(value.getAsJsonArray().size()).append(" rules)");
            } else {
                sb.append(key);
            }
            first = false;
        }
        return sb.toString();
    }
}
