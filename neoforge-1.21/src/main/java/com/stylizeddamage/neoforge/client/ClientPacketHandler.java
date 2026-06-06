package com.stylizeddamage.neoforge.client;

import com.mojang.logging.LogUtils;
import com.stylizeddamage.common.animation.AnimationConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.config.DisplayOpacityConfig;
import com.stylizeddamage.common.damage.ScreenPosition;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.PacketHandler;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import com.stylizeddamage.common.selector.EntityClassifier;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.selector.StyleMatchResult;
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

public final class ClientPacketHandler implements PacketHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final DamageNumberRenderer renderer;
    private final TotalDamageHudRenderer totalDamageRenderer;
    private final Minecraft minecraft;

    public ClientPacketHandler(final DamageNumberRenderer renderer, final Minecraft minecraft) {
        this(renderer, null, minecraft);
    }

    public ClientPacketHandler(final DamageNumberRenderer renderer,
                                final TotalDamageHudRenderer totalDamageRenderer,
                                final Minecraft minecraft) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.totalDamageRenderer = totalDamageRenderer;
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public void handleDamageSync(final DamageSyncPacket packet) {
        if (packet == null) return;
        final boolean isKill = "kill".equals(packet.damageTypeId());
        if (!isKill) {
            final double minDisplay = renderer.getConfig() != null
                    ? renderer.getConfig().minDamageDisplay() : 0.1;
            if (packet.damage() < minDisplay) return;
        }
        final int entityId = packet.targetEntityId();
        final Entity entity = getEntityById(entityId);
        if (entity == null) return;
        final EntityClassifier.EntityType entityType = classifyEntity(entity);
        final StyleMatchResult matchResult = resolveMatchResult(packet, entityType);
        if (matchResult == null) return;
        final String styleName = matchResult.styleName();
        final Style style = resolveStyleByName(styleName);
        if (style == null) return;
        // Total damage only accumulates via TotalDamageSyncPacket (player-caused damage)
        final Random random = new Random();
        AnimationConfig.Resolved resolvedAnim = style.animation().resolve(random);

        // Apply damage-based hold scaling
        final com.stylizeddamage.common.style.DamageScaleConfig dmgCfg = style.damageScale();
        final int extraHold = dmgCfg.computeHoldExtra(packet.damage());
        if (extraHold > 0) {
            resolvedAnim = new AnimationConfig.Resolved(
                    resolvedAnim.holdTicks() + extraHold,
                    resolvedAnim.position(),
                    resolvedAnim.size(),
                    resolvedAnim.brightness(),
                    resolvedAnim.opacity());
        }

        // Use server-authoritative hit position from the packet
        final double wx = packet.hitX();
        final double wy = packet.hitY();
        final double wz = packet.hitZ();

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
        final long now = System.currentTimeMillis();
        final Entity sourceEntity = getEntityById(packet.sourceEntityId());
        final double displayOpacity = computeDisplayOpacity(sourceEntity != null ? sourceEntity : entity);
        final ActiveDamageNumber activeNumber = new ActiveDamageNumber(
                packet, style, resolvedAnim, zeroPos, now, random, entityId,
                0.0, 0.0, wx, wy, wz, displayOpacity);
        renderer.enqueue(activeNumber);
    }

    @Override
    public void handleTotalDamageSync(final TotalDamageSyncPacket packet) {
        if (packet == null || totalDamageRenderer == null) return;
        if (packet.reset()) {
            totalDamageRenderer.reset();
        } else {
            final float damage = packet.damage() != null ? packet.damage() : 0f;
            final String damageTypeId = packet.styleName() != null ? packet.styleName() : "player";
            String styleName = "default";
            try {
                final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
                final var match = api.getSelectorEngine().match(
                        damage, damageTypeId, EntityClassifier.EntityType.PLAYER, "common", false);
                if (match.isPresent()) styleName = match.get().styleName();
            } catch (Exception ignored) {}
            totalDamageRenderer.accumulate(damage, styleName);
        }
    }

    private Entity getEntityById(final int entityId) {
        if (minecraft.level == null) return null;
        return minecraft.level.getEntity(entityId);
    }

    private static EntityClassifier.EntityType classifyEntity(final Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return EntityClassifier.EntityType.PLAYER;
        }
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            final String categoryName = mob.getType().getCategory().name().toLowerCase();
            if (EntityClassifier.isHostile(categoryName)) return EntityClassifier.EntityType.MOB_HOSTILE;
            if (EntityClassifier.isPassive(categoryName)) return EntityClassifier.EntityType.MOB_PASSIVE;
        }
        return EntityClassifier.EntityType.OTHER;
    }

    private StyleMatchResult resolveMatchResult(final DamageSyncPacket packet,
                                                  final EntityClassifier.EntityType entityType) {
        final StylizedDamageAPI api;
        try { api = StylizedDamageAPI.getInstance(); }
        catch (final IllegalStateException e) { return null; }
        final SelectorEngine selectorEngine = api.getSelectorEngine();
        final Optional<StyleMatchResult> matchResult = selectorEngine.match(
                packet.damage(), packet.damageTypeId(), entityType, "common", packet.isCritical());
        return matchResult.orElse(null);
    }

    private static Style resolveStyleByName(final String styleName) {
        try {
            final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
            final Map<String, Style> styles = api.getStyleLoader().getStyles();
            final Style matched = styles.get(styleName);
            if (matched != null) return matched;
            return styles.getOrDefault("default", null);
        } catch (final IllegalStateException e) { return null; }
    }

    private static double computeDisplayOpacity(final Entity entity) {
        try {
            final DisplayOpacityConfig op = ConfigManager.getInstance()
                    .getConfig().displayOpacity();
            final boolean isPlayer = entity instanceof net.minecraft.world.entity.player.Player;
            final boolean isMob = entity instanceof net.minecraft.world.entity.Mob;
            boolean isHostile = false;
            if (isMob) {
                final String cat = ((net.minecraft.world.entity.Mob) entity)
                        .getType().getCategory().getName().toLowerCase();
                isHostile = EntityClassifier.isHostile(cat);
            }
            return op.getOpacity(isPlayer, isMob, isHostile);
        } catch (final Exception e) { return 1.0; }
    }
}
