package com.stylizeddamage.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.config.TotalDamageConfig;
import com.stylizeddamage.common.damage.DamageAccumulator;
import com.stylizeddamage.common.damage.TrailEntry;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.style.StyleLoader;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.style.color.ColorSource;
import com.stylizeddamage.common.util.EasingCurve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HUD renderer for the total-damage panel displayed in the top-right corner.
 *
 * <h3>Layout</h3>
 * <pre>
 *          Total: 1,284   ← large, styled by totalDamage.selectors
 *   -32   -24   -12       ← trail entries, newest first, each in its own style
 *  -48
 * </pre>
 *
 * <h3>Animations (configurable via {@code enable*Animation} toggles)</h3>
 * <ul>
 *   <li><b>Entry:</b> total number fades in + overshoots scale (ease-out-back), 12 ticks</li>
 *   <li><b>Exit:</b> total number fades out + shrinks (ease-in), 10 ticks</li>
 *   <li><b>Bounce:</b> total number scale pops on value change (gentle overshoot), 6 ticks, interruptible</li>
 *   <li><b>Trail entry:</b> slides in from right + fades in (ease-out), 8 ticks</li>
 *   <li><b>Trail exit:</b> slides out to left + fades out (ease-in), 8 ticks</li>
 * </ul>
 */
