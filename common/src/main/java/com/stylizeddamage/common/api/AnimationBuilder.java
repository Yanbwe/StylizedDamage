package com.stylizeddamage.common.api;

import com.stylizeddamage.common.animation.*;
import com.stylizeddamage.common.util.EasingCurve;
import com.stylizeddamage.common.util.RandomValue;

import java.util.Objects;

/**
 * Chainable builder for constructing an {@link AnimationConfig} with
 * nested sub-builders for the four animation modules.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * api.createAnimation()
 *     .hold(6)
 *     .position()
 *         .enter("normal", 8, easeInOut())
 *         .startOffset(direction(90, 12))
 *         .targetOffset(xy(0, 0))
 *         .exitNone()
 *     .size()
 *         .enter("normal", 6, easeInOut(), 0.3, 0)
 *         .exit("normal", 10, easeInOut(), -0.5)
 *     .opacity()
 *         .enter("normal", 4, easeInOut(), 0, 1)
 *         .exit("normal", 12, easeInOut(), 0)
 *     .done();
 * }</pre>
 *
 * <p>Each module sub-builder is configured inline and automatically returns
 * to this parent builder after its exit phase is set. The {@link #done()}
 * method finalises the build and returns the immutable {@link AnimationConfig}.
 */
public final class AnimationBuilder {

    // ── Parent data ─────────────────────────────────────────────────
    private int holdTicks;

    // ── Module builders (lazily initialised) ────────────────────────
    private PositionBuilder positionBuilder;
    private SizeBuilder sizeBuilder;
    private BrightnessBuilder brightnessBuilder;
    private OpacityBuilder opacityBuilder;

    /** Called internally via {@link StylizedDamageAPI#createAnimation()}. */
    AnimationBuilder() {
        this.holdTicks = 0;
    }

    // ── Top-level setters ───────────────────────────────────────────

