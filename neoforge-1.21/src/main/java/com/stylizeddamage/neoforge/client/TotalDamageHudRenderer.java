package com.stylizeddamage.neoforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.config.TotalDamageConfig;
import com.stylizeddamage.common.damage.DamageAccumulator;
import com.stylizeddamage.common.damage.TrailEntry;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.style.color.ColorSource;
import com.stylizeddamage.common.api.StylizedDamageAPI;
import com.stylizeddamage.common.util.EasingCurve;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HUD renderer for the total-damage panel displayed in the top-right corner.
 *
 * <h3>Animations (configurable via {@code enable*Animation} toggles)</h3>
 * <ul>
 *   <li><b>Entry:</b> total number fades in + overshoots scale, 12 ticks</li>
 *   <li><b>Exit:</b> total number fades out + shrinks, 10 ticks</li>
 *   <li><b>Bounce:</b> scale pops on value change (gentle), 6 ticks, interruptible</li>
 *   <li><b>Trail entry:</b> slides in from right + fades in, 8 ticks</li>
 *   <li><b>Trail exit:</b> slides out to left + fades out, 8 ticks</li>
 * </ul>
 */
public final class TotalDamageHudRenderer {

    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath("stylizeddamage", "total_damage_panel");

    private static final Logger LOG = LoggerFactory.getLogger("stylizeddamage");

    // ── Animation timing constants (hard-coded) ─────────────────────
    private static final int TOTAL_ENTRY_TICKS = 12;
    private static final int TOTAL_EXIT_TICKS = 10;
    private static final int BOUNCE_TICKS = 6;
    private static final int TRAIL_ENTRY_TICKS = 8;
    private static final int TRAIL_EXIT_TICKS = 8;
    // bounceScalePeak is read from config, no hard-coded default

    // ── Phases ──────────────────────────────────────────────────────
    private enum TotalPhase { INACTIVE, ENTERING, ACTIVE, EXITING }
    private enum TrailPhase { ENTERING, ACTIVE, EXITING }

    private static final class TrailAnimState {
        final TrailEntry entry;
        TrailPhase phase;
        long startTimeMs;

        TrailAnimState(TrailEntry entry, TrailPhase phase, long startTimeMs) {
            this.entry = entry;
            this.phase = phase;
            this.startTimeMs = startTimeMs;
        }
    }

    private static TotalDamageHudRenderer instance;

    private final Minecraft minecraft;
    private DamageAccumulator accumulator;

    // ── Animation state ─────────────────────────────────────────────
    private TotalPhase totalPhase = TotalPhase.INACTIVE;
    private long totalAnimStartMs;
    private double prevTotalDamage;
    private long bounceStartMs;
    private long renderTimeMs;
    private final List<TrailAnimState> trailAnimStates = new ArrayList<>();

