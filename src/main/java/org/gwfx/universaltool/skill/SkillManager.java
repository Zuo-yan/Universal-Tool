package org.gwfx.universaltool.skill;



import net.minecraft.ChatFormatting;

import net.minecraft.core.BlockPos;

import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerLevel;

import net.minecraft.server.level.ServerPlayer;

import net.minecraft.sounds.SoundEvents;

import net.minecraft.sounds.SoundSource;

import net.minecraft.tags.BlockTags;

import net.minecraft.world.effect.MobEffectInstance;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.entity.Entity;

import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.entity.Mob;

import net.minecraft.world.entity.TamableAnimal;

import net.minecraft.world.entity.animal.Animal;

import net.minecraft.world.entity.monster.Enemy;

import net.minecraft.world.entity.player.Player;

import net.minecraft.world.level.block.Blocks;

import net.minecraft.world.level.block.CropBlock;

import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.minecraft.world.phys.AABB;

import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.common.EventBusSubscriber;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.neoforged.neoforge.event.level.BlockEvent;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.neoforged.neoforge.network.PacketDistributor;

import org.gwfx.universaltool.UniversalToolMod;

import org.gwfx.universaltool.init.ModSounds;

import org.gwfx.universaltool.network.SyncSkillDataPayload;
import org.gwfx.universaltool.network.OreSightPayload;
import org.gwfx.universaltool.network.CounterGuardEffectPayload;
import net.minecraft.nbt.CompoundTag;



import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Map;

import java.util.Set;

import java.util.UUID;

import java.util.concurrent.ConcurrentHashMap;



/**

 * Server-authoritative skill runtime: progression, learning, loadout, casting, and passive effects.

 *

 * <p>All client input arrives as intent and is validated here. Cooldowns and combat timers use

 * wall-clock time so they keep recovering across server restarts and offline periods.

 */

@EventBusSubscriber(modid = UniversalToolMod.MODID)

public final class SkillManager {

    public static final int PULL_FREEZE_RADIUS = 10;

    public static final int FREEZE_TICKS = 100;

    public static final int ORE_SIGHT_TICKS = 200;

    public static final int PLAYER_DISCOVERY_EXPERIENCE = 200;

    public static final int STRUCTURE_DISCOVERY_EXPERIENCE = 400;

    public static final int COMBAT_XP_RADIUS = 32;

    /** Cancelling a charge applies a short lockout so cancel-and-recast cannot be farmed. */

    public static final int CANCEL_COOLDOWN_TICKS = 20;

    public static final int CHARGE_MIN_TICKS = 6;

    public static final int MOMENTUM_WINDOW_TICKS = 60;

    public static final int MOMENTUM_MAX_STACKS = 5;
    public static final int STASIS_DAMAGE_INTERVAL_TICKS = 10;
    public static final float STASIS_DAMAGE_FRACTION = 0.04F;



    private static final Map<UUID, ChargeState> CHARGES = new ConcurrentHashMap<>();

    private static final Map<UUID, GuardState> GUARDS = new ConcurrentHashMap<>();

    /** Monotonic presentation sequence used to reject stale shield-state packets. */
    private static final java.util.concurrent.atomic.AtomicLong GUARD_EFFECT_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();

    private static final Map<UUID, FrozenState> FROZEN = new ConcurrentHashMap<>();

    private static final Map<UUID, Momentum> MOMENTUM = new ConcurrentHashMap<>();
    private static final Map<UUID, StasisState> STASIS = new ConcurrentHashMap<>();

    private static final Map<UUID, OreSightState> ORE_SIGHT = new ConcurrentHashMap<>();
    private static final List<GravityWellState> GRAVITY_WELLS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final Map<UUID, Map<String, Long>> PLACED_BLOCKS = new ConcurrentHashMap<>();

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();



    private record ChargeState(String skillId, long startGameTime) {

    }



    private record GuardState(float reflectMultiplier, int level) {

    }



    private record FrozenState(Entity entity, ServerLevel level, long until, boolean previousNoAi) {

    }



    private record Momentum(int stacks, long lastHitGameTime) {

    }

    private record StasisState(Entity target, ServerLevel level, ServerPlayer owner,
                               long until, long nextDamageAt, float fixedDamage) {

    }



    private record OreSightState(ServerLevel level, Set<BlockPos> orePositions, long expiresAt) {

    }

    private record GravityWellState(ServerLevel level, Vec3 center, double radius,
                                    long expiresAt, UUID ownerId) {
    }



    public static PlayerSkillData data(ServerPlayer player) {

        return player.getData(SkillAttachments.SKILL_DATA.get());

    }



    public static void sync(ServerPlayer player) {

        DIRTY.remove(player.getUUID());

        PacketDistributor.sendToPlayer(player,

                new SyncSkillDataPayload(data(player).serializeForSync(System.currentTimeMillis())));

    }



    /** Batches frequent progress changes so mining and exploring do not spam one packet per block. */

    private static void markDirty(ServerPlayer player) {

        DIRTY.add(player.getUUID());

    }



    public static void handleAction(ServerPlayer player, org.gwfx.universaltool.network.SkillActionPayload.Action action,

                                    String skillId, List<String> loadout) {

        PlayerSkillData data = data(player);

        long gameTime = player.serverLevel().getGameTime();

        if (action != org.gwfx.universaltool.network.SkillActionPayload.Action.SELECT_ACTIVE

                && data.inCombat(gameTime)) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.in_combat"), true);

            return;

        }

        if (action != org.gwfx.universaltool.network.SkillActionPayload.Action.SELECT_ACTIVE

                && CHARGES.containsKey(player.getUUID())) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.executing"), true);

