package com.stylizeddamage.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thread-safe singleton that loads, holds, and hot-reloads
 * the StylizedDamage configuration from {@code common.json}.
 * <p>
 * Usage:
 * <pre>{@code
 *   ConfigManager.initialize(configDir);
 *   CommonConfig cfg = ConfigManager.getInstance().getConfig();
 *   // After file changes:
 *   ConfigManager.getInstance().hotReload();
 * }</pre>
 * <p>
 * The config directory is provided by the platform-specific module.
 * common module has zero platform dependencies — only {@code java.nio.file}.
 */
public final class ConfigManager {

    private static volatile ConfigManager instance;

    private final Path configDir;
    private final Gson gson;
    private volatile CommonConfig config;

    private ConfigManager(Path configDir) {
        this.configDir = configDir;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(CommonConfig.class,
                        new CommonConfigDeserializer())
                .create();
        loadConfig();
    }

    /**
     * Initializes the singleton with the given config directory.
     * Thread-safe via double-checked locking. Safe to call multiple times —
     * subsequent calls return the existing instance without reinitializing.
     *
     * @param configDir the directory containing {@code common.json} (e.g. {@code config/stylizeddamage})
     * @return the singleton instance
     */
    public static ConfigManager initialize(Path configDir) {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager(configDir);
                }
            }
        }
        return instance;
    }

    /**
     * Returns the singleton instance. Must be initialized first via {@link #initialize(Path)}.
     *
     * @return the singleton
     * @throws IllegalStateException if not yet initialized
     */
    public static ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ConfigManager not initialized — call initialize(Path) first");
        }
        return instance;
    }

    /**
     * Returns the current configuration snapshot.
     * Safe for concurrent reads — the reference is volatile.
     *
     * @return current config, never null
     */
    public CommonConfig getConfig() {
        return config;
    }

    /**
     * Hot-reloads all configuration from disk.
     * Thread-safe — only one reload can execute at a time.
     * <p>
     * Reloads {@code common.json}. Styles directory rescanning will
     * be added when the styles subsystem is implemented.
     */
    public synchronized void hotReload() {
        loadConfig();
        // TODO: trigger style directory rescan when styles system is implemented
    }

    /**
     * Returns the config directory path (for use by style/system loaders).
     */
    public Path getConfigDir() {
        return configDir;
    }

    /**
     * Resets the singleton for testing purposes.
     * Not part of the public API — package-private access.
     */
    static synchronized void resetForTesting() {
        instance = null;
    }

    // ── Internal loading ───────────────────────────────────────────

    private void loadConfig() {
        Path commonJson = configDir.resolve("common.json");

        // If file doesn't exist, write defaults to disk
        if (!Files.exists(commonJson)) {
            try {
                Files.createDirectories(configDir);
                CommonConfig defaults = CommonConfig.createDefault();
                Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(commonJson, prettyGson.toJson(defaults));
                config = defaults;
                return;
            } catch (IOException e) {
                System.err.println("[StylizedDamage] Failed to write default common.json: " + e.getMessage());
                config = CommonConfig.createDefault();
                return;
            }
        }

        try (Reader reader = Files.newBufferedReader(commonJson)) {
            config = gson.fromJson(reader, CommonConfig.class);
            if (config == null) {
                config = CommonConfig.createDefault();
            }
        } catch (IOException e) {
            System.err.println("[StylizedDamage] Failed to read common.json: "
                    + e.getMessage() + " — using defaults");
            config = CommonConfig.createDefault();
        } catch (Exception e) {
            System.err.println("[StylizedDamage] Failed to parse common.json: "
                    + e.getMessage() + " — using defaults");
            config = CommonConfig.createDefault();
        }
    }
}
