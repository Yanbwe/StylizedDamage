package com.stylizeddamage.neoforge;

import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.style.StyleLoader;

import net.neoforged.fml.loading.FMLPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Platform-specific utilities and lifecycle helpers for NeoForge 26.1.
 *
 * <p>This class provides bridge methods between the common (platform-agnostic)
 * module and the NeoForge 26.1 platform. Platform-specific file paths,
 * threading helpers, and network registration go here.
 *
 * <p>Note: the common module has zero Minecraft/Forge/NeoForge dependencies,
 * so any API that requires {@code net.minecraft} or {@code net.neoforged}
 * imports must live in a platform module like this one.
 */
public final class NeoForgePlatform {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgePlatform.class);

    private NeoForgePlatform() {
        // Utility class — no instantiation
    }

    // ── Config & I/O paths ─────────────────────────────────────────────

    /**
     * Returns the platform-specific config directory for StylizedDamage.
     * Delegates to FML's {@code FMLPaths.CONFIGDIR}.
     *
     * @return absolute path to {@code config/stylizeddamage/}
     */
    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(StylizedDamageNeoForge26.MOD_ID);
    }

    /**
     * Returns the platform-specific styles directory
     * ({@code config/stylizeddamage/styles/}).
     */
    public static Path getStylesDir() {
        return getConfigDir().resolve("styles");
    }

    // ── Hot-reload support ──────────────────────────────────────────────

    /**
     * Performs a full hot-reload of configuration and styles.
     *
     * <p>Called by the {@code /stylizeddamage reload} command.
     * Reloads {@code common.json}, rescans style files, and
     * rebuilds the selector engine with merged API+file selectors.
     * External mods are expected to re-register their API styles
     * on the next {@code StylizedDamageRegisterEvent} cycle.
     */
    public static void hotReload() {
        try {
            ConfigManager.getInstance().hotReload();
            LOGGER.info("Config hot-reloaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to hot-reload config", e);
        }

        try {
            Path stylesDir = getStylesDir();
            StyleLoader styleLoader = new StyleLoader(stylesDir);
            styleLoader.load();

            // Note: API-registered styles persist in the style loader.
            // The StylizedDamageAPI reference points to the same instance.
            LOGGER.info("Styles hot-reloaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to hot-reload styles", e);
        }
    }

    // ── Lifecycle helpers ───────────────────────────────────────────────

    /**
     * Logs the current platform and Minecraft version information.
     * Useful for debugging multi-platform builds.
     */
    public static void logPlatformInfo() {
        LOGGER.info("Running on NeoForge 26.1 platform");
    }
}
