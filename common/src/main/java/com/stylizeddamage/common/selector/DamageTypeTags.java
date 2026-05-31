package com.stylizeddamage.common.selector;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Hard-coded damage type tag → member IDs for tag expansion.
 * Cross-platform — works on both server and client without registry access.
 *
 * <p>Includes both 1.20.1-style IDs (e.g. {@code "magic"}) and
 * 1.21.1-style IDs (e.g. {@code "minecraft:magic"}).
 */
final class DamageTypeTags {

    private DamageTypeTags() {}

    // ── 1.20.1 (old-style, camelCase, no namespace) ────────────────
    private static final String MAGIC = "magic";
    private static final String INDIRECT_MAGIC = "indirectMagic";
    private static final String IN_FIRE = "inFire";
    private static final String ON_FIRE = "onFire";
    private static final String LAVA = "lava";
    private static final String HOT_FLOOR = "hotFloor";
    private static final String FIREBALL = "fireball";
    private static final String UNATTRIBUTED_FIREBALL = "unattributedFireball";
    private static final String DROWN = "drown";
    private static final String STARVE = "starve";
    private static final String WITHER = "wither";
    private static final String GENERIC_KILL = "genericKill";
    private static final String OUT_OF_WORLD = "outOfWorld";
    private static final String EXPLOSION = "explosion";
    private static final String EXPLOSION_PLAYER = "explosion.player";
    private static final String BAD_RESPAWN = "badRespawnPoint";
    private static final String ARROW = "arrow";
    private static final String TRIDENT = "trident";
    private static final String MOB_PROJECTILE = "mobProjectile";
    private static final String WITHER_SKULL = "witherSkull";
    private static final String THROWN = "thrown";
    private static final String FALL = "fall";
    private static final String ENDER_PEARL = "enderPearl";
    private static final String STALAGMITE = "stalagmite";
    private static final String LIGHTNING_BOLT = "lightningBolt";
    private static final String POISON = "poison";
    private static final String DRAGON_BREATH = "dragonBreath";
    private static final String PLAYER_ATTACK = "playerAttack";
    private static final String MOB_ATTACK = "mobAttack";
    private static final String MOB_ATTACK_NO_AGGRO = "mobAttackNoAggro";
    private static final String FREEZE = "freeze";
    private static final String CACTUS = "cactus";
    private static final String SWEET_BERRY = "sweetBerryBush";
    private static final String THORNS = "thorns";
    private static final String FALLING_ANVIL = "fallingAnvil";
    private static final String FALLING_BLOCK = "fallingBlock";
    private static final String MACE_SMASH = "maceSmash";

    // ── 1.21.1 (new-style, snake_case, with namespace) ─────────────
    private static final String M_MAGIC = "minecraft:magic";
    private static final String M_INDIRECT_MAGIC = "minecraft:indirect_magic";
    private static final String M_IN_FIRE = "minecraft:in_fire";
    private static final String M_ON_FIRE = "minecraft:on_fire";
    private static final String M_LAVA = "minecraft:lava";
    private static final String M_HOT_FLOOR = "minecraft:hot_floor";
    private static final String M_FIREBALL = "minecraft:fireball";
    private static final String M_UNATTRIBUTED_FIREBALL = "minecraft:unattributed_fireball";
    private static final String M_DROWN = "minecraft:drown";
    private static final String M_STARVE = "minecraft:starve";
    private static final String M_WITHER = "minecraft:wither";
    private static final String M_GENERIC_KILL = "minecraft:generic_kill";
    private static final String M_OUT_OF_WORLD = "minecraft:out_of_world";
    private static final String M_EXPLOSION = "minecraft:explosion";
    private static final String M_EXPLOSION_PLAYER = "minecraft:explosion.player";
    private static final String M_BAD_RESPAWN = "minecraft:bad_respawn_point";
    private static final String M_ARROW = "minecraft:arrow";
    private static final String M_TRIDENT = "minecraft:trident";
    private static final String M_MOB_PROJECTILE = "minecraft:mob_projectile";
    private static final String M_WITHER_SKULL = "minecraft:wither_skull";
    private static final String M_THROWN = "minecraft:thrown";
    private static final String M_FALL = "minecraft:fall";
    private static final String M_ENDER_PEARL = "minecraft:ender_pearl";
    private static final String M_STALAGMITE = "minecraft:stalagmite";
    private static final String M_LIGHTNING = "minecraft:lightning_bolt";
    private static final String M_POISON = "minecraft:poison";
    private static final String M_DRAGON_BREATH = "minecraft:dragon_breath";
    private static final String M_PLAYER_ATTACK = "minecraft:player_attack";
    private static final String M_MOB_ATTACK = "minecraft:mob_attack";
    private static final String M_MOB_ATTACK_NO_AGGRO = "minecraft:mob_attack_no_aggro";
    private static final String M_FREEZE = "minecraft:freeze";
    private static final String M_CACTUS = "minecraft:cactus";
    private static final String M_SWEET_BERRY = "minecraft:sweet_berry_bush";
    private static final String M_THORNS = "minecraft:thorns";
    private static final String M_FALLING_ANVIL = "minecraft:falling_anvil";
    private static final String M_FALLING_BLOCK = "minecraft:falling_block";
    private static final String M_MACE_SMASH = "minecraft:mace_smash";

