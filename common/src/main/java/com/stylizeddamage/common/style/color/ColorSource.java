package com.stylizeddamage.common.style.color;

/**
 * Sealed interface for all color sources that can be resolved to a {@link Color}
 * given a progress value and a tick count.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link Color} — fixed color, always returns itself</li>
 *   <li>{@link RainbowColor} — cycles through the HSV hue wheel based on tick</li>
 * </ul>
 */
public sealed interface ColorSource permits Color, RainbowColor {

    /**
     * Resolves this color source to a concrete {@link Color}.
     *
     * @param progress interpolation progress in [0, 1] (ignored by fixed/rainbow)
     * @param tick     the current tick count (used by rainbow; ignored by fixed)
     * @return the resolved ARGB color, never null
     */
    Color resolve(float progress, int tick);
}