    /** Sets the number of ticks to hold at the enter-target state between phases. */
    public AnimationBuilder hold(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("hold ticks must be >= 0, got: " + ticks);
        }
        this.holdTicks = ticks;
        return this;
    }

    // ── Module entry points ─────────────────────────────────────────

    /** Enters the position animation module sub-builder. */
    public PositionBuilder position() {
        if (positionBuilder == null) {
            positionBuilder = new PositionBuilder(this);
        }
        return positionBuilder;
    }

    /** Enters the size animation module sub-builder. */
    public SizeBuilder size() {
        if (sizeBuilder == null) {
            sizeBuilder = new SizeBuilder(this);
        }
        return sizeBuilder;
    }

    /** Enters the brightness animation module sub-builder. */
    public BrightnessBuilder brightness() {
        if (brightnessBuilder == null) {
            brightnessBuilder = new BrightnessBuilder(this);
        }
        return brightnessBuilder;
    }

    /** Enters the opacity animation module sub-builder. */
    public OpacityBuilder opacity() {
        if (opacityBuilder == null) {
            opacityBuilder = new OpacityBuilder(this);
        }
        return opacityBuilder;
    }

    // ── Build ───────────────────────────────────────────────────────

    /** Builds and returns the immutable {@link AnimationConfig}. */
    public AnimationConfig build() {
        return new AnimationConfig(
                holdTicks,
                positionBuilder != null ? positionBuilder.build() : defaultPosition(),
                sizeBuilder != null ? sizeBuilder.build() : defaultSize(),
                brightnessBuilder != null ? brightnessBuilder.build() : defaultBrightness(),
                opacityBuilder != null ? opacityBuilder.build() : defaultOpacity());
    }

    /** Alias for {@link #build()}. */
    public AnimationConfig done() {
        return build();
    }

    // ════════════════════════════════════════════════════════════════
    // Static helper factories
    // ════════════════════════════════════════════════════════════════

    /** Ease-in-out quadratic curve. */
    public static EasingCurve easeInOut() { return EasingCurve.EASE_IN_OUT; }

    /** Ease-in quadratic curve. */
    public static EasingCurve easeIn() { return EasingCurve.EASE_IN; }

    /** Ease-out quadratic curve. */
    public static EasingCurve easeOut() { return EasingCurve.EASE_OUT; }

    /** Linear (no) easing. */
    public static EasingCurve linear() { return EasingCurve.LINEAR; }

    /** Direction-based offset: {@code angle} in degrees, {@code distance} in pixels. */
    public static OffsetValue direction(double angle, double distance) {
        return new OffsetValue.Direction(
                RandomValue.fixed(angle), RandomValue.fixed(distance));
    }

    /** Absolute XY pixel offset. */
    public static OffsetValue xy(double x, double y) {
        return new OffsetValue.XY(RandomValue.fixed(x), RandomValue.fixed(y));
    }

    // ── Defaults ────────────────────────────────────────────────────

    static AnimationConfig defaultConfig() {
        return new AnimationConfig(0, defaultPosition(), defaultSize(),
                defaultBrightness(), defaultOpacity());
    }

    private static PositionConfig defaultPosition() {
        return new PositionConfig(PhaseConfig.NONE, PhaseConfig.NONE,
                OffsetValue.XY.FIXED_ZERO,
                OffsetValue.XY.FIXED_ZERO,
                OffsetValue.XY.FIXED_ZERO);
    }

    private static SizeConfig defaultSize() {
        return new SizeConfig(PhaseConfig.NONE, PhaseConfig.NONE,
                RandomValue.ZERO, RandomValue.ZERO, RandomValue.ZERO);
    }

    private static BrightnessConfig defaultBrightness() {
        return new BrightnessConfig(PhaseConfig.NONE, PhaseConfig.NONE,
                RandomValue.ZERO, RandomValue.ZERO);
    }

    private static OpacityConfig defaultOpacity() {
        return new OpacityConfig(PhaseConfig.NONE, PhaseConfig.NONE,
                RandomValue.ZERO, RandomValue.ZERO, RandomValue.ZERO);
    }

    // ── Phase helpers ───────────────────────────────────────────────

    static PhaseConfig parsePhase(String type, int duration, EasingCurve easing) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(easing, "easing must not be null");
        if ("normal".equalsIgnoreCase(type)) {
            if (duration <= 0) {
                throw new IllegalArgumentException("duration must be > 0 for normal phase, got: " + duration);
            }
            return PhaseConfig.normal(duration, easing);
        }
        if ("none".equalsIgnoreCase(type)) {
            return PhaseConfig.NONE;
        }
        throw new IllegalArgumentException("Unknown phase type: " + type + " (expected 'normal' or 'none')");
    }

    // ════════════════════════════════════════════════════════════════
    // Nested: PositionBuilder
    // ════════════════════════════════════════════════════════════════

    /** Sub-builder for the position animation module. */
    public static final class PositionBuilder {
        private final AnimationBuilder parent;
        private PhaseConfig enterPhase = PhaseConfig.NONE;
        private PhaseConfig exitPhase = PhaseConfig.NONE;
        private OffsetValue enterStartOffset = OffsetValue.XY.FIXED_ZERO;
        private OffsetValue enterTargetOffset = OffsetValue.XY.FIXED_ZERO;
        private OffsetValue exitTargetOffset = OffsetValue.XY.FIXED_ZERO;

        PositionBuilder(AnimationBuilder parent) {
            this.parent = parent;
        }

        /**
         * Sets the enter phase.
         * @param type     {@code "normal"} or {@code "none"}
         * @param duration duration in ticks (required for normal)
         * @param easing   easing curve
         */
        public PositionBuilder enter(String type, int duration, EasingCurve easing) {
            this.enterPhase = parsePhase(type, duration, easing);
            return this;
        }

        /** Sets the enter phase directly. */
        public PositionBuilder enter(PhaseConfig phase) {
            this.enterPhase = Objects.requireNonNull(phase, "phase must not be null");
            return this;
        }

        /** Sets the starting position offset for the enter phase. */
        public PositionBuilder startOffset(OffsetValue offset) {
            this.enterStartOffset = Objects.requireNonNull(offset, "offset must not be null");
            return this;
        }

        /** Sets the target position offset for the enter phase (held during hold). */
        public PositionBuilder targetOffset(OffsetValue offset) {
            this.enterTargetOffset = Objects.requireNonNull(offset, "offset must not be null");
            return this;
        }

        /**
         * Sets the exit phase to {@code NONE} (no exit animation) and
         * returns to the parent {@link AnimationBuilder}.
         */
        public AnimationBuilder exitNone() {
            this.exitPhase = PhaseConfig.NONE;
            return parent;
        }

        /**
         * Sets the exit phase with a target offset.
         * @param type     {@code "normal"} or {@code "none"}
         * @param duration duration in ticks
         * @param easing   easing curve
         * @param exitTarget the target offset for the exit phase
         */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, OffsetValue exitTarget) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOffset = Objects.requireNonNull(exitTarget, "exitTarget must not be null");
            return parent;
        }

        /** Builds the {@link PositionConfig}. Called internally. */
        PositionConfig build() {
            return new PositionConfig(enterPhase, exitPhase,
                    enterStartOffset, enterTargetOffset, exitTargetOffset);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Nested: SizeBuilder
    // ════════════════════════════════════════════════════════════════

    /** Sub-builder for the size animation module. */
    public static final class SizeBuilder {
        private final AnimationBuilder parent;
        private PhaseConfig enterPhase = PhaseConfig.NONE;
        private PhaseConfig exitPhase = PhaseConfig.NONE;
        private RandomValue enterStartOffset = RandomValue.ZERO;
        private RandomValue enterTargetOffset = RandomValue.ZERO;
        private RandomValue exitTargetOffset = RandomValue.ZERO;

        SizeBuilder(AnimationBuilder parent) {
            this.parent = parent;
        }

        /**
         * Sets the enter phase with start and target offset multipliers.
         * @param type        {@code "normal"} or {@code "none"}
         * @param duration    duration in ticks
         * @param easing      easing curve
         * @param startOffset start size offset (e.g. 0.3 = 130% size)
         * @param targetOffset target size offset (e.g. 0 = 100% size)
         */
        public SizeBuilder enter(String type, int duration, EasingCurve easing,
                                  double startOffset, double targetOffset) {
            this.enterPhase = parsePhase(type, duration, easing);
            this.enterStartOffset = RandomValue.fixed(startOffset);
            this.enterTargetOffset = RandomValue.fixed(targetOffset);
            return this;
        }

        /** Sets the enter phase with random start/target offsets. */
        public SizeBuilder enter(String type, int duration, EasingCurve easing,
                                  RandomValue startOffset, RandomValue targetOffset) {
            this.enterPhase = parsePhase(type, duration, easing);
            this.enterStartOffset = Objects.requireNonNull(startOffset, "startOffset must not be null");
            this.enterTargetOffset = Objects.requireNonNull(targetOffset, "targetOffset must not be null");
            return this;
        }

        /**
         * Sets the exit phase and returns to the parent {@link AnimationBuilder}.
         * @param type        {@code "normal"} or {@code "none"}
         * @param duration    duration in ticks
         * @param easing      easing curve
         * @param targetOffset exit target size offset
         */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, double targetOffset) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOffset = RandomValue.fixed(targetOffset);
            return parent;
        }

        /** Sets the exit phase with a random target offset. */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, RandomValue targetOffset) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOffset = Objects.requireNonNull(targetOffset, "targetOffset must not be null");
            return parent;
        }

        /** Sets the exit phase to {@code NONE} and returns to the parent. */
        public AnimationBuilder exitNone() {
            this.exitPhase = PhaseConfig.NONE;
            return parent;
        }

        /** Builds the {@link SizeConfig}. Called internally. */
        SizeConfig build() {
            return new SizeConfig(enterPhase, exitPhase,
                    enterStartOffset, enterTargetOffset, exitTargetOffset);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Nested: BrightnessBuilder
    // ════════════════════════════════════════════════════════════════

    /** Sub-builder for the brightness animation module. */
    public static final class BrightnessBuilder {
        private final AnimationBuilder parent;
        private PhaseConfig enterPhase = PhaseConfig.NONE;
        private PhaseConfig exitPhase = PhaseConfig.NONE;
        private RandomValue enterStartOffset = RandomValue.ZERO;
        private RandomValue exitTargetOffset = RandomValue.ZERO;

        BrightnessBuilder(AnimationBuilder parent) {
            this.parent = parent;
        }

        /**
         * Sets the enter phase. Brightness enter starts from the given offset
         * and interpolates to 0 (original brightness).
         * @param type        {@code "normal"} or {@code "none"}
         * @param duration    duration in ticks
         * @param easing      easing curve
         * @param startOffset starting brightness offset (positive = brighter, negative = darker)
         */
        public BrightnessBuilder enter(String type, int duration, EasingCurve easing, double startOffset) {
            this.enterPhase = parsePhase(type, duration, easing);
            this.enterStartOffset = RandomValue.fixed(startOffset);
            return this;
        }

        /** Sets the enter phase with a random start offset. */
        public BrightnessBuilder enter(String type, int duration, EasingCurve easing, RandomValue startOffset) {
            this.enterPhase = parsePhase(type, duration, easing);
            this.enterStartOffset = Objects.requireNonNull(startOffset, "startOffset must not be null");
            return this;
        }

        /**
         * Sets the exit phase and returns to the parent {@link AnimationBuilder}.
         */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, double targetOffset) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOffset = RandomValue.fixed(targetOffset);
            return parent;
        }

        /** Sets the exit phase with a random target offset. */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, RandomValue targetOffset) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOffset = Objects.requireNonNull(targetOffset, "targetOffset must not be null");
            return parent;
        }

        /** Sets the exit phase to {@code NONE} and returns to the parent. */
        public AnimationBuilder exitNone() {
            this.exitPhase = PhaseConfig.NONE;
            return parent;
        }

        /** Builds the {@link BrightnessConfig}. Called internally. */
        BrightnessConfig build() {
            return new BrightnessConfig(enterPhase, exitPhase,
                    enterStartOffset, exitTargetOffset);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Nested: OpacityBuilder
    // ════════════════════════════════════════════════════════════════

    /** Sub-builder for the opacity animation module. */
    public static final class OpacityBuilder {
        private final AnimationBuilder parent;
        private PhaseConfig enterPhase = PhaseConfig.NONE;
        private PhaseConfig exitPhase = PhaseConfig.NONE;
        private RandomValue enterStartOpacity = RandomValue.ZERO;
        private RandomValue enterTargetOpacity = RandomValue.fixed(1.0);
        private RandomValue exitTargetOpacity = RandomValue.ZERO;

        OpacityBuilder(AnimationBuilder parent) {
            this.parent = parent;
        }

        /**
         * Sets the enter phase with start and target opacity values.
         * @param type          {@code "normal"} or {@code "none"}
         * @param duration      duration in ticks
         * @param easing        easing curve
         * @param startOpacity  starting opacity (0.0–1.0)
         * @param targetOpacity target opacity (0.0–1.0)
         */
        public OpacityBuilder enter(String type, int duration, EasingCurve easing,
                                     double startOpacity, double targetOpacity) {
            this.enterPhase = parsePhase(type, duration, easing);
            this.enterStartOpacity = RandomValue.fixed(clampOpacity(startOpacity));
            this.enterTargetOpacity = RandomValue.fixed(clampOpacity(targetOpacity));
            return this;
        }

        /** Sets the enter phase with random opacity values. */
        public OpacityBuilder enter(String type, int duration, EasingCurve easing,
                                     RandomValue startOpacity, RandomValue targetOpacity) {
            this.enterPhase = parsePhase(type, duration, easing);
            this.enterStartOpacity = Objects.requireNonNull(startOpacity, "startOpacity must not be null");
            this.enterTargetOpacity = Objects.requireNonNull(targetOpacity, "targetOpacity must not be null");
            return this;
        }

        /**
         * Sets the exit phase and returns to the parent {@link AnimationBuilder}.
         */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, double targetOpacity) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOpacity = RandomValue.fixed(clampOpacity(targetOpacity));
            return parent;
        }

        /** Sets the exit phase with a random target opacity. */
        public AnimationBuilder exit(String type, int duration, EasingCurve easing, RandomValue targetOpacity) {
            this.exitPhase = parsePhase(type, duration, easing);
            this.exitTargetOpacity = Objects.requireNonNull(targetOpacity, "targetOpacity must not be null");
            return parent;
        }

        /** Sets the exit phase to {@code NONE} and returns to the parent. */
        public AnimationBuilder exitNone() {
            this.exitPhase = PhaseConfig.NONE;
            return parent;
        }

        /** Builds the {@link OpacityConfig}. Called internally. */
        OpacityConfig build() {
            return new OpacityConfig(enterPhase, exitPhase,
                    enterStartOpacity, enterTargetOpacity, exitTargetOpacity);
        }

        private static double clampOpacity(double value) {
            if (value < 0.0) return 0.0;
            if (value > 1.0) return 1.0;
            return value;
        }
    }
}
