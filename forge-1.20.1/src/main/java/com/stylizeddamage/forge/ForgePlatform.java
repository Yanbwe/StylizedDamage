package com.stylizeddamage.forge;

import com.stylizeddamage.common.Platform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Forge 1.20.1 implementation of the {@link Platform} abstraction.
 *
 * <p>Provides access to Forge-specific utilities like the configuration
 * directory path and the current physical side (client vs. server).
 */
public final class ForgePlatform implements Platform {

    /**
     * Returns the configuration directory specific to this mod.
     *
     * <p>On Forge 1.20.1 this resolves to {@code ./config/stylizeddamage/}
     * inside the game directory.
     *
     * @return the mod's config directory path
     */
    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve("stylizeddamage");
    }

    /**
     * Returns the platform identifier for logging / debugging purposes.
     *
     * @return {@code "forge-1.20.1"}
     */
    @Override
    public String getPlatformName() {
        return "forge-1.20.1";
    }

    /**
     * Determines whether the current environment is a physical client.
     *
     * <p>Returns {@code true} when running on a player client (not a
     * dedicated server and not a data-generation run).
     *
     * @return {@code true} on a physical client
     */
    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
