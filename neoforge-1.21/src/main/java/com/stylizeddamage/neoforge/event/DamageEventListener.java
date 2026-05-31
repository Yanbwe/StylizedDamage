package com.stylizeddamage.neoforge.event;

import com.stylizeddamage.common.config.CommonConfig;
import com.stylizeddamage.common.config.ConfigManager;
import com.stylizeddamage.common.network.DamageSyncPacket;
import com.stylizeddamage.common.network.NetworkRegistrar;
import com.stylizeddamage.common.network.TotalDamageSyncPacket;
import com.stylizeddamage.common.selector.DisplayFilter;
import com.stylizeddamage.common.selector.EntityClassifier;
import com.stylizeddamage.common.selector.EntityInfo;
import com.stylizeddamage.neoforge.NeoForgePlatform;
import com.stylizeddamage.neoforge.StylizedDamageNeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Server-side listener for {@link LivingDamageEvent.Post} that extracts final
 * damage values and dispatches {@link DamageSyncPacket} to tracking clients.
 *
 * <p>Uses {@link LivingDamageEvent.Post} because it fires after all damage
 * reductions (armor, enchantments, resistance, absorption) have been applied,
 * providing the true final damage value. The pre-damage event
 * ({@link LivingDamageEvent.Pre}) is reserved for potential future damage
 * modification features.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Receive {@code LivingDamageEvent.Post} on the server thread.</li>
 *   <li>Extract damage amount, source, type ID, and entity IDs.</li>
 *   <li>Apply display filter from {@code common.json} (hide self-damage,
 *       source/target filtering).</li>
 *   <li>Build a {@link DamageSyncPacket} and send to all players tracking
 *       the damaged entity via {@link NetworkRegistrar#sendToTracking}.</li>
 * </ol>
 *
 * <h3>Note on LivingIncomingDamageEvent</h3>
 * <p>{@code LivingIncomingDamageEvent} still exists in NeoForge 1.21.1 but
 * fires before any damage processing — use {@code LivingDamageEvent.Post}
 * for the final, post-reduction value used in display.
 */
public final class DamageEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(StylizedDamageNeoForge.MOD_ID);

    private DamageEventListener() {
        // Utility class — no instances
    }

    /**
     * Registers this listener on the NeoForge game event bus.
     * Called once during mod construction.
     *
     * @param gameBus the NeoForge game event bus ({@code NeoForge.EVENT_BUS})
     */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(DamageEventListener::onLivingDamagePost);
        // Pre is reserved for future damage modification
        gameBus.addListener(DamageEventListener::onLivingDamagePre);
        LOG.debug("DamageEventListener registered on game bus");
    }

    // ── Main damage handler ────────────────────────────────────────────

    /**
     * Handles {@link LivingDamageEvent.Post}, which fires after the entity's
     * health has been modified and all reductions have been applied.
     *
     * <p>This is the primary entry point for generating floating damage numbers.
     * Only processes server-side damage events with positive damage values.
     */
    private static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) {
            return;
        }

        float damage = event.getNewDamage();
        if (damage <= 0) {
            return;
        }

        DamageSource source = event.getSource();
        String damageTypeId = extractDamageTypeId(source);

        // Apply the display filter from common.json
        CommonConfig config = ConfigManager.getInstance().getConfig();
        DisplayFilter filter = new DisplayFilter(config.displayFilter());

        Entity sourceEntity = source.getEntity();
        boolean isSelf = sourceEntity != null && sourceEntity.equals(entity);

        EntityInfo targetInfo = classifyEntity(entity);
        EntityInfo sourceInfo = sourceEntity != null
                ? classifyEntity(sourceEntity) : targetInfo;

        if (!filter.shouldDisplay(sourceInfo, targetInfo, isSelf, damage)) {
            LOG.trace("Damage display filtered: entity={}, type={}, amount={}",
                    entity.getName().getString(), damageTypeId, damage);
            return;
        }

        // Build the packet
        int sourceEntityId = sourceEntity != null ? sourceEntity.getId() : -1;
        int targetEntityId = entity.getId();
        long timestamp = System.currentTimeMillis();

        boolean isCritical = false; // TODO: detect via player attack state

        // Compute the world position where the damage landed
        final Vec3 hitPos = computeHitPosition(source, entity, damageTypeId);

        DamageSyncPacket packet = new DamageSyncPacket(
                sourceEntityId, targetEntityId, damage, damageTypeId,
                isCritical, timestamp,
                hitPos.x, hitPos.y, hitPos.z);

        LOG.trace("Dispatching damage sync: target={}, amount={}, type={}",
                entity.getName().getString(), damage, damageTypeId);

        // Send to all players tracking the damaged entity
        @SuppressWarnings("unchecked")
        NetworkRegistrar<Entity, ServerPlayer> registrar = NeoForgePlatform.getNetworkRegistrar();
        if (registrar != null) {
            registrar.sendToTracking(packet, entity);

            // If a player caused this damage, update their total damage panel
            if (sourceEntity instanceof ServerPlayer player) {
                TotalDamageSyncPacket totalPacket = new TotalDamageSyncPacket(
                        false, damage, damageTypeId);
                registrar.sendToPlayer(totalPacket, player);
            }

            // If this damage killed the entity, send a kill notification
            if (entity.getHealth() <= 0f) {
                DamageSyncPacket killPacket = new DamageSyncPacket(
                        sourceEntityId, targetEntityId, 0f, "kill",
                        false, timestamp,
                        hitPos.x, hitPos.y, hitPos.z);
                registrar.sendToTracking(killPacket, entity);
            }
        } else {
            LOG.warn("NetworkRegistrar not initialized — skipping damage packet dispatch");
        }
    }

    // ── Pre-damage handler (reserved) ──────────────────────────────────

    private static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // Reserved for future damage modification logic.
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Computes the world-space hit position for a damage event.
     *
     * <p>Prioritises the most accurate source available:
     * <ol>
     *   <li>{@link DamageSource#getSourcePosition()} — projectile impact /
     *       explosion centre.</li>
     *   <li>Player melee ray trace — from eyes along look direction to
     *       target bounding box.</li>
     *   <li>Target body centre — fallback for environmental / status-effect
     *       damage.</li>
     * </ol>
     */
    private static Vec3 computeHitPosition(final DamageSource source,
                                            final LivingEntity target,
                                            final String damageTypeId) {
        // Use sourcePositionRaw() — not getSourcePosition() which falls
        // back to directEntity.position() and breaks melee ray tracing.
        final Vec3 sourcePos = source.sourcePositionRaw();
        if (sourcePos != null
                && !damageTypeId.contains("explosion")
                && !damageTypeId.contains("magic")) {
            return sourcePos;
        }
        final Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof final Player player) {
            final Vec3 eyePos = player.getEyePosition();
            final Vec3 lookVec = player.getLookAngle();
            final double reach = 4.5;
            final Vec3 endPos = eyePos.add(lookVec.scale(reach));
            final AABB targetBox = target.getBoundingBox();
            final Optional<Vec3> hitVec = targetBox.clip(eyePos, endPos);
            if (hitVec.isPresent()) {
                return hitVec.get();
            }
        }
        return new Vec3(
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ());
    }

    /**
     * Extracts the damage type identifier string from a {@link DamageSource}.
     * In 1.21.1, the damage type is accessed via the holder key's location.
     *
     * @param source the damage source from the event
     * @return the damage type ID string, e.g. "minecraft:in_fire"
     */
    static String extractDamageTypeId(DamageSource source) {
        return source.typeHolder().getKey().location().toString();
    }

    /**
     * Classifies a Minecraft entity into an {@link EntityInfo} record
     * suitable for the common module's {@link DisplayFilter}.
     *
     * <p>Uses {@link EntityClassifier} static methods to determine the
     * entity type (player / hostile mob / passive mob / other), then
     * builds the corresponding {@code EntityInfo} with team ID and
     * display name for team-based filtering.
     */
    private static EntityInfo classifyEntity(Entity entity) {
        boolean isPlayer = entity instanceof Player;
        String mobCategoryName = entity instanceof Mob
                ? entity.getType().getCategory().getName() : null;

        EntityClassifier.EntityType type =
                EntityClassifier.classify(isPlayer, mobCategoryName);

        String teamId = null;
        PlayerTeam team = entity.getTeam();
        if (team != null) {
            teamId = team.getName();
        }
        String name = entity.getName().getString();

        return switch (type) {
            case PLAYER -> EntityInfo.player(teamId, name);
            case MOB_HOSTILE -> EntityInfo.hostileMob(name);
            case MOB_PASSIVE -> EntityInfo.passiveMob(name);
            case OTHER -> EntityInfo.other(name);
        };
    }
}