    public TotalDamageHudRenderer(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.accumulator = DamageAccumulator.create(getTotalDamageConfig());
        instance = this;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterGuiLayersEvent.class, event -> {
            TotalDamageHudRenderer r = instance != null ? instance
                    : new TotalDamageHudRenderer(Minecraft.getInstance());
            event.registerAboveAll(LAYER_ID, r::renderLayer);
            LOG.info("Registered StylizedDamage total-damage HUD layer");
        });
    }

    // ── Accumulation API ───────────────────────────────────────────────

    public void accumulate(float damage, String styleName) {
        synchronized (this) {
            final float oldTotal = accumulator.totalDamage();
            final TotalDamageConfig config = getTotalDamageConfig();
            final long now = System.currentTimeMillis();
            accumulator = accumulator.accumulate(damage, styleName, now);
            final float newTotal = accumulator.totalDamage();

            // Phase transitions
            if (config.enableEntryAnimation()
                    && oldTotal <= 0f && newTotal > 0f
                    && totalPhase != TotalPhase.ENTERING) {
                totalPhase = TotalPhase.ENTERING;
                totalAnimStartMs = now;
                bounceStartMs = now;
            } else if (totalPhase == TotalPhase.EXITING) {
                if (config.enableEntryAnimation()) {
                    totalPhase = TotalPhase.ENTERING;
                    totalAnimStartMs = now;
                } else {
                    totalPhase = TotalPhase.ACTIVE;
                }
                bounceStartMs = now;
            }

            // Bounce on value change
            if (config.enableBounceAnimation()
                    && newTotal > 0f
                    && Math.abs(newTotal - prevTotalDamage) > 0.001f
                    && newTotal != oldTotal) {
                bounceStartMs = now;
            }
            prevTotalDamage = newTotal;

            syncTrailEntries(config, now);
        }
    }

    public void reset() {
        synchronized (this) {
            final TotalDamageConfig config = getTotalDamageConfig();
            if (config.enableExitAnimation()
                    && (accumulator.totalDamage() > 0f || !trailAnimStates.isEmpty())) {
                if (accumulator.totalDamage() > 0f
                        && totalPhase != TotalPhase.EXITING
                        && totalPhase != TotalPhase.INACTIVE) {
                    totalPhase = TotalPhase.EXITING;
                    totalAnimStartMs = System.currentTimeMillis();
                }
                markAllTrailExiting();
            } else {
                totalPhase = TotalPhase.INACTIVE;
                totalAnimStartMs = 0;
                bounceStartMs = 0;
                trailAnimStates.clear();
            }
            accumulator = DamageAccumulator.create(config);
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────

    private void renderLayer(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!DisplaySettings.isDisplayEnabled()) return;

        final Font font = minecraft.font;
        if (font == null) return;

        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        final long now = System.currentTimeMillis();

        // ── Fetch latest config first (hot-reload-aware) ────────────
        final TotalDamageConfig latestCfg = getTotalDamageConfig();

        // ── Config-disabled check: force INACTIVE regardless of current phase ──
        if (!latestCfg.enabled()) {
            if (totalPhase != TotalPhase.INACTIVE) {
                totalPhase = TotalPhase.INACTIVE;
                totalAnimStartMs = 0;
                bounceStartMs = 0;
                trailAnimStates.clear();
                this.accumulator = DamageAccumulator.create(latestCfg);
            }
            return;
        }

        tickAnimations(now);

        final DamageAccumulator acc = this.accumulator;
        final TotalDamageConfig config = acc.config();

        // Tick accumulator & detect timer-expiry reset
        final float oldTotal = acc.totalDamage();
        final DamageAccumulator tickedAcc = acc.tick(now);
        final boolean resetDetected = oldTotal > 0f && tickedAcc.totalDamage() <= 0f
                && tickedAcc.trailList().isEmpty();

        if (resetDetected && config.enableExitAnimation()) {
            if (totalPhase != TotalPhase.EXITING && totalPhase != TotalPhase.INACTIVE) {
                totalPhase = TotalPhase.EXITING;
                totalAnimStartMs = now;
                markAllTrailExiting();
            }
        } else if (resetDetected) {
            // Exit animation disabled — reset instantly
            totalPhase = TotalPhase.INACTIVE;
            totalAnimStartMs = 0;
            bounceStartMs = 0;
            trailAnimStates.clear();
            this.accumulator = tickedAcc;
        } else {
            this.accumulator = tickedAcc;
        }

        // Hot-reload detection (other config changes)
        if (latestCfg.resetTimeout() != config.resetTimeout()
                || latestCfg.maxTrailCount() != config.maxTrailCount()) {
            this.accumulator = DamageAccumulator.create(latestCfg);
            totalPhase = TotalPhase.INACTIVE;
            totalAnimStartMs = 0;
            bounceStartMs = 0;
            trailAnimStates.clear();
            return;
        }

        // Visibility
        final float totalDamage = this.accumulator.totalDamage();
        final List<TrailEntry> trail = this.accumulator.trailList();
        final boolean hasVisibleContent = totalPhase != TotalPhase.INACTIVE
                || totalDamage > 0f || !trail.isEmpty()
                || hasExitingTrailEntries();

        if (!hasVisibleContent) return;

        // Layout
        final int baseRightMargin = 16;
        final int baseTopStart = 16;
        final int rightMargin = baseRightMargin - (int) (latestCfg.positionX() * screenWidth);
        final int topStart = baseTopStart + (int) (latestCfg.positionY() * screenHeight);

        // ── Total damage number ─────────────────────────────────────
        final String totalStyleName = this.accumulator.getTotalStyleName();
        final Style totalStyle = resolveStyle(totalStyleName);
        final double fontSize = calculateFontSize(latestCfg, totalDamage);

        final float totalAlpha = computeTotalAlpha();
        final float totalScaleMul = computeTotalScaleMultiplier(latestCfg);
        final float totalScale = (float) fontSize * totalScaleMul;

        int currentY = topStart;

        // ── Render label above total damage ──────────────────────────
        final String labelText = latestCfg.labelText();
        if (!labelText.isEmpty() && totalAlpha > 0.01f) {
            final float labelScale = (float) latestCfg.baseFontSize() * 0.5f * totalScaleMul;
            final int labelArgb = applyAlpha(0xFFFFFFFF, totalAlpha);
            final int labelTextWidth = (int) (font.width(labelText) * labelScale);
            final int labelTextHeight = (int) (font.lineHeight * labelScale);
            final int labelX = screenWidth - rightMargin - labelTextWidth;
            drawText(guiGraphics, font, labelText, labelX, currentY,
                    labelScale, labelArgb, false, -1);
            currentY += labelTextHeight + 2;
        }

        final String totalPrefix = totalStyle != null ? totalStyle.prefix() : "";
        final String totalSuffix = totalStyle != null ? totalStyle.suffix() : "";
        final String totalText = totalPrefix + formatDamage(totalDamage) + totalSuffix;

        final int totalArgb = applyAlpha(resolveArgb(totalStyle), totalAlpha);
        final int totalOutline = totalStyle != null && totalStyle.outlineColor() != null
                ? applyAlpha(totalStyle.outlineColor().argb(), totalAlpha) : -1;

        final int totalTextWidth = (int) (font.width(totalText) * totalScale);
        final int totalTextHeight = (int) (font.lineHeight * totalScale);
        final int rawTotalX = screenWidth - rightMargin - totalTextWidth;
        final boolean totalIconRight = totalStyle != null && "right".equals(totalStyle.iconPosition());
        final int totalIconW = totalStyle != null
                ? drawIcon(guiGraphics, totalStyle.icon(), rawTotalX, currentY,
                        totalTextHeight, totalScale, totalArgb, totalIconRight, totalTextWidth,
                        totalStyle.iconOffsetX(), totalStyle.iconOffsetY())
                : 0;
        final int totalX = totalIconRight ? rawTotalX : rawTotalX + totalIconW;

        drawText(guiGraphics, font, totalText, totalX, currentY,
                totalScale, totalArgb, totalStyle != null && totalStyle.shadow(), totalOutline);

        currentY += (int) (font.lineHeight * totalScale) + 4;

        // ── Trail entries ───────────────────────────────────────────
        for (TrailAnimState tas : trailAnimStates) {
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
            final int rawEntryX = screenWidth - rightMargin - entryTextWidth + (int) trailOffsetX;
            final boolean entryIconRight = entryStyle != null && "right".equals(entryStyle.iconPosition());
            final int entryIconW = entryStyle != null
                    ? drawIcon(guiGraphics, entryStyle.icon(), rawEntryX, currentY,
                            entryTextHeight, entryScale, entryArgb, entryIconRight, entryTextWidth,
                            entryStyle.iconOffsetX(), entryStyle.iconOffsetY())
                    : 0;
            final int entryX = entryIconRight ? rawEntryX : rawEntryX + entryIconW;

            drawText(guiGraphics, font, entryText, entryX, currentY,
                    entryScale, entryArgb, entryStyle != null && entryStyle.shadow(), entryOutline);

            currentY += (int) (font.lineHeight * entryScale) + 4;
        }

        // Cleanup
        trailAnimStates.removeIf(tas ->
                tas.phase == TrailPhase.EXITING && (now - tas.startTimeMs) / 50.0 >= TRAIL_EXIT_TICKS);

        if (totalPhase == TotalPhase.EXITING
                && (now - totalAnimStartMs) / 50.0 >= TOTAL_EXIT_TICKS
                && trailAnimStates.stream().noneMatch(t -> t.phase == TrailPhase.EXITING)) {
            totalPhase = TotalPhase.INACTIVE;
            totalAnimStartMs = 0;
            this.accumulator = DamageAccumulator.create(latestCfg);
            trailAnimStates.clear();
        }
    }

    // ── Animation tick ─────────────────────────────────────────────────

    private void tickAnimations(long now) {
        this.renderTimeMs = now;

        switch (totalPhase) {
            case ENTERING:
                if ((now - totalAnimStartMs) / 50.0 >= TOTAL_ENTRY_TICKS) totalPhase = TotalPhase.ACTIVE;
                break;
            case EXITING:
                break;
            default: break;
        }

        // Bounce: naturally expires based on elapsed time (no tick increment needed)

        for (TrailAnimState tas : trailAnimStates) {
            if (tas.phase == TrailPhase.ENTERING && (now - tas.startTimeMs) / 50.0 >= TRAIL_ENTRY_TICKS) {
                tas.phase = TrailPhase.ACTIVE;
            }
        }
    }

    // ── Trail sync ─────────────────────────────────────────────────────

    private void syncTrailEntries(TotalDamageConfig config, long now) {
        List<TrailEntry> currentTrail = accumulator.trailList();
        List<TrailAnimState> newList = new ArrayList<>();

        for (TrailEntry entry : currentTrail) {
            TrailAnimState existing = findAnimForEntry(entry);
            if (existing != null) {
                newList.add(existing);
            } else {
                if (config.enableTrailEntryAnimation()) {
                    newList.add(new TrailAnimState(entry, TrailPhase.ENTERING, now));
                } else {
                    newList.add(new TrailAnimState(entry, TrailPhase.ACTIVE, now));
                }
            }
        }

        if (config.enableTrailExitAnimation()) {
            for (TrailAnimState tas : trailAnimStates) {
                if (tas.phase != TrailPhase.EXITING && !currentTrail.contains(tas.entry)) {
                    tas.phase = TrailPhase.EXITING;
                    tas.startTimeMs = now;
                    newList.add(tas);
                }
            }
        }

        trailAnimStates.clear();
        trailAnimStates.addAll(newList);
    }

    private TrailAnimState findAnimForEntry(TrailEntry entry) {
        for (TrailAnimState tas : trailAnimStates) {
            if (tas.entry.equals(entry)) return tas;
        }
        return null;
    }

    private void markAllTrailExiting() {
        final long now = System.currentTimeMillis();
        for (TrailAnimState tas : trailAnimStates) {
            if (tas.phase != TrailPhase.EXITING) {
                tas.phase = TrailPhase.EXITING;
                tas.startTimeMs = now;
            }
        }
    }

    private boolean hasExitingTrailEntries() {
        for (TrailAnimState tas : trailAnimStates) {
            if (tas.phase == TrailPhase.EXITING) return true;
        }
        return false;
    }

    // ── Animation calculators ──────────────────────────────────────────

    private float computeTotalAlpha() {
        final double totalElapsed = (renderTimeMs - totalAnimStartMs) / 50.0;
        return switch (totalPhase) {
            case INACTIVE -> 0f;
            case ENTERING -> {
                if (totalElapsed >= TOTAL_ENTRY_TICKS) yield 1f;
                float t = (float) (totalElapsed / TOTAL_ENTRY_TICKS);
                yield (float) EasingCurve.EASE_OUT.apply(t);
            }
            case ACTIVE -> 1f;
            case EXITING -> {
                if (totalElapsed >= TOTAL_EXIT_TICKS) yield 0f;
                float t = (float) (totalElapsed / TOTAL_EXIT_TICKS);
                yield 1f - (float) EasingCurve.EASE_IN.apply(t);
            }
        };
    }

    private float computeTotalScaleMultiplier(TotalDamageConfig cfg) {
        final double bounceElapsed = bounceStartMs > 0 ? (renderTimeMs - bounceStartMs) / 50.0 : BOUNCE_TICKS;
        if (bounceStartMs > 0 && bounceElapsed < BOUNCE_TICKS) {
            float t = (float) (bounceElapsed / BOUNCE_TICKS);
            float bounce = (float) EasingCurve.EASE_OUT_BACK.apply(t);
            return 1f + (float) ((bounce - 1.0) * (cfg.bounceScalePeak() - 1.0) / 0.08);
        }

        final double totalElapsed = (renderTimeMs - totalAnimStartMs) / 50.0;
        return switch (totalPhase) {
            case INACTIVE -> 1f;
            case ENTERING -> {
                if (totalElapsed >= TOTAL_ENTRY_TICKS) yield 1f;
                float t = (float) (totalElapsed / TOTAL_ENTRY_TICKS);
                float eased = (float) EasingCurve.EASE_OUT_BACK.apply(t);
                yield 0.3f + eased * 0.7f;
            }
            case ACTIVE -> 1f;
            case EXITING -> {
                if (totalElapsed >= TOTAL_EXIT_TICKS) yield 0.7f;
                float t = (float) (totalElapsed / TOTAL_EXIT_TICKS);
                float eased = (float) EasingCurve.EASE_IN.apply(t);
                yield 1f - eased * 0.3f;
            }
        };
    }

    private float computeTrailAlpha(TrailAnimState tas) {
        final double elapsed = (renderTimeMs - tas.startTimeMs) / 50.0;
        return switch (tas.phase) {
            case ENTERING -> {
                if (elapsed >= TRAIL_ENTRY_TICKS) yield 1f;
                float t = (float) (elapsed / TRAIL_ENTRY_TICKS);
                yield (float) EasingCurve.EASE_OUT.apply(t);
            }
            case ACTIVE -> 1f;
            case EXITING -> {
                if (elapsed >= TRAIL_EXIT_TICKS) yield 0f;
                float t = (float) (elapsed / TRAIL_EXIT_TICKS);
                yield 1f - (float) EasingCurve.EASE_IN.apply(t);
            }
        };
    }

    private float computeTrailOffsetX(TrailAnimState tas) {
        final double elapsed = (renderTimeMs - tas.startTimeMs) / 50.0;
        return switch (tas.phase) {
            case ENTERING -> {
                if (elapsed >= TRAIL_ENTRY_TICKS) yield 0f;
                float t = (float) (elapsed / TRAIL_ENTRY_TICKS);
                float eased = (float) EasingCurve.EASE_OUT.apply(t);
                yield 30f * (1f - eased);
            }
            case ACTIVE -> 0f;
            case EXITING -> {
                if (elapsed >= TRAIL_EXIT_TICKS) yield -30f;
                float t = (float) (elapsed / TRAIL_EXIT_TICKS);
                float eased = (float) EasingCurve.EASE_IN.apply(t);
                yield -30f * eased;
            }
        };
    }

    // ── Draw helpers ───────────────────────────────────────────────────

    private static void drawText(GuiGraphics g, Font font, String text,
                                  int x, int y, float scale, int color, boolean shadow, int outlineColor) {
        if (text.isEmpty() || scale <= 0.01f) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0f);
        if (outlineColor >= 0) {
            g.drawString(font, text, -1, 0, outlineColor, false);
            g.drawString(font, text, 1, 0, outlineColor, false);
            g.drawString(font, text, 0, -1, outlineColor, false);
            g.drawString(font, text, 0, 1, outlineColor, false);
        }
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static Style resolveStyle(String styleName) {
        try {
            Map<String, Style> styles = StylizedDamageAPI.getInstance().getStyleLoader().getStyles();
            return styles.getOrDefault(styleName, styles.get("default"));
        } catch (Exception e) { return null; }
    }

    private static TotalDamageConfig getTotalDamageConfig() {
        try { return ConfigManager.getInstance().getConfig().totalDamage(); }
        catch (Exception e) { return TotalDamageConfig.defaults(); }
    }

    private static double calculateFontSize(TotalDamageConfig cfg, double dmg) {
        double steps = dmg / 100.0;
        double raw = cfg.baseFontSize() + steps * cfg.sizeOffsetPerThousand();
        return Math.max(cfg.baseFontSize(), Math.min(raw, cfg.sizeOffsetMax()));
    }

    private static int resolveArgb(Style style) {
        if (style == null) return 0xFFFFFFFF;
        ColorSource cs = style.color();
        return com.stylizeddamage.neoforge.client.ColorRenderer.toArgb(cs, 0f, 0, 0.0, 1.0);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static String formatDamage(float dmg) {
        if (dmg == (int) dmg) return String.valueOf((int) dmg);
        return String.format("%.1f", dmg);
    }

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
