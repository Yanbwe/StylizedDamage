package com.stylizeddamage.forge;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.stylizeddamage.common.Platform;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.selector.SelectorConfig;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.style.StyleLoader;
import com.stylizeddamage.forge.client.ClientPacketHandler;
import com.stylizeddamage.forge.client.DamageNumberRenderer;
import com.stylizeddamage.forge.client.TotalDamageHudRenderer;
import com.stylizeddamage.forge.command.StylizedDamageCommand;
import com.stylizeddamage.forge.event.AbsorptionTracker;
import com.stylizeddamage.forge.event.DamageEventListener;
import com.stylizeddamage.forge.event.HealEventListener;
import com.stylizeddamage.forge.network.ForgeNetworkRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Main mod class for the Forge 1.20.1 platform module.
 *
 * <p>Entry point registered by the {@code @Mod} annotation. Responsible for
 * initialising the shared logic from {@code common}, registering event
 * listeners, and setting up the platform-specific infrastructure.
 */
@Mod(StylizedDamageForge.MOD_ID)
public final class StylizedDamageForge {

    /** Mod identifier used across registries and metadata. */
    public static final String MOD_ID = "stylizeddamage";

    /** Platform abstraction instance — used by common logic when needed. */
    public static final Platform PLATFORM = new ForgePlatform();

    /** Network registrar for the Forge 1.20.1 SimpleChannel. */
    public static final ForgeNetworkRegistrar NETWORK = new ForgeNetworkRegistrar();

