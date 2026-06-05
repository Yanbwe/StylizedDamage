package com.stylizeddamage.neoforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.stylizeddamage.common.animation.AnimationState;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.config.DistanceScaleConfig;
import com.stylizeddamage.common.damage.DamageCalculator;
import com.stylizeddamage.common.damage.ScreenPosition;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.style.color.ColorSource;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Central renderer for floating damage numbers on the HUD layer (NeoForge 1.21.1).
 * Ported from forge-1.20.1 with neoforge-specific API adaptations.
 */
public final class DamageNumberRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation HUD_ID =
            ResourceLocation.fromNamespaceAndPath("stylizeddamage", "damage_numbers");

    private static volatile DamageNumberRenderer instance;

    public static DamageNumberRenderer getInstance() { return instance; }

    private final Minecraft minecraft;
    private final List<ActiveDamageNumber> activeNumbers = new ArrayList<>();
    private int clientTick = 0;

    public DamageNumberRenderer(final Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        instance = this;
    }

    /** Register HUD overlay via NeoForge's RegisterGuiLayersEvent on the mod bus. */
    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(RegisterGuiLayersEvent.class, event -> {
            DamageNumberRenderer r = instance != null ? instance : new DamageNumberRenderer(Minecraft.getInstance());
            event.registerAboveAll(HUD_ID, r::renderOverlay);
            LOGGER.info("Registered StylizedDamage NeoForge HUD layer");
        });
    }

    // ── Per-frame rendering ─────────────────────────────────────────────

    /** NeoForge layered draw callback: (GuiGraphics, DeltaTracker). */
    public void renderOverlay(final GuiGraphics guiGraphics, final DeltaTracker deltaTracker) {
        clientTick++;
        if (!DisplaySettings.isDisplayEnabled()) return;
        final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        tick();

        final Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) return;

        final Font font = minecraft.font;
        if (font == null) return;

        final LocalPlayer player = minecraft.player;
        if (player == null) return;

        // Fast-path: skip synchronized block + allocation when idle (most frames)
        if (activeNumbers.isEmpty()) return;

        final List<ActiveDamageNumber> snapshot;
        synchronized (activeNumbers) {
            if (activeNumbers.isEmpty()) return; // Double-check within lock
            snapshot = new ArrayList<>(activeNumbers);
        }

        // Fetch config once per frame — re-use across all renderSingle calls
        final CommonConfig config = getConfig();

        // Pass 1: normal numbers (skip kill-type)
        for (final ActiveDamageNumber active : snapshot) {
            if ("kill".equals(active.packet().damageTypeId())) continue;
            renderSingle(active, guiGraphics, font, camera, player, partialTick, screenWidth, screenHeight, config);
        }

        // Pass 2: kill numbers (rendered on top)
        for (final ActiveDamageNumber active : snapshot) {
            if (!"kill".equals(active.packet().damageTypeId())) continue;
            renderSingle(active, guiGraphics, font, camera, player, partialTick, screenWidth, screenHeight, config);
        }
    }

    // ── Tick ────────────────────────────────────────────────────────────

    private void tick() {
        synchronized (activeNumbers) {
            final Iterator<ActiveDamageNumber> it = activeNumbers.iterator();
            while (it.hasNext()) {
                if (it.next().isComplete(clientTick)) it.remove();
            }
        }
    }

    // ── Single number rendering ─────────────────────────────────────────

    private void renderSingle(
            final ActiveDamageNumber active,
            final GuiGraphics guiGraphics,
            final Font font,
            final Camera camera,
            final LocalPlayer player,
            final float partialTick,
            final int screenWidth,
            final int screenHeight,
            final CommonConfig config) {

        // 1. Project fixed world position
        ScreenPosition screenPos = EntityScreenMapper.worldToScreen(
                camera, active.worldX(), active.worldY(), active.worldZ(),
                screenWidth, screenHeight);
        if (screenPos == null) return;

        // 2. Distance scaling (uses stored world position; survives entity death)
        final double dx = active.worldX() - player.getX();
        final double dy = active.worldY() - player.getY();
        final double dz = active.worldZ() - player.getZ();
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final Style style = active.style();
        final DistanceScaleConfig distCfg = config != null ? config.distanceScale() : DistanceScaleConfig.defaults();
        final double distanceScale = DamageCalculator.distanceScale(
                distance, style.fontSize(), distCfg.segment(), distCfg.factor(),
                distCfg.min(), distCfg.maxDisplayDistance());
        if (distanceScale <= 0.0) return;

        // 3. Animation state (smooth with partialTick)
        final AnimationState animState = active.tick(clientTick + partialTick);

        // 4. Position offset scaled by distance
        final double posScale = distanceScale / Math.max(style.fontSize(), 0.01);
        final double offsetX = (animState.offsetX() + active.overlapOffsetX()) * posScale;
        final double offsetY = (animState.offsetY() + active.overlapOffsetY()) * posScale;
        final double posX = screenPos.x() + offsetX;
        final double posY = screenPos.y() + offsetY;
        if (posX < -200 || posX > screenWidth + 200 || posY < -200 || posY > screenHeight + 200) return;

        // 5. Final scale
        final double dmgScale = style.damageScale().computeScale(active.packet().damage());
        final double finalScale = distanceScale * dmgScale * animState.scale();

        // 6. Color
        final int relativeTick = clientTick - active.createTick();
        final float progress = Math.max(0f, Math.min(1f, relativeTick / (float) Math.max(1, style.animation().holdTicks() + 10)));
        final ColorSource colorSource = style.color();
        final double finalOpacity = style.bypassDisplayOpacity()
                ? animState.opacity()
                : animState.opacity() * active.displayOpacity();
        int argb = ColorRenderer.toArgb(colorSource, progress, clientTick,
                animState.brightnessOffset(),
                finalOpacity);
        if (!animState.isComplete() && (argb >>> 24) == 0) {
            argb = (argb & 0x00FFFFFF) | 0x01000000;
        }

        // 7. Build text
        final boolean isKill = "kill".equals(active.packet().damageTypeId());
        final String prefix = style.prefix();
        final String suffix = style.suffix();
        final String damageText = isKill && style.killText() != null
                ? style.killText()
                : formatDamage(active.packet().damage());
        final String displayText = prefix + damageText + suffix;

        // 7b. Draw icon
        final int ipx = (int) Math.round(posX);
        final int ipy = (int) Math.round(posY);
        final int textH = (int) (font.lineHeight * finalScale);
        final boolean iconRight = "right".equals(style.iconPosition());
        final int textW = (int) (font.width(displayText) * finalScale);
        final int iconShift = drawIcon(guiGraphics, style.icon(),
                ipx, ipy, textH, (float) finalScale, argb, iconRight, textW,
                style.iconOffsetX(), style.iconOffsetY());

        // 8. Draw text (offset by icon shift when icon is on the left)
        drawText(guiGraphics, font, displayText,
                ipx + iconShift, ipy,
                (float) finalScale, argb, style.shadow());
    }

    // ── Text drawing ────────────────────────────────────────────────────

    private void drawText(GuiGraphics guiGraphics, Font font, String text,
                          int x, int y, float scale, int color, boolean shadow) {
        if (text.isEmpty() || scale <= 0.01f) return;
        final com.mojang.blaze3d.vertex.PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        final int textWidth = font.width(text);
        guiGraphics.drawString(font, text, -textWidth / 2, 0, color, shadow);
        poseStack.popPose();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    public void enqueue(final ActiveDamageNumber number) {
        Objects.requireNonNull(number, "number");
        synchronized (activeNumbers) { activeNumbers.add(number); }
    }

    public int getClientTick() { return clientTick; }

    public int getScreenCenterX() { return minecraft.getWindow().getGuiScaledWidth() / 2; }

    public int getScreenCenterY() { return minecraft.getWindow().getGuiScaledHeight() / 2; }

    public CommonConfig getConfig() {
        try { return ConfigManager.getInstance().getConfig(); }
        catch (final IllegalStateException e) { return null; }
    }

    private static String formatDamage(final float damage) {
        if (damage == (int) damage) return String.valueOf((int) damage);
        return String.format("%.1f", damage);
    }

    private static int drawIcon(final GuiGraphics guiGraphics, final String iconPath,
                                 final int centerX, final int centerY,
                                 final int textHeight,
                                 final float scale, final int argb,
                                 final boolean rightSide, final int textWidth,
                                 final double offsetX, final double offsetY) {
        if (iconPath == null || iconPath.isEmpty()) return 0;
        final ResourceLocation loc = ResourceLocation.tryParse(iconPath);
        if (loc == null) return 0;
        final int size = Math.max(1, (int) Math.round(8.0 * scale));
        final int iconX = (rightSide
                ? centerX + textWidth / 2
                : centerX - (textWidth + size) / 2)
                + (int) Math.round(offsetX);
        final int iconY = centerY - size / 2 + (int) Math.round(offsetY);

        final float a = ((argb >> 24) & 0xFF) / 255.0f;
        final float r = ((argb >> 16) & 0xFF) / 255.0f;
        final float g = ((argb >> 8) & 0xFF) / 255.0f;
        final float b = (argb & 0xFF) / 255.0f;

        RenderSystem.setShaderTexture(0, loc);
        RenderSystem.setShaderColor(r, g, b, a);
        RenderSystem.enableBlend();
        guiGraphics.blit(loc, iconX, iconY, 0, 0, size, size, size, size);

        return rightSide ? 0 : size / 2;
    }
}
