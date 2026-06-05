package com.stylizeddamage.forge.event;

import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import com.stylizeddamage.common.selector.DisplayFilter;
import com.stylizeddamage.common.selector.EntityClassifier;
import com.stylizeddamage.common.selector.EntityInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * Server-side listener that captures post-reduction damage events and
 * broadcasts them as {@link DamageSyncPacket}s to all players tracking
 * the damaged entity.
 */
public final class DamageEventListener {

    private static final boolean DEBUG = Boolean.getBoolean("stylizeddamage.debug");

    private final NetworkRegistrar<Entity, ServerPlayer> network;

    /**
     * @param network the platform network registrar used to send packets
     */
    public DamageEventListener(final NetworkRegistrar<Entity, ServerPlayer> network) {
        this.network = network;
    }

    /**
     * Handles {@link LivingDamageEvent} on the Forge event bus.
     *
     * <p>Extracts the final damage amount, damage type identifier,
     * source entity, and critical-hit flag, then broadcasts the data
     * to all clients tracking the damaged entity.
     *
     * @param event the post-reduction living damage event
     */
    @SubscribeEvent
    public void onLivingDamage(final LivingDamageEvent event) {
        final LivingEntity target = event.getEntity();

        // Safeguard: only process on the logical server.
        // While LivingDamageEvent typically only fires server-side in Forge 1.20.1,
        // this guard ensures consistent behavior with the neoforge-1.21 counterpart
        // and prevents ClassCastException if the event ever fires on the client.
        if (target.level().isClientSide()) {
            return;
        }

        final DamageSource source = event.getSource();
        final float damage = event.getAmount();

        // Ignore zero or negative damage (should not happen post-reduction, but guard)
        if (damage <= 0.0F) {
            return;
        }

        // Determine source entity
        final Entity sourceEntity = source.getEntity();
        final int sourceEntityId = sourceEntity != null ? sourceEntity.getId() : -1;

        // Get the damage type identifier (e.g. "minecraft:in_fire")
        final String damageTypeId = source.getMsgId();

        // Detect critical hit: player attacker falling without being on ground
        final boolean isCritical = isCriticalHit(source);

        // Compute the world position where the damage landed
        final Vec3 hitPos = computeHitPosition(source, target, damageTypeId);

        final DamageSyncPacket packet = new DamageSyncPacket(
                sourceEntityId,
                target.getId(),
                damage,
                damageTypeId,
                isCritical,
                System.currentTimeMillis(),
                hitPos.x, hitPos.y, hitPos.z
        );

        // ── Apply display filter from common.json ──
        // DisplayFilter determines whether normal damage numbers are shown.
        // Placed AFTER kill detection so kills always fire regardless of filter.
        final CommonConfig config = ConfigManager.getInstance().getConfig();
        final DisplayFilter filter = new DisplayFilter(config.displayFilter());
        final boolean isSelf = sourceEntity != null && sourceEntity.equals(target);
        final EntityInfo targetInfo = classifyEntity(target);
        final EntityInfo sourceInfo = sourceEntity != null
                ? classifyEntity(sourceEntity) : targetInfo;

        if (filter.shouldDisplay(sourceInfo, targetInfo, isSelf, damage)) {
            network.sendToTracking(packet, target);
        }

        // If a player caused this damage, update their total damage panel
        if (sourceEntity instanceof ServerPlayer player) {
            TotalDamageSyncPacket totalPacket = new TotalDamageSyncPacket(
                    false, damage, damageTypeId);
            network.sendToPlayer(totalPacket, player);
        }

        // If this damage killed the entity, send a kill notification
        // Forge's LivingDamageEvent fires BEFORE health is actually subtracted,
        // so target.getHealth() returns pre-damage health. A fatal blow is when
        // the remaining health is ≤ the incoming damage.
        // (kill pseudo-type does NOT count toward total damage)
        if (target.getHealth() <= damage) {
            final DamageSyncPacket killPacket = new DamageSyncPacket(
                    sourceEntityId, target.getId(), 0f, "kill",
                    false, System.currentTimeMillis(),
                    hitPos.x, hitPos.y, hitPos.z);
            network.sendToTracking(killPacket, target);
        }
    }

