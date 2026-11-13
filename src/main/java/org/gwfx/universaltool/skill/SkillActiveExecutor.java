package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModSounds;
import org.gwfx.universaltool.network.CounterGuardEffectPayload;
import org.gwfx.universaltool.network.OreSightPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles the 8 active skills: casting, charging, cooldowns, raycast targeting,
 * gravity wells, frozen pull stasis, counter guard, and lightning spear.
 */
final class SkillActiveExecutor {
    private static final double GRAVITY_WELL_AREA = 12.0D;
    private static final ResourceLocation BERSERK_ATTACK_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "berserk_attack_damage");

    private static final Map<UUID, ChargeState> CHARGES = new ConcurrentHashMap<>();
    private static final Map<UUID, GuardState> GUARDS = new ConcurrentHashMap<>();
    private static final AtomicLong GUARD_EFFECT_SEQUENCE = new AtomicLong();
    private static final Map<UUID, FrozenState> FROZEN = new ConcurrentHashMap<>();
    private static final Map<UUID, StasisState> STASIS = new ConcurrentHashMap<>();
    private static final Map<UUID, OreSightState> ORE_SIGHT = new ConcurrentHashMap<>();
    private static final List<GravityWellState> GRAVITY_WELLS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long> BERSERK_EXPIRES = new ConcurrentHashMap<>();

    private record ChargeState(String skillId, long startGameTime) {}
    private record GuardState(float reflectMultiplier, int level) {}
    private record FrozenState(Entity entity, ServerLevel level, long until, boolean previousNoAi) {}
    private record StasisState(Entity target, ServerLevel level, ServerPlayer owner,
                               long until, long nextDamageAt, float fixedDamage) {}
    private record OreSightState(ServerLevel level, Set<BlockPos> orePositions, long expiresAt) {}
    private record GravityWellState(ServerLevel level, Vec3 center, double radius, long expiresAt, UUID ownerId) {}

    private SkillActiveExecutor() {}

    static void clearAll() {
        CHARGES.clear();
        GUARDS.clear();
        FROZEN.clear();
        STASIS.clear();
        ORE_SIGHT.clear();
        GRAVITY_WELLS.clear();
        BERSERK_EXPIRES.clear();
    }

    static boolean isCharging(UUID playerId) {
        return CHARGES.containsKey(playerId);
    }

    static boolean isFrozen(Entity entity) {
        return entity != null && FROZEN.containsKey(entity.getUUID());
    }

    static double gravityWellRadius(int level) {
        return 24.0D + 4.0D * level;
    }

    static int gravityWellDurationSeconds(int level) {
        return 2 + 3 * Math.max(1, Math.min(ModSkills.MAX_SKILL_LEVEL, level));
    }

    static int gravityWellDurationTicks(int level) {
        return gravityWellDurationSeconds(level) * 20;
    }

    static int freezeDurationSeconds(int level) {
        return 4 + Math.max(1, Math.min(ModSkills.MAX_SKILL_LEVEL, level));
    }

    static int freezeDurationTicks(int level) {
        return freezeDurationSeconds(level) * 20;
    }

    static double oppressiveRadius(int level) {
        return 12 + 3 * (Math.max(1, Math.min(3, level)) - 1);
    }

    static int oppressiveDurationTicks(int level) {
        return 120 + 40 * (Math.max(1, Math.min(3, level)) - 1);
    }

    static int airStepDurationTicks(int level) {
        return 20 * Math.max(1, Math.min(3, level));
    }

    static void handleCast(ServerPlayer player, boolean releasing) {
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

    static void castSelected(ServerPlayer player) {
        PlayerSkillData data = SkillManager.data(player);
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
            SkillManager.sync(player);
        }
    }

    private static void releaseCharge(ServerPlayer player) {
        ChargeState state = CHARGES.remove(player.getUUID());
        if (state == null) {
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        SkillDefinition skill = ModSkills.find(state.skillId()).orElse(null);
        if (skill == null) {
            return;
        }
        long elapsed = player.serverLevel().getGameTime() - state.startGameTime();
        if (ModSkills.AIR_STEP.equals(skill.id())) {
            data.startCooldown(skill.id(), skill.cooldownTicks());
            SkillManager.sync(player);
            return;
        }
        if (elapsed < SkillManager.CHARGE_MIN_TICKS) {
            data.startCooldown(skill.id(), SkillManager.CANCEL_COOLDOWN_TICKS);
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.charge_cancelled"), true);
            SkillManager.sync(player);
            return;
        }
        float ratio = Math.min(1.0F, elapsed / (float) skill.chargeTicks());
        if (castCharged(player, data, skill, ratio)) {
            data.startCooldown(skill.id(), skill.cooldownTicks());
        } else {
            data.startCooldown(skill.id(), SkillManager.CANCEL_COOLDOWN_TICKS);
        }
        SkillManager.sync(player);
    }

    static void cancelCharge(ServerPlayer player) {
        ChargeState state = CHARGES.remove(player.getUUID());
        if (state == null) {
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        data.startCooldown(state.skillId(), ModSkills.AIR_STEP.equals(state.skillId())
                ? ModSkills.find(state.skillId()).orElseThrow().cooldownTicks()
                : SkillManager.CANCEL_COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.charge_cancelled"), true);
        SkillManager.sync(player);
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

    private static boolean castCounterGuard(ServerPlayer player, int level) {
        if (GUARDS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.guard_active"), true);
            return false;
        }
        GUARDS.put(player.getUUID(), new GuardState(1.0F + 0.25F * (level - 1), level));
        broadcastGuardEffect(player, CounterGuardEffectPayload.Action.EXPAND);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.guard_ready"), true);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,
                player.getX(), player.getY() + 1.0, player.getZ(), 28, 0.8, 1.0, 0.8, 0.06);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.5F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    private static boolean castGravityWell(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        double radius = gravityWellRadius(level);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(GRAVITY_WELL_AREA));
        Vec3 center = player.isShiftKeyDown()
                ? world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()
                : end;
        GRAVITY_WELLS.add(new GravityWellState(world, center, radius,
                world.getGameTime() + gravityWellDurationTicks(level), player.getUUID()));
        tickGravityWells(world.getGameTime());

        world.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 160, 1.0, 1.6, 1.0, 0.35);
        world.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 35, 0.5, 0.8, 0.5, 0.015);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 0.6F, 0.65F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.gravity_well",
                (int) radius), true);
        return true;
    }

    private static void tickGravityWells(long gameTime) {
        if (GRAVITY_WELLS.isEmpty()) {
            return;
        }
        GRAVITY_WELLS.removeIf(well -> !well.level().isLoaded(BlockPos.containing(well.center()))
                || gameTime >= well.expiresAt());

        for (GravityWellState well : GRAVITY_WELLS) {
            ServerPlayer owner = well.level().getServer().getPlayerList().getPlayer(well.ownerId());
            if (owner == null) {
                continue;
            }
            AABB bounds = new AABB(well.center(), well.center()).inflate(well.radius());
            for (LivingEntity target : well.level().getEntitiesOfClass(LivingEntity.class, bounds,
                    entity -> canAffect(owner, entity))) {
                Vec3 toCenter = well.center().subtract(target.position());
                double distance = toCenter.length();
                if (distance < 0.4 || distance > well.radius()) {
                    continue;
                }
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
                        well.center().x, well.center().y + 0.5, well.center().z, 18, 0.6, 0.8, 0.6, 0.45);
            }
        }
    }

    private static boolean castFrozenPull(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        double range = 16.0D + 4.0D * level;
        LivingEntity target = findCrosshairTarget(player, range);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.no_target"), true);
            return false;
        }
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        Vec3 destination = player.position().add(flat.normalize().scale(2.2));
        target.teleportTo(destination.x, player.getY(), destination.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;

        freeze(player, target, world, freezeDurationTicks(level));

        world.sendParticles(ParticleTypes.SNOWFLAKE, destination.x, destination.y + 1.0, destination.z,
                40, 0.5, 0.8, 0.5, 0.08);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.frozen_pull"), true);
        return true;
    }

    private static void freeze(ServerPlayer owner, LivingEntity target, ServerLevel level, int freezeTicks) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, freezeTicks, 255, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, freezeTicks, 255, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, freezeTicks, 255, false, true, true));
        FrozenState existing = FROZEN.get(target.getUUID());
        boolean previousNoAi = false;
        if (target instanceof Mob mob) {
            previousNoAi = existing != null ? existing.previousNoAi() : mob.isNoAi();
            mob.setNoAi(true);
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setAggressive(false);
        }
        FROZEN.put(target.getUUID(), new FrozenState(target, level, level.getGameTime() + freezeTicks, previousNoAi));
        STASIS.put(target.getUUID(), new StasisState(target, level, owner,
                level.getGameTime() + freezeTicks,
                level.getGameTime() + SkillManager.STASIS_DAMAGE_INTERVAL_TICKS,
                Math.max(1.0F, target.getMaxHealth() * SkillManager.STASIS_DAMAGE_FRACTION)));

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
            if (gameTime >= state.until() || state.entity().isRemoved() || !state.entity().isAlive()) {
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
            if (target == null || target.isRemoved() || !target.isAlive() || gameTime >= state.until()) {
                STASIS.remove(entry.getKey());
                continue;
            }
            if (gameTime < state.nextDamageAt()) {
                continue;
            }
            state.level().sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.25, 0.35, 0.25, 0.02);
            target.hurt(state.level().damageSources().indirectMagic(state.owner(), state.owner()),
                    state.fixedDamage());
            STASIS.put(entry.getKey(), new StasisState(state.target(), state.level(), state.owner(),
                    state.until(), gameTime + SkillManager.STASIS_DAMAGE_INTERVAL_TICKS, state.fixedDamage()));
        }
    }

    private static boolean castOppressiveField(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        double radius = oppressiveRadius(level);
        int duration = oppressiveDurationTicks(level);
        for (LivingEntity target : targets(world, player, player.position(), radius)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2, false, true, true));
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, level >= 3 ? 1 : 0,
                false, true, true));
        world.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1.0, player.getZ(),
                70, radius / 3.0, 2.0, radius / 3.0, 0.02);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.75F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.oppressive_field"), true);
        return true;
    }

    private static boolean castOreSight(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        int radius = 12 + 4 * level;
        int vertical = 8 + level;
        Set<BlockPos> ores = new LinkedHashSet<>();
        BlockPos origin = player.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -vertical; y <= vertical; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (world.isLoaded(pos) && SkillCombatTracker.isOre(world.getBlockState(pos))) {
                        ores.add(pos.immutable());
                    }
                }
            }
        }
        ORE_SIGHT.put(player.getUUID(), new OreSightState(world, ores, world.getGameTime() + SkillManager.ORE_SIGHT_TICKS));
        syncOreMarkers(player, ores, SkillManager.ORE_SIGHT_TICKS);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.ore_sight",
                ores.size()), true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);
        return true;
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
        ServerLevel world = player.serverLevel();
        float damage = (8.0F + 5.0F * level * ratio) * 2.5F;
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        float projectileSpeed = 2.4F;

        LightningSpearProjectile projectile = new LightningSpearProjectile(world, player, damage);
        projectile.setChargeRatio(ratio);
        projectile.setPos(start.x + direction.x * 0.8, start.y + direction.y * 0.8, start.z + direction.z * 0.8);
        projectile.shoot(direction.x, direction.y, direction.z, projectileSpeed, 0.0F);
        world.addFreshEntity(projectile);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.LIGHTNING_SPEAR_THROW.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.lightning_spear"), true);
        return true;
    }

    private static boolean castBerserk(ServerPlayer player, int level) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, level - 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, level - 1, false, true, true));

        BERSERK_EXPIRES.put(player.getUUID(), player.serverLevel().getGameTime() + 200);
        var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            attackAttr.addTransientModifier(new AttributeModifier(
                    BERSERK_ATTACK_DAMAGE_ID, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0,
                player.getZ(), 45, 0.7, 1.0, 0.7, 0.08);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 0.8F);
        return true;
    }

    static void tickPlayerActives(ServerPlayer player, long gameTime) {
        if (gameTime % 2 == 0) {
            tickFrozen(gameTime);
        }
        tickGravityWells(gameTime);
        tickAirStep(player, gameTime);
        tickBerserk(player, gameTime);
        if (GUARDS.containsKey(player.getUUID())) {
            tickCounterGuardProjectiles(player);
        }
        tickOreSight(player, gameTime);
    }

    private static void tickBerserk(ServerPlayer player, long now) {
        Long expire = BERSERK_EXPIRES.get(player.getUUID());
        if (expire != null && now >= expire) {
            BERSERK_EXPIRES.remove(player.getUUID());
            var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            }
        }
    }

    private static void tickAirStep(ServerPlayer player, long now) {
        ChargeState state = CHARGES.get(player.getUUID());
        if (state == null || !ModSkills.AIR_STEP.equals(state.skillId())) {
            return;
        }
        int level = SkillManager.data(player).learnedLevel(ModSkills.AIR_STEP);
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

    private static void tickOreSight(ServerPlayer player, long gameTime) {
        OreSightState state = ORE_SIGHT.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (gameTime >= state.expiresAt() || state.level() != player.serverLevel()) {
            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);
            return;
        }
        if (gameTime % 20 != 0) {
            return;
        }
        state.orePositions().removeIf(pos -> !player.serverLevel().isLoaded(pos)
                || !SkillCombatTracker.isOre(player.serverLevel().getBlockState(pos)));
        syncOreMarkers(player, state.orePositions(), (int) (state.expiresAt() - gameTime));
    }

    static void applyGuard(ServerPlayer player, LivingIncomingDamageEvent event) {
        if (!GUARDS.containsKey(player.getUUID())) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                || event.getSource().getDirectEntity() instanceof Projectile) {
            event.setAmount(0.0F);
            if (event.getSource().getDirectEntity() instanceof Projectile proj) {
                deflectProjectile(player, proj);
            }
            return;
        }
        Entity damageOwner = event.getSource().getEntity();
        if (damageOwner == null) {
            damageOwner = event.getSource().getDirectEntity();
        }
        if (!(damageOwner instanceof LivingEntity attacker)) {
            return;
        }
        GuardState guard = GUARDS.remove(player.getUUID());
        if (guard == null) {
            return;
        }
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
                player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
        player.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM,
                player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,
                player.getX(), player.getY() + 1.0, player.getZ(), 35, 1.2, 0.8, 1.2, 0.12);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.9F, 1.7F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    static void deflectProjectile(ServerPlayer player, Projectile projectile) {
        if (!projectile.isAlive()) {
            return;
        }
        Entity shooter = projectile.getOwner();
        Vec3 motion = projectile.getDeltaMovement();
        double speed = Math.max(1.5D, motion.length() * 1.35D);
        if (shooter != null && shooter != player && shooter.isAlive()) {
            Vec3 toShooter = shooter.getEyePosition().subtract(projectile.position()).normalize();
            projectile.setDeltaMovement(toShooter.scale(speed));
        } else {
            Vec3 away = projectile.position().subtract(player.getEyePosition());
            if (away.lengthSqr() < 1.0E-4) {
                away = player.getLookAngle();
            }
            projectile.setDeltaMovement(away.normalize().scale(speed));
        }
        projectile.setOwner(player);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;

        player.serverLevel().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.8F);
        player.serverLevel().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.9F, 1.7F);
        player.serverLevel().sendParticles(ParticleTypes.CRIT,
                projectile.getX(), projectile.getY(), projectile.getZ(), 12, 0.25, 0.25, 0.25, 0.15);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,
                projectile.getX(), projectile.getY(), projectile.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
    }

    private static void tickCounterGuardProjectiles(ServerPlayer player) {
        AABB zone = player.getBoundingBox().inflate(2.2);
        List<Projectile> projectiles = player.serverLevel().getEntitiesOfClass(
                Projectile.class, zone, p -> p.isAlive() && p.getOwner() != player);
        for (Projectile p : projectiles) {
            deflectProjectile(player, p);
        }
    }

    static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer player
                && GUARDS.containsKey(player.getUUID())) {
            event.setCanceled(true);
            deflectProjectile(player, event.getProjectile());
        }
    }

    private static void broadcastGuardEffect(ServerPlayer player, CounterGuardEffectPayload.Action action) {
        CounterGuardEffectPayload payload = new CounterGuardEffectPayload(
                player.getId(), action, GUARD_EFFECT_SEQUENCE.incrementAndGet());
        for (ServerPlayer recipient : player.serverLevel().players()) {
            if (recipient == player || recipient.distanceToSqr(player) <= 64.0 * 64.0) {
                PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
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
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, box, e -> canPull(player, e))) {
            var clip = candidate.getBoundingBox().inflate(1.5).clip(eyePos, end);
            if (clip.isPresent()) {
                double dist = clip.get().distanceToSqr(eyePos);
                if (dist < nearestDistSqr) {
                    nearestDistSqr = dist;
                    bestHit = candidate;
                }
            }
        }
        if (bestHit != null) {
            return bestHit;
        }

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, box, e -> canPull(player, e))) {
            Vec3 candidatePos = candidate.getBoundingBox().getCenter();
            Vec3 toCandidate = candidatePos.subtract(eyePos);
            double forwardDist = toCandidate.dot(look);
            if (forwardDist < 0.5 || forwardDist > maxRange) {
                continue;
            }
            Vec3 rayPoint = eyePos.add(look.scale(forwardDist));
            double distToRay = candidatePos.distanceTo(rayPoint);
            double maxAllowedDist = Math.max(2.5, forwardDist * 0.22);
            if (distToRay <= maxAllowedDist) {
                double score = distToRay + (forwardDist * 0.04);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    static boolean canPull(ServerPlayer source, LivingEntity target) {
        if (target == source || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof TamableAnimal tameable && tameable.getOwner() instanceof Player owner) {
            if (owner == source || owner.getUUID().equals(source.getUUID())) return false;
            return source.server.isPvpAllowed() && !source.isAlliedTo(owner);
        }
        if (target instanceof Player other) {
            return source.server.isPvpAllowed() && !source.isAlliedTo(other) && !other.isSpectator();
        }
        return true;
    }

    static boolean canAffect(ServerPlayer source, LivingEntity target) {
        if (target == source || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof Villager) return false;
        if (target instanceof IronGolem golem && !golem.isAngryAt(source)) return false;
        if (target instanceof TamableAnimal tameable && tameable.getOwner() instanceof Player owner) {
            if (owner == source || owner.getUUID().equals(source.getUUID())) return false;
            return source.server.isPvpAllowed() && !source.isAlliedTo(owner);
        }
        if (target instanceof Player other) {
            return source.server.isPvpAllowed() && !source.isAlliedTo(other) && !other.isSpectator();
        }
        if (target instanceof Animal) return false;
        if (target instanceof Enemy) return true;
        return target instanceof NeutralMob neutral && neutral.isAngryAt(source);
    }

    static void clearOnUnsetActive(ServerPlayer player) {
        if (GUARDS.remove(player.getUUID()) != null) {
            broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
        }
        var active = SkillManager.data(player).activeLoadout();
        if (!active.contains(ModSkills.ORE_SIGHT)) {
            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);
        }
        if (!active.contains(ModSkills.GRAVITY_WELL)) {
            GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(player.getUUID()));
        }
        if (!active.contains(ModSkills.BERSERK)) {
            BERSERK_EXPIRES.remove(player.getUUID());
            var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            }
        }
    }

    static void clearSingleSkill(ServerPlayer player, String skillId) {
        if (ModSkills.COUNTER_GUARD.equals(skillId)) {
            if (GUARDS.remove(player.getUUID()) != null) {
                broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
            }
        } else if (ModSkills.ORE_SIGHT.equals(skillId)) {
            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), 0);
        } else if (ModSkills.GRAVITY_WELL.equals(skillId)) {
            GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(player.getUUID()));
        } else if (ModSkills.BERSERK.equals(skillId)) {
            BERSERK_EXPIRES.remove(player.getUUID());
            var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            }
        } else if (ModSkills.BATTLE_MOMENTUM.equals(skillId)) {
            SkillPassiveHandler.clearMomentum(player.getUUID());
        }
    }

    static void clearAllForPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        if (GUARDS.remove(id) != null) {
            broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
        }
        ORE_SIGHT.remove(id);
        syncOreMarkers(player, Set.of(), 0);
        GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(id));
        BERSERK_EXPIRES.remove(id);
        var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
        }
    }

    static void clearTransient(ServerPlayer player) {
        UUID id = player.getUUID();
        ChargeState held = CHARGES.get(id);
        if (held != null && ModSkills.AIR_STEP.equals(held.skillId())) {
            cancelCharge(player);
        }
        CHARGES.remove(id);
        FROZEN.entrySet().removeIf(entry -> entry.getValue().entity() instanceof Player owner
                && owner.getUUID().equals(id));
        STASIS.entrySet().removeIf(entry -> entry.getValue().owner().getUUID().equals(id));
        clearAllForPlayer(player);
    }
}
