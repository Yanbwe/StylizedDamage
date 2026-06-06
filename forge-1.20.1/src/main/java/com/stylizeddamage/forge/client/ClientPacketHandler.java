package com.stylizeddamage.forge.client;

import com.mojang.logging.LogUtils;
import com.stylizeddamage.common.animation.AnimationConfig;
import com.stylizeddamage.common.damage.ScreenPosition;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import com.stylizeddamage.common.selector.EntityClassifier;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.selector.StyleMatchResult;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.config.DisplayOpacityConfig;
import com.stylizeddamage.common.style.DamageScaleConfig;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Client-side packet handler that bridges incoming damage network packets
 * into the HUD rendering queue.
 *
 * <p>Registered via {@code ForgeNetworkRegistrar#setHandler} during client
 * setup. Each incoming {@link DamageSyncPacket} is resolved through the
 * style selector, wrapped in an {@link ActiveDamageNumber}, and pushed
 * into the {@link DamageNumberRenderer} queue.
 *
 * <p>Thread-safety: packet handling occurs on the main thread (Forge's
 * {@code consumerMainThread} guarantees this). The renderer queue is
 * accessed only from the main/render thread.
 */
public final class ClientPacketHandler implements PacketHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The damage number renderer that owns the rendering queue. */
    private final DamageNumberRenderer renderer;

    /** The total-damage HUD panel renderer (may be null if panel is disabled). */
    private final TotalDamageHudRenderer totalDamageRenderer;

    /** The Minecraft client instance (valid on physical client). */
    private final Minecraft minecraft;

    /**
     * Creates a handler that pushes damage packets into the given renderer.
     *
     * @param renderer the HUD renderer (must not be null)
     * @param minecraft the Minecraft client instance
     */
    public ClientPacketHandler(final DamageNumberRenderer renderer, final Minecraft minecraft) {
        this(renderer, null, minecraft);
    }

    /**
     * Creates a handler with an optional total-damage HUD renderer.
     *
     * @param renderer             the HUD renderer (must not be null)
     * @param totalDamageRenderer  the total-damage panel renderer (may be null)
     * @param minecraft            the Minecraft client instance
     */
    public ClientPacketHandler(final DamageNumberRenderer renderer,
                                final TotalDamageHudRenderer totalDamageRenderer,
                                final Minecraft minecraft) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.totalDamageRenderer = totalDamageRenderer;
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    // ── PacketHandler implementation ──────────────────────────────────

    /**
     * Handles an incoming damage sync packet.
     *
     * <ol>
     *   <li>Validates the packet data against the config minimum.</li>
     *   <li>Resolves the entity from the client world.</li>
     *   <li>Matches a style via the selector engine.</li>
     *   <li>Creates and enqueues an {@link ActiveDamageNumber}.</li>
     * </ol>
     */
    @Override
    public void handleDamageSync(final DamageSyncPacket packet) {
        if (packet == null) {
            return;
        }

        // Validate damage amount against config minimum (skip for kill type)
        final boolean isKill = "kill".equals(packet.damageTypeId());
        if (!isKill) {
            final double minDisplay = renderer.getConfig() != null
                    ? renderer.getConfig().minDamageDisplay()
                    : 0.1;
            if (packet.damage() < minDisplay) {
                return;
            }
        }

        // ── Resolve the entity (needed for style matching + distance tracking) ──
        final int entityId = packet.targetEntityId();
        final Entity entity = getEntityById(entityId);
        if (entity == null) {
            return;
        }

        // ── Classify the entity for selector matching ──
        final EntityClassifier.EntityType entityType = classifyEntity(entity);

        // ── Style matching via selector engine ──
        final StyleMatchResult matchResult = resolveMatchResult(packet, entityType);
        if (matchResult == null) {
            return; // No matching style — don't render
        }
        final String styleName = matchResult.styleName();
        final Style style = resolveStyleByName(styleName);
        if (style == null) {
            return;
        }

        // Total damage only accumulates via TotalDamageSyncPacket
        // (sent by server only for player-caused damage)

        // ── Resolve animation (one-time random evaluation) ──
        final Random random = new Random();
        AnimationConfig.Resolved resolvedAnim = style.animation().resolve(random);

        // ── Apply damage-based hold scaling ──
        final DamageScaleConfig dmgCfg = style.damageScale();
        final int extraHold = dmgCfg.computeHoldExtra(packet.damage());
        if (extraHold > 0) {
            resolvedAnim = new AnimationConfig.Resolved(
                    resolvedAnim.holdTicks() + extraHold,
                    resolvedAnim.position(),
                    resolvedAnim.size(),
                    resolvedAnim.brightness(),
                    resolvedAnim.opacity());
        }

        // ── Use server-authoritative hit position from the packet ──
        // This is the exact world position where the damage landed, computed
        // server-side (projectile impact point, melee ray trace result, or
        // target body centre as fallback).
        final double wx = packet.hitX();
        final double wy = packet.hitY();
        final double wz = packet.hitZ();

        // ── Calculate initial screen position from the hit world position ──
        final ScreenPosition zeroPos;
        final Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera != null && camera.isInitialized()) {
            final int sw = minecraft.getWindow().getGuiScaledWidth();
            final int sh = minecraft.getWindow().getGuiScaledHeight();
            final ScreenPosition projected = EntityScreenMapper.worldToScreen(
                    camera, wx, wy, wz, sw, sh);
            zeroPos = projected != null ? projected : new ScreenPosition(sw / 2, sh / 2);
        } else {
            zeroPos = new ScreenPosition(
                    minecraft.getWindow().getGuiScaledWidth() / 2,
                    minecraft.getWindow().getGuiScaledHeight() / 2);
        }

        // ── Look up global opacity override (based on damage SOURCE) ──
        final Entity sourceEntity = getEntityById(packet.sourceEntityId());
        final double displayOpacity = computeDisplayOpacity(sourceEntity != null ? sourceEntity : entity);

        // ── Create the ActiveDamageNumber ──
        final long now = System.currentTimeMillis();
        final ActiveDamageNumber activeNumber = new ActiveDamageNumber(
                packet, style, resolvedAnim, zeroPos, now, random, entityId,
                0.0, 0.0, wx, wy, wz, displayOpacity);

        // ── Enqueue (renderer handles overlap and max queue limits) ──
        renderer.enqueue(activeNumber);
    }

    /**
     * Handles an incoming total damage sync packet by accumulating into
     * the total-damage HUD panel renderer.
     */
    @Override
    public void handleTotalDamageSync(final TotalDamageSyncPacket packet) {
        if (packet == null || totalDamageRenderer == null) {
            return;
        }
        if (packet.reset()) {
            totalDamageRenderer.reset();
        } else {
            final float damage = packet.damage() != null ? packet.damage() : 0f;
            final String damageTypeId = packet.styleName() != null ? packet.styleName() : "player";

            // Match style via SelectorEngine (same as handleDamageSync)
            String styleName = "default";
            try {
                final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
                final var matchResult = api.getSelectorEngine().match(
                        damage, damageTypeId, EntityClassifier.EntityType.PLAYER,
                        "common", false);
                if (matchResult.isPresent()) {
                    styleName = matchResult.get().styleName();
                }
            } catch (Exception ignored) {}

            totalDamageRenderer.accumulate(damage, styleName);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Resolves the entity by its network ID from the client world.
     * Returns {@code null} if the entity is no longer loaded/tracked.
     */
    private Entity getEntityById(final int entityId) {
        if (minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(entityId);
    }

    /**
     * Classifies the given entity for the selector engine.
     */
    private static EntityClassifier.EntityType classifyEntity(final Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return EntityClassifier.EntityType.PLAYER;
        }
        // For mobs, use the MobCategory name
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            final String categoryName = mob.getType().getCategory().name().toLowerCase();
            if (EntityClassifier.isHostile(categoryName)) {
                return EntityClassifier.EntityType.MOB_HOSTILE;
            }
            if (EntityClassifier.isPassive(categoryName)) {
                return EntityClassifier.EntityType.MOB_PASSIVE;
            }
        }
        return EntityClassifier.EntityType.OTHER;
    }

    /**
     * Resolves the matched style result for a damage packet using the selector engine.
     * Returns {@code null} if no match was found or the API is not initialized.
     */
    private StyleMatchResult resolveMatchResult(final DamageSyncPacket packet,
                                                  final EntityClassifier.EntityType entityType) {
        final StylizedDamageAPI api;
        try {
            api = StylizedDamageAPI.getInstance();
        } catch (final IllegalStateException e) {
            LOGGER.warn("API not initialised — skipping style resolution");
            return null;
        }

        final SelectorEngine selectorEngine = api.getSelectorEngine();
        final String damageType = packet.damageTypeId();

        // Team relation: can't determine from packet alone; use "common"
        final String teamRelation = "common";

        final Optional<StyleMatchResult> matchResult = selectorEngine.match(
                packet.damage(),
                damageType,
                entityType,
                teamRelation,
                packet.isCritical());

        return matchResult.orElse(null);
    }

    /**
     * Looks up a Style by name from the StyleLoader.
     * Returns the default style if the named style is not found.
     */
    private static Style resolveStyleByName(final String styleName) {
        try {
            final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
            final Map<String, Style> styles = api.getStyleLoader().getStyles();
            final Style matched = styles.get(styleName);
            if (matched != null) {
                return matched;
            }
            return styles.getOrDefault("default", Style.createDefault());
        } catch (final IllegalStateException e) {
            return Style.createDefault();
        }
    }

    /**
     * Looks up the global display opacity for the given entity
     * from {@code displayOpacity} config.
     */
    private static double computeDisplayOpacity(final Entity entity) {
        try {
            final DisplayOpacityConfig op = ConfigManager.getInstance()
                    .getConfig().displayOpacity();
            final boolean isPlayer = entity instanceof net.minecraft.world.entity.player.Player;
            final boolean isMob = entity instanceof net.minecraft.world.entity.Mob;
            boolean isHostile = false;
            if (isMob) {
                final String cat = ((net.minecraft.world.entity.Mob) entity)
                        .getType().getCategory().name().toLowerCase();
                isHostile = com.stylizeddamage.common.selector.EntityClassifier.isHostile(cat);
            }
            return op.getOpacity(isPlayer, isMob, isHostile);
        } catch (final Exception e) {
            return 1.0;
        }
    }
}
