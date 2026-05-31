package com.stylizeddamage.common.selector;

import java.util.Set;

/**
 * Classifies Minecraft entities into categories used by {@link DisplayFilter}.
 *
 * <p>Because the common module has zero platform dependencies, this class works
 * with {@code MobCategory} names as strings. Each platform module maps the
 * real {@code MobCategory} enum to the sets defined here.
 *
 * <p>Classification rules (per design doc):
 * <ul>
 *   <li>{@code MONSTER} → {@link EntityType#MOB_HOSTILE}</li>
 *   <li>{@code CREATURE, AMBIENT, WATER_CREATURE, WATER_AMBIENT, AXOLOTLS,
 *       UNDERGROUND_WATER_CREATURE} → {@link EntityType#MOB_PASSIVE}</li>
 *   <li>{@code MISC} → not a mob (handled by player/other branches)</li>
 * </ul>
 */
public final class EntityClassifier {

    private EntityClassifier() {
        // Utility class — no instances
    }

    /** MobCategory names that map to hostile mobs. */
    public static final Set<String> HOSTILE_MOB_CATEGORIES = Set.of("monster");

    /** MobCategory names that map to passive mobs. */
    public static final Set<String> PASSIVE_MOB_CATEGORIES = Set.of(
            "creature", "ambient", "water_creature", "water_ambient",
            "axolotls", "underground_water_creature"
    );

    /**
     * Entity type used by the display filter.
     */
    public enum EntityType {
        PLAYER,
        MOB_HOSTILE,
        MOB_PASSIVE,
        OTHER
    }

    /**
     * Classifies an entity given whether it is a player and its
     * {@code MobCategory} name (may be {@code null} for non-mobs).
     *
     * @param isPlayer         {@code true} if the entity is a player
     * @param mobCategoryName  the {@code MobCategory} serialized name, or {@code null}
     * @return the corresponding {@link EntityType}
     */
    public static EntityType classify(boolean isPlayer, String mobCategoryName) {
        if (isPlayer) {
            return EntityType.PLAYER;
        }
        if (mobCategoryName == null) {
            return EntityType.OTHER;
        }
        if (HOSTILE_MOB_CATEGORIES.contains(mobCategoryName)) {
            return EntityType.MOB_HOSTILE;
        }
        if (PASSIVE_MOB_CATEGORIES.contains(mobCategoryName)) {
            return EntityType.MOB_PASSIVE;
        }
        return EntityType.OTHER;
    }

    /**
     * Returns {@code true} if the given {@code MobCategory} name represents
     * a hostile mob.
     */
    public static boolean isHostile(String mobCategoryName) {
        return mobCategoryName != null && HOSTILE_MOB_CATEGORIES.contains(mobCategoryName);
    }

    /**
     * Returns {@code true} if the given {@code MobCategory} name represents
     * a passive mob.
     */
    public static boolean isPassive(String mobCategoryName) {
        return mobCategoryName != null && PASSIVE_MOB_CATEGORIES.contains(mobCategoryName);
    }
}