    /**
     * Computes the world-space hit position for a damage event.
     *
     * <p>Prioritises the most accurate source available:
     * <ol>
     *   <li>{@link DamageSource#getSourcePosition()} — used by projectiles
     *       (arrows, fireballs, tridents) and explosions.</li>
     *   <li>Player melee ray trace — traces from the player's eyes along
     *       their look direction to the target's bounding box.</li>
     *   <li>Target body centre — fallback for environmental / potion /
     *       status-effect damage that has no spatial origin.</li>
     * </ol>
     *
     * @param source       the damage source from the event
     * @param target       the entity that received damage
     * @param damageTypeId the damage type identifier string
     * @return world coordinates where the damage number should spawn
     */
    private static Vec3 computeHitPosition(final DamageSource source,
                                            final LivingEntity target,
                                            final String damageTypeId) {
        // 1. Raw source position — only set for projectiles/explosions.
        //    Use sourcePositionRaw() (not getSourcePosition()) because
        //    getSourcePosition() falls back to directEntity.position(),
        //    which for player melee is the player's feet — not the hit point.
        //    Skip explosion & magic damage; spawn per-target body centre instead.
        final Vec3 sourcePos = source.sourcePositionRaw();
        if (sourcePos != null
                && !damageTypeId.contains("explosion")
                && !damageTypeId.contains("magic")) {
            return sourcePos;
        }

        // 2. Player melee: ray trace from eyes to target bounding box
        final Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof final Player player) {
            final Vec3 eyePos = player.getEyePosition();
            final Vec3 lookVec = player.getLookAngle();
            final double reach = 4.5; // generous enough for Forge-modified attack range
            final Vec3 endPos = eyePos.add(lookVec.scale(reach));
            final AABB targetBox = target.getBoundingBox();
            final Optional<Vec3> hitVec = targetBox.clip(eyePos, endPos);
            if (hitVec.isPresent()) {
                return hitVec.get();
            }
        }

        // 3. Fallback: target body centre
        return new Vec3(
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ());
    }

    /**
     * Determines whether the damage was a critical hit.
     *
     * <p>Matches vanilla Minecraft's critical-hit conditions:
     * the attacker must be a {@link Player} who is not on the ground,
     * has positive fall distance, is not in water, is not riding
     * another entity, and is not using elytra.
     *
     * @param source the damage source to inspect
     * @return {@code true} if this was a critical hit
     */
    private static boolean isCriticalHit(final DamageSource source) {
        final Entity attacker = source.getEntity();
        if (!(attacker instanceof final Player player)) {
            return false;
        }
        return !player.onGround()
                && player.fallDistance > 0.0F
                && !player.isInWater()
                && !player.isPassenger()
                && !player.isFallFlying();
    }

    /**
     * Classifies a Minecraft entity into an {@link EntityInfo} record
     * suitable for the common module's {@link DisplayFilter}.
     */
    private static EntityInfo classifyEntity(final Entity entity) {
        final boolean isPlayer = entity instanceof Player;
        final String mobCategoryName = entity instanceof Mob
                ? entity.getType().getCategory().getName() : null;

        final EntityClassifier.EntityType type =
                EntityClassifier.classify(isPlayer, mobCategoryName);

        String teamId = null;
        final net.minecraft.world.scores.Team team = entity.getTeam();
        if (team != null) {
            teamId = team.getName();
        }
        final String name = entity.getName().getString();

        return switch (type) {
            case PLAYER -> EntityInfo.player(teamId, name);
            case MOB_HOSTILE -> EntityInfo.hostileMob(name);
            case MOB_PASSIVE -> EntityInfo.passiveMob(name);
            case OTHER -> EntityInfo.other(name);
        };
    }
}
