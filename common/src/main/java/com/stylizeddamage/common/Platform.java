package com.stylizeddamage.common;

import java.nio.file.Path;

/**
 * Platform abstraction interface.
 *
 * <p>Each platform module (forge-1.20.1, neoforge-1.21, neoforge-26) provides
 * its own implementation. This allows the common module to access platform-specific
 * resources without depending on any loader API directly.
 *
 * <p>The {@link com.stylizeddamage.common.api.StylizedDamageAPI} singleton
 * receives a Platform instance during initialization.
 */
public interface Platform {

    /**
     * Returns the absolute path to the configuration directory for this mod.
     * The returned directory is guaranteed to exist after the platform
     * module creates it during setup.
     *
     * @return the platform-specific config directory path
     */
    Path getConfigDir();

    /**
     * Returns an identifier string for the current platform.
     *
     * @return e.g. {@code "forge-1.20.1"}, {@code "neoforge-1.21.1"}, ...
     */
    String getPlatformName();

    /**
     * Returns {@code true} when the current environment is a physical client
     * (i.e. the player has a screen attached). On dedicated servers this
     * returns {@code false}.
     *
     * @return whether the game is running on a physical client
     */
    boolean isPhysicalClient();
}
