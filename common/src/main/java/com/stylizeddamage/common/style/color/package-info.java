/**
 * Color system — ARGB color values, rainbow cycling, and color string parsing.
 * All classes are immutable records or stateless utilities with no platform dependencies.
 *
 * <p>Supported color formats:
 * <ul>
 *   <li>Fixed hex: {@code #RRGGBB} (e.g. {@code #FF4444})</li>
 *   <li>Fixed hex with alpha: {@code #AARRGGBB} (e.g. {@code #80FF4444})</li>
 *   <li>Rainbow cycle: {@code rainbow:speed:N} (hue shift per tick)</li>
 * </ul>
 *
 * @see ColorParser
 * @see ColorSource
 */
package com.stylizeddamage.common.style.color;
