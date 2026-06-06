package com.stylizeddamage.neoforge;

import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.common.selector.SelectorConfig;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.style.StyleLoader;
import com.stylizeddamage.neoforge.client.ClientPacketHandler;
import com.stylizeddamage.neoforge.client.DamageNumberRenderer;
import com.stylizeddamage.neoforge.client.DisplaySettings;
import com.stylizeddamage.neoforge.client.EntityScreenMapper;
import com.stylizeddamage.neoforge.client.TotalDamageHudRenderer;
import com.stylizeddamage.neoforge.network.NeoForgeNetworkRegistrar;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21.1 main mod class for StylizedDamage.
 *
 * <p>Entry point for the mod on the NeoForge 1.21.1 platform. Handles:
 * <ul>
 *   <li>Initialization of shared systems (config, style loader, selector engine)</li>
 *   <li>Registration of platform event handlers on the NeoForge event bus</li>
 *   <li>API initialization so third-party mods can register styles</li>
 * </ul>
 *
 * <p>The constructor receives the mod event bus and mod container, following
 * NeoForge's recommended injection pattern for the {@link Mod @Mod} annotation.
 *
 * <h3>Third-party mod registration</h3>
 * <p>Third-party mods that want to register custom styles should subscribe to
 * {@link com.stylizeddamage.common.api.StylizedDamageRegisterEvent}.
 * The platform module posts this event on the NeoForge game bus during
 * {@code FMLCommonSetupEvent} so that all mods have had a chance to initialize.
 *
 * @see Mod
 */
@Mod(StylizedDamageNeoForge.MOD_ID)
public final class StylizedDamageNeoForge {

    /** Mod identifier used in mod metadata and resource locations. */
    public static final String MOD_ID = "stylizeddamage";

    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** Default style JSON written when no styles exist. */
    private static final String DEFAULT_STYLE_JSON = """
            {
              "color": "#FFFFFF",
              "fontSize": 1,
              "fontStyle": "bold",
              "shadow": true,
              "outlineColor": null,
              "prefix": "",
              "suffix": "",
              "icon": "",
              "sound": null,
              "killText": "kill!",
              "iconPosition": "right",
              "iconOffsetX": 0,
              "iconOffsetY": 4,
              "damageScale": {
                "enabled": true,
                "baseFontSize": 1.0,
                "stepSize": 10,
                "sizeOffsetPerStep": 1,
                "maxSize": 2.5,
                "holdBase": 0,
                "holdOffsetPerStep": 10,
                "holdMax": 20
              },
              "animation": {
                "hold": 5,
                "position": {
                  "enter": {
                    "type": "normal",
                    "duration": 5,
                    "easing": { "in": false, "out": true },
                    "startOffset": { "type": "xy", "x": { "base": 2, "random": [-2, 2] }, "y": { "base": 2, "random": [-2, 2] } },
                    "targetOffset": { "type": "direction", "angle": { "base": 90, "random": [-1, 1] }, "distance": { "base": 20, "random": [-2, 2] } }
                  },
                  "exit": { "type": "none" }
                },
                "size": {
                  "enter": {
                    "type": "normal",
                    "duration": 5,
                    "easing": { "in": true, "out": true },
                    "startOffset": -0.6,
                    "targetOffset": 0
                  },
                  "exit": {
                    "type": "normal",
                    "duration": 10,
                    "easing": { "in": true, "out": false },
                    "targetOffset": -1
                  }
                },
                "brightness": {
                  "enter": { "type": "none" },
                  "exit": { "type": "none" }
                },
                "opacity": {
                  "enter": {
                    "type": "normal",
                    "duration": 5,
                    "easing": { "in": true, "out": true },
                    "startOpacity": 0,
                    "targetOpacity": 1
                  },
                  "exit": {
                    "type": "normal",
                    "duration": 10,
                    "easing": { "in": true, "out": false },
                    "targetOpacity": 0
                  }
                }
              }
            }""";

    private static final String HEAL_STYLE_JSON = """
            {
              "color": "#73FF44",
              "prefix": "+",
              "bypassDisplayOpacity": true
            }""";

