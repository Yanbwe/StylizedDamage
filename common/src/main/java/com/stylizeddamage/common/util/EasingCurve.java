package com.stylizeddamage.common.util;

import com.google.gson.JsonObject;

/**
 * Quadratic easing curve with four standard modes.
 *
 * <p>All {@link #apply(double)} methods accept a progress {@code t} in [0, 1]
 * and return a mapped progress in [0, 1]. Values outside [0, 1] are clamped.
 *
 * <p>Formula reference:
 * <ul>
 *   <li>Ease-in:   {@code t²}</li>
 *   <li>Ease-out:  {@code 1 − (1−t)²}</li>
 *   <li>Ease-in-out: ease-in for the first half, ease-out for the second</li>
 *   <li>Linear:    {@code t}</li>
 * </ul>
 *
 * <p>Parsing from JSON:
 * <pre>{@code
 * EasingCurve.fromJSON(json);   // reads "in" / "out" booleans
 * }</pre>
 */
public enum EasingCurve {

    /** Start slow, end fast — {@code t²}. */
    EASE_IN {
        @Override
        public double apply(double t) {
            t = clamp(t);
            return t * t;
        }
    },

    /** Start fast, end slow — {@code 1 − (1−t)²}. */
    EASE_OUT {
        @Override
        public double apply(double t) {
            t = clamp(t);
            return 1.0 - (1.0 - t) * (1.0 - t);
        }
    },

    /** Slow at both ends, fast in the middle. */
    EASE_IN_OUT {
        @Override
        public double apply(double t) {
            t = clamp(t);
            if (t < 0.5) {
                return 2.0 * t * t;
            }
            return 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0;
        }
    },

    /** Uniform speed — {@code t}. */
    LINEAR {
        @Override
        public double apply(double t) {
            return clamp(t);
        }
    },

    /**
     * Overshoot easing (CSS back-out).
     * Scales past 1.0 and settles back — creates a "spring" / "pop" effect.
     * <p>Formula: {@code 1 + c3·(t−1)³ + c1·(t−1)²} with c1 = 1.70158.
     */
    EASE_OUT_BACK {
        @Override
        public double apply(double t) {
            t = clamp(t);
            final double c1 = 1.70158;
            final double c3 = c1 + 1.0;
            return 1.0 + c3 * Math.pow(t - 1.0, 3) + c1 * Math.pow(t - 1.0, 2);
        }
    };

    /**
     * Maps a progress value through this easing curve.
     *
     * @param t progress in [0, 1]; out-of-range values are clamped
     * @return eased progress in [0, 1]
     */
    public abstract double apply(double t);

    /* ---- convenience queries ---- */

    /** Returns {@code true} when this curve is {@link #LINEAR}. */
    public boolean isLinear() {
        return this == LINEAR;
    }

    /** Returns {@code true} when this curve has an ease-out component. */
    public boolean hasEaseOut() {
        return this == EASE_OUT || this == EASE_IN_OUT;
    }

    /** Returns {@code true} when this curve has an ease-in component. */
    public boolean hasEaseIn() {
        return this == EASE_IN || this == EASE_IN_OUT;
    }

    /* ---- JSON parsing ---- */

    /**
     * Derives an easing curve from a JSON object with optional {@code "in"} and {@code "out"}
     * boolean fields.
     *
     * <p>Defaults to {@link #EASE_OUT} when both fields are absent.
     *
     * @param json a JSON object; may be {@code null} → defaults to ease-out
     * @return the corresponding easing curve
     */
    public static EasingCurve fromJSON(JsonObject json) {
        if (json == null) {
            return EASE_OUT;
        }
        boolean in = json.has("in") && json.get("in").getAsBoolean();
        boolean out = !json.has("out") || json.get("out").getAsBoolean(); // absent → true

        if (in && out) {
            return EASE_IN_OUT;
        }
        if (in) {
            return EASE_IN;
        }
        if (out) {
            return EASE_OUT;
        }
        return LINEAR;
    }

    /* ---- internal ---- */

    private static double clamp(double t) {
        if (t < 0.0) return 0.0;
        if (t > 1.0) return 1.0;
        return t;
    }
}
