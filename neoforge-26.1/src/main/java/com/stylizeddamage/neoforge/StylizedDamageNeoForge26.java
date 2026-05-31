package com.stylizeddamage.neoforge;

import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.selector.SelectorConfig;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.style.StyleLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(StylizedDamageNeoForge26.MOD_ID)
public final class StylizedDamageNeoForge26 {

    public static final String MOD_ID = "stylizeddamage";
    private static final Logger LOGGER = LoggerFactory.getLogger(StylizedDamageNeoForge26.class);

    public StylizedDamageNeoForge26(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("StylizedDamage NeoForge 26.1 initializing...");

        Path configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        Path stylesDir = configDir.resolve("styles");
        try {
            Files.createDirectories(stylesDir);
        } catch (Exception e) {
            LOGGER.error("Failed to create config dirs", e);
        }

        ConfigManager.initialize(configDir);
        StyleLoader styleLoader = new StyleLoader(stylesDir);
        styleLoader.load();
        LOGGER.info("Loaded {} styles", styleLoader.getStyles().size());

        CommonConfig config = ConfigManager.getInstance().getConfig();
        SelectorConfig selectorConfig = SelectorConfig.from(config.selectors());
        SelectorEngine selectorEngine = new SelectorEngine(selectorConfig);
        StylizedDamageAPI.initialize(styleLoader, selectorEngine);
        SelectorConfig finalConfig = StylizedDamageAPI.getInstance()
                .buildFinalSelectorConfig(config.selectors());
        StylizedDamageAPI.initialize(styleLoader, new SelectorEngine(finalConfig));

        NeoForge.EVENT_BUS.register(new NeoForgeEventHandler());
        LOGGER.info("StylizedDamage NeoForge 26.1 initialized");
    }
}
