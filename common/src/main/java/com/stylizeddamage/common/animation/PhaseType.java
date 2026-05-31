package com.stylizeddamage.common.animation;

/**
 * The type of an animation phase.
 *
 * <ul>
 *   <li>{@link #NONE} — no interpolation, value instantly jumps to target</li>
 *   <li>{@link #NORMAL} — smooth interpolation over the phase duration using an easing curve</li>
 * </ul>
 */
public enum PhaseType {
    NONE,
    NORMAL
}
