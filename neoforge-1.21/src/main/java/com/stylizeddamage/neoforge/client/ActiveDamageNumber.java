package com.stylizeddamage.neoforge.client;

import com.stylizeddamage.common.animation.AnimationConfig;
import com.stylizeddamage.common.animation.AnimationEngine;
import com.stylizeddamage.common.animation.AnimationState;
import com.stylizeddamage.common.damage.ScreenPosition;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.style.Style;

import java.util.Objects;
import java.util.Random;

/**
 * An active floating damage number tracking state across its animation lifecycle.
 *
 * <p>Created when a {@link DamageSyncPacket} arrives on the client. Holds the
 * resolved animation config, the screen position, and the style so that the
 * renderer can draw it each frame without recomputing static values.
 *
 * <p>This class is <b>not</b> thread-safe. All access must occur on the
 * client render thread.
 */
public final class ActiveDamageNumber {

    private final DamageSyncPacket packet;
    private final Style style;
    private final AnimationConfig.Resolved resolvedAnimation;
    private final ScreenPosition screenPosition;
    private final int createTick;
    private final Random random;
    private final int entityId;
    private final double overlapOffsetX;
    private final double overlapOffsetY;
    /** World position where damage occurred (fixed, does not follow entity). */
    private final double worldX, worldY, worldZ;
    private final double displayOpacity;

    /**
     * Fully initialises an active damage number.
     *
     * @param packet            the network packet carrying damage data
     * @param style             the resolved visual style to apply
     * @param resolvedAnimation the animation config with all random values fixed
     * @param screenPosition    the initial screen-space position
     * @param createTick        the client tick at which this number was created
     * @param random            the random source for any future random usage
     * @param entityId          the network entity ID of the damaged entity (for distance tracking)
     * @param overlapOffsetX    X offset from overlap detection (added to screen position)
     * @param overlapOffsetY    Y offset from overlap detection (added to screen position)
     */
    public ActiveDamageNumber(
            final DamageSyncPacket packet,
            final Style style,
            final AnimationConfig.Resolved resolvedAnimation,
            final ScreenPosition screenPosition,
            final int createTick,
            final Random random,
            final int entityId,
            final double overlapOffsetX,
            final double overlapOffsetY,
            final double worldX,
            final double worldY,
            final double worldZ,
            final double displayOpacity) {
        this.packet = Objects.requireNonNull(packet, "packet");
        this.style = Objects.requireNonNull(style, "style");
        this.resolvedAnimation = Objects.requireNonNull(resolvedAnimation, "resolvedAnimation");
        this.screenPosition = Objects.requireNonNull(screenPosition, "screenPosition");
        this.createTick = createTick;
        this.random = Objects.requireNonNull(random, "random");
        this.entityId = entityId;
        this.overlapOffsetX = overlapOffsetX;
        this.overlapOffsetY = overlapOffsetY;
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.displayOpacity = displayOpacity;
    }

    /**
     * Convenience constructor with zero overlap offset.
     */
    public ActiveDamageNumber(
            final DamageSyncPacket packet,
            final Style style,
            final AnimationConfig.Resolved resolvedAnimation,
            final ScreenPosition screenPosition,
            final int createTick,
            final Random random,
            final int entityId) {
        this(packet, style, resolvedAnimation, screenPosition, createTick, random, entityId, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
    }

    /**
     * Advances the animation by computing the state for the given
     * absolute client tick.
     *
     * @param currentTick the current client tick counter
     * @return the instantaneous animation state for this frame
     */
    public AnimationState tick(final double currentTick) {
        final double relativeTick = currentTick - createTick;
        return AnimationEngine.update(relativeTick, resolvedAnimation);
    }

    public boolean isComplete(final int currentTick) {
        return tick((double) currentTick).isComplete();
    }

    // ── Accessors ────────────────────────────────────────────────────

    /** The damage event data carried from the server. */
    public DamageSyncPacket packet() {
        return packet;
    }

    /** The visual style to apply during rendering. */
    public Style style() {
        return style;
    }

    /** The resolved animation config (random values already evaluated). */
    public AnimationConfig.Resolved resolvedAnimation() {
        return resolvedAnimation;
    }

    /** The initial (or overlap-resolved) screen position. */
    public ScreenPosition screenPosition() {
        return screenPosition;
    }

    /** The client tick when this number was queued. */
    public int createTick() {
        return createTick;
    }

    /** The random source associated with this number. */
    public Random random() {
        return random;
    }

    /** The network entity ID of the damaged entity. */
    public int entityId() {
        return entityId;
    }

    /** Overlap resolution X offset in pixels (added to screen position during rendering). */
    public double overlapOffsetX() {
        return overlapOffsetX;
    }

    /** Overlap resolution Y offset in pixels (added to screen position during rendering). */
    public double overlapOffsetY() {
        return overlapOffsetY;
    }

    /** World X coordinate where the damage occurred (fixed, for screen projection). */
    public double worldX() { return worldX; }

    /** World Y coordinate where the damage occurred (fixed, for screen projection). */
    public double worldY() { return worldY; }

    /** World Z coordinate where the damage occurred (fixed, for screen projection). */
    public double worldZ() { return worldZ; }
    public double displayOpacity() { return displayOpacity; }
}