public final class TotalDamageHudRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Animation timing constants (hard-coded) ─────────────────────
    private static final int TOTAL_ENTRY_TICKS = 12;
    private static final int TOTAL_EXIT_TICKS = 10;
    private static final int BOUNCE_TICKS = 6;
    private static final int TRAIL_ENTRY_TICKS = 8;
    private static final int TRAIL_EXIT_TICKS = 8;
    // bounceScalePeak is read from config, no hard-coded default

    // ── Total-number animation phases ───────────────────────────────
    private enum TotalPhase { INACTIVE, ENTERING, ACTIVE, EXITING }

    // ── Trail-entry animation phases ────────────────────────────────
    private enum TrailPhase { ENTERING, ACTIVE, EXITING }

    private static final class TrailAnimState {
        final TrailEntry entry;
        TrailPhase phase;
        int tick;

        TrailAnimState(TrailEntry entry, TrailPhase phase, int tick) {
            this.entry = entry;
            this.phase = phase;
            this.tick = tick;
        }
    }

    private final Minecraft minecraft;

    /** The accumulator driving the panel state (immutable pattern). */
    private DamageAccumulator accumulator;

    // ── Animation state ─────────────────────────────────────────────
    private TotalPhase totalPhase = TotalPhase.INACTIVE;
    private int totalAnimTick;
    private double prevTotalDamage;
    private int bounceTick;
    private final List<TrailAnimState> trailAnimStates = new ArrayList<>();

    /**
     * Creates the renderer (used when overlay is registered via event directly).
     */
    public TotalDamageHudRenderer(final Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        final TotalDamageConfig config = getTotalDamageConfig();
        this.accumulator = DamageAccumulator.create(config);
    }

    /**
     * Creates the renderer and registers the HUD overlay on the MOD bus.
     */
    public TotalDamageHudRenderer(final Minecraft minecraft, final IEventBus modEventBus) {
        this(minecraft);
        Objects.requireNonNull(modEventBus, "modEventBus")
                .addListener(this::onRegisterOverlays);
    }

    // ── Event handler: register the overlay ───────────────────────────

    private void onRegisterOverlays(final RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("stylizeddamage_total_hud", this::renderOverlay);
        LOGGER.info("Registered StylizedDamage total-damage HUD overlay");
    }

    // ── Per-frame rendering callback ───────────────────────────────────

    /**
     * Called by Forge each frame to render the total-damage panel overlay.
     */
    public void renderOverlay(
            final net.minecraft.client.gui.Gui gui,
            final GuiGraphics guiGraphics,
            final float partialTick,
            final int screenWidth,
            final int screenHeight) {

        if (!DisplaySettings.isDisplayEnabled()) {
            return;
        }

        final Font font = minecraft.font;
        if (font == null) {
            return;
        }

        final int currentTick = getClientTick();

        // ── Tick animations ─────────────────────────────────────────
        tickAnimations(currentTick);

        // ── Tick the accumulator ────────────────────────────────────
        final DamageAccumulator acc = this.accumulator;
        final TotalDamageConfig config = acc.config();

        if (!config.enabled() && totalPhase == TotalPhase.INACTIVE) {
            return;
        }

        // Tick accumulator and detect reset (timer expiry)
        final float oldTotal = acc.totalDamage();
        final DamageAccumulator tickedAcc = acc.tick(currentTick);
        final boolean resetDetected = oldTotal > 0f && tickedAcc.totalDamage() <= 0f
                && tickedAcc.trailList().isEmpty();

        if (resetDetected && config.enableExitAnimation()) {
            // Trigger exit animations — keep old accumulator for rendering during exit
            if (totalPhase != TotalPhase.EXITING && totalPhase != TotalPhase.INACTIVE) {
                totalPhase = TotalPhase.EXITING;
                totalAnimTick = 0;
                markAllTrailExiting();
            }
        } else if (resetDetected) {
            // Exit animation disabled — reset instantly
            totalPhase = TotalPhase.INACTIVE;
            totalAnimTick = 0;
            bounceTick = 0;
            trailAnimStates.clear();
            this.accumulator = tickedAcc;
        } else {
            this.accumulator = tickedAcc;
        }

        // ── Hot-reload detection ────────────────────────────────────
        final TotalDamageConfig latestCfg = getTotalDamageConfig();
        if (latestCfg.resetTimeout() != config.resetTimeout()
                || latestCfg.maxTrailCount() != config.maxTrailCount()) {
            this.accumulator = DamageAccumulator.create(latestCfg);
            totalPhase = TotalPhase.INACTIVE;
            totalAnimTick = 0;
            bounceTick = 0;
            trailAnimStates.clear();
            return;
        }

        // ── Determine visibility ────────────────────────────────────
        final float totalDamage = this.accumulator.totalDamage();
        final List<TrailEntry> trail = this.accumulator.trailList();
        final boolean hasVisibleContent = totalPhase != TotalPhase.INACTIVE
                || totalDamage > 0f || !trail.isEmpty()
                || hasExitingTrailEntries();

        if (!hasVisibleContent) {
            return;
        }

        // ── Resolve styles ──────────────────────────────────────────
        final String totalStyleName = this.accumulator.getTotalStyleName();
        final Style totalStyle = resolveStyle(totalStyleName);

        // ── Layout constants ────────────────────────────────────────
        final int baseRightMargin = 16;
        final int baseTopStart = 16;
        final int rightMargin = baseRightMargin - (int) latestCfg.positionX();
        final int topStart = baseTopStart + (int) latestCfg.positionY();

        // ── Render total damage ─────────────────────────────────────
        final double fontSize = calculateFontSize(latestCfg, totalDamage);
        final float totalAlpha = computeTotalAlpha();
        final float totalScaleMul = computeTotalScaleMultiplier(latestCfg);
        final float totalScale = (float) fontSize * totalScaleMul;

        final String totalPrefix = totalStyle != null ? totalStyle.prefix() : "";
        final String totalSuffix = totalStyle != null ? totalStyle.suffix() : "";
        final String totalText = totalPrefix + formatDamage(totalDamage) + totalSuffix;

        final int totalArgb = applyAlpha(
                resolveArgb(totalStyle), totalAlpha);
        final int totalOutline = totalStyle != null && totalStyle.outlineColor() != null
                ? applyAlpha(totalStyle.outlineColor().argb(), totalAlpha) : -1;

        final int totalTextWidth = (int) (font.width(totalText) * totalScale);
        final int totalTextHeight = (int) (font.lineHeight * totalScale);
        final boolean totalIconRight = totalStyle != null && "right".equals(totalStyle.iconPosition());
        final int totalX = screenWidth - rightMargin - totalTextWidth;
        final int totalIconW = totalStyle != null
                ? drawIcon(guiGraphics, totalStyle.icon(), totalX, topStart,
                        totalTextHeight, totalScale, totalArgb, totalIconRight, totalTextWidth,
                        totalStyle.iconOffsetX(), totalStyle.iconOffsetY())
                : 0;
        final int adjustedTotalX = totalIconRight ? totalX : totalX + totalIconW;
        int currentY = topStart;

        drawText(guiGraphics, font, totalText, adjustedTotalX, currentY,
                totalScale, totalArgb,
                totalStyle != null && totalStyle.shadow(),
                totalOutline);

        currentY += (int) (font.lineHeight * totalScale) + 4;

        // ── Render trail entries with per-entry animations ──────────
        for (final TrailAnimState tas : trailAnimStates) {
            final float trailAlpha = computeTrailAlpha(tas);
            if (trailAlpha <= 0.01f) continue;

            final float trailOffsetX = computeTrailOffsetX(tas);

            final Style entryStyle = resolveStyle(tas.entry.styleName());
            final String entryPrefix = entryStyle != null ? entryStyle.prefix() : "";
            final String entrySuffix = entryStyle != null ? entryStyle.suffix() : "";
            final String entryText = entryPrefix + formatDamage(tas.entry.damage()) + entrySuffix;

            final int entryArgb = applyAlpha(resolveArgb(entryStyle), trailAlpha);
            final float entryScale = entryStyle != null ? entryStyle.fontSize() : 1.0f;
            final int entryOutline = entryStyle != null && entryStyle.outlineColor() != null
                    ? applyAlpha(entryStyle.outlineColor().argb(), trailAlpha) : -1;

            final int entryTextWidth = (int) (font.width(entryText) * entryScale);
            final int entryTextHeight = (int) (font.lineHeight * entryScale);
            final int entryX = screenWidth - rightMargin - entryTextWidth + (int) trailOffsetX;
            final boolean entryIconRight = entryStyle != null && "right".equals(entryStyle.iconPosition());
            final int entryIconW = entryStyle != null
                    ? drawIcon(guiGraphics, entryStyle.icon(), entryX, currentY,
                            entryTextHeight, entryScale, entryArgb, entryIconRight, entryTextWidth,
                            entryStyle.iconOffsetX(), entryStyle.iconOffsetY())
                    : 0;
            final int adjustedEntryX = entryIconRight ? entryX : entryX + entryIconW;

            drawText(guiGraphics, font, entryText, adjustedEntryX, currentY,
                    entryScale, entryArgb,
                    entryStyle != null && entryStyle.shadow(),
                    entryOutline);

            currentY += (int) (font.lineHeight * entryScale) + 4;
        }

        // ── Clean up finished EXITING trail entries ────────────────
        trailAnimStates.removeIf(tas ->
                tas.phase == TrailPhase.EXITING && tas.tick >= TRAIL_EXIT_TICKS);

        // ── When EXITING animation completes, go INACTIVE ───────────
        if (totalPhase == TotalPhase.EXITING
                && totalAnimTick >= TOTAL_EXIT_TICKS
                && trailAnimStates.stream().noneMatch(t -> t.phase == TrailPhase.EXITING)) {
            totalPhase = TotalPhase.INACTIVE;
            totalAnimTick = 0;
            this.accumulator = DamageAccumulator.create(latestCfg);
            trailAnimStates.clear();
        }
    }

    // ── Accumulation API ───────────────────────────────────────────────

    /**
     * Accumulates a damage event into the total and resets the countdown timer.
     * Called by {@link ClientPacketHandler} when a {@code TotalDamageSyncPacket} arrives.
     */
    public void accumulate(final float damage, final String styleName) {
        final float oldTotal = accumulator.totalDamage();
        final TotalDamageConfig config = getTotalDamageConfig();
        this.accumulator = accumulator.accumulate(damage, styleName, getClientTick());
        final float newTotal = this.accumulator.totalDamage();

        // ── Phase transitions ───────────────────────────────────────
        if (config.enableEntryAnimation()
                && oldTotal <= 0f && newTotal > 0f
                && totalPhase != TotalPhase.ENTERING) {
            // Just became active → entry animation
            totalPhase = TotalPhase.ENTERING;
            totalAnimTick = 0;
            bounceTick = 0;
            } else if (totalPhase == TotalPhase.EXITING) {
                // Interrupt exit → restart entry
                if (config.enableEntryAnimation()) {
                    totalPhase = TotalPhase.ENTERING;
                    totalAnimTick = 0;
                } else {
                    totalPhase = TotalPhase.ACTIVE;
                }
                bounceTick = 1;
            }

            // ── Bounce on value change ──────────────────────────────────
            if (config.enableBounceAnimation()
                    && newTotal > 0f
                    && Math.abs(newTotal - prevTotalDamage) > 0.001f
                    && newTotal != oldTotal) {
                bounceTick = 1; // start / restart bounce (1 to trigger tickAnimations)
            }
        prevTotalDamage = newTotal;

        // ── Sync trail entries ──────────────────────────────────────
        syncTrailEntries(config);
    }

    /**
     * Resets the total damage panel (clears total and trail).
     */
    public void reset() {
        final TotalDamageConfig config = getTotalDamageConfig();
        if (config.enableExitAnimation()
                && (accumulator.totalDamage() > 0f || !trailAnimStates.isEmpty())) {
            // Trigger exit animations
            if (accumulator.totalDamage() > 0f
                    && totalPhase != TotalPhase.EXITING
                    && totalPhase != TotalPhase.INACTIVE) {
                totalPhase = TotalPhase.EXITING;
                totalAnimTick = 0;
            }
            markAllTrailExiting();
        } else {
            totalPhase = TotalPhase.INACTIVE;
            totalAnimTick = 0;
            bounceTick = 0;
            trailAnimStates.clear();
        }
        this.accumulator = DamageAccumulator.create(config);
    }

    // ── Animation tick ─────────────────────────────────────────────────

    private void tickAnimations(int currentTick) {
        // Total number animation progress
        switch (totalPhase) {
            case ENTERING:
                totalAnimTick++;
                if (totalAnimTick >= TOTAL_ENTRY_TICKS) {
                    totalPhase = TotalPhase.ACTIVE;
                }
                break;
            case EXITING:
                totalAnimTick++;
                break;
            default:
                break;
        }

        // Bounce progress (triggers when bounceTick >= 1)
        if (bounceTick > 0) {
            bounceTick++;
            if (bounceTick >= BOUNCE_TICKS) {
                bounceTick = 0;
            }
        }

        // Trail entry animations
        for (TrailAnimState tas : trailAnimStates) {
            tas.tick++;
            if (tas.phase == TrailPhase.ENTERING && tas.tick >= TRAIL_ENTRY_TICKS) {
                tas.phase = TrailPhase.ACTIVE;
            }
        }
    }

    // ── Trail sync ─────────────────────────────────────────────────────

    /**
     * Synchronises the internal trail animation list with the accumulator's
     * trail. New entries start ENTERING, removed entries start EXITING.
     */
    private void syncTrailEntries(TotalDamageConfig config) {
        List<TrailEntry> currentTrail = accumulator.trailList();
        List<TrailAnimState> newList = new ArrayList<>();

        // Keep existing entries that are still in the trail (preserve phase)
        for (TrailEntry entry : currentTrail) {
            TrailAnimState existing = findAnimForEntry(entry);
            if (existing != null) {
                newList.add(existing);
            } else {
                // New entry
                if (config.enableTrailEntryAnimation()) {
                    newList.add(new TrailAnimState(entry, TrailPhase.ENTERING, 0));
                } else {
                    newList.add(new TrailAnimState(entry, TrailPhase.ACTIVE, 0));
                }
            }
        }

        // Entries no longer in the trail → EXITING (keep rendering them)
        if (config.enableTrailExitAnimation()) {
            for (TrailAnimState tas : trailAnimStates) {
                if (tas.phase != TrailPhase.EXITING && !currentTrail.contains(tas.entry)) {
                    tas.phase = TrailPhase.EXITING;
                    tas.tick = 0;
                    newList.add(tas);
                }
            }
        }

        trailAnimStates.clear();
        trailAnimStates.addAll(newList);
    }

    private TrailAnimState findAnimForEntry(TrailEntry entry) {
        for (TrailAnimState tas : trailAnimStates) {
            if (tas.entry.equals(entry)) {
                return tas;
            }
        }
        return null;
    }

    private void markAllTrailExiting() {
        for (TrailAnimState tas : trailAnimStates) {
            if (tas.phase != TrailPhase.EXITING) {
                tas.phase = TrailPhase.EXITING;
                tas.tick = 0;
            }
        }
    }

    private boolean hasExitingTrailEntries() {
        for (TrailAnimState tas : trailAnimStates) {
            if (tas.phase == TrailPhase.EXITING) return true;
        }
        return false;
    }

    // ── Animation value calculators ────────────────────────────────────

    /** 0..1 opacity for the total-damage number. */
    private float computeTotalAlpha() {
        return switch (totalPhase) {
            case INACTIVE -> 0f;
            case ENTERING -> {
                if (totalAnimTick >= TOTAL_ENTRY_TICKS) yield 1f;
                float t = (float) totalAnimTick / TOTAL_ENTRY_TICKS;
                yield (float) EasingCurve.EASE_OUT.apply(t);
            }
            case ACTIVE -> 1f;
            case EXITING -> {
                if (totalAnimTick >= TOTAL_EXIT_TICKS) yield 0f;
                float t = (float) totalAnimTick / TOTAL_EXIT_TICKS;
                yield 1f - (float) EasingCurve.EASE_IN.apply(t);
            }
        };
    }

    /** Scale multiplier for the total-damage number (1.0 = normal). */
    private float computeTotalScaleMultiplier(TotalDamageConfig cfg) {
        // Bounce takes priority when active
        if (bounceTick > 0 && bounceTick < BOUNCE_TICKS) {
            float t = (float) bounceTick / BOUNCE_TICKS;
            float bounce = (float) EasingCurve.EASE_OUT_BACK.apply(t);
            return 1f + (float) ((bounce - 1.0) * (cfg.bounceScalePeak() - 1.0) / 0.08);
        }

        return switch (totalPhase) {
            case INACTIVE -> 1f;
            case ENTERING -> {
                if (totalAnimTick >= TOTAL_ENTRY_TICKS) yield 1f;
                float t = (float) totalAnimTick / TOTAL_ENTRY_TICKS;
                // Start at 0.3, overshoot to 1.15, settle to 1.0
                float eased = (float) EasingCurve.EASE_OUT_BACK.apply(t);
                yield 0.3f + eased * 0.7f;
            }
            case ACTIVE -> 1f;
            case EXITING -> {
                if (totalAnimTick >= TOTAL_EXIT_TICKS) yield 0.7f;
                float t = (float) totalAnimTick / TOTAL_EXIT_TICKS;
                float eased = (float) EasingCurve.EASE_IN.apply(t);
                yield 1f - eased * 0.3f;
            }
        };
    }

    /** 0..1 opacity for a trail entry based on its animation phase. */
    private float computeTrailAlpha(TrailAnimState tas) {
        return switch (tas.phase) {
            case ENTERING -> {
                if (tas.tick >= TRAIL_ENTRY_TICKS) yield 1f;
                float t = (float) tas.tick / TRAIL_ENTRY_TICKS;
                yield (float) EasingCurve.EASE_OUT.apply(t);
            }
            case ACTIVE -> 1f;
            case EXITING -> {
                if (tas.tick >= TRAIL_EXIT_TICKS) yield 0f;
                float t = (float) tas.tick / TRAIL_EXIT_TICKS;
                yield 1f - (float) EasingCurve.EASE_IN.apply(t);
            }
        };
    }

    /** Horizontal offset in pixels for a trail entry. Positive = right. */
    private float computeTrailOffsetX(TrailAnimState tas) {
        return switch (tas.phase) {
            case ENTERING -> {
                if (tas.tick >= TRAIL_ENTRY_TICKS) yield 0f;
                float t = (float) tas.tick / TRAIL_ENTRY_TICKS;
                float eased = (float) EasingCurve.EASE_OUT.apply(t);
                yield 30f * (1f - eased);
            }
            case ACTIVE -> 0f;
            case EXITING -> {
                if (tas.tick >= TRAIL_EXIT_TICKS) yield -30f;
                float t = (float) tas.tick / TRAIL_EXIT_TICKS;
                float eased = (float) EasingCurve.EASE_IN.apply(t);
                yield -30f * eased;
            }
        };
    }

    // ── Style resolution ───────────────────────────────────────────────

    private static Style resolveStyle(final String styleName) {
        try {
            final StylizedDamageAPI api = StylizedDamageAPI.getInstance();
            final StyleLoader loader = api.getStyleLoader();
            final Map<String, Style> styles = loader.getStyles();
            return styles.get(styleName);
        } catch (final IllegalStateException e) {
            return null;
        }
    }

    /**
     * Resolves the packed ARGB color for a style, using the first frame's
     * colour source with full brightness and opacity.
     */
    private static int resolveArgb(final Style style) {
        if (style == null) {
            return 0xFFFFFFFF;
        }
        final ColorSource colorSource = style.color();
        return ColorRenderer.toArgb(colorSource, 0f, 0, 0.0, 1.0);
    }

    /**
     * Overrides the alpha channel of a packed ARGB color.
     *
     * @param argb  the original colour (ARGB format)
     * @param alpha 0.0 = fully transparent, 1.0 = fully opaque
     * @return the colour with the alpha channel replaced
     */
    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    // ── Draw helpers ───────────────────────────────────────────────────

    private static void drawText(
            final GuiGraphics guiGraphics, final Font font,
            final String text, final int x, final int y,
            final float scale, final int color, final boolean shadow) {
        drawText(guiGraphics, font, text, x, y, scale, color, shadow, -1);
    }

    private static void drawText(
            final GuiGraphics guiGraphics, final Font font,
            final String text, final int x, final int y,
            final float scale, final int color, final boolean shadow,
            final int outlineColor) {
        if (text.isEmpty() || scale <= 0.01f) return;
        final com.mojang.blaze3d.vertex.PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0f);

        if (outlineColor >= 0) {
            guiGraphics.drawString(font, text, -1,  0, outlineColor, false);
            guiGraphics.drawString(font, text,  1,  0, outlineColor, false);
            guiGraphics.drawString(font, text,  0, -1, outlineColor, false);
            guiGraphics.drawString(font, text,  0,  1, outlineColor, false);
        }

        guiGraphics.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private int getClientTick() {
        return minecraft.level != null ? (int) minecraft.level.getGameTime() : 0;
    }

    private static TotalDamageConfig getTotalDamageConfig() {
        try {
            final CommonConfig config = ConfigManager.getInstance().getConfig();
            return config.totalDamage();
        } catch (final IllegalStateException e) {
            return TotalDamageConfig.defaults();
        }
    }

    private static double calculateFontSize(TotalDamageConfig config, double totalDamage) {
        double steps = totalDamage / 100.0;
        double raw = config.baseFontSize() + steps * config.sizeOffsetPerThousand();
        return Math.max(config.baseFontSize(), Math.min(raw, config.sizeOffsetMax()));
    }

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
     * Draws a small icon texture beside the text.
     *
     * @param textX     the left edge of the text
     * @param rightSide {@code true} to place icon to the right of the text
     * @param textWidth pixel width of the text (used for right-side placement)
     * @return the pixel width consumed by the icon (0 if on the right, size if on the left)
     */
    private static int drawIcon(final GuiGraphics guiGraphics, final String iconPath,
                                 final int textX, final int textY,
                                 final int textHeight,
                                 final float scale, final int argb,
                                 final boolean rightSide, final int textWidth,
                                 final double offsetX, final double offsetY) {
        if (iconPath == null || iconPath.isEmpty()) return 0;

        final ResourceLocation loc = ResourceLocation.tryParse(iconPath);
        if (loc == null) return 0;

        final int size = Math.max(1, (int) Math.round(8.0 * scale));
        final int iconX = (rightSide ? textX + textWidth : textX - size)
                + (int) Math.round(offsetX);
        final int iconY = textY - size / 2 + (int) Math.round(offsetY);

        final float a = ((argb >> 24) & 0xFF) / 255.0f;
        final float r = ((argb >> 16) & 0xFF) / 255.0f;
        final float g = ((argb >> 8) & 0xFF) / 255.0f;
        final float b = (argb & 0xFF) / 255.0f;

        RenderSystem.setShaderTexture(0, loc);
        RenderSystem.setShaderColor(r, g, b, a);
        RenderSystem.enableBlend();
        guiGraphics.blit(loc, iconX, iconY, 0, 0, size, size, size, size);

        return rightSide ? 0 : size;
    }
}