    /** Per-tag members in BOTH 1.20.1 and 1.21.1 naming conventions. */
    static final Map<String, Set<String>> TAGS = Map.ofEntries(
            // ── bypasses_armor ────────────────────────────────────
            Map.entry("minecraft:bypasses_armor", Set.of(
                    MAGIC, INDIRECT_MAGIC, DROWN, STARVE, WITHER, GENERIC_KILL, OUT_OF_WORLD,
                    M_MAGIC, M_INDIRECT_MAGIC, M_DROWN, M_STARVE, M_WITHER, M_GENERIC_KILL, M_OUT_OF_WORLD)),
            // ── bypasses_shield ───────────────────────────────────
            Map.entry("minecraft:bypasses_shield", Set.of(
                    MAGIC, INDIRECT_MAGIC, DROWN, STARVE, WITHER, GENERIC_KILL, OUT_OF_WORLD,
                    M_MAGIC, M_INDIRECT_MAGIC, M_DROWN, M_STARVE, M_WITHER, M_GENERIC_KILL, M_OUT_OF_WORLD)),
            // ── is_fire ──────────────────────────────────────────
            Map.entry("minecraft:is_fire", Set.of(
                    IN_FIRE, ON_FIRE, LAVA, HOT_FLOOR, FIREBALL, UNATTRIBUTED_FIREBALL,
                    M_IN_FIRE, M_ON_FIRE, M_LAVA, M_HOT_FLOOR, M_FIREBALL, M_UNATTRIBUTED_FIREBALL)),
            // ── is_projectile ────────────────────────────────────
            Map.entry("minecraft:is_projectile", Set.of(
                    ARROW, TRIDENT, MOB_PROJECTILE, FIREBALL, WITHER_SKULL, THROWN,
                    M_ARROW, M_TRIDENT, M_MOB_PROJECTILE, M_FIREBALL, M_WITHER_SKULL, M_THROWN)),
            // ── is_explosion ─────────────────────────────────────
            Map.entry("minecraft:is_explosion", Set.of(
                    EXPLOSION, EXPLOSION_PLAYER, BAD_RESPAWN, FIREBALL,
                    M_EXPLOSION, M_EXPLOSION_PLAYER, M_BAD_RESPAWN, M_FIREBALL)),
            // ── is_fall ──────────────────────────────────────────
            Map.entry("minecraft:is_fall", Set.of(
                    FALL, ENDER_PEARL, STALAGMITE,
                    M_FALL, M_ENDER_PEARL, M_STALAGMITE)),
            // ── is_lightning ─────────────────────────────────────
            Map.entry("minecraft:is_lightning", Set.of(LIGHTNING_BOLT, M_LIGHTNING)),
            // ── bypasses_enchantments ────────────────────────────
            Map.entry("minecraft:bypasses_enchantments", Set.of(
                    MAGIC, INDIRECT_MAGIC, DROWN, STARVE, WITHER, GENERIC_KILL,
                    M_MAGIC, M_INDIRECT_MAGIC, M_DROWN, M_STARVE, M_WITHER, M_GENERIC_KILL)),
            // ── bypasses_effects ─────────────────────────────────
            Map.entry("minecraft:bypasses_effects", Set.of(
                    MAGIC, INDIRECT_MAGIC, DROWN, STARVE, WITHER, GENERIC_KILL,
                    M_MAGIC, M_INDIRECT_MAGIC, M_DROWN, M_STARVE, M_WITHER, M_GENERIC_KILL)),
            // ── bypasses_invulnerability ─────────────────────────
            Map.entry("minecraft:bypasses_invulnerability", Set.of(OUT_OF_WORLD, M_OUT_OF_WORLD)),
            // ── witch_resistant_to ───────────────────────────────
            Map.entry("minecraft:witch_resistant_to", Set.of(
                    MAGIC, INDIRECT_MAGIC, DROWN, WITHER, POISON,
                    M_MAGIC, M_INDIRECT_MAGIC, M_DROWN, M_WITHER, M_POISON)),
            // ── no_impact ────────────────────────────────────────
            Map.entry("minecraft:no_impact", Set.of(DROWN, M_DROWN)),
            // ── no_knockback ─────────────────────────────────────
            Map.entry("minecraft:no_knockback", Set.of(
                    EXPLOSION, EXPLOSION_PLAYER, BAD_RESPAWN, DRAGON_BREATH,
                    M_EXPLOSION, M_EXPLOSION_PLAYER, M_BAD_RESPAWN, M_DRAGON_BREATH)),
            // ── burns_armor_stands ───────────────────────────────
            Map.entry("minecraft:burns_armor_stands", Set.of(
                    ON_FIRE, IN_FIRE, LAVA, M_ON_FIRE, M_IN_FIRE, M_LAVA)),
            // ── damages_helmet ───────────────────────────────────
            Map.entry("minecraft:damages_helmet", Set.of(
                    FALLING_ANVIL, FALLING_BLOCK, M_FALLING_ANVIL, M_FALLING_BLOCK)),
            // ── avoids_guardian_thorns ───────────────────────────
            Map.entry("minecraft:avoids_guardian_thorns", Set.of(
                    MAGIC, THORNS, EXPLOSION, EXPLOSION_PLAYER,
                    M_MAGIC, M_THORNS, M_EXPLOSION, M_EXPLOSION_PLAYER)),
            // ── always_most_significant_fall ─────────────────────
            Map.entry("minecraft:always_most_significant_fall", Set.of(OUT_OF_WORLD, M_OUT_OF_WORLD)),
            // ── is_player_attack ─────────────────────────────────
            Map.entry("minecraft:is_player_attack", Set.of(PLAYER_ATTACK, M_PLAYER_ATTACK)),
            // ── is_freezing ──────────────────────────────────────
            Map.entry("minecraft:is_freezing", Set.of(FREEZE, M_FREEZE)),
            // ── panic_causes ─────────────────────────────────────
            Map.entry("minecraft:panic_causes", Set.of(
                    IN_FIRE, ON_FIRE, LAVA, MAGIC, FREEZE, LIGHTNING_BOLT,
                    DRAGON_BREATH, WITHER_SKULL,
                    M_IN_FIRE, M_ON_FIRE, M_LAVA, M_MAGIC, M_FREEZE,
                    M_LIGHTNING, M_DRAGON_BREATH, M_WITHER_SKULL)),
            // ── panic_environmental_causes ───────────────────────
            Map.entry("minecraft:panic_environmental_causes", Set.of(
                    CACTUS, FREEZE, HOT_FLOOR, IN_FIRE, LAVA, LIGHTNING_BOLT,
                    ON_FIRE, SWEET_BERRY,
                    M_CACTUS, M_FREEZE, M_HOT_FLOOR, M_IN_FIRE, M_LAVA,
                    M_LIGHTNING, M_ON_FIRE, M_SWEET_BERRY)),
            // ── wither_immune_to ─────────────────────────────────
            Map.entry("minecraft:wither_immune_to", Set.of(DROWN, M_DROWN)),
            // ── can_break_armor_stand ────────────────────────────
            Map.entry("minecraft:can_break_armor_stand", Set.of(
                    PLAYER_ATTACK, ARROW, TRIDENT, MOB_ATTACK, MOB_ATTACK_NO_AGGRO,
                    M_PLAYER_ATTACK, M_ARROW, M_TRIDENT, M_MOB_ATTACK, M_MOB_ATTACK_NO_AGGRO)),
            // ── is_mace_smash ────────────────────────────────────
            Map.entry("minecraft:is_mace_smash", Set.of(MACE_SMASH, M_MACE_SMASH)),
            // ── always_hurts_ender_dragons ───────────────────────
            Map.entry("minecraft:always_hurts_ender_dragons", Set.of(EXPLOSION, M_EXPLOSION))
    );

    static Set<String> resolve(String tag) {
        return TAGS.getOrDefault(tag, Collections.emptySet());
    }

    static Set<String> resolveAny(String tag) {
        Set<String> r = TAGS.get(tag);
        if (r != null) return r;
        if (tag.startsWith("minecraft:")) {
            return TAGS.getOrDefault(tag.substring(10), Collections.emptySet());
        }
        return TAGS.getOrDefault("minecraft:" + tag, Collections.emptySet());
    }
}
