package com.stylizeddamage.common.animation;

import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;
import java.util.Random;

/**
 * A sealed interface for two‑dimensional position offset specifications.
 *
 * <p>Two concrete types are supported:
 * <ul>
 *   <li>{@link XY} — absolute pixel offsets on the X and Y axes</li>
 *   <li>{@link Direction} — polar offset defined by an angle (degrees) and distance (pixels)</li>
 * </ul>
 *
 * <p>Call {@link #resolveX(Random)} / {@link #resolveY(Random)} to obtain concrete
 * pixel offsets. Resolution should happen exactly once per animation instance.
 */
public sealed interface OffsetValue permits OffsetValue.XY, OffsetValue.Direction {

    /**
     * Resolves the X‑axis offset in pixels.
     */
    double resolveX(Random random);

    /**
     * Resolves the Y‑axis offset in pixels.
     */
    double resolveY(Random random);

    /**
     * Absolute pixel offsets.
     *
     * <p>Both {@code x} and {@code y} may be fixed or random.
     */
    record XY(RandomValue x, RandomValue y) implements OffsetValue {

        /** A zero-offset constant for conveniently skipping position animation. */
        public static final XY FIXED_ZERO = new XY(RandomValue.ZERO, RandomValue.ZERO);

        public XY {
            Objects.requireNonNull(x, "x must not be null");
            Objects.requireNonNull(y, "y must not be null");
        }

        @Override
        public double resolveX(Random random) {
            return x.resolve(random);
        }

        @Override
        public double resolveY(Random random) {
            return y.resolve(random);
        }
    }

    /**
     * Direction‑based offset defined by an angle and distance.
     *
     * <p>Angle convention (screen coordinates, Y increases downward):
     * <ul>
     *   <li>0° = right (X+)</li>
     *   <li>90° = up (Y−)</li>
     *   <li>−90° = down (Y+)</li>
     * </ul>
     *
     * <p>Formula:
     * <pre>{@code
     *   x = distance * cos(angle)
     *   y = -distance * sin(angle)   // negative because screen Y is inverted
     * }</pre>
     */
    record Direction(RandomValue angle, RandomValue distance) implements OffsetValue {
        public Direction {
            Objects.requireNonNull(angle, "angle must not be null");
            Objects.requireNonNull(distance, "distance must not be null");
        }

        @Override
        public double resolveX(Random random) {
            double resolvedDistance = distance.resolve(random);
            double resolvedAngleDeg = angle.resolve(random);
            double rad = Math.toRadians(resolvedAngleDeg);
            return resolvedDistance * Math.cos(rad);
        }

        @Override
        public double resolveY(Random random) {
            double resolvedDistance = distance.resolve(random);
            double resolvedAngleDeg = angle.resolve(random);
            double rad = Math.toRadians(resolvedAngleDeg);
            return -resolvedDistance * Math.sin(rad); // screen Y-axis is inverted
        }
    }
}