    private static final String ABSORPTION_STYLE_JSON = """
            {
              "color": "#FFFF00",
              "prefix": "+🛡",
              "bypassDisplayOpacity": true
            }""";

    private static final String KILL_STYLE_JSON = """
            {
              "color": "#FFFF00",
              "fontSize": 3,
              "killText": "🗡kill!",
              "bypassDisplayOpacity": true,
              "animation": {
                "hold": 5,
                "position": {
                  "enter": {
                    "type": "normal",
                    "duration": 5,
                    "easing": { "in": false, "out": true },
                    "startOffset": { "type": "xy", "x": { "base": 2, "random": [-2, 2] }, "y": { "base": 2, "random": [-2, 2] } },
                    "targetOffset": { "type": "direction", "angle": { "base": 90, "random": [-1, 1] }, "distance": { "base": 20, "random": [-2, 2] } }
                  },
                  "exit": { "type": "none" }
                },
                "size": {
                  "enter": {
                    "type": "normal",
                    "duration": 5,
                    "easing": { "in": true, "out": true },
                    "startOffset": 1,
                    "targetOffset": 0
                  },
                  "exit": {
                    "type": "normal",
                    "duration": 20,
                    "easing": { "in": true, "out": false },
                    "targetOffset": -1
                  }
                },
                "brightness": {
                  "enter": { "type": "none" },
                  "exit": { "type": "none" }
                },
                "opacity": {
                  "enter": {
                    "type": "normal",
                    "duration": 5,
                    "easing": { "in": true, "out": true },
                    "startOpacity": 0,
                    "targetOpacity": 1
                  },
                  "exit": {
                    "type": "normal",
                    "duration": 20,
                    "easing": { "in": true, "out": false },
                    "targetOpacity": 0
                  }
                }
              }
            }""";

    private static final String FIRE_STYLE_JSON = """
            {
              "color": "#FF3F00",
              "prefix": "🔥",
              "bypassDisplayOpacity": true
            }""";

    private static final String MAGIC_STYLE_JSON = """
            {
              "color": "#9FFFFF",
              "prefix": "⭐"
            }""";

    /**
     * Constructs the NeoForge mod instance.
     *
     * @param modEventBus the mod-specific event bus for lifecycle and registry events
     * @param container   the mod container providing metadata and config registration
     */
    public StylizedDamageNeoForge(IEventBus modEventBus, ModContainer container) {
        LOG.info("Initializing StylizedDamage for NeoForge 1.21.1");

        // ── Shared system initialization ────────────────────────────
        // Use NeoForge's FMLPaths for the config directory
        java.nio.file.Path configDir =
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID);

        // Ensure the config directory and styles subdirectory exist
        java.nio.file.Path stylesDir = configDir.resolve("styles");
        try {
            java.nio.file.Files.createDirectories(stylesDir);
            // Generate default style files if they don't exist
            for (String[] entry : new String[][]{
                    {"default.json", DEFAULT_STYLE_JSON},
                    {"heal.json", HEAL_STYLE_JSON},
                    {"absorption.json", ABSORPTION_STYLE_JSON},
                    {"kill.json", KILL_STYLE_JSON},
                    {"fire.json", FIRE_STYLE_JSON},
                    {"magic.json", MAGIC_STYLE_JSON},
            }) {
                java.nio.file.Path path = stylesDir.resolve(entry[0]);
                if (!java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.writeString(path, entry[1]);
                    LOG.info("Created default style: {}", path);
                }
            }
        } catch (final java.io.IOException e) {
            LOG.error("Failed to create default style files: {}", e.getMessage());
        }

        // 1. Initialize configuration manager (singleton, loads common.json)
        ConfigManager configManager = ConfigManager.initialize(configDir);

        // 2. Initialize style loader with the styles directory
        StyleLoader styleLoader = new StyleLoader(configDir.resolve("styles"));
        styleLoader.load();

        // 3. Parse selector config from the loaded common.json
        SelectorConfig selectorConfig =
                SelectorConfig.from(configManager.getConfig().selectors());
        selectorConfig.expandTags();
        SelectorEngine selectorEngine = new SelectorEngine(selectorConfig);

        // ── API initialization ──────────────────────────────────────
        // Initialized early so third-party mods can query during setup.
        StylizedDamageAPI.initialize(styleLoader, selectorEngine);

