package com.stylizeddamage.neoforge.command;

import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central command registration for StylizedDamage on NeoForge 1.21.1.
 *
 * <h3>Command structure</h3>
 * <pre>
 * /stylizeddamage (permission=2, server-side)
 *   ├── reload   — hot-reload config + styles
 *   ├── list     — list loaded styles and selector bindings
 *   └── toggle   — toggle damage-number display on/off
 *
 * /stylizeddamage (permission=1, client-side)
 *   └── test &lt;type&gt; &lt;value&gt; — spawn a test damage number
 * </pre>
 *
 * <h3>Display toggle</h3>
 * <p>The {@link #displayEnabled} flag is a runtime, server-authoritative toggle
 * that prevents damage-number rendering when set to {@code false}. Both
 * {@link com.stylizeddamage.neoforge.client.DamageNumberRenderer} and
 * {@link com.stylizeddamage.neoforge.client.TotalDamageHudRenderer} check this
 * flag before rendering each frame.
 */
public final class StylizedDamageCommand {

    static final String ROOT = "stylizeddamage";
    static final Logger LOG = LoggerFactory.getLogger(ROOT);

    /** Runtime display toggle — when {@code false}, damage numbers are hidden. */
    private static volatile boolean displayEnabled = true;

    private StylizedDamageCommand() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    // ── Display toggle accessors ──────────────────────────────────────

    /** Returns whether damage-number display is currently enabled. */
    public static boolean isDisplayEnabled() {
        return displayEnabled;
    }

    /** Sets the display toggle state. Thread-safe via volatile. */
    public static void setDisplayEnabled(boolean enabled) {
        displayEnabled = enabled;
        LOG.info("Damage-number display toggled: {}", enabled ? "ON" : "OFF");
    }

    // ── Server-side command registration ───────────────────────────────

    /**
     * Registers server-side commands (reload / list / toggle) on the
     * NeoForge game event bus via {@link RegisterCommandsEvent}.
     *
     * <p>All subcommands require permission level 2 (operator-only).
     *
     * @param event the command registration event
     */
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal(ROOT)
                .requires(source -> source.hasPermission(2));

        ReloadCommand.register(root);
        ListCommand.register(root);
        ToggleCommand.register(root);

        event.getDispatcher().register(root);
        LOG.debug("Server-side commands registered: /{} [reload|list|toggle]", ROOT);
    }

    // ── Client-side command registration ───────────────────────────────

    /**
     * Registers client-side commands (test) on the NeoForge game event bus
     * via {@link RegisterClientCommandsEvent}. Only fires on the logical client.
     *
     * <p>The test command requires permission level 1 (cheats enabled).
     *
     * @param event the client command registration event
     */
    public static void registerClient(RegisterClientCommandsEvent event) {
        var root = Commands.literal(ROOT)
                .requires(source -> source.hasPermission(1));

        TestCommand.register(root);

        event.getDispatcher().register(root);
        LOG.debug("Client-side command registered: /{} test", ROOT);
    }
}
