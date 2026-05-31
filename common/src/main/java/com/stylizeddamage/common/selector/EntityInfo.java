package com.stylizeddamage.common.selector;

import java.util.Objects;

/**
 * Lightweight, platform-independent snapshot of entity metadata used by
 * {@link DisplayFilter} to decide whether a damage number should be shown.
 *
 * <p>This record carries no reference to real Minecraft entity objects,
 * keeping the common module free of platform dependencies.
 */
public record EntityInfo(
        EntityClassifier.EntityType entityType,
        String teamId,
        String name
) {
    /** Canonical constructor with null-safety for display purposes. */
    public EntityInfo {
        Objects.requireNonNull(entityType, "entityType must not be null");
        // teamId and name are nullable (non-player entities have no team)
    }

    /**
     * Convenience factory for a player entity.
     */
    public static EntityInfo player(String teamId, String name) {
        return new EntityInfo(EntityClassifier.EntityType.PLAYER, teamId, name);
    }

    /**
     * Convenience factory for a hostile mob.
     */
    public static EntityInfo hostileMob(String name) {
        return new EntityInfo(EntityClassifier.EntityType.MOB_HOSTILE, null, name);
    }

    /**
     * Convenience factory for a passive mob.
     */
    public static EntityInfo passiveMob(String name) {
        return new EntityInfo(EntityClassifier.EntityType.MOB_PASSIVE, null, name);
    }

    /**
     * Convenience factory for an "other" entity (projectile, item, etc.).
     */
    public static EntityInfo other(String name) {
        return new EntityInfo(EntityClassifier.EntityType.OTHER, null, name);
    }
}
