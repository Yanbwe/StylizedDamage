package com.stylizeddamage.neoforge.client;

import com.stylizeddamage.common.style.color.Color;
import com.stylizeddamage.common.style.color.ColorSource;

/**
 * Converts ColorSource to ARGB int for Minecraft font rendering.
 */
public final class ColorRenderer {

    private ColorRenderer() {}

    public static int toArgb(ColorSource source, float progress, int tick) {
        return source.resolve(progress, tick).argb();
    }

    public static int toArgb(ColorSource source, float progress, int tick,
                             double brightnessOffset, double opacity) {
        Color color = source.resolve(progress, tick);
        return applyBrightnessAndOpacity(color, brightnessOffset, opacity);
    }

    private static int applyBrightnessAndOpacity(Color color, double brightnessOffset, double opacity) {
        double b = Math.max(-1.0, Math.min(1.0, brightnessOffset));
        double o = Math.max(0.0, Math.min(1.0, opacity));
        int r = applyChannel(color.red(), b);
        int g = applyChannel(color.green(), b);
        int bl = applyChannel(color.blue(), b);
        int a = (int) Math.round(color.alpha() * o);
        return Color.of(r, g, bl, a).argb();
    }

    private static int applyChannel(int channel, double offset) {
        if (offset >= 0) return (int) Math.round(channel + (255 - channel) * offset);
        return (int) Math.round(channel * (1.0 + offset));
    }

    public static void renderStyledText(
            net.minecraft.client.gui.GuiGraphics guiGraphics,
            net.minecraft.client.gui.Font font,
            String text, float x, float y,
            com.stylizeddamage.common.style.Style style,
            com.stylizeddamage.common.animation.AnimationState animState,
            int tick) {
        // Simplified render: draw text with style color + opacity
        int argb = toArgb(style.color(), 0.5f, tick,
                animState.brightnessOffset(), animState.opacity());
        guiGraphics.drawString(font, text, (int) x, (int) y, argb);
    }
}