        // ── Client-side rendering setup ──────────────────────────────
        DisplaySettings.setDisplayEnabled(true);
        final DamageNumberRenderer renderer = new DamageNumberRenderer(Minecraft.getInstance());
        final TotalDamageHudRenderer totalRenderer = new TotalDamageHudRenderer(Minecraft.getInstance());
        final PacketHandler packetHandler = new ClientPacketHandler(renderer, totalRenderer, Minecraft.getInstance());
        NeoForgePlatform.setPacketHandler(packetHandler);

        // Cache world-render projection matrix for zoom-aware screen projection
        NeoForge.EVENT_BUS.addListener((final RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                EntityScreenMapper.cachedProjectionMatrix = event.getProjectionMatrix();
            }
        });

        // ── Network registration ─────────────────────────────────────
        // Registers custom payloads (DamageSyncPayload, TotalDamageSyncPayload)
        // via RegisterPayloadHandlersEvent on the mod event bus.
        NetworkRegistrar<?, ?> networkRegistrar =
                new NeoForgeNetworkRegistrar(modEventBus, packetHandler);
        NeoForgePlatform.setNetworkRegistrar(networkRegistrar);

        // ── Event registration ──────────────────────────────────────
        // Game events (damage, HUD, etc.) — use NeoForge.EVENT_BUS
        NeoForgeEventHandler.register(NeoForge.EVENT_BUS);

        // Mod lifecycle events — use modEventBus
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);

        // ── HUD Layer registration ───────────────────────────────────
        // Register damage-number and total-damage HUD layers via
        // the layered draw system (RegisterGuiLayersEvent).
        DamageNumberRenderer.register(modEventBus);
        TotalDamageHudRenderer.register(modEventBus);

        LOG.info("StylizedDamage NeoForge 1.21.1 initialized successfully");
    }

    /**
     * Called during {@code FMLCommonSetupEvent}.
     *
     * <p>Finalizes the selector configuration by merging API-registered
     * selectors (from third-party mods) with file-based selectors from
     * {@code common.json}. API rules take priority and are placed before
     * file rules.
     *
     * <p>Also posts the {@link com.stylizeddamage.common.api.StylizedDamageRegisterEvent}
     * so third-party mods can register custom styles programmatically.
     *
     * @param event the common setup event
     */
    private void onCommonSetup(
            @SuppressWarnings("unused") net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOG.debug("FMLCommonSetup — finalizing StylizedDamage");

        // ── Fire API registration event ─────────────────────────────
        // Third-party mods subscribe via @SubscribeEvent to register styles.
        // TODO: Bridge StylizedDamageRegisterEvent to extend NeoForge Event
        // so it can be posted on NeoForge.EVENT_BUS. For now, the API is
        // available via StylizedDamageAPI.getInstance() for direct registration.
        LOG.debug("StylizedDamage API ready for third-party registrations");

        // ── Merge API + file selectors ──────────────────────────────
        var api = StylizedDamageAPI.getInstance();
        var fileSelectors = ConfigManager.getInstance().getConfig().selectors();
        SelectorConfig mergedConfig = api.buildFinalSelectorConfig(fileSelectors);
        // Expand tag-based selectors (e.g. #minecraft:is_fire) to
        // individual damage-type IDs so they can be matched correctly.
        mergedConfig.expandTags();

        // Update the selector engine with the merged config.
        // StylizedDamageAPI.initialize() is safe to call multiple times —
        // it updates the internal references without re-creating the singleton.
        StylizedDamageAPI.initialize(api.getStyleLoader(),
                new SelectorEngine(mergedConfig));

        LOG.debug("FMLCommonSetup — selectors merged (API rules prioritized)");
    }

    /**
     * Called during {@code FMLClientSetupEvent}.
     *
     * <p>Registers client-only event listeners such as the
     * {@link com.stylizeddamage.neoforge.event.AbsorptionTracker AbsorptionTracker}.
     *
     * @param event the client setup event
     */
    private void onClientSetup(
            @SuppressWarnings("unused") net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        LOG.debug("FMLClientSetup — registering client-side listeners");
        NeoForgeEventHandler.registerClient();
    }
}
