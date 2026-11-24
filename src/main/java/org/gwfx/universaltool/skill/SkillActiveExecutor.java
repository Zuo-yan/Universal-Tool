package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 主动技能运行时执行器：管理全部 14 项主动技能的起手、蓄力、冷却、目标保护与 Lv.3 星辉觉醒效果。
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
    private static final Map<UUID, BerserkState> BERSERK_STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> AIR_STEP_LANDING_SHOCKWAVE = ConcurrentHashMap.newKeySet();

    private record ChargeState(String skillId, long startGameTime) {}
    private record GuardState(float reflectMultiplier, int level) {}
    private record FrozenState(Entity entity, ServerLevel level, long until, boolean previousNoAi) {}
    private record StasisState(Entity target, ServerLevel level, ServerPlayer owner,
                               long until, long nextDamageAt, float fixedDamage) {}
    private record OreSightState(ServerLevel level, Set<BlockPos> orePositions, Set<BlockPos> specialPositions, long expiresAt) {}
    private record GravityWellState(ServerLevel level, Vec3 center, double radius, int skillLevel, long expiresAt, UUID ownerId) {}
    private record BerserkState(int skillLevel, long expiresAt, int extendedSeconds) {}

    private SkillActiveExecutor() {}

    static void clearAll() {
        restoreAllFrozenAi();
        CHARGES.clear();
        GUARDS.clear();
        FROZEN.clear();
        STASIS.clear();
        ORE_SIGHT.clear();
        GRAVITY_WELLS.clear();
        BERSERK_STATES.clear();
        AIR_STEP_LANDING_SHOCKWAVE.clear();
        SkillNewActivesHelper.clearAll();
    }

    private static void restoreAllFrozenAi() {
        for (FrozenState state : FROZEN.values()) {
            if (state.entity() instanceof Mob mob && !mob.isRemoved()) {
                mob.setNoAi(state.previousNoAi());
            }
        }
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
        if (!SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
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
        if (!SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
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
        int adjustedCd = SkillResonanceHandler.adjustCooldownTicks(player, skill.cooldownTicks());

        if (ModSkills.AIR_STEP.equals(id)) {
            if (castAirStep(player, data.learnedLevel(id))) {
                CHARGES.put(player.getUUID(), new ChargeState(id, player.serverLevel().getGameTime()));
            }
            return;
        }
        if (skill.chargeTicks() > 0) {
            CHARGES.put(player.getUUID(), new ChargeState(id, player.serverLevel().getGameTime()));
            if (ModSkills.LIGHTNING_SPEAR.equals(id)) {
                Vec3 focus = player.position().add(player.getLookAngle().scale(0.75)).add(0, 0.22, 0);
                SkillVfx.spiral(player.serverLevel(), focus, 0.65, 1.0, 0, SkillVfx.Tint.SPEAR);
            }
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.charging"), true);
            return;
        }
        if (ModSkills.VOID_BLINK.equals(id)) {
            if (SkillNewActivesHelper.handleVoidBlink(player, data, data.learnedLevel(id), adjustedCd)) {
                data.startCooldown(id, adjustedCd);
                SkillResonanceHandler.onActiveSkillCast(player);
                SkillManager.sync(player);
            } else {
                SkillResonanceHandler.onActiveSkillCast(player);
                SkillManager.sync(player);
            }
            return;
        }
        if (castInstant(player, data, skill)) {
            data.startCooldown(id, adjustedCd);
            SkillResonanceHandler.onActiveSkillCast(player);
            SkillManager.sync(player);
        }
    }

    private static void releaseCharge(ServerPlayer player) {
        ChargeState state = CHARGES.remove(player.getUUID());
        if (state == null) {
            return;
        }
        if (isFrozen(player)) {
            PlayerSkillData data = SkillManager.data(player);
            data.startCooldown(state.skillId(), SkillManager.CANCEL_COOLDOWN_TICKS);
            SkillManager.sync(player);
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        SkillDefinition skill = ModSkills.find(state.skillId()).orElse(null);
        if (skill == null) {
            return;
        }
        int adjustedCd = SkillResonanceHandler.adjustCooldownTicks(player, skill.cooldownTicks());
        long elapsed = player.serverLevel().getGameTime() - state.startGameTime();
        if (ModSkills.AIR_STEP.equals(skill.id())) {
            data.startCooldown(skill.id(), adjustedCd);
            SkillResonanceHandler.onActiveSkillCast(player);
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
            data.startCooldown(skill.id(), adjustedCd);
            SkillResonanceHandler.onActiveSkillCast(player);
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
        int cd = ModSkills.AIR_STEP.equals(state.skillId())
                ? SkillResonanceHandler.adjustCooldownTicks(player,
                        ModSkills.find(state.skillId()).map(SkillDefinition::cooldownTicks).orElse(100))
                : SkillManager.CANCEL_COOLDOWN_TICKS;
        data.startCooldown(state.skillId(), cd);
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
            case ModSkills.ASTRAL_CLEAVE -> SkillNewActivesHelper.castAstralCleave(player, level);
            case ModSkills.THUNDER_JUDGMENT -> SkillNewActivesHelper.castThunderJudgment(player, level);
            case ModSkills.STARLIGHT_SANCTUARY -> SkillNewActivesHelper.castStarlightSanctuary(player, level);
            case ModSkills.VEIN_RESONANCE -> SkillNewActivesHelper.castVeinResonance(player, level);
            case ModSkills.ARCANE_TRANSMUTE -> SkillNewActivesHelper.castArcaneTransmute(player, level);
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

    // ===== 1. 反击护体 =====

    private static boolean castCounterGuard(ServerPlayer player, int level) {
        if (GUARDS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.guard_active"), true);
            return false;
        }
        GUARDS.put(player.getUUID(), new GuardState(1.25F + 0.25F * (level - 1), level));
        broadcastGuardEffect(player, CounterGuardEffectPayload.Action.EXPAND);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.guard_ready"), true);
        SkillVfx.ring(player.serverLevel(), player.position().add(0, 0.30, 0), 1.15, 0, 12, SkillVfx.Tint.GUARD);
        SkillVfx.sparks(player.serverLevel(), player.position().add(0, 0.9, 0), 0.48, 0.65, 18, SkillVfx.Tint.GUARD);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.5F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    // ===== 2. 重力牵引 =====

    private static boolean castGravityWell(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        double radius = gravityWellRadius(level);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(GRAVITY_WELL_AREA));
        Vec3 center = player.isShiftKeyDown()
                ? world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()
                : end;
        GRAVITY_WELLS.add(new GravityWellState(world, center, radius, level,
                world.getGameTime() + gravityWellDurationTicks(level), player.getUUID()));
        tickGravityWells(world.getGameTime());

        SkillVfx.spiral(world, center, Math.min(2.5, radius * 0.24), 1.35, 0, SkillVfx.Tint.GRAVITY);
        SkillVfx.sparks(world, center.add(0, 0.35, 0), 0.65, 0.75, 24, SkillVfx.Tint.GRAVITY);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 0.55F, 0.65F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.gravity_well",
                (int) radius), true);
        return true;
    }

    private static void tickGravityWells(long gameTime) {
        if (GRAVITY_WELLS.isEmpty()) {
            return;
        }
        List<GravityWellState> finished = new ArrayList<>();
        for (GravityWellState well : GRAVITY_WELLS) {
            if (!well.level().isLoaded(BlockPos.containing(well.center())) || gameTime >= well.expiresAt()) {
                finished.add(well);
                continue;
            }
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
                Vec3 orbitPoint = new Vec3(px, well.center().y + 0.4, pz);
                SkillVfx.sparks(well.level(), orbitPoint, 0.46, 0.35, 5, SkillVfx.Tint.GRAVITY);
                SkillVfx.point(well.level(), well.center().add((well.center().x - px) * 0.78, 0.25, (well.center().z - pz) * 0.78),
                        SkillVfx.Tint.GRAVITY.dust);
            }
        }
        for (GravityWellState well : finished) {
            GRAVITY_WELLS.remove(well);
            // Lv.3 星辉觉醒：重力坍缩结束瞬间触发范围星爆伤害
            if (well.skillLevel() >= 3 && well.level().isLoaded(BlockPos.containing(well.center()))) {
                ServerPlayer owner = well.level().getServer().getPlayerList().getPlayer(well.ownerId());
                if (owner != null) {
                    AABB burstBox = new AABB(well.center(), well.center()).inflate(6.0);
                    for (LivingEntity target : well.level().getEntitiesOfClass(LivingEntity.class, burstBox,
                            e -> canAffect(owner, e))) {
                        target.hurt(well.level().damageSources().indirectMagic(owner, owner), 8.0F);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true));
                    }
                    SkillVfx.ring(well.level(), well.center().add(0, 0.45, 0), 4.0, 0, 16, SkillVfx.Tint.GRAVITY);
                    SkillVfx.sparks(well.level(), well.center(), 0.95, 0.55, 22, SkillVfx.Tint.GRAVITY);
                }
            }
        }
    }

    // ===== 3. 冰封牵引 =====

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
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        Vec3 destination = player.position().add(flat.normalize().scale(2.2));
        Vec3 departure = target.position().add(0, target.getBbHeight() * 0.55, 0);
        target.teleportTo(destination.x, player.getY(), destination.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;

        freeze(player, target, world, freezeDurationTicks(level));

        if (level >= 3) {
            // Lv.3 觉醒：冰霜溅射周边 3 格敌怪
            for (LivingEntity nearby : world.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(3.0), e -> e != target && canAffect(player, e))) {
                nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                nearby.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true, true));
                nearby.hurt(world.damageSources().indirectMagic(player, player), 6.0F);
            }
        }

        Vec3 arrival = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 pulled = arrival.subtract(departure);
        if (pulled.lengthSqr() > 0.25) {
            SkillVfx.wake(world, departure, pulled, pulled.length(), 0.45, true, SkillVfx.Tint.ICE);
        }
        SkillVfx.ring(world, target.position().add(0, 0.20, 0), level >= 3 ? 2.4 : 0.85,
                Math.PI / 6.0, 12, SkillVfx.Tint.ICE);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.frozen_pull"), true);
        return true;
    }

    /** 冻结实体；重复冻结仅延长时间，保留首次进入前的 AI 状态（修复历史缺陷 #1）。 */
    static void freeze(ServerPlayer owner, LivingEntity target, ServerLevel level, int freezeTicks) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, freezeTicks, 255, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, freezeTicks, 255, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, freezeTicks, 255, false, true, true));

        FrozenState existing = FROZEN.get(target.getUUID());
        boolean previousNoAi = existing != null ? existing.previousNoAi()
                : (target instanceof Mob mob && mob.isNoAi());
        long newUntil = Math.max(existing != null ? existing.until() : 0L, level.getGameTime() + freezeTicks);

        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setAggressive(false);
        }
        FROZEN.put(target.getUUID(), new FrozenState(target, level, newUntil, previousNoAi));
        STASIS.put(target.getUUID(), new StasisState(target, level, owner,
                newUntil,
                level.getGameTime() + SkillManager.STASIS_DAMAGE_INTERVAL_TICKS,
                Math.max(1.0F, target.getMaxHealth() * SkillManager.STASIS_DAMAGE_FRACTION)));

        SkillVfx.spiral(level, target.position().add(0, 0.14, 0),
                Math.min(1.7, 0.44 + target.getBbWidth() * 0.33), Math.min(2.4, target.getBbHeight()),
                0, SkillVfx.Tint.ICE);
        SkillVfx.sparks(level, target.position().add(0, Math.min(1.6, target.getBbHeight() * 0.55), 0),
                0.52, 0.70, 20, SkillVfx.Tint.ICE);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 0.65F);
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

    // ===== 4. 威压领域 =====

    private static boolean castOppressiveField(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        double radius = oppressiveRadius(level);
        int duration = oppressiveDurationTicks(level);
        for (LivingEntity target : targets(world, player, player.position(), radius)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2, false, true, true));
            if (level >= 3) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, true, true));
                target.hurt(world.damageSources().indirectMagic(player, player), 6.0F);
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, level >= 3 ? 1 : 0,
                false, true, true));
        SkillVfx.ring(world, player.position().add(0, 0.25, 0), radius, Math.PI / 12.0, 12, SkillVfx.Tint.DREAD);
        SkillVfx.ring(world, player.position().add(0, 0.45, 0), 2.5, 0, 10, SkillVfx.Tint.DREAD);
        SkillVfx.sparks(world, player.position().add(0, 0.75, 0), 1.2, 0.45, 22, SkillVfx.Tint.DREAD);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.75F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.oppressive_field"), true);
        return true;
    }

    // ===== 5. 矿脉感知（Lv.3 额外高亮宝箱/刷怪笼） =====

    private static boolean castOreSight(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        int radius = 12 + 4 * level;
        int vertical = 8 + level;
        Set<BlockPos> ores = new LinkedHashSet<>();
        Set<BlockPos> specials = new LinkedHashSet<>();
        BlockPos origin = player.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -vertical; y <= vertical; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!world.isLoaded(pos)) continue;
                    BlockState state = world.getBlockState(pos);
                    if (SkillCombatTracker.isOre(state)) {
                        ores.add(pos.immutable());
                    } else if (level >= 3 && isSpecialContainerOrSpawner(world, pos, state)) {
                        specials.add(pos.immutable());
                    }
                }
            }
        }
        ORE_SIGHT.put(player.getUUID(), new OreSightState(world, ores, specials,
                world.getGameTime() + SkillManager.ORE_SIGHT_TICKS));
        syncOreMarkers(player, ores, specials, SkillManager.ORE_SIGHT_TICKS);
        SkillVfx.ring(world, player.position().add(0, 0.23, 0), 2.0, 0, 10, SkillVfx.Tint.ORE);
        SkillVfx.sparks(world, player.position().add(0, 0.5, 0), 0.48, 0.38, 12, SkillVfx.Tint.ORE);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.ore_sight",
                ores.size() + specials.size()), true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);
        return true;
    }

    private static boolean isSpecialContainerOrSpawner(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.is(Blocks.SPAWNER)) return true;
        if (state.getBlock() instanceof ChestBlock) {
            BlockEntity be = world.getBlockEntity(pos);
            return be instanceof RandomizableContainerBlockEntity;
        }
        return false;
    }

    // ===== 6. 凌空踏步 =====

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
        if (level >= 3) {
            AIR_STEP_LANDING_SHOCKWAVE.add(player.getUUID());
        }
        Vec3 step = player.position().add(0, 0.20, 0);
        SkillVfx.ring(player.serverLevel(), step, 0.82, 0, 10, SkillVfx.Tint.AIR);
        SkillVfx.wake(player.serverLevel(), step, direction.scale(-1), 1.8, 0.45, true, SkillVfx.Tint.AIR);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.25F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.air_step"), true);
        return true;
    }

    // ===== 7. 雷霆之矛 =====

    private static boolean castLightningSpear(ServerPlayer player, int level, float ratio) {
        ServerLevel world = player.serverLevel();
        float damage = (8.0F + 5.0F * level * ratio) * 2.5F;
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        float projectileSpeed = 2.4F;

        LightningSpearProjectile projectile = new LightningSpearProjectile(world, player, damage);
        projectile.setChargeRatio(ratio);
        projectile.setSkillLevel(level);
        projectile.setPos(start.x + direction.x * 0.8, start.y + direction.y * 0.8, start.z + direction.z * 0.8);
        projectile.shoot(direction.x, direction.y, direction.z, projectileSpeed, 0.0F);
        world.addFreshEntity(projectile);
        SkillVfx.wake(world, start.add(0, -0.28, 0), direction, 2.8 + ratio, 0.50, false, SkillVfx.Tint.SPEAR);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.LIGHTNING_SPEAR_THROW.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.lightning_spear"), true);
        return true;
    }

    // ===== 8. 狂战爆发 =====

    private static boolean castBerserk(ServerPlayer player, int level) {
        int dur = 200;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, level - 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, level - 1, false, true, true));

        BERSERK_STATES.put(player.getUUID(), new BerserkState(level, player.serverLevel().getGameTime() + dur, 0));
        var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            attackAttr.addTransientModifier(new AttributeModifier(
                    BERSERK_ATTACK_DAMAGE_ID, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        SkillVfx.spiral(player.serverLevel(), player.position().add(0, 0.12, 0),
                1.10, 1.6, Math.PI / 3.0, SkillVfx.Tint.RAGE);
        SkillVfx.ring(player.serverLevel(), player.position().add(0, 0.20, 0), 1.7, 0, 10, SkillVfx.Tint.RAGE);
        SkillVfx.sparks(player.serverLevel(), player.position().add(0, 0.65, 0), 0.62, 0.8, 20, SkillVfx.Tint.RAGE);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 0.8F);
        return true;
    }

    /** 狂战爆发 Lv.3 觉醒：期间击杀延长持续时间最多 6 秒并回血。 */
    static void onBerserkKill(ServerPlayer player) {
        BerserkState state = BERSERK_STATES.get(player.getUUID());
        if (state == null || state.skillLevel() < 3) return;
        long now = player.serverLevel().getGameTime();
        if (now >= state.expiresAt() || state.extendedSeconds() >= 6) return;
        long nextExp = state.expiresAt() + 40;
        BERSERK_STATES.put(player.getUUID(), new BerserkState(state.skillLevel(), nextExp, state.extendedSeconds() + 2));
        int rem = (int) Math.max(40, nextExp - now);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, rem, state.skillLevel() - 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, rem, state.skillLevel() - 1, false, true, true));
        player.heal(2.0F);
    }

    // ===== Tick 调度 =====

    static void tickServer(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 2 == 0) {
            tickFrozen(gameTime);
        }
        tickGravityWells(gameTime);
        SkillNewActivesHelper.tickServer(server);
    }

    static void tickPlayerActives(ServerPlayer player, long gameTime) {
        tickAirStep(player, gameTime);
        tickBerserk(player, gameTime);
        if (GUARDS.containsKey(player.getUUID())) {
            tickCounterGuardProjectiles(player);
        }
        tickOreSight(player, gameTime);
    }

    private static void tickBerserk(ServerPlayer player, long now) {
        BerserkState state = BERSERK_STATES.get(player.getUUID());
        if (state != null && now >= state.expiresAt()) {
            BERSERK_STATES.remove(player.getUUID());
            var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            }
        }
    }

    private static void tickAirStep(ServerPlayer player, long now) {
        if (player.onGround() && AIR_STEP_LANDING_SHOCKWAVE.remove(player.getUUID())) {
            player.resetFallDistance();
            for (LivingEntity nearby : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(4.5), e -> canAffect(player, e))) {
                Vec3 push = nearby.position().subtract(player.position()).normalize().scale(1.1);
                nearby.setDeltaMovement(push.x, 0.35, push.z);
                nearby.hurtMarked = true;
                nearby.hurt(player.serverLevel().damageSources().indirectMagic(player, player), 5.0F);
            }
            SkillVfx.ring(player.serverLevel(), player.position().add(0, 0.12, 0), 3.4, 0, 14, SkillVfx.Tint.AIR);
            SkillVfx.sparks(player.serverLevel(), player.position(), 1.15, 0.15, 18, SkillVfx.Tint.AIR);
        }

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
        if (state == null) return;
        if (gameTime >= state.expiresAt() || state.level() != player.serverLevel()) {
            ORE_SIGHT.remove(player.getUUID());
            syncOreMarkers(player, Set.of(), Set.of(), 0);
            return;
        }
        if (gameTime % 20 != 0) return;
        state.orePositions().removeIf(pos -> !player.serverLevel().isLoaded(pos)
                || !SkillCombatTracker.isOre(player.serverLevel().getBlockState(pos)));
        state.specialPositions().removeIf(pos -> !player.serverLevel().isLoaded(pos)
                || !isSpecialContainerOrSpawner(player.serverLevel(), pos, player.serverLevel().getBlockState(pos)));
        syncOreMarkers(player, state.orePositions(), state.specialPositions(), (int) (state.expiresAt() - gameTime));
    }

    // ===== 反击护体拦截与反弹 =====

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
        if (damageOwner == null) damageOwner = event.getSource().getDirectEntity();
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
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 1, false, true, true));
            for (LivingEntity nearby : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(6.0), e -> canAffect(player, e))) {
                Vec3 push = nearby.position().subtract(player.position()).normalize().scale(1.2);
                nearby.setDeltaMovement(push.x, 0.38, push.z);
                nearby.hurtMarked = true;
            }
        }

        SkillVfx.ring(player.serverLevel(), player.position().add(0, 0.32, 0),
                guard.level() >= 3 ? 3.4 : 1.7, 0, 16, SkillVfx.Tint.GUARD);
        SkillVfx.sparks(player.serverLevel(), player.position().add(0, 0.72, 0),
                0.70, 0.54, 22, SkillVfx.Tint.GUARD);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.9F, 1.7F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    static void deflectProjectile(ServerPlayer player, Projectile projectile) {
        if (!projectile.isAlive()) return;
        Entity shooter = projectile.getOwner();
        Vec3 motion = projectile.getDeltaMovement();
        double speed = Math.max(1.5D, motion.length() * 1.35D);
        if (shooter != null && shooter != player && shooter.isAlive()) {
            Vec3 toShooter = shooter.getEyePosition().subtract(projectile.position()).normalize();
            projectile.setDeltaMovement(toShooter.scale(speed));
        } else {
            Vec3 away = projectile.position().subtract(player.getEyePosition());
            if (away.lengthSqr() < 1.0E-4) away = player.getLookAngle();
            projectile.setDeltaMovement(away.normalize().scale(speed));
        }
        projectile.setOwner(player);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;

        player.serverLevel().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.8F);
        player.serverLevel().sendParticles(ParticleTypes.CRIT,
                projectile.getX(), projectile.getY(), projectile.getZ(), 12, 0.25, 0.25, 0.25, 0.15);
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

    private static void syncOreMarkers(ServerPlayer player, Set<BlockPos> ores, Set<BlockPos> specials, int ticks) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putInt("ticks", ticks);
        tag.putLongArray("positions", ores.stream()
                .sorted(Comparator.comparingDouble(pos -> player.distanceToSqr(Vec3.atCenterOf(pos))))
                .limit(512).mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("specialPositions", specials.stream()
                .sorted(Comparator.comparingDouble(pos -> player.distanceToSqr(Vec3.atCenterOf(pos))))
                .limit(128).mapToLong(BlockPos::asLong).toArray());
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
        if (bestHit != null) return bestHit;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, box, e -> canPull(player, e))) {
            Vec3 candidatePos = candidate.getBoundingBox().getCenter();
            Vec3 toCandidate = candidatePos.subtract(eyePos);
            double forwardDist = toCandidate.dot(look);
            if (forwardDist < 0.5 || forwardDist > maxRange) continue;
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

    /** 牵引与范围技能的统一友好目标保护（修复历史缺陷 #3 & #4）。 */
    static boolean canPull(ServerPlayer source, LivingEntity target) {
        return canAffect(source, target);
    }

    static boolean canAffect(ServerPlayer source, LivingEntity target) {
        if (target == null || target == source || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof Villager) return false;
        if (target instanceof IronGolem golem && !golem.isAngryAt(source)) return false;
        if (target instanceof TamableAnimal tameable) {
            UUID ownerId = tameable.getOwnerUUID();
            if (Objects.equals(ownerId, source.getUUID())) {
                return false;
            }
            if (ownerId != null) {
                if (tameable.getOwner() instanceof Player owner) {
                    return source.server.isPvpAllowed() && !source.isAlliedTo(owner);
                }
                return false; // 主人离线时保护其宠物
            }
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
            syncOreMarkers(player, Set.of(), Set.of(), 0);
        }
        if (!active.contains(ModSkills.GRAVITY_WELL)) {
            GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(player.getUUID()));
        }
        if (!active.contains(ModSkills.BERSERK)) {
            BERSERK_STATES.remove(player.getUUID());
            var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            }
        }
        for (String newId : List.of(ModSkills.ASTRAL_CLEAVE, ModSkills.THUNDER_JUDGMENT,
                ModSkills.STARLIGHT_SANCTUARY, ModSkills.VOID_BLINK, ModSkills.VEIN_RESONANCE)) {
            if (!active.contains(newId)) {
                SkillNewActivesHelper.clearSingle(player, newId);
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
            syncOreMarkers(player, Set.of(), Set.of(), 0);
        } else if (ModSkills.GRAVITY_WELL.equals(skillId)) {
            GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(player.getUUID()));
        } else if (ModSkills.BERSERK.equals(skillId)) {
            BERSERK_STATES.remove(player.getUUID());
            var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
            }
        } else if (ModSkills.BATTLE_MOMENTUM.equals(skillId)) {
            SkillPassiveHandler.clearMomentum(player.getUUID());
        } else {
            SkillNewActivesHelper.clearSingle(player, skillId);
        }
    }

    static void clearAllForPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        if (GUARDS.remove(id) != null) {
            broadcastGuardEffect(player, CounterGuardEffectPayload.Action.CLEAR);
        }
        ORE_SIGHT.remove(id);
        syncOreMarkers(player, Set.of(), Set.of(), 0);
        GRAVITY_WELLS.removeIf(well -> well.ownerId().equals(id));
        BERSERK_STATES.remove(id);
        AIR_STEP_LANDING_SHOCKWAVE.remove(id);
        var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(BERSERK_ATTACK_DAMAGE_ID);
        }
        SkillNewActivesHelper.clearPlayer(player);
    }

    static void clearTransient(ServerPlayer player) {
        UUID id = player.getUUID();
        ChargeState held = CHARGES.get(id);
        if (held != null && ModSkills.AIR_STEP.equals(held.skillId())) {
            cancelCharge(player);
        }
        CHARGES.remove(id);
        FrozenState selfFrozen = FROZEN.remove(id);
        if (selfFrozen != null && selfFrozen.entity() instanceof Mob mob && !mob.isRemoved()) {
            mob.setNoAi(selfFrozen.previousNoAi());
        }
        STASIS.entrySet().removeIf(entry -> entry.getValue().owner().getUUID().equals(id));
        clearAllForPlayer(player);
    }
}