            return;

        }

        switch (action) {

            case LEARN -> {

                if (data.learnOrUpgrade(skillId)) {

                    applyPassiveEffects(player);

                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.learned"), true);

                    sync(player);

                } else {

                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.learn_failed"), true);

                }

            }

            case SELECT_ACTIVE -> {

                if (data.setSelectedActive(parseIndex(skillId))) {

                    cancelCharge(player);

                    sendActionFeedback(player, "message.universal_tool.skills.active_selected",

                            data.selectedDefinition().map(SkillDefinition::translationKey)

                                    .map(Component::translatable).orElse(Component.empty()));

                }

            }

            case SET_ACTIVE -> {

                if (loadout.isEmpty() ? data.addToLoadout(true, skillId) : data.setLoadout(true, loadout)) {

                    sync(player);

                } else {

                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.loadout_failed"), true);

                }

            }

            case SET_PASSIVE -> {

                if (loadout.isEmpty() ? data.addToLoadout(false, skillId) : data.setLoadout(false, loadout)) {

                    applyPassiveEffects(player);

                    sync(player);

                } else {

                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.loadout_failed"), true);

                }

            }

            case UNSET_ACTIVE, UNSET_PASSIVE -> {

                boolean active = action == org.gwfx.universaltool.network.SkillActionPayload.Action.UNSET_ACTIVE;

                int index = parseIndex(skillId);

                if (index >= 0 && data.removeFromLoadout(active, index)) {

                    clearSkillEffects(player, active, index);

                    applyPassiveEffects(player);

                    sync(player);

                } else {

                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.loadout_failed"), true);

                }

            }

            case RESET -> {

                boolean safeGround = player.onGround() || player.isCreative() || player.fallDistance < 1.5F || player.getAbilities().flying;
                if (safeGround) {

                    if (skillId != null && !skillId.isEmpty()) {

                        int refunded = data.resetSkill(skillId);

                        if (refunded > 0) {

                            clearSingleSkillEffects(player, skillId);

                            sendActionFeedback(player, "message.universal_tool.skills.skill_reset_done",

                                    Component.translatable("skill.universal_tool." + skillId), refunded);

                        } else {

                            sendActionFeedback(player, "message.universal_tool.skills.skill_not_learned");

                        }

                    } else {

                        clearAllSkillEffects(player);

                        data.resetAllocations();

                        sendActionFeedback(player, "message.universal_tool.skills.reset_done");

                    }

                } else {

                    sendActionFeedback(player, "message.universal_tool.skills.reset_unsafe");

                }

            }

        }

    }



    private static void sendActionFeedback(ServerPlayer player, String key, Object... args) {

        player.sendSystemMessage(Component.translatable(key, args));

        sync(player);

    }



    private static int parseIndex(String raw) {

        try {

            return Integer.parseInt(raw);

        } catch (NumberFormatException ignored) {

            return -1;

        }

    }



    /** Entry point for the cast key. A release finishes a charge; a press starts one or casts instantly. */

    public static boolean isFrozen(Entity entity) {

        return entity != null && FROZEN.containsKey(entity.getUUID());

    }



    public static void handleCast(ServerPlayer player, boolean releasing) {

        if (isFrozen(player)) {

            if (CHARGES.containsKey(player.getUUID())) {

                cancelCharge(player);

            }

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.frozen_cannot_cast"), true);

            return;

        }

        if (releasing) {

            releaseCharge(player);

            return;

        }

        castSelected(player);

    }



    public static void castSelected(ServerPlayer player) {

        PlayerSkillData data = data(player);

        String id = data.selectedSkill();

        if (id.isEmpty()) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.no_skill"), true);

            return;

        }

        SkillDefinition skill = ModSkills.find(id).orElse(null);

        if (skill == null || skill.type() != SkillType.ACTIVE || !data.learned(id)) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.no_skill"), true);

            return;

        }

        if (isFrozen(player)) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.frozen_cannot_cast"), true);

            return;

        }

        if (CHARGES.containsKey(player.getUUID()) || player.isUsingItem()) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.executing"), true);

            return;

        }

        if (data.onCooldown(id)) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.cooldown",

                    (data.cooldownRemainingTicks(id) + 19) / 20), true);

            return;

        }

        if (ModSkills.AIR_STEP.equals(id)) {
            if (castAirStep(player, data.learnedLevel(id))) {
                CHARGES.put(player.getUUID(), new ChargeState(id, player.serverLevel().getGameTime()));
            }
            return;
        }

        if (skill.chargeTicks() > 0) {

            CHARGES.put(player.getUUID(), new ChargeState(id, player.serverLevel().getGameTime()));

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.charging"), true);

            return;

        }

        if (castInstant(player, data, skill)) {

            data.startCooldown(id, skill.cooldownTicks());

            sync(player);

        }

    }



    private static void releaseCharge(ServerPlayer player) {

        ChargeState state = CHARGES.remove(player.getUUID());

        if (state == null) return;

        PlayerSkillData data = data(player);

        SkillDefinition skill = ModSkills.find(state.skillId()).orElse(null);

        if (skill == null) return;

        long elapsed = player.serverLevel().getGameTime() - state.startGameTime();

        if (ModSkills.AIR_STEP.equals(skill.id())) {
            data.startCooldown(skill.id(), skill.cooldownTicks());
            sync(player);
            return;
        }

        if (elapsed < CHARGE_MIN_TICKS) {

            data.startCooldown(skill.id(), CANCEL_COOLDOWN_TICKS);

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.charge_cancelled"), true);

            sync(player);

            return;

        }

        float ratio = Math.min(1.0F, elapsed / (float) skill.chargeTicks());

        if (castCharged(player, data, skill, ratio)) {

            data.startCooldown(skill.id(), skill.cooldownTicks());

        } else {

            data.startCooldown(skill.id(), CANCEL_COOLDOWN_TICKS);

        }

        sync(player);

    }



    /** Cancels an in-flight charge without producing an effect, applying the short lockout. */

    public static void cancelCharge(ServerPlayer player) {

        ChargeState state = CHARGES.remove(player.getUUID());

        if (state == null) return;

        PlayerSkillData data = data(player);

        data.startCooldown(state.skillId(), ModSkills.AIR_STEP.equals(state.skillId())
                ? ModSkills.find(state.skillId()).orElseThrow().cooldownTicks() : CANCEL_COOLDOWN_TICKS);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.charge_cancelled"), true);

        sync(player);

    }



    private static boolean castInstant(ServerPlayer player, PlayerSkillData data, SkillDefinition skill) {

        int level = Math.max(1, data.learnedLevel(skill.id()));

        return switch (skill.id()) {

            case ModSkills.COUNTER_GUARD -> castCounterGuard(player, level);

            case ModSkills.GRAVITY_WELL -> castGravityWell(player, level);

            case ModSkills.FROZEN_PULL -> castFrozenPull(player, level);

            case ModSkills.OPPRESSIVE_FIELD -> castOppressiveField(player, level);

            case ModSkills.ORE_SIGHT -> castOreSight(player, level);

            case ModSkills.AIR_STEP -> castAirStep(player, level);

            case ModSkills.BERSERK -> castBerserk(player, level);


            default -> false;

        };

    }



    private static boolean castCharged(ServerPlayer player, PlayerSkillData data, SkillDefinition skill, float ratio) {

        int level = Math.max(1, data.learnedLevel(skill.id()));

        return switch (skill.id()) {

            case ModSkills.LIGHTNING_SPEAR -> castLightningSpear(player, level, ratio);

            default -> false;

        };

    }



    public static double gravityWellRadius(int level) {

        return 12.0D + 2.0D * level;

    }



    private static final int GRAVITY_WELL_DURATION_TICKS = 100;

    private static final double GRAVITY_WELL_AREA = 12.0D;



    private static boolean castCounterGuard(ServerPlayer player, int level) {

        if (GUARDS.containsKey(player.getUUID())) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.guard_active"), true);

            return false;

        }

        GUARDS.put(player.getUUID(), new GuardState(1.0F + 0.25F * (level - 1), level));

        broadcastGuardEffect(player, CounterGuardEffectPayload.Action.EXPAND);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.guard_ready"), true);

        player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,

                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.7, 1.0, 0.7, 0.05);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);

        return true;

    }



    private static boolean castGravityWell(ServerPlayer player, int level) {

        ServerLevel level0 = player.serverLevel();

        double radius = gravityWellRadius(level);

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(GRAVITY_WELL_AREA));
        Vec3 center = player.isShiftKeyDown()
                ? level0.clip(new net.minecraft.world.level.ClipContext(start, end,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getLocation()
                : end;
        GRAVITY_WELLS.add(new GravityWellState(level0, center, radius,
                level0.getGameTime() + GRAVITY_WELL_DURATION_TICKS, player.getUUID()));
        tickGravityWells(level0.getGameTime());

        level0.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 160,

                1.0, 1.6, 1.0, 0.35);

        level0.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z,

                35, 0.5, 0.8, 0.5, 0.015);

        level0.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_GROWL,

                SoundSource.PLAYERS, 0.6F, 0.65F);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.gravity_well",

                (int) radius), true);

        return true;

    }



    private static void tickGravityWells(long gameTime) {

        if (GRAVITY_WELLS.isEmpty()) return;

        GRAVITY_WELLS.removeIf(well -> !well.level().isLoaded(

                BlockPos.containing(well.center())) || gameTime >= well.expiresAt());

        for (GravityWellState well : GRAVITY_WELLS) {

            ServerPlayer owner = well.level().getServer().getPlayerList().getPlayer(well.ownerId());

            if (owner == null) continue;

            AABB bounds = new AABB(well.center(), well.center()).inflate(well.radius());

            for (LivingEntity target : well.level().getEntitiesOfClass(LivingEntity.class, bounds,

                    entity -> canAffect(owner, entity))) {

                Vec3 toCenter = well.center().subtract(target.position());

                double distance = toCenter.length();

                if (distance < 0.4 || distance > well.radius()) continue;

                Vec3 pull = toCenter.normalize().scale(Math.min(0.85, 0.22 + distance * 0.035));

                target.setDeltaMovement(target.getDeltaMovement().scale(0.35)

                        .add(pull.x, Math.max(-0.18, pull.y * 0.25), pull.z));

                target.hurtMarked = true;

            }

            if (gameTime % 4 == 0) {

                double angle = gameTime * 0.35;

                double orbit = Math.max(1.0, well.radius() * 0.18);

                double px = well.center().x + Math.cos(angle) * orbit;

                double pz = well.center().z + Math.sin(angle) * orbit;

                well.level().sendParticles(ParticleTypes.PORTAL,

                        px, well.center().y + 0.4, pz, 8, 1.3, 1.0, 1.3, 0.22);

                well.level().sendParticles(ParticleTypes.REVERSE_PORTAL,

                        well.center().x, well.center().y + 0.5, well.center().z,

                        18, 0.6, 0.8, 0.6, 0.45);

            }

        }

    }



    private static boolean castFrozenPull(ServerPlayer player, int level) {

        ServerLevel level0 = player.serverLevel();

        double range = 16.0D + 4.0D * level;

        LivingEntity target = findCrosshairTarget(player, range);

        if (target == null) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.no_target"), true);

            return false;

        }

        Vec3 look = player.getViewVector(1.0F);

        Vec3 flat = new Vec3(look.x, 0, look.z);

        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);

        Vec3 destination = player.position().add(flat.normalize().scale(2.2));

        target.teleportTo(destination.x, player.getY(), destination.z);

        target.setDeltaMovement(Vec3.ZERO);

        target.hurtMarked = true;

        freeze(player, target, level0);

        level0.sendParticles(ParticleTypes.SNOWFLAKE, destination.x, destination.y + 1.0, destination.z,

                40, 0.5, 0.8, 0.5, 0.08);

        level0.playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.frozen_pull"), true);

        return true;

    }



    /**

     * Freezes a target for {@link #FREEZE_TICKS}. The target is fully stunned: no movement, attacks,

     * or abilities. Damage still applies normally and does not break the freeze. While frozen,
     * the target also takes a small fixed fraction of its maximum health every half second.

     */

    private static void freeze(ServerPlayer owner, LivingEntity target, ServerLevel level) {

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_TICKS, 255, false, true, true));

        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FREEZE_TICKS, 255, false, true, true));

        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FREEZE_TICKS, 255, false, true, true));

        FrozenState existing = FROZEN.get(target.getUUID());

        boolean previousNoAi = false;

        if (target instanceof Mob mob) {

            previousNoAi = existing != null ? existing.previousNoAi() : mob.isNoAi();

            mob.setNoAi(true);

            mob.setTarget(null);

            mob.getNavigation().stop();

            mob.setAggressive(false);

        }

        FROZEN.put(target.getUUID(), new FrozenState(target, level, level.getGameTime() + FREEZE_TICKS, previousNoAi));
        STASIS.put(target.getUUID(), new StasisState(target, level, owner,
                level.getGameTime() + FREEZE_TICKS,
                level.getGameTime() + STASIS_DAMAGE_INTERVAL_TICKS,
                Math.max(1.0F, target.getMaxHealth() * STASIS_DAMAGE_FRACTION)));

        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1.0, target.getZ(),

                45, 0.7, 1.0, 0.7, 0.08);

        level.playSound(null, target.getX(), target.getY(), target.getZ(),

                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);

    }



    private static void tickFrozen(long gameTime) {

        if (FROZEN.isEmpty()) {
            STASIS.clear();
            return;
        }

        List<UUID> expired = new ArrayList<>();

        for (Map.Entry<UUID, FrozenState> entry : FROZEN.entrySet()) {

            FrozenState state = entry.getValue();

            if (gameTime >= state.until()) {

                expired.add(entry.getKey());

                continue;

            }

            if (state.entity().isRemoved() || !state.entity().isAlive()) {

                expired.add(entry.getKey());

                continue;

            }

            if (state.entity() instanceof Mob mob) {

                mob.setNoAi(true);

                mob.setTarget(null);

                mob.getNavigation().stop();

                Vec3 movement = mob.getDeltaMovement();

                mob.setDeltaMovement(0, Math.min(0, movement.y), 0);

            }

            if (gameTime % 5 == 0) {

                state.level().sendParticles(ParticleTypes.SNOWFLAKE,

                        state.entity().getX(), state.entity().getY() + 0.8, state.entity().getZ(),

                        2, 0.3, 0.5, 0.3, 0.01);

            }

        }

        for (UUID id : expired) {

            FrozenState state = FROZEN.remove(id);

            if (state != null && state.entity() instanceof Mob mob && !mob.isRemoved()) {

                mob.setNoAi(state.previousNoAi());

            }

            STASIS.remove(id);

        }

        tickStasis(gameTime);

    }

    private static void tickStasis(long gameTime) {
        for (Map.Entry<UUID, StasisState> entry : STASIS.entrySet()) {
            StasisState state = entry.getValue();
            LivingEntity target = state.target() instanceof LivingEntity living ? living : null;
            if (target == null || target.isRemoved() || !target.isAlive()
                    || gameTime >= state.until()) {
                STASIS.remove(entry.getKey());
                continue;
            }
            if (gameTime < state.nextDamageAt()) continue;
            state.level().sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.25, 0.35, 0.25, 0.02);
            target.hurt(state.level().damageSources().indirectMagic(state.owner(), state.owner()),
                    state.fixedDamage());
            STASIS.put(entry.getKey(), new StasisState(state.target(), state.level(), state.owner(),
                    state.until(), gameTime + STASIS_DAMAGE_INTERVAL_TICKS, state.fixedDamage()));
        }
    }



    private static boolean castOppressiveField(ServerPlayer player, int level) {

        ServerLevel level0 = player.serverLevel();

        double radius = oppressiveRadius(level);
        int duration = oppressiveDurationTicks(level);

        for (LivingEntity target : targets(level0, player, player.position(), radius)) {

            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2, false, true, true));

        }

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, level >= 3 ? 1 : 0,

                false, true, true));

        level0.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1.0, player.getZ(),

                70, radius / 3.0, 2.0, radius / 3.0, 0.02);

        level0.playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.75F);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.oppressive_field"), true);

        return true;

    }



    private static boolean castOreSight(ServerPlayer player, int level) {

        ServerLevel level0 = player.serverLevel();

        int radius = 12 + 4 * level;

        int vertical = 8 + level;

        Set<BlockPos> ores = new LinkedHashSet<>();

        BlockPos origin = player.blockPosition();

        for (int x = -radius; x <= radius; x++) {

            for (int y = -vertical; y <= vertical; y++) {

                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos = origin.offset(x, y, z);

                    if (level0.isLoaded(pos) && isOre(level0.getBlockState(pos))) ores.add(pos.immutable());

                }

            }

        }

        ORE_SIGHT.put(player.getUUID(), new OreSightState(level0, ores, level0.getGameTime() + ORE_SIGHT_TICKS));
        syncOreMarkers(player, ores, ORE_SIGHT_TICKS);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.ore_sight",

                ores.size()), true);

        level0.playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);

        return true;

    }



    public static double oppressiveRadius(int level) {
        return 12 + 3 * (Math.max(1, Math.min(3, level)) - 1);
    }

    public static int oppressiveDurationTicks(int level) {
        return 120 + 40 * (Math.max(1, Math.min(3, level)) - 1);
    }

    public static int airStepDurationTicks(int level) {
        return 20 * Math.max(1, Math.min(3, level));
    }

    private static void tickAirStep(ServerPlayer player, long now) {
        ChargeState state = CHARGES.get(player.getUUID());
        if (state == null || !ModSkills.AIR_STEP.equals(state.skillId())) return;
        int level = data(player).learnedLevel(ModSkills.AIR_STEP);
        if (!player.isAlive() || isFrozen(player) || player.onGround()
                || now - state.startGameTime() >= airStepDurationTicks(level)) {
            releaseCharge(player);
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 direction = new Vec3(look.x, Math.max(0.05, look.y), look.z).normalize();
        player.setDeltaMovement(direction.scale(0.65 + 0.1 * level));
        player.resetFallDistance();
        player.hurtMarked = true;
        if (now % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(),
                    player.getZ(), 2, 0.15, 0.05, 0.15, 0.01);
        }
    }

    private static boolean castAirStep(ServerPlayer player, int level) {

        if (player.onGround()) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.air_step_ground"), true);

            return false;

        }

        Vec3 look = player.getLookAngle();

        Vec3 direction = new Vec3(look.x, Math.max(0.15, look.y), look.z).normalize();

        Vec3 current = player.getDeltaMovement();

        double strength = 1.35 + 0.15 * level;

        player.setDeltaMovement(direction.scale(strength).add(current.x * 0.25, 0.18, current.z * 0.25));

        player.resetFallDistance();

        player.hurtMarked = true;

        player.serverLevel().sendParticles(ParticleTypes.CLOUD,

                player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.35, 0.1, 0.35, 0.08);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.25F);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.air_step"), true);

        return true;

    }



    private static boolean castLightningSpear(ServerPlayer player, int level, float ratio) {
        ServerLevel level0 = player.serverLevel();
        float damage = 8.0F + 5.0F * level * ratio;
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        float projectileSpeed = 2.4F;

        LightningSpearProjectile projectile = new LightningSpearProjectile(
                level0, player, damage);
        projectile.setChargeRatio(ratio);
        projectile.setPos(start.x + direction.x * 0.8,
                start.y + direction.y * 0.8, start.z + direction.z * 0.8);
        projectile.shoot(direction.x, direction.y, direction.z, projectileSpeed, 0.0F);
        level0.addFreshEntity(projectile);

        level0.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.LIGHTNING_SPEAR_THROW.get(), SoundSource.PLAYERS, 0.9F, 1.0F);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.lightning_spear"), true);
        return true;
    }

    /** Test harness hook for interacting with a live damage source without changing game rules. */
    private static boolean castBerserk(ServerPlayer player, int level) {

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, level - 1, false, true, true));

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, level - 1, false, true, true));

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, level >= 3 ? 1 : 0,

                false, true, true));

        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0,

                player.getZ(), 45, 0.7, 1.0, 0.7, 0.08);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 0.8F);

        return true;

    }



    private static List<LivingEntity> targets(ServerLevel level, ServerPlayer source, Vec3 center, double radius) {

        AABB bounds = new AABB(center.x - radius, center.y - radius, center.z - radius,

                center.x + radius, center.y + radius, center.z + radius);

        return level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> canAffect(source, entity));

    }



    private static LivingEntity findCrosshairTarget(ServerPlayer player, double maxRange) {

        Vec3 eyePos = player.getEyePosition();

        Vec3 look = player.getViewVector(1.0F).normalize();

        Vec3 end = eyePos.add(look.scale(maxRange));

        AABB box = player.getBoundingBox().expandTowards(look.scale(maxRange)).inflate(4.0D);



        LivingEntity bestHit = null;

        double nearestDistSqr = maxRange * maxRange;

        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,

                e -> canPull(player, e))) {

            var clip = candidate.getBoundingBox().inflate(1.5).clip(eyePos, end);

            if (clip.isPresent()) {

                double dist = clip.get().distanceToSqr(eyePos);

                if (dist < nearestDistSqr) {

                    nearestDistSqr = dist;

                    bestHit = candidate;

                }

            }

        }

        if (bestHit != null) return bestHit;



        LivingEntity best = null;

        double bestScore = Double.MAX_VALUE;



        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,

                e -> canPull(player, e))) {

            Vec3 candidatePos = candidate.getBoundingBox().getCenter();

            Vec3 toCandidate = candidatePos.subtract(eyePos);

            double forwardDist = toCandidate.dot(look);

            if (forwardDist < 0.5 || forwardDist > maxRange) continue;



            Vec3 rayPoint = eyePos.add(look.scale(forwardDist));

            double distToRay = candidatePos.distanceTo(rayPoint);

            double maxAllowedDist = Math.max(2.5, forwardDist * 0.22);

            if (distToRay <= maxAllowedDist) {

                // Prioritize closest alignment with crosshair center, with mild distance weighting

                double score = distToRay + (forwardDist * 0.04);

                if (score < bestScore) {

                    bestScore = score;

                    best = candidate;

                }

            }

        }

        return best;

    }



    /**

     * Skill targeting stays inside the agreed boundaries: hostile creatures, plus players and pets

     * only when the server allows PVP and they are not allied. Villagers, golems, animals, and

     * display entities are never valid targets.

     */

    public static boolean canPull(ServerPlayer source, LivingEntity target) {

        if (target == source || !target.isAlive() || target.isSpectator()) return false;

        if (target instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;

        if (target instanceof TamableAnimal tameable && tameable.getOwner() instanceof Player owner) {

            if (owner == source || owner.getUUID().equals(source.getUUID())) return false;

            return source.server.isPvpAllowed() && !source.isAlliedTo(owner);

        }

        if (target instanceof Player other) {

            return source.server.isPvpAllowed() && !source.isAlliedTo(other) && !other.isSpectator();

        }

        return true;

    }



    public static boolean canAffect(ServerPlayer source, LivingEntity target) {

        if (target == source || !target.isAlive() || target.isSpectator()) return false;

        if (target instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;

        if (target instanceof net.minecraft.world.entity.npc.Villager) return false;

        if (target instanceof net.minecraft.world.entity.animal.IronGolem golem && !golem.isAngryAt(source)) {

            return false;

        }

        if (target instanceof TamableAnimal tameable && tameable.getOwner() instanceof Player owner) {

            if (owner == source || owner.getUUID().equals(source.getUUID())) return false;

            return source.server.isPvpAllowed() && !source.isAlliedTo(owner);

        }

        if (target instanceof Player other) {

            return source.server.isPvpAllowed() && !source.isAlliedTo(other) && !other.isSpectator();

        }

        if (target instanceof Animal) return false;

        if (target instanceof Enemy) return true;

        if (target instanceof net.minecraft.world.entity.NeutralMob neutral && neutral.isAngryAt(source)) return true;

        return false;

    }



    private static boolean isOre(BlockState state) {

        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)

                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)

                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)

                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)

                || state.is(Blocks.ANCIENT_DEBRIS)

                || state.is(Blocks.NETHER_QUARTZ_ORE);

    }



    private static boolean isMatureCrop(BlockState state) {

        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);

        if (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) {

            return state.hasProperty(BlockStateProperties.AGE_3) && state.getValue(BlockStateProperties.AGE_3) >= 3;

        }

        return false;

    }



    /** Passive level if the skill is currently equipped, otherwise 0. */

    private static int equippedLevel(ServerPlayer player, String id) {

        PlayerSkillData data = data(player);

        if (!data.passiveLoadout().contains(id)) return 0;

        return data.learnedLevel(id);

    }



    private static final ResourceLocation IRON_PHYSIQUE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "iron_physique");
    private static final ResourceLocation SWIFT_PACE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "swift_pace");

    static void applyPassiveEffects(ServerPlayer player) {
        int mining = equippedLevel(player, ModSkills.FOCUS_MINING);
        if (mining > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 45, mining - 1, true, false, true));
        }

        int healthLvl = equippedLevel(player, ModSkills.IRON_PHYSIQUE);
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(IRON_PHYSIQUE_MODIFIER_ID);
            if (healthLvl > 0) {
                double bonusHp = switch (healthLvl) {
                    case 1 -> 6.0;
                    case 2 -> 12.0;
                    case 3 -> 20.0;
                    default -> 0.0;
                };
                healthAttr.addTransientModifier(new AttributeModifier(
                        IRON_PHYSIQUE_MODIFIER_ID, bonusHp, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        int paceLvl = equippedLevel(player, ModSkills.SWIFT_PACE);
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SWIFT_PACE_MODIFIER_ID);
            if (paceLvl > 0) {
                double speedBonus = switch (paceLvl) {
                    case 1 -> 0.10;
                    case 2 -> 0.18;
                    case 3 -> 0.25;
                    default -> 0.0;
                };
                speedAttr.addTransientModifier(new AttributeModifier(
                        SWIFT_PACE_MODIFIER_ID, speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    private static void clearSkillEffects(ServerPlayer player, boolean active, int index) {

        if (!active) {

            applyPassiveEffects(player);

            return;

        }

        // An active skill that is no longer equipped must not keep granting its own state.

        if (GUARDS.remove(player.getUUID()) != null) {
            broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
        }

        if (!data(player).activeLoadout().contains(ModSkills.ORE_SIGHT)) {

            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);

        }

    }



    private static void clearSingleSkillEffects(ServerPlayer player, String skillId) {

        if (ModSkills.COUNTER_GUARD.equals(skillId)) {

            if (GUARDS.remove(player.getUUID()) != null) {
                broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
            }

        } else if (ModSkills.ORE_SIGHT.equals(skillId)) {

            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);

        } else if (ModSkills.BATTLE_MOMENTUM.equals(skillId)) {

            MOMENTUM.remove(player.getUUID());

        }

        applyPassiveEffects(player);

    }



    private static void clearAllSkillEffects(ServerPlayer player) {

        if (GUARDS.remove(player.getUUID()) != null) {
            broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
        }

        MOMENTUM.remove(player.getUUID());

        ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);
        GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(player.getUUID()));
        var hAttr = player.getAttribute(Attributes.MAX_HEALTH); if (hAttr != null) hAttr.removeModifier(IRON_PHYSIQUE_MODIFIER_ID);
        var sAttr = player.getAttribute(Attributes.MOVEMENT_SPEED); if (sAttr != null) sAttr.removeModifier(SWIFT_PACE_MODIFIER_ID);

    }



    private static void clearTransient(ServerPlayer player) {

        UUID id = player.getUUID();

        ChargeState held = CHARGES.get(id);
        if (held != null && ModSkills.AIR_STEP.equals(held.skillId())) cancelCharge(player);
        CHARGES.remove(id);
        FROZEN.entrySet().removeIf(entry -> entry.getValue().entity() instanceof Player owner
                && owner.getUUID().equals(id));
        STASIS.entrySet().removeIf(entry -> entry.getValue().owner().getUUID().equals(id));

        if (GUARDS.remove(id) != null) {
            broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
        }

        MOMENTUM.remove(id);

        ORE_SIGHT.remove(id);
        syncOreMarkers(player, Set.of(), 0);
        GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(id));

        DIRTY.remove(id);

        // PLACED_BLOCKS intentionally survives logout, respawn, and dimension changes: forgetting it

        // would let a player place a block, relog, and then mine it for growth experience.

    }



    @SubscribeEvent

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {

            clearTransient(player);

            data(player).clearCombat();

            sync(player);

        }

    }



    @SubscribeEvent

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {

            clearTransient(player);

        }

    }



    @SubscribeEvent

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {

            clearTransient(player);

            data(player).clearCombat();

            player.getPersistentData().remove("universal_tool_combat_game_time");

            sync(player);

        }

    }



    @SubscribeEvent

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        clearTransient(player);

        data(player).clearCombat();

        String dimension = event.getTo().location().toString();

        PlayerSkillData data = data(player);

        if (data.addDiscovery("dimension:" + dimension)) {

            data.addExperience(PLAYER_DISCOVERY_EXPERIENCE);

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.discovery",

                    PLAYER_DISCOVERY_EXPERIENCE), true);

        }

        sync(player);

    }



    @SubscribeEvent

    public static void onLivingDamage(LivingDamageEvent.Post event) {

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        if (!(event.getEntity() instanceof LivingEntity target)) return;

        long gameTime = player.serverLevel().getGameTime();

        data(player).markCombat(gameTime);

        player.getPersistentData().putLong("universal_tool_combat_game_time", gameTime);

        if (target instanceof Enemy) {

            player.getPersistentData().putUUID("universal_tool_last_enemy", target.getUUID());

        }

        trackMomentum(player, target, gameTime);
        applyBloodthirst(player, target, event.getNewDamage());

        if (target.isAlive() || target == player) return;

        if (!markKillReward(player, target)) return;

        int xp = target instanceof Enemy ? 20 : target instanceof Animal ? 4 : 6;

        if (target instanceof Enemy) {

            data(player).record("combat:hostile_kill");

            int hostileKills = player.getPersistentData().getInt("universal_tool_hostile_kills") + 1;

            player.getPersistentData().putInt("universal_tool_hostile_kills", hostileKills);

            if (hostileKills >= 10) data(player).record("combat:ten_kills");

            player.getPersistentData().putString("universal_tool_last_enemy_type",

                    target.getType().builtInRegistryHolder().getRegisteredName());

            long hit = player.getPersistentData().getLong("universal_tool_last_hit");

            if (hit > 0 && gameTime - hit <= 200

                    && player.getPersistentData().hasUUID("universal_tool_last_attacker")

                    && player.getPersistentData().getUUID("universal_tool_last_attacker").equals(target.getUUID())) {

                data(player).record("combat:counter_kill");

            }

        }

        if (target instanceof Enemy && target.getPersistentData().getBoolean("universal_tool_ranged_enemy")) {

            data(player).record("combat:ranged_kill");

            long first = player.getPersistentData().getLong("universal_tool_ranged_window_start");

            if (first == 0 || gameTime - first > 200) {

                player.getPersistentData().putLong("universal_tool_ranged_window_start", gameTime);

                player.getPersistentData().putInt("universal_tool_ranged_kills", 0);

            }

            int rangedKills = player.getPersistentData().getInt("universal_tool_ranged_kills") + 1;

            player.getPersistentData().putInt("universal_tool_ranged_kills", rangedKills);

            if (rangedKills >= 3) data(player).record("combat:three_ranged_kills");

        }

        int enemyCount = player.serverLevel().getEntitiesOfClass(LivingEntity.class,

                player.getBoundingBox().inflate(12.0), entity -> entity instanceof Enemy).size();

        if (enemyCount >= 3) data(player).record("combat:three_nearby");

        if (target instanceof Enemy) {

            long first = player.getPersistentData().getLong("universal_tool_combat_window_start");

            if (first == 0 || gameTime - first > 100) {

                player.getPersistentData().putLong("universal_tool_combat_window_start", gameTime);

                player.getPersistentData().putInt("universal_tool_combat_window_kills", 0);

            }

            int kills = player.getPersistentData().getInt("universal_tool_combat_window_kills") + 1;

            player.getPersistentData().putInt("universal_tool_combat_window_kills", kills);

            if (kills >= 3) data(player).record("combat:multi_kill");

        }

        if (player.getY() >= 100) data(player).record("explore:high_place");



        double maxDistSqr = COMBAT_XP_RADIUS * COMBAT_XP_RADIUS;

        List<ServerPlayer> nearby = player.serverLevel().getPlayers(candidate ->

                candidate.isAlive() && !candidate.isSpectator() && candidate.distanceToSqr(target) <= maxDistSqr);

        if (nearby.isEmpty()) nearby = List.of(player);

        int each = Math.max(1, xp / nearby.size());

        for (ServerPlayer recipient : nearby) {

            award(recipient, each, false);

        }

    }



    private static void trackMomentum(ServerPlayer player, LivingEntity target, long gameTime) {

        if (target == player || equippedLevel(player, ModSkills.BATTLE_MOMENTUM) <= 0) return;

        Momentum previous = MOMENTUM.get(player.getUUID());

        int stacks = previous != null && gameTime - previous.lastHitGameTime() <= MOMENTUM_WINDOW_TICKS

                ? Math.min(MOMENTUM_MAX_STACKS, previous.stacks() + 1)

                : 1;

        MOMENTUM.put(player.getUUID(), new Momentum(stacks, gameTime));

    }



    private static void applyLightFeather(ServerPlayer player, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.FALL)) return;
        int feather = equippedLevel(player, ModSkills.LIGHT_FEATHER);
        if (feather <= 0) return;
        if (feather >= 3) {
            event.setCanceled(true);
            player.fallDistance = 0.0F;
        } else {
            float reduction = feather == 1 ? 0.40F : 0.75F;
            event.setAmount(Math.max(0.0F, event.getAmount() * (1.0F - reduction)));
        }
    }

    private static void applyArmorPiercing(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int pierce = equippedLevel(attacker, ModSkills.ARMOR_PIERCING);
        if (pierce <= 0) return;
        float factor = 1.0F + (pierce * 0.15F);
        event.setAmount(event.getAmount() * factor);
    }

    private static void applyBloodthirst(ServerPlayer player, LivingEntity target, float damageDealt) {
        int blood = equippedLevel(player, ModSkills.BLOODTHIRST);
        if (blood <= 0 || damageDealt <= 0) return;
        float percent = blood * 0.05F;
        float heal = Math.max(0.2F, damageDealt * percent);
        player.heal(heal);
    }

    private static void pullNearbyItems(ServerPlayer player, int magnetLevel) {
        if (magnetLevel <= 0) return;
        double radius = 5.0 + 2.0 * magnetLevel;
        AABB box = player.getBoundingBox().inflate(radius);
        Vec3 target = player.position().add(0, 0.5, 0);
        for (var item : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            Vec3 delta = target.subtract(item.position());
            double dist = delta.length();
            if (dist > 0.4) {
                item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.35)));
                item.hasImpulse = true;
            }
        }
        for (var orb : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box, e -> e.isAlive())) {
            Vec3 delta = target.subtract(orb.position());
            double dist = delta.length();
            if (dist > 0.4) {
                orb.setDeltaMovement(orb.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.35)));
                orb.hasImpulse = true;
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {

        if (event.getEntity() instanceof ServerPlayer victim) {

            long gameTime = victim.serverLevel().getGameTime();

            if (event.getSource().getDirectEntity() instanceof LivingEntity attacker) {

                data(victim).markCombat(gameTime);

                victim.getPersistentData().putLong("universal_tool_last_hit", gameTime);

                victim.getPersistentData().putUUID("universal_tool_last_attacker", attacker.getUUID());

            }

            applyLastStand(victim, event);

            applyLightFeather(victim, event);
            applyGuard(victim, event);
        }

        Entity damageOwner = event.getSource().getEntity();
        if (damageOwner == null) {
            damageOwner = event.getSource().getDirectEntity();
        }
        if (damageOwner instanceof ServerPlayer attacker) {

            applyMomentum(attacker, event);
            applyArmorPiercing(attacker, event);

        }

    }



    /** Last Stand: while below 35% health, the equipped passive cuts incoming damage. */

    private static void applyLastStand(ServerPlayer victim, LivingIncomingDamageEvent event) {

        int level = equippedLevel(victim, ModSkills.LAST_STAND);

        if (level <= 0) return;

        float max = victim.getMaxHealth();

        if (max <= 0 || victim.getHealth() / max >= 0.35F) return;

        float reduction = Math.min(0.6F, 0.15F * level);

        event.setAmount(event.getAmount() * (1.0F - reduction));

    }



    /** Battle Momentum: consecutive hits within the window add stacking damage. */

    private static void applyMomentum(ServerPlayer attacker, LivingIncomingDamageEvent event) {

        int level = equippedLevel(attacker, ModSkills.BATTLE_MOMENTUM);

        if (level <= 0) return;

        Momentum momentum = MOMENTUM.get(attacker.getUUID());

        if (momentum == null) return;

        long gameTime = attacker.serverLevel().getGameTime();

        if (gameTime - momentum.lastHitGameTime() > MOMENTUM_WINDOW_TICKS) return;

        event.setAmount(event.getAmount() * (1.0F + 0.04F * momentum.stacks() * level));

    }



    /** Counter Guard: negate one direct hit and reflect its pre-mitigation damage. */

    private static void applyGuard(ServerPlayer player, LivingIncomingDamageEvent event) {

        Entity damageOwner = event.getSource().getEntity();
        if (damageOwner == null) {
            damageOwner = event.getSource().getDirectEntity();
        }
        if (!(damageOwner instanceof LivingEntity attacker)) {
            return;
        }

        GuardState guard = GUARDS.remove(player.getUUID());

        if (guard == null) return;

        float incoming = event.getOriginalAmount();

        if (incoming <= 0) {

            GUARDS.put(player.getUUID(), guard);

            return;

        }

        event.setAmount(0.0F);

        attacker.hurt(player.damageSources().thorns(player), incoming * guard.reflectMultiplier());

        broadcastGuardEffect(player, CounterGuardEffectPayload.Action.TRIGGER);

        if (guard.level() >= 3) {

            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true, true));

        }

        player.serverLevel().sendParticles(ParticleTypes.FLASH,

                player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.1, 0.1, 0.1, 0.0);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),

                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.6F);

    }

    /**
     * Synchronizes shield visuals to nearby clients. Gameplay state remains on the server;
     * this packet only carries the player entity id, lifecycle action, and ordering token.
     */
    private static void broadcastGuardEffect(ServerPlayer player,
                                             CounterGuardEffectPayload.Action action) {
        CounterGuardEffectPayload payload = new CounterGuardEffectPayload(
                player.getId(), action, GUARD_EFFECT_SEQUENCE.incrementAndGet());
        for (ServerPlayer recipient : player.serverLevel().players()) {
            if (recipient == player || recipient.distanceToSqr(player) <= 64.0 * 64.0) {
                PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
    }



    private static boolean markKillReward(ServerPlayer player, LivingEntity target) {

        String key = target.getUUID().toString();

        long lastTick = player.getPersistentData().getLong("universal_tool_rewarded_kill_tick");

        String lastKey = player.getPersistentData().getString("universal_tool_rewarded_kill_key");

        if (key.equals(lastKey) && player.serverLevel().getGameTime() - lastTick <= 1) {

            return false;

        }

        player.getPersistentData().putString("universal_tool_rewarded_kill_key", key);

        player.getPersistentData().putLong("universal_tool_rewarded_kill_tick", player.serverLevel().getGameTime());

        return true;

    }



    /** Grants experience, reports level-ups and unlocks, and syncs only when something visible changed. */

    private static void award(ServerPlayer player, long xp, boolean immediate) {

        PlayerSkillData data = data(player);

        int levels = data.addExperience(xp, definition -> player.displayClientMessage(

                Component.translatable("message.universal_tool.skills.unlocked",

                        Component.translatable(definition.translationKey())), true));

        if (levels > 0) {

            player.displayClientMessage(Component.translatable("message.universal_tool.skills.level_up",

                    data.level()), true);

        }

        if (levels > 0 || immediate) sync(player);

        else markDirty(player);

    }



    @SubscribeEvent

    public static void onPlayerTick(PlayerTickEvent.Post event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerSkillData data = data(player);

        long gameTime = player.serverLevel().getGameTime();



        if (gameTime % 2 == 0) tickFrozen(gameTime);
        if (gameTime % 2 == 0) pullNearbyItems(player, equippedLevel(player, ModSkills.ITEM_MAGNET));

        tickGravityWells(gameTime);
        tickAirStep(player, gameTime);



        if (player.tickCount % 20 == 0) {

            applyPassiveEffects(player);

        }

        if (player.tickCount % 60 == 0) {

            int flow = equippedLevel(player, ModSkills.SURVIVAL_FLOW);

            if (flow > 0 && !data.inCombat(gameTime) && player.getHealth() < player.getMaxHealth()) {

                player.heal(0.5F * flow);

            }

        }

        if (player.tickCount % 20 == 0 && DIRTY.remove(player.getUUID())) {

            sync(player);

        }



        if (player.tickCount % 40 == 0) {

            player.serverLevel().getEntitiesOfClass(LivingEntity.class,

                            player.getBoundingBox().inflate(COMBAT_XP_RADIUS), entity -> entity instanceof Enemy)

                    .forEach(entity -> {

                        String type = entity.getType().getDescriptionId();

                        if (type.contains("skeleton") || type.contains("pillager")

                                || type.contains("witch") || type.contains("blaze")) {

                            entity.getPersistentData().putBoolean("universal_tool_ranged_enemy", true);

                        }

                    });

        }



        String biome = player.serverLevel().getBiome(player.blockPosition()).unwrapKey()

                .map(key -> key.location().toString()).orElse("");

        if (!biome.isEmpty() && data.addDiscovery("biome:" + biome)) {

            awardDiscovery(player, PLAYER_DISCOVERY_EXPERIENCE);

        }

        var chunk = player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z,

                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);

        if (chunk != null) {

            chunk.getAllStarts().forEach((structure, start) -> {

                if (start.isValid() && start.getBoundingBox().isInside(player.blockPosition())) {

                    String dimension = player.serverLevel().dimension().location().toString();

                    String structureKey = "structure:" + dimension + ":" + start.getChunkPos().toLong();

                    if (data.addDiscovery(structureKey)) {

                        awardDiscovery(player, STRUCTURE_DISCOVERY_EXPERIENCE);

                    }

                }

            });

        }

        if (player.getY() >= 120 && data.record("explore:high_place")) {

            data.unlockEligibleSkills(definition -> player.displayClientMessage(

                    Component.translatable("message.universal_tool.skills.unlocked",

                            Component.translatable(definition.translationKey())), true));

            sync(player);

        }



        OreSightState state = ORE_SIGHT.get(player.getUUID());

        if (state == null) return;

        if (gameTime >= state.expiresAt() || state.level() != player.serverLevel()) {

            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);

            return;

        }

        if (gameTime % 20 != 0) return;
        state.orePositions().removeIf(pos -> !player.serverLevel().isLoaded(pos)
                || !isOre(player.serverLevel().getBlockState(pos)));
        syncOreMarkers(player, state.orePositions(), (int) (state.expiresAt() - gameTime));
    }

    private static void syncOreMarkers(ServerPlayer player, Set<BlockPos> positions, int ticks) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putInt("ticks", ticks);
        tag.putLongArray("positions", positions.stream()
                .sorted(Comparator.comparingDouble(pos -> player.distanceToSqr(Vec3.atCenterOf(pos))))
                .limit(512).mapToLong(BlockPos::asLong).toArray());
        PacketDistributor.sendToPlayer(player, new OreSightPayload(tag));
    }

    @SubscribeEvent

    public static void onBlockBreak(BlockEvent.BreakEvent event) {

        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        if (isPlayerPlaced(player, event.getPos())) {

            forgetPlacement(player, event.getPos());

            return;

        }

        BlockState state = event.getState();

        PlayerSkillData data = data(player);

        if (isMatureCrop(state)) {

            award(player, 8, false);

            return;

        }

        if (isOre(state)) data.record("mine:ore");

        award(player, isOre(state) ? 20 : 1, false);

    }



    @SubscribeEvent

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Map<String, Long> placed = PLACED_BLOCKS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());

        placed.put(placementKey(player, event.getPos()), player.serverLevel().getGameTime());

        if (placed.size() > 16384) {

            var it = placed.keySet().iterator();

            if (it.hasNext()) {

                it.next();

                it.remove();

            }

        }

    }



    /** A block the same player placed recently is not a legitimate mining target for growth XP. */

    private static boolean isPlayerPlaced(ServerPlayer player, BlockPos pos) {

        Map<String, Long> placed = PLACED_BLOCKS.get(player.getUUID());

        if (placed == null) return false;

        return placed.containsKey(placementKey(player, pos));

    }



    private static void forgetPlacement(ServerPlayer player, BlockPos pos) {

        Map<String, Long> placed = PLACED_BLOCKS.get(player.getUUID());

        if (placed != null) placed.remove(placementKey(player, pos));

    }



    private static String placementKey(ServerPlayer player, BlockPos pos) {

        return player.serverLevel().dimension().location() + "|" + pos.asLong();

    }



    private static void awardDiscovery(ServerPlayer player, int xp) {

        PlayerSkillData data = data(player);

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.discovery", xp)

                .withStyle(ChatFormatting.GREEN), true);

        award(player, xp, true);

    }



    private SkillManager() {

    }

}