    private static final Logger LOGGER = LogUtils.getLogger();

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
                "sizeOffsetPerStep": 0.5,
                "maxSize": 3.0,
                "holdBase": 0,
                "holdOffsetPerStep": 40,
                "holdMax": 200
              },
              "animation": {
                "hold": 10,
                "position": {
                  "enter": {
                    "type": "normal",
                    "duration": 30,
                    "easing": { "in": false, "out": true },
                    "startOffset": { "type": "xy", "x": { "base": 2, "random": [-2, 2] }, "y": { "base": 2, "random": [-2, 2] } },
                    "targetOffset": { "type": "direction", "angle": { "base": 90, "random": [-1, 1] }, "distance": { "base": 20, "random": [-2, 2] } }
                  },
                  "exit": { "type": "none" }
                },
                "size": {
                  "enter": {
                    "type": "normal",
                    "duration": 40,
                    "easing": { "in": true, "out": true },
                    "startOffset": 0.3,
                    "targetOffset": 0
                  },
                  "exit": {
                    "type": "normal",
                    "duration": 40,
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
                    "duration": 10,
                    "easing": { "in": true, "out": true },
                    "startOpacity": 0,
                    "targetOpacity": 1
                  },
                  "exit": {
                    "type": "normal",
                    "duration": 40,
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
              "bypassDisplayOpacity": true
            }""";

    private static final String FIRE_STYLE_JSON = """
            {
              "color": "#FF3F00",
              "prefix": "🔥",
              "bypassDisplayOpacity": true
            }""";

    private static final String MAGIC_STYLE_JSON = """
            {
              "color": "#00FFFF",
              "prefix": "💔"
            }""";

    /**
     * Constructs the mod instance.
     *
     * <p>Forge calls this constructor automatically during mod discovery.
     * The {@link FMLJavaModLoadingContext} provides access to the mod event bus
     * for lifecycle events, while {@link MinecraftForge#EVENT_BUS} handles
     * gameplay events.
     *
     * @param context the Forge mod loading context (injected by the loader)
     */
    public StylizedDamageForge() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ── Initialize network channel early (before event listeners need it) ──
        NETWORK.registerPackets(null);

        // ── Common setup on the mod event bus ──
        modEventBus.addListener(this::commonSetup);

        // ── Register gameplay event listeners on the Forge bus ──
        // Server-side damage and heal event listeners (inject network registrar)
        MinecraftForge.EVENT_BUS.register(new DamageEventListener(NETWORK));
        MinecraftForge.EVENT_BUS.register(new HealEventListener(NETWORK));

        // ── Register command handler on the Forge bus ──
        MinecraftForge.EVENT_BUS.register(new StylizedDamageCommand());

        // ── Client-only: register HUD overlays BEFORE FMLClientSetupEvent ──
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> clientRegisterOverlays(modEventBus));
    }

    /**
     * Registers HUD overlays on the MOD bus. Must be called in the constructor
     * so the listener is registered before {@code RegisterGuiOverlaysEvent} fires.
     */
    private void clientRegisterOverlays(final IEventBus modEventBus) {
        modEventBus.addListener((net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) -> {
            final Minecraft minecraft = Minecraft.getInstance();
            
            // Create renderers without mod bus (overlay registration done directly below)
            final DamageNumberRenderer renderer = new DamageNumberRenderer(minecraft);
            final TotalDamageHudRenderer totalRenderer = new TotalDamageHudRenderer(minecraft);
            
            // Register HUD overlays via the event directly
            event.registerAboveAll("stylizeddamage_hud", renderer::renderOverlay);
            event.registerAboveAll("stylizeddamage_total_hud", totalRenderer::renderOverlay);
            
            final ClientPacketHandler clientHandler = new ClientPacketHandler(renderer, totalRenderer, minecraft);
            NETWORK.setHandler(clientHandler);
            MinecraftForge.EVENT_BUS.register(new AbsorptionTracker(clientHandler));
            LOGGER.info("StylizedDamage HUD overlays registered.");
        });
    }

    /**
     * Lifecycle handler for {@link FMLCommonSetupEvent}.
     * Loads configuration and initialises the shared API.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("StylizedDamage Forge 1.20.1 initialising...");

        // Ensure the config directory and styles subdirectory exist
        final Path configDir = PLATFORM.getConfigDir();
        final Path stylesDir = configDir.resolve("styles");
        try {
            Files.createDirectories(stylesDir);
            // Generate default style files if they don't exist
            for (var entry : new String[][]{
                    {"default.json", DEFAULT_STYLE_JSON},
                    {"heal.json", HEAL_STYLE_JSON},
                    {"absorption.json", ABSORPTION_STYLE_JSON},
                    {"kill.json", KILL_STYLE_JSON},
                    {"fire.json", FIRE_STYLE_JSON},
                    {"magic.json", MAGIC_STYLE_JSON},
            }) {
                final Path path = stylesDir.resolve(entry[0]);
                if (!Files.exists(path)) {
                    Files.writeString(path, entry[1]);
                    LOGGER.info("Created style: {}", path);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to create config directories: {}", configDir, e);
        }

        // Initialise ConfigManager singleton — loads common.json (or creates defaults)
        ConfigManager.initialize(configDir);

        // Initialise StyleLoader for .json style files in config/stylizeddamage/styles/
        final StyleLoader styleLoader = new StyleLoader(stylesDir);
        styleLoader.load();

        // Build a SelectorConfig from the loaded config's raw selectors
        final CommonConfig commonConfig = ConfigManager.getInstance().getConfig();
        final SelectorConfig selectorConfig = SelectorConfig.from(
                buildSelectorsJson(commonConfig.selectors()));

        // Expand damage type tags in selectors
        selectorConfig.expandTags();

        // Create the SelectorEngine backed by the current selector rules
        final SelectorEngine selectorEngine = new SelectorEngine(selectorConfig);

        // Bootstrap the API singleton (third-party mods can now register)
        StylizedDamageAPI.initialize(styleLoader, selectorEngine);

        LOGGER.info("StylizedDamage Forge 1.20.1 initialised successfully.");
    }

    /**
     * Converts the {@code Map<String, JsonObject>} selector representation
     * (as stored in {@link CommonConfig}) into a single {@link JsonObject}
     * suitable for {@link SelectorConfig#from(JsonObject)}.
     */
    private static JsonObject buildSelectorsJson(final Map<String, JsonObject> selectors) {
        final JsonObject root = new JsonObject();
        for (final Map.Entry<String, JsonObject> entry : selectors.entrySet()) {
            root.add(entry.getKey(), entry.getValue());
        }
        return root;
    }
}
