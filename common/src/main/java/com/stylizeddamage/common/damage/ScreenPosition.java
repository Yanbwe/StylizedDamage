package com.stylizeddamage.common.damage;

/**
 * Immutable record representing a 2D screen coordinate for a damage number.
 *
 * <p>Screen positions are used by the rendering system to place damage
 * numbers on the HUD layer and by {@link OverlapDetector} to detect and
 * resolve overlapping numbers.
 *
 * @param x the horizontal screen coordinate in pixels
 * @param y the vertical screen coordinate in pixels
 */
public record ScreenPosition(double x, double y) {
    /**
     * Creates a new position offset by the given delta values.
     *
     * @param dx horizontal offset in pixels
     * @param dy vertical offset in pixels
     * @return a new ScreenPosition at (x + dx, y + dy)
     */
    public ScreenPosition offset(double dx, double dy) {
        return new ScreenPosition(x + dx, y + dy);
    }

    /**
     * Calculates the Euclidean distance to another position.
     *
     * @param other the other screen position
     * @return the pixel distance between the two positions
     */
    public double distanceTo(ScreenPosition other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
