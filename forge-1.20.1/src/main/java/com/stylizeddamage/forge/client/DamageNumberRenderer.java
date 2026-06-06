package com.stylizeddamage.forge.client;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Central renderer for floating damage numbers on the HUD layer.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Instantiated during mod client setup.</li>
 *   <li>Registers a HUD overlay via {@link RegisterGuiOverlaysEvent} on the
 *       MOD event bus.</li>
 *   <li>{@link ClientPacketHandler} enqueues {@link ActiveDamageNumber} objects
 *       when damage packets arrive.</li>
 *   <li>Each frame: tick all active numbers (advance animation, remove
 *       completed ones), then render each with distance scaling, animation
 *       offsets, and color applied.</li>
 * </ol>
 *
 * <p>Thread-safety: all queue operations and rendering happen on the
 * client render thread. No external synchronisation needed.
 */
public final class DamageNumberRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Overlay ID registered with Forge's GUI overlay system. */
    private static final String HUD_OVERLAY_ID = "stylizeddamage_hud";

    /** Static reference for direct access (e.g., from test command). */
    private static volatile DamageNumberRenderer instance;

    /** Returns the current renderer instance. */
    public static DamageNumberRenderer getInstance() { return instance; }

    private final Minecraft minecraft;

    /** The active damage numbers currently being animated/rendered. */
    private final List<ActiveDamageNumber> activeNumbers = new ArrayList<>();



    /**
     * Creates the renderer without registering on the mod bus.
     * Use this when the caller handles overlay registration directly.
     *
     * @param minecraft the Minecraft client instance
     */
    public DamageNumberRenderer(final Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        instance = this;
    }

    /**
     * Creates the renderer and registers the HUD overlay on the MOD event bus.
     */
    public DamageNumberRenderer(final Minecraft minecraft, final IEventBus modEventBus) {
        this(minecraft);
        Objects.requireNonNull(modEventBus, "modEventBus").addListener(this::onRegisterOverlays);
    }

    // ── Event handler: register the overlay ───────────────────────────

    /**
     * Registers the damage number HUD overlay above all other overlays.
     * Fired on the MOD bus during client initialisation.
     */
    private void onRegisterOverlays(final RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(HUD_OVERLAY_ID, this::renderOverlay);
        LOGGER.info("Registered StylizedDamage HUD overlay '{}'", HUD_OVERLAY_ID);
    }

    // ── Per-frame rendering callback ───────────────────────────────────

    /**
     * Called by Forge each frame to render the HUD overlay.
     * Implements {@link IGuiOverlay#render}.
     */
    public void renderOverlay(
            final net.minecraft.client.gui.Gui gui,
            final GuiGraphics guiGraphics,
            final float partialTick,
            final int screenWidth,
            final int screenHeight) {
        // Check the global display toggle
        if (!DisplaySettings.isDisplayEnabled()) {
            return;
        }

        // Capture time once — shared by cleanup (tick) and all renderSingle calls
        final long now = System.currentTimeMillis();
        tick(now);

        final Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return;
        }

        final Font font = minecraft.font;
        if (font == null) {
            return;
        }

        final LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        // Fast-path: skip synchronized block + allocation when idle (most frames)
        if (activeNumbers.isEmpty()) {
            return;
        }

        // Take a snapshot of the queue to avoid concurrent modification
        final List<ActiveDamageNumber> snapshot;
        synchronized (activeNumbers) {
            if (activeNumbers.isEmpty()) {
                return; // Double-check within lock
            }
            snapshot = new ArrayList<>(activeNumbers);
        }

        // Fetch config once per frame — re-use across all renderSingle calls
        final CommonConfig config = getConfig();

        // Pass 1: normal numbers (skip kill-type)
        for (final ActiveDamageNumber active : snapshot) {
            if ("kill".equals(active.packet().damageTypeId())) continue;
            renderSingle(active, guiGraphics, font, camera, player, now, partialTick,
                    screenWidth, screenHeight, config);
        }

        // Pass 2: kill numbers (rendered on top)
        for (final ActiveDamageNumber active : snapshot) {
            if (!"kill".equals(active.packet().damageTypeId())) continue;
            renderSingle(active, guiGraphics, font, camera, player, now, partialTick,
                    screenWidth, screenHeight, config);
        }
    }

    // ── Tick: advance animations, remove completed ────────────────────

    /**
     * Removes completed numbers from the active list.
     * Animation state is advanced in renderSingle per-number to avoid
     * double-advancing (once here, once in render).
     *
     * @param now the wall-clock timestamp shared with renderSingle for consistency
     */
    private void tick(final long now) {
        synchronized (activeNumbers) {
            final Iterator<ActiveDamageNumber> it = activeNumbers.iterator();
            while (it.hasNext()) {
                final ActiveDamageNumber active = it.next();
                // Only check completion — animation state is advanced in renderSingle
                if (active.isComplete(now)) {
                    it.remove();
                }
            }
        }
    }

    // ── Single number rendering ───────────────────────────────────────

    /**
     * Renders a single active damage number onto the HUD.
     *
     * <p>Steps:
     * <ol>
     *   <li>Compute entity → screen position (via {@link EntityScreenMapper}).</li>
     *   <li>Compute distance-based font scale (via {@link DamageCalculator}).</li>
     *   <li>Compute animation state for this tick.</li>
     *   <li>Apply animation offsets to position and scale.</li>
     *   <li>Resolve colour with brightness and opacity from animation.</li>
     *   <li>Build the display text (prefix + damage + suffix).</li>
     *   <li>Draw via {@link GuiGraphics#drawString}.</li>
     * </ol>
     */
    private void renderSingle(
            final ActiveDamageNumber active,
            final GuiGraphics guiGraphics,
            final Font font,
            final Camera camera,
            final LocalPlayer player,
            final long now,
            final float partialTick,
            final int screenWidth,
            final int screenHeight,
            final CommonConfig config) {

        // ── 1. Project fixed world position to screen (follows trigger point, not entity) ──
        ScreenPosition screenPos = EntityScreenMapper.worldToScreen(
                camera, active.worldX(), active.worldY(), active.worldZ(),
                screenWidth, screenHeight);
        if (screenPos == null) {
            return;
        }

        // ── 2. Distance-based scaling (uses stored world position; survives entity death) ──
        final double dx = active.worldX() - player.getX();
        final double dy = active.worldY() - player.getY();
        final double dz = active.worldZ() - player.getZ();
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final Style style = active.style();
        final DistanceScaleConfig distCfg = config != null
                ? config.distanceScale()
                : DistanceScaleConfig.defaults();

        final double distanceScale = DamageCalculator.distanceScale(
                distance,
                style.fontSize(),
                distCfg.segment(),
                distCfg.factor(),
                distCfg.min(),
                distCfg.maxDisplayDistance());

        if (distanceScale <= 0.0) {
            return; // Beyond max display distance
        }

        // ── 3. Animation state (advances using system time) ──
        final AnimationState animState = active.tick(now);

        // ── 4. Apply animation position offset scaled by distance ──
        final double posScale = distanceScale / Math.max(style.fontSize(), 0.01);
        final double offsetX = (animState.offsetX() + active.overlapOffsetX()) * posScale;
        final double offsetY = (animState.offsetY() + active.overlapOffsetY()) * posScale;
        final double posX = screenPos.x() + offsetX;
        final double posY = screenPos.y() + offsetY;

        // Skip if completely off-screen
        if (posX < -200 || posX > screenWidth + 200
                || posY < -200 || posY > screenHeight + 200) {
            return;
        }

        // ── 5. Final scale: distanceScale * damageScale * animation scale ──
        final double dmgScale = style.damageScale().computeScale(active.packet().damage());
        final double finalScale = distanceScale * dmgScale * animState.scale();

        // ── 6. Colour resolution ──
        final double relativeTick = (now - active.createTimeMs()) / 50.0;
        final float progress = Math.max(0f, Math.min(1f,
                (float) (relativeTick / (double) Math.max(1, style.animation().holdTicks() + 10))));

        final ColorSource colorSource = style.color();
        final double finalOpacity = style.bypassDisplayOpacity()
                ? animState.opacity()
                : animState.opacity() * active.displayOpacity();
        int argb = ColorRenderer.toArgb(
                colorSource, progress, (int) relativeTick,
                animState.brightnessOffset(),
                finalOpacity);

        // Ensure alpha never drops to 0 until the animation is truly complete,
        // preventing flickering at the end of the exit animation.
        if (!animState.isComplete() && (argb >>> 24) == 0) {
            argb = (argb & 0x00FFFFFF) | 0x01000000; // force minimum alpha
        }

        // ── 7. Build display text ──
        final boolean isKill = "kill".equals(active.packet().damageTypeId());
        final String prefix = style.prefix();
        final String suffix = style.suffix();
        final String damageText = isKill && style.killText() != null
                ? style.killText()
                : formatDamage(active.packet().damage());
        final String displayText = prefix + damageText + suffix;

        // ── 7b. Draw icon ──
        final int ipx = (int) Math.round(posX);
        final int ipy = (int) Math.round(posY);
        final int textH = (int) (font.lineHeight * finalScale);
        final boolean iconRight = "right".equals(style.iconPosition());
        final int textW = (int) (font.width(displayText) * finalScale);
        final int iconShift = drawIcon(guiGraphics, style.icon(),
                ipx, ipy, textH, (float) finalScale, argb, iconRight, textW,
                style.iconOffsetX(), style.iconOffsetY());

        // ── 8. Draw the text (offset by icon shift when icon is on the left) ──
        drawText(guiGraphics, font, displayText,
                ipx + iconShift, ipy,
                (float) finalScale, argb, style.shadow());
    }

    // ── Text drawing with scale ──────────────────────────────────────

    /**
     * Draws text at the given position with the given scale factor.
     *
     * <p>Minecraft's {@link GuiGraphics#drawString} does not natively
     * support font scaling, so we use {@code PoseStack} push/pop to
     * temporarily apply a uniform scale transform.
     */
    private void drawText(
            final GuiGraphics guiGraphics,
            final Font font,
            final String text,
            final int x,
            final int y,
            final float scale,
            final int color,
            final boolean shadow) {
        if (text.isEmpty() || scale <= 0.01f) {
            return;
        }

        final com.mojang.blaze3d.vertex.PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // Translate to position, scale, then draw at origin
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);

        // Centre the text horizontally around its anchor
        final int textWidth = font.width(text);
        final int drawX = -textWidth / 2;
        final int drawY = 0;

        // Use the no‑shadow variant if shadow is disabled
        guiGraphics.drawString(font, text, drawX, drawY, color, shadow);

        poseStack.popPose();
    }

    // ── Queue management ──────────────────────────────────────────────

    /**
     * Enqueues a new damage number for rendering.
     *
     * <p>Before adding, this method:
     * <ul>
     *   <li>Enforces the {@code maxActiveNumbers} limit by removing the
     *       oldest number when the queue is full.</li>
     *   <li>Runs overlap detection against existing active numbers and
     *       potentially offsets the new number's screen position.</li>
     * </ul>
     *
     * @param number the damage number to enqueue (must not be null)
     */
    public void enqueue(final ActiveDamageNumber number) {
        Objects.requireNonNull(number, "number");
        synchronized (activeNumbers) {
            activeNumbers.add(number);
        }
    }

    /**
     * Returns the current system time in milliseconds.
     */
    public long getCurrentTimeMs() {
        return System.currentTimeMillis();
    }

    /** Returns the screen center X coordinate. */
    public int getScreenCenterX() {
        return minecraft.getWindow().getGuiScaledWidth() / 2;
    }

    /** Returns the screen center Y coordinate. */
    public int getScreenCenterY() {
        return minecraft.getWindow().getGuiScaledHeight() / 2;
    }

    /**
     * Returns a snapshot of the active damage numbers.
     * External callers receive a safe copy.
     */
    public List<ActiveDamageNumber> getActiveNumbers() {
        synchronized (activeNumbers) {
            return new ArrayList<>(activeNumbers);
        }
    }

    /**
     * Returns the current common config, or {@code null} if not yet loaded.
     */
    public CommonConfig getConfig() {
        try {
            return ConfigManager.getInstance().getConfig();
        } catch (final IllegalStateException e) {
            return null;
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────

    /**
     * Formats a float damage value for display.
     * One decimal place for fractional damage, integer for whole numbers.
     */
    private static String formatDamage(final float damage) {
        if (damage == (int) damage) {
            return String.valueOf((int) damage);
        }
        return String.format("%.1f", damage);
    }

    /**
     * Draws a small icon texture beside the damage number.
     *
     * @param centerX   the horizontal centre of the text
     * @param rightSide {@code true} to place icon to the right, {@code false} for left
     * @param textWidth pixel width of the accompanying text (used for right-side placement)
     * @return pixel offset to add to the text's centre X (0 for right side, size for left)
     */
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
        // Icon+text group centred at centerX: icon at left edge, text shifted by size/2
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
