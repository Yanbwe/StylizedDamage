package com.stylizeddamage.common.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Random;

/**
 * An immutable random value that can be fixed or vary within a percentage range.
 *
 * <p>Supports three JSON representations:
 * <ul>
 *   <li>Fixed: a plain number literal (e.g. {@code 10}, {@code 0.5})</li>
 *   <li>Single random: {@code {"base": 20, "random": 0.5}} — adds 0%–50% of base</li>
 *   <li>Range random: {@code {"base": 20, "random": [-0.3, 0.3]}} — adds –30% to +30%</li>
 * </ul>
 *
 * <p>Call {@link #resolve(Random)} to obtain the final value at usage time.
 */
public record RandomValue(double base, double randomMin, double randomMax) {

    /** A fixed value of zero — useful as a default. */
    public static final RandomValue ZERO = new RandomValue(0.0, 0.0, 0.0);

    /**
     * Compact constructor that normalises the random range so {@code randomMin ≤ randomMax}.
     */
    public RandomValue {
        if (randomMin > randomMax) {
            double tmp = randomMin;
            randomMin = randomMax;
            randomMax = tmp;
        }
    }

    /* ---- queries ---- */

    /** Returns {@code true} when this value never varies (random range is zero width). */
    public boolean isFixed() {
        return randomMin == 0.0 && randomMax == 0.0;
    }

    /* ---- evaluation ---- */

    /**
     * Resolves this value using the supplied {@link Random} instance.
     *
     * @param random source of randomness
     * @return {@code base * (1 + randomFactor)} where {@code randomFactor}
     *         is uniformly distributed in [{@link #randomMin}, {@link #randomMax}]
     */
    public double resolve(Random random) {
        if (isFixed()) {
            return base;
        }
        double factor = randomMin + random.nextDouble() * (randomMax - randomMin);
        return base * (1.0 + factor);
    }

    /* ---- factories ---- */

    /** Creates a fixed value (no randomness). */
    public static RandomValue fixed(double value) {
        return new RandomValue(value, 0.0, 0.0);
    }

    /**
     * Creates a random value that adds 0% to {@code randomPercent} of {@code base}.
     *
     * @param base          the base value
     * @param randomPercent upper bound of the random adder, relative to base (≥ 0)
     */
    public static RandomValue of(double base, double randomPercent) {
        return new RandomValue(base, 0.0, Math.max(0.0, randomPercent));
    }

    /**
     * Creates a random value that adds a percentage in [{@code minPercent}, {@code maxPercent}]
     * relative to {@code base}.
     */
    public static RandomValue of(double base, double minPercent, double maxPercent) {
        return new RandomValue(base, minPercent, maxPercent);
    }

    /* ---- JSON parsing ---- */

    /**
     * Parses a random value from a JSON element.
     *
     * <p>Accepted shapes:
     * <ol>
     *   <li>{@code <number>} — fixed value</li>
     *   <li>{@code {"base": <number>, "random": <number>}} — base + 0%–random%</li>
     *   <li>{@code {"base": <number>, "random": [<min>, <max>]}} — base + min%–max%</li>
     * </ol>
     *
     * @param json the JSON element to parse
     * @return a parsed {@code RandomValue}
     * @throws IllegalArgumentException if the JSON shape is unrecognised
     */
    public static RandomValue fromJSON(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return fixed(json.getAsDouble());
        }
        if (json.isJsonObject()) {
            return parseObject(json.getAsJsonObject());
        }
        throw new IllegalArgumentException(
                "RandomValue must be a number or a {base, random} object, got: " + json);
    }

    private static RandomValue parseObject(JsonObject obj) {
        if (!obj.has("base")) {
            throw new IllegalArgumentException("RandomValue object must contain \"base\"");
        }
        double base = obj.get("base").getAsDouble();

        if (!obj.has("random")) {
            return fixed(base);
        }

        JsonElement random = obj.get("random");
        if (random.isJsonPrimitive()) {
            double pct = random.getAsDouble();
            if (pct < 0) {
                // negative single value → range [pct, 0]
                return of(base, pct, 0.0);
            }
            return of(base, pct);
        }
        if (random.isJsonArray()) {
            JsonArray arr = random.getAsJsonArray();
            double min = arr.get(0).getAsDouble();
            double max = arr.get(1).getAsDouble();
            return of(base, min, max);
        }

        throw new IllegalArgumentException(
                "RandomValue \"random\" must be a number or [min, max] array, got: " + random);
    }

    @Override
    public String toString() {
        if (isFixed()) {
            return String.valueOf(base);
        }
        return String.format("{base=%.4f, random=[%.4f, %.4f]}", base, randomMin, randomMax);
    }
}
