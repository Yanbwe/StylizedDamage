package com.stylizeddamage.common.damage;

import java.util.List;
import java.util.Random;

/**
 * Detects and resolves overlapping damage numbers on screen.
 *
 * <p>When a new damage number is about to appear at a screen position that
 * overlaps with existing active numbers, this class finds an offset position
 * to separate them. The offset direction is chosen randomly, and the offset
 * distance equals the estimated rendered width of a damage number.
 *
 * <p>This class is stateless; all state (the random source) is injected.
 *
 * <p>Package: {@code com.stylizeddamage.common.damage}
 */
public final class OverlapDetector {

    /** Default estimated width of a damage number in pixels. */
    public static final double DEFAULT_ESTIMATED_WIDTH = 50.0;

    /** Maximum number of random angles to try before giving up. */
    private static final int MAX_ATTEMPTS = 8;

    private OverlapDetector() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Checks whether a candidate position overlaps with any existing positions
     * and returns a possibly offset position.
     *
     * <p>The overlap check uses a simple distance threshold: if the distance
     * between two positions is less than {@code estimatedWidth}, they are
     * considered overlapping.
     *
     * <p>When overlap is detected, up to {@value #MAX_ATTEMPTS} random offset
     * directions are tried. The first non-overlapping position is returned.
     * If all attempts still overlap, the last attempted position is returned
     * (better to overlap than to be unrendered).
     *
     * @param existing       the list of currently active damage number screen positions
     * @param newPosition    the desired screen position for the new damage number
     * @param estimatedWidth the estimated rendered pixel width of a damage number
     * @param random         the random source used for direction selection
     * @return a screen position that minimizes overlap (may be the original)
     * @throws IllegalArgumentException if existing or random is null, or estimatedWidth is non-positive
     */
    public static ScreenPosition resolveOverlap(
            List<ScreenPosition> existing,
            ScreenPosition newPosition,
            double estimatedWidth,
            Random random
    ) {
        if (existing == null) {
            throw new IllegalArgumentException("existing must not be null");
        }
        if (newPosition == null) {
            throw new IllegalArgumentException("newPosition must not be null");
        }
        if (estimatedWidth <= 0) {
            throw new IllegalArgumentException("estimatedWidth must be positive, got: " + estimatedWidth);
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }

        // No existing numbers → no overlap possible
        if (existing.isEmpty()) {
            return newPosition;
        }

        // Quick check — is there any overlap at all?
        if (!isOverlapping(existing, newPosition, estimatedWidth)) {
            return newPosition;
        }

        // Try random offset directions
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double dx = Math.cos(angle) * estimatedWidth;
            double dy = Math.sin(angle) * estimatedWidth;
            ScreenPosition candidate = newPosition.offset(dx, dy);

            if (!isOverlapping(existing, candidate, estimatedWidth)) {
                return candidate;
            }
        }

        // All attempts overlapped — return the last candidate
        double angle = random.nextDouble() * 2.0 * Math.PI;
        double dx = Math.cos(angle) * estimatedWidth;
        double dy = Math.sin(angle) * estimatedWidth;
        return newPosition.offset(dx, dy);
    }

    /**
     * Convenience overload using {@link #DEFAULT_ESTIMATED_WIDTH}.
     */
    public static ScreenPosition resolveOverlap(
            List<ScreenPosition> existing,
            ScreenPosition newPosition,
            Random random
    ) {
        return resolveOverlap(existing, newPosition, DEFAULT_ESTIMATED_WIDTH, random);
    }

    /**
     * Checks whether the candidate position overlaps with any existing position.
     *
     * <p>Two positions overlap if their Euclidean distance is less than
     * {@code estimatedWidth}.
     *
     * @param existing       list of occupied screen positions
     * @param candidate      the position to check
     * @param estimatedWidth the minimum separation distance in pixels
     * @return true if the candidate overlaps with any existing position
     */
    public static boolean isOverlapping(
            List<ScreenPosition> existing,
            ScreenPosition candidate,
            double estimatedWidth
    ) {
        for (ScreenPosition pos : existing) {
            if (candidate.distanceTo(pos) < estimatedWidth) {
                return true;
            }
        }
        return false;
    }
}
