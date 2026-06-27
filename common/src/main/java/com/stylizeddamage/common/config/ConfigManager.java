package com.stylizeddamage.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger("StylizedDamage");

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
                writePrettyJson(commonJson, defaults);
                config = defaults;
                return;
            } catch (IOException e) {
                LOGGER.error("Failed to write default common.json: {}", e.getMessage());
                config = CommonConfig.createDefault();
                return;
            }
        }

        // Read raw content first — needed for diff-based auto-fill detection
        String rawJson;
        try {
            rawJson = Files.readString(commonJson);
        } catch (IOException e) {
            LOGGER.error("Failed to read common.json: {} — using defaults", e.getMessage());
            config = CommonConfig.createDefault();
            return;
        }

        // Parse config — missing fields are filled with defaults in memory
        // by CommonConfigDeserializer and the record's compact constructor.
        try {
            config = gson.fromJson(rawJson, CommonConfig.class);
            if (config == null) {
                config = CommonConfig.createDefault();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse common.json: {} — using defaults", e.getMessage());
            config = CommonConfig.createDefault();
        }

        // Auto-fill any new fields that were added in a mod update
        // by writing the default-filled config back if it differs.
        tryAutoFill(commonJson, rawJson);
    }

    /**
     * Writes the config back to disk if the default-filled version
     * differs from the original file content (i.e. new fields were
     * added in a mod update). Best-effort — failures are logged but
     * never propagate.
     */
    private void tryAutoFill(Path filePath, String originalJson) {
        try {
            Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
            String updatedJson = prettyGson.toJson(config);
            if (!updatedJson.equals(originalJson)) {
                Files.writeString(filePath, updatedJson);
                LOGGER.info("Auto-filled missing fields in common.json");
            }
        } catch (Exception e) {
            // Best-effort: silently skip (read-only dir, disk full, etc.)
            LOGGER.debug("Auto-fill skipped: {}", e.getMessage());
        }
    }

    /** Convenience helper to reduce duplication. */
    private static void writePrettyJson(Path path, Object obj) throws IOException {
        Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(path, prettyGson.toJson(obj));
    }
}
