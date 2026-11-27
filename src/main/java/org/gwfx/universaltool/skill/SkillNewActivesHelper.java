package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.OreSightPayload;
import org.gwfx.universaltool.network.SanctuaryDomeEffectPayload;
import org.gwfx.universaltool.network.Skill3dEffectPayload;
import org.gwfx.universaltool.network.Skill3dEffectPayload.EffectKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 执行进阶主动技能的释放与持续态调度：
 * 裂空星斩、天罚雷域、圣域穹顶、虚空折跃（锁敌背刺+10秒返回）、点石成矿（重做）、炼金脉冲。
 */
final class SkillNewActivesHelper {
    enum VoidBlinkResult {
        FIRST_BLINK_ANCHORED,
        RECALLED,
        CANCELLED
    }

    private static final List<CleaveEcho> CLEAVE_ECHOES = new CopyOnWriteArrayList<>();
    private static final Set<UUID> PENDING_CLEAVE_REFUND = ConcurrentHashMap.newKeySet();
    private static final List<ThunderZone> THUNDER_ZONES = new CopyOnWriteArrayList<>();
    private static final List<SanctuaryDome> SANCTUARIES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, BlinkAnchor> BLINK_ANCHORS = new ConcurrentHashMap<>();

    private record CleaveEcho(ServerLevel level, UUID ownerId, Vec3 origin, Vec3 direction,
                              double range, double width, float damage, float missingPct,
                              long fireAtGameTime, boolean cdAlreadyRefunded) {}

    private static final class ThunderZone {
        final ServerLevel level;
        final UUID ownerId;
        final Vec3 center;
        final int skillLevel;
        final long strikeAt;
        final long expiresAt;
        boolean struck;

        ThunderZone(ServerLevel level, UUID ownerId, Vec3 center, int skillLevel, long strikeAt, long expiresAt) {
            this.level = level;
            this.ownerId = ownerId;
            this.center = center;
            this.skillLevel = skillLevel;
            this.strikeAt = strikeAt;
            this.expiresAt = expiresAt;
        }
    }

    private record SanctuaryDome(ServerLevel level, UUID ownerId, Vec3 center, double radius,
                                 int skillLevel, long expiresAt, long visualId) {
        void fadeVisual() {
            SanctuaryDomeEffectPayload.fade(level, center, visualId);
        }
    }

    private record BlinkAnchor(ServerLevel level, Vec3 originPos, float originYaw, float originPitch,
                               float originHealth, long expiresAt, int cooldownTicks, long visualId) {
        void fadeVisual() {
            Skill3dEffectPayload.fade(level, originPos, visualId);
        }
    }

    private SkillNewActivesHelper() {}

    static void clearAll() {
        CLEAVE_ECHOES.clear();
        PENDING_CLEAVE_REFUND.clear();
        THUNDER_ZONES.clear();
        SANCTUARIES.forEach(SanctuaryDome::fadeVisual);
        SANCTUARIES.clear();
        BLINK_ANCHORS.values().forEach(BlinkAnchor::fadeVisual);
        BLINK_ANCHORS.clear();
    }

    static void clearPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        CLEAVE_ECHOES.removeIf(e -> e.ownerId().equals(id));
        PENDING_CLEAVE_REFUND.remove(id);
        THUNDER_ZONES.removeIf(z -> z.ownerId.equals(id));
        removeSanctuaries(id);
        settleRemovedBlinkAnchor(player, BLINK_ANCHORS.remove(id));
    }

    static void clearSingle(ServerPlayer player, String skillId) {
        UUID id = player.getUUID();
        switch (skillId) {
            case ModSkills.ASTRAL_CLEAVE -> {
                CLEAVE_ECHOES.removeIf(e -> e.ownerId().equals(id));
                PENDING_CLEAVE_REFUND.remove(id);
            }
            case ModSkills.THUNDER_JUDGMENT -> THUNDER_ZONES.removeIf(z -> z.ownerId.equals(id));
            case ModSkills.STARLIGHT_SANCTUARY -> removeSanctuaries(id);
            case ModSkills.VOID_BLINK -> settleRemovedBlinkAnchor(player, BLINK_ANCHORS.remove(id));
            default -> {}
        }
    }

    private static void settleRemovedBlinkAnchor(ServerPlayer player, BlinkAnchor anchor) {
        if (anchor != null) {
            anchor.fadeVisual();
            if (anchor.level().getGameTime() < anchor.expiresAt()) {
                PlayerSkillData data = SkillManager.data(player);
                if (data.learned(ModSkills.VOID_BLINK) && !data.onCooldown(ModSkills.VOID_BLINK)) {
                    data.startCooldown(ModSkills.VOID_BLINK, anchor.cooldownTicks());
                }
            }
        }
    }

    static boolean consumeImmediateCleaveRefund(UUID playerId) {
        return PENDING_CLEAVE_REFUND.remove(playerId);
    }

    static Vec3 latestThunderZoneCenterForTest(UUID ownerId) {
        for (int i = THUNDER_ZONES.size() - 1; i >= 0; i--) {
            ThunderZone zone = THUNDER_ZONES.get(i);
            if (zone.ownerId.equals(ownerId)) {
                return zone.center;
            }
        }
        return null;
    }

    static boolean isInsideSanctuary(ServerPlayer player) {
        if (SANCTUARIES.isEmpty()) return false;
        ServerLevel world = player.serverLevel();
        for (SanctuaryDome dome : SANCTUARIES) {
            if (dome.level() != world) continue;
            if (dome.center().distanceToSqr(player.position()) <= dome.radius() * dome.radius()) {
                if (dome.ownerId().equals(player.getUUID())) return true;
                ServerPlayer owner = world.getServer().getPlayerList().getPlayer(dome.ownerId());
                if (owner != null && player.isAlliedTo(owner)) return true;
            }
        }
        return false;
    }

    private static void removeSanctuaries(UUID owner) {
        SANCTUARIES.removeIf(dome -> {
            if (!dome.ownerId().equals(owner)) return false;
            dome.fadeVisual();
            return true;
        });
    }

    static boolean hasPendingBlinkRecall(UUID playerId, long gameTime) {
        BlinkAnchor anchor = BLINK_ANCHORS.get(playerId);
        return anchor != null && gameTime < anchor.expiresAt();
    }

    // ===== 1. 裂空星斩 (astral_cleave) =====

    static boolean castAstralCleave(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 origin = player.getEyePosition().subtract(0, 0.2, 0);
        Vec3 dir = player.getLookAngle().normalize();
        boolean holdingModWeapon = SkillModSynergyHandler.isHoldingUniversalTool(player);
        float baseDamage = (14.0F + 8.0F * (level - 1)) * (holdingModWeapon ? 1.25F : 1.0F);
        float missingPct = level == 1 ? 0.04F : (level == 2 ? 0.07F : 0.10F);
        double range = holdingModWeapon ? 13.0 : 11.0;
        double width = holdingModWeapon ? 3.4 : 2.8;

        boolean killed = sweepCleaveBeam(world, player, origin, dir, range, width, baseDamage, missingPct, false);
        Skill3dEffectPayload.spawn(world, EffectKind.ASTRAL_CLEAVE, origin, dir,
                (float) range, 12, holdingModWeapon ? 0xFFE38C : 0xB892FF);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.35F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.6F);

        if (level >= 3) {
            boolean refundFirst = killed;
            if (refundFirst) {
                PENDING_CLEAVE_REFUND.add(player.getUUID());
            }
            CLEAVE_ECHOES.add(new CleaveEcho(world, player.getUUID(),
                    origin.add(dir.scale(range)), dir.scale(-1.0),
                    range, width, baseDamage * 0.65F, missingPct * 0.65F,
                    world.getGameTime() + 8, refundFirst));
        }
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.astral_cleave"), true);
        return true;
    }

    private static boolean sweepCleaveBeam(ServerLevel world, ServerPlayer player, Vec3 origin, Vec3 dir,
                                           double range, double width, float baseDamage, float missingPct, boolean echo) {
        Vec3 end = origin.add(dir.scale(range));
        AABB sweepBox = new AABB(origin, end).inflate(width);
        SkillVfx.wake(world, origin, dir, range, width, echo, SkillVfx.Tint.ASTRAL);
        SkillVfx.point(world, origin.add(dir.scale(range * 0.62)), ParticleTypes.SWEEP_ATTACK);

        boolean anyKilled = false;
        for (LivingEntity target : world.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> SkillActiveExecutor.canAffect(player, e))) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(origin);
            double along = toTarget.dot(dir);
            if (along < -0.5 || along > range + 1.0) continue;
            double perpSqr = toTarget.subtract(dir.scale(along)).lengthSqr();
            if (perpSqr <= width * width) {
                float totalDmg = SkillActiveExecutor.pctDamage(target, baseDamage, 0.0F, 0.0F, missingPct);
                target.hurt(world.damageSources().indirectMagic(player, player), totalDmg);
                if (!target.isAlive()) {
                    anyKilled = true;
                }
            }
        }
        return anyKilled;
    }

    // ===== 2. 天罚雷域 (thunder_judgment) =====

    static boolean castThunderJudgment(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(24.0));
        BlockHitResult hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getLocation();
        if (hit.getType() == BlockHitResult.Type.MISS) {
            BlockHitResult down = world.clip(new ClipContext(center, center.subtract(0, 32.0, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (down.getType() != BlockHitResult.Type.MISS) {
                center = down.getLocation();
            } else {
                Vec3 forwardGround = start.add(player.getLookAngle().scale(6.0));
                BlockHitResult nearDown = world.clip(new ClipContext(forwardGround, forwardGround.subtract(0, 16.0, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                center = nearDown.getType() != BlockHitResult.Type.MISS ? nearDown.getLocation() : player.position();
            }
        }

        long now = world.getGameTime();
        long strikeAt = now + 16;
        long expiresAt = level >= 3 ? strikeAt + 100 : strikeAt + 2;
        THUNDER_ZONES.add(new ThunderZone(world, player.getUUID(), center, level, strikeAt, expiresAt));

        float radius = (float) (3.5 + level);
        int totalDur = (int) (expiresAt - now + 18);
        Skill3dEffectPayload.spawn(world, EffectKind.THUNDER_ZONE, center, Vec3.ZERO,
                radius, totalDur, 0xFFAE42);
        SkillVfx.ring(world, center.add(0, 0.22, 0), radius, 0, 16, SkillVfx.Tint.THUNDER);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.5F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.thunder_judgment"), true);
        return true;
    }

    // ===== 3. 圣域穹顶 (starlight_sanctuary) =====

    static boolean castStarlightSanctuary(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        double radius = 4.5 + level;
        int duration = 100 + level * 40;
        Vec3 center = player.position();
        long expiry = world.getGameTime() + duration;
        long visualId = SanctuaryDomeEffectPayload.open(world, center, radius, duration);
        SANCTUARIES.add(new SanctuaryDome(world, player.getUUID(), center, radius, level, expiry, visualId));

        SkillVfx.canopy(world, center, radius, 0, SkillVfx.Tint.SANCTUARY);
        SkillVfx.sparks(world, center.add(0, 0.52, 0), 0.62, 0.48, 15, SkillVfx.Tint.SANCTUARY);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.25F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.starlight_sanctuary"), true);
        return true;
    }

    // ===== 4. 虚空折跃 (void_blink · 锁敌瞬移背后 + 10 秒返回原点) =====

    static VoidBlinkResult handleVoidBlink(ServerPlayer player, PlayerSkillData data, int level, int adjustedCooldown) {
        ServerLevel world = player.serverLevel();
        long now = world.getGameTime();
        BlinkAnchor anchor = BLINK_ANCHORS.remove(player.getUUID());
        if (anchor != null && now < anchor.expiresAt() && anchor.level() == world) {
            // 二段返回原点
            anchor.fadeVisual();
            Vec3 cur = player.position();
            SkillVfx.portal(world, cur, false);
            player.teleportTo(world, anchor.originPos().x, anchor.originPos().y, anchor.originPos().z,
                    anchor.originYaw(), anchor.originPitch());
            player.resetFallDistance();
            if (level >= 3) {
                if (player.getHealth() < anchor.originHealth()) {
                    player.setHealth(Math.min(player.getMaxHealth(), anchor.originHealth()));
                }
                for (LivingEntity nearby : world.getEntitiesOfClass(LivingEntity.class,
                        new AABB(cur, cur).inflate(4.5D), e -> SkillActiveExecutor.canAffect(player, e))) {
                    nearby.hurt(world.damageSources().indirectMagic(player, player),
                            SkillActiveExecutor.pctDamage(nearby, 10.0F, 0.0F, 0.0F, 0.08F));
                }
                Skill3dEffectPayload.spawn(world, EffectKind.VOID_SLASH, cur, Vec3.ZERO, 3.0F, 14, 0xD47BFF);
            }
            SkillVfx.portal(world, anchor.originPos(), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.4F);
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.void_blink_recall"), true);
            data.startCooldown(ModSkills.VOID_BLINK, adjustedCooldown);
            SkillManager.sync(player);
            return VoidBlinkResult.RECALLED;
        }
        if (anchor != null) {
            anchor.fadeVisual();
        }

        // 一段折跃：锁定准星前方生物实体（24 / 36 / 48 格）瞬移至背后
        double maxDist = 24.0D + (level - 1) * 12.0D;
        LivingEntity locked = SkillActiveExecutor.findCrosshairTarget(player, maxDist);
        if (locked == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.no_target"), true);
            return VoidBlinkResult.CANCELLED;
        }

        Vec3 oldPos = player.position();
        float oldYaw = player.getYRot();
        float oldPitch = player.getXRot();
        float oldHealth = player.getHealth();

        Vec3 dest = findSafeBehindSpot(world, player, locked);
        Vec3 toTarget = locked.getEyePosition().subtract(dest.x, dest.y + player.getEyeHeight(), dest.z);
        double horiz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float newYaw = (float) (Math.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0F;
        float newPitch = (float) -(Math.atan2(toTarget.y, Math.max(0.01, horiz)) * (180.0 / Math.PI));

        SkillVfx.portal(world, oldPos, false);
        player.teleportTo(world, dest.x, dest.y, dest.z, newYaw, newPitch);
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, true));
        SkillVfx.portal(world, dest, true);
        world.playSound(null, dest.x, dest.y, dest.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.15F);

        // 赋予 3 秒影袭强化背刺
        SkillActiveExecutor.grantVoidBackstab(player.getUUID(), level, world.getGameTime() + 60L);

        // Lv.3 觉醒：瞬移落地立刻自动劈出一记暗影背刺斩
        if (level >= 3 && SkillActiveExecutor.canAffect(player, locked)) {
            locked.hurt(world.damageSources().indirectMagic(player, player),
                    SkillActiveExecutor.pctDamage(locked, 10.0F, 0.0F, 0.0F, 0.08F));
            Skill3dEffectPayload.spawn(world, EffectKind.VOID_SLASH, locked.position(), Vec3.ZERO, 2.2F, 12, 0xD47BFF);
        }

        // 10 秒（200 ticks）返回锚点
        long anchorVis = Skill3dEffectPayload.nextId();
        Skill3dEffectPayload.sendWithId(world, anchorVis, EffectKind.VOID_ANCHOR, oldPos, Vec3.ZERO,
                1.2F, 200, 0xB870FF);
        BLINK_ANCHORS.put(player.getUUID(),
                new BlinkAnchor(world, oldPos, oldYaw, oldPitch, oldHealth, now + 200L, adjustedCooldown, anchorVis));
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.void_blink"), true);
        return VoidBlinkResult.FIRST_BLINK_ANCHORED;
    }

    private static Vec3 findSafeBehindSpot(ServerLevel world, ServerPlayer player, LivingEntity target) {
        Vec3 targetLook = target.getLookAngle();
        Vec3 flatDir = new Vec3(targetLook.x, 0, targetLook.z);
        if (flatDir.lengthSqr() < 1.0E-4) {
            flatDir = target.position().subtract(player.position());
            flatDir = new Vec3(flatDir.x, 0, flatDir.z);
        }
        if (flatDir.lengthSqr() < 1.0E-4) {
            flatDir = new Vec3(0, 0, 1);
        }
        flatDir = flatDir.normalize();
        Vec3 side = new Vec3(-flatDir.z, 0, flatDir.x);

        Vec3[] candidates = {
                target.position().subtract(flatDir.scale(1.9)),
                target.position().subtract(flatDir.scale(1.4)),
                target.position().subtract(flatDir.scale(1.5)).add(side.scale(1.2)),
                target.position().subtract(flatDir.scale(1.5)).subtract(side.scale(1.2)),
                target.position().add(flatDir.scale(1.6)),
                target.position().add(0, 0.5, 0)
        };
        for (Vec3 cand : candidates) {
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos feet = BlockPos.containing(cand.x, cand.y + dy, cand.z);
                Vec3 spot = new Vec3(cand.x, feet.getY(), cand.z);
                if (world.noCollision(player, player.getBoundingBox().move(spot.subtract(player.position())))) {
                    return spot;
                }
            }
        }
        return target.position();
    }

    // ===== 5. 点石成矿 (vein_resonance · 重做替换原连锁采集) =====

    static boolean castVeinResonance(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        int radius = level == 1 ? 6 : (level == 2 ? 9 : 12);
        int vertical = level == 1 ? 4 : (level == 2 ? 5 : 7);
        int maxConvert = level == 1 ? 6 : (level == 2 ? 12 : 20);

        BlockPos origin = player.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!world.isLoaded(pos) || SkillCombatTracker.isPlayerPlaced(player, pos)) continue;
                    if (isTransmutableStone(world.getBlockState(pos))) {
                        candidates.add(pos.immutable());
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.transmute_stone_empty"), true);
            return false;
        }

        // 随机打乱后按距离温和加权排序，优先点化玩家身边近处的自然石块，同时保留矿脉分布的随机性
        Collections.shuffle(candidates);
        var rng = world.getRandom();
        candidates.sort(java.util.Comparator.comparingDouble(
                pos -> pos.distSqr(origin) + rng.nextDouble() * 12.0D));
        int converted = 0;
        List<Long> newOrePacked = new ArrayList<>();
        List<Integer> newOreColors = new ArrayList<>();
        boolean isNether = world.dimension().equals(Level.NETHER);

        for (BlockPos pos : candidates) {
            if (converted >= maxConvert) break;
            BlockState src = world.getBlockState(pos);
            BlockState ore = pickRandomOre(world, src, level, isNether);
            world.setBlock(pos, ore, Block.UPDATE_ALL);
            newOrePacked.add(pos.asLong());
            newOreColors.add(SkillActiveExecutor.oreColor(ore));
            SkillVfx.sparks(world, Vec3.atCenterOf(pos), 0.25, 0.25, 4, SkillVfx.Tint.VEIN);
            converted++;
        }

        Skill3dEffectPayload.spawn(world, EffectKind.STONE_TO_ORE_WAVE, player.position(), Vec3.ZERO,
                radius, 22, 0x4BE8A6);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.3F);

        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", world.dimension().location().toString());
        tag.putInt("ticks", 120);
        tag.putLongArray("positions", newOrePacked.stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray("colors", newOreColors.stream().mapToInt(Integer::intValue).toArray());
        tag.putLongArray("specialPositions", new long[0]);
        PacketDistributor.sendToPlayer(player, new OreSightPayload(tag));

        player.displayClientMessage(Component.translatable("message.universal_tool.skills.vein_resonance",
                converted), true);
        return true;
    }

    private static boolean isTransmutableStone(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.NETHERRACK) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT)
                || state.is(Blocks.END_STONE);
    }

    private static BlockState pickRandomOre(ServerLevel world, BlockState source, int level, boolean isNether) {
        if (isNether || source.is(Blocks.NETHERRACK) || source.is(Blocks.BLACKSTONE) || source.is(Blocks.BASALT)) {
            float r = world.random.nextFloat();
            float debrisChance = level == 1 ? 0.04F : (level == 2 ? 0.10F : 0.22F);
            if (r < debrisChance) return Blocks.ANCIENT_DEBRIS.defaultBlockState();
            if (r < debrisChance + 0.40F) return Blocks.NETHER_GOLD_ORE.defaultBlockState();
            return Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
        }
        boolean deep = source.is(Blocks.DEEPSLATE) || source.is(Blocks.TUFF);
        float r = world.random.nextFloat();
        if (level >= 3) {
            if (r < 0.26F) return (deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE).defaultBlockState();
            if (r < 0.42F) return (deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE).defaultBlockState();
            if (r < 0.62F) return (deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE).defaultBlockState();
            if (r < 0.80F) return (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState();
            if (r < 0.90F) return (deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE).defaultBlockState();
            return (deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE).defaultBlockState();
        }
        if (level == 2) {
            if (r < 0.12F) return (deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE).defaultBlockState();
            if (r < 0.20F) return (deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE).defaultBlockState();
            if (r < 0.40F) return (deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE).defaultBlockState();
            if (r < 0.68F) return (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState();
            if (r < 0.84F) return (deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE).defaultBlockState();
            return (deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE).defaultBlockState();
        }
        if (r < 0.04F) return (deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE).defaultBlockState();
        if (r < 0.24F) return (deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE).defaultBlockState();
        if (r < 0.56F) return (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState();
        if (r < 0.78F) return (deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE).defaultBlockState();
        return (deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE).defaultBlockState();
    }

    // ===== 6. 炼金脉冲 (arcane_transmute) =====

    static boolean castArcaneTransmute(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        int maxCount = switch (level) {
            case 1 -> 16;
            case 2 -> 32;
            default -> 64;
        };

        ItemStack offhand = player.getOffhandItem();
        Optional<RecipeHolder<SmeltingRecipe>> recipe = findSmelt(world, offhand);
        ItemStack sourceStack = offhand;

        if (recipe.isEmpty()) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack candidate = player.getInventory().items.get(i);
                if (i == player.getInventory().selected) continue;
                Optional<RecipeHolder<SmeltingRecipe>> found = findSmelt(world, candidate);
                if (found.isPresent()) {
                    recipe = found;
                    sourceStack = candidate;
                    break;
                }
            }
        }

        if (recipe.isEmpty() || sourceStack.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.transmute_empty"), true);
            return false;
        }

        int processCount = Math.min(maxCount, sourceStack.getCount());
        ItemStack unitResult = recipe.get().value().getResultItem(world.registryAccess()).copy();
        if (unitResult.isEmpty()) {
            return false;
        }
        sourceStack.shrink(processCount);

        int outCount = processCount * unitResult.getCount();
        if (level >= 3 && world.random.nextFloat() < 0.25F) {
            outCount *= 2;
        }
        while (outCount > 0) {
            int batch = Math.min(unitResult.getMaxStackSize(), outCount);
            ItemStack produced = unitResult.copyWithCount(batch);
            if (!player.getInventory().add(produced)) {
                player.drop(produced, false);
            }
            outCount -= batch;
        }

        ItemStack main = player.getMainHandItem();
        if (main.isDamageableItem() && main.isDamaged()) {
            double repairRatio = level == 1 ? 0.08 : level == 2 ? 0.16 : 0.25;
            int repair = Math.max(2, (int) Math.round(main.getMaxDamage() * repairRatio));
            main.setDamageValue(Math.max(0, main.getDamageValue() - repair));
        }

        Skill3dEffectPayload.spawn(world, EffectKind.ALCHEMY_CIRCLE, player.position(), Vec3.ZERO,
                2.0F, 18, 0xFF9C3D);
        SkillVfx.ring(world, player.position().add(0, 0.20, 0), 1.0, Math.PI / 8.0, 12, SkillVfx.Tint.ALCHEMY);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.9F, 1.3F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.arcane_transmute",
                processCount, unitResult.getHoverName()), true);
        return true;
    }

    private static Optional<RecipeHolder<SmeltingRecipe>> findSmelt(ServerLevel world, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return world.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), world);
    }

    // ===== 全局 Tick 调度 =====

    static void tickServer(MinecraftServer server) {
        // 1. 裂空星斩二段回旋与返还冷却
        if (!CLEAVE_ECHOES.isEmpty()) {
            Iterator<CleaveEcho> it = CLEAVE_ECHOES.iterator();
            List<CleaveEcho> toRemove = new ArrayList<>();
            while (it.hasNext()) {
                CleaveEcho echo = it.next();
                if (echo.level().getGameTime() >= echo.fireAtGameTime()) {
                    toRemove.add(echo);
                    ServerPlayer owner = server.getPlayerList().getPlayer(echo.ownerId());
                    if (owner != null && owner.isAlive() && echo.level() == owner.serverLevel()) {
                        Skill3dEffectPayload.spawn(echo.level(), EffectKind.ASTRAL_CLEAVE,
                                echo.origin(), echo.direction(), (float) echo.range(), 12, 0xFFD97D);
                        boolean killed = sweepCleaveBeam(echo.level(), owner, echo.origin(),
                                echo.direction(), echo.range(), echo.width(), echo.damage(), echo.missingPct(), true);
                        if (killed && !echo.cdAlreadyRefunded()) {
                            PlayerSkillData data = SkillManager.data(owner);
                            long rem = data.cooldownRemainingMillis(ModSkills.ASTRAL_CLEAVE);
                            if (rem > 0) {
                                data.startCooldown(ModSkills.ASTRAL_CLEAVE, Math.max(10, (int) Math.round(rem * 0.6 / 50.0)));
                                SkillManager.sync(owner);
                            }
                        }
                    }
                }
            }
            CLEAVE_ECHOES.removeAll(toRemove);
        }

        // 2. 天罚雷域
        if (!THUNDER_ZONES.isEmpty()) {
            List<ThunderZone> finished = new ArrayList<>();
            for (ThunderZone zone : THUNDER_ZONES) {
                long t = zone.level.getGameTime();
                ServerPlayer owner = server.getPlayerList().getPlayer(zone.ownerId);
                if (t >= zone.expiresAt || owner == null) {
                    finished.add(zone);
                    continue;
                }
                double radius = 3.5 + zone.skillLevel;
                if (!zone.struck && t >= zone.strikeAt) {
                    zone.struck = true;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(zone.level);
                    if (bolt != null) {
                        bolt.moveTo(zone.center.x, zone.center.y, zone.center.z);
                        bolt.setVisualOnly(true);
                        zone.level.addFreshEntity(bolt);
                    }
                    float baseDmg = 22.0F + (zone.skillLevel - 1) * 12.0F;
                    float maxPct = zone.skillLevel == 1 ? 0.08F : (zone.skillLevel == 2 ? 0.11F : 0.15F);
                    AABB box = new AABB(zone.center, zone.center).inflate(radius);
                    for (LivingEntity target : zone.level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> SkillActiveExecutor.canAffect(owner, e))) {
                        target.hurt(zone.level.damageSources().indirectMagic(owner, owner),
                                SkillActiveExecutor.pctDamage(target, baseDmg, maxPct, 0.0F, 0.0F));
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                    }
                    SkillVfx.ring(zone.level, zone.center.add(0, 0.30, 0), radius, Math.PI / 12.0, 16, SkillVfx.Tint.THUNDER);
                    SkillVfx.sparks(zone.level, zone.center.add(0, 0.5, 0), 1.0, 0.68, 28, SkillVfx.Tint.THUNDER);
                } else if (zone.struck && zone.skillLevel >= 3 && t % 20 == 0) {
                    AABB box = new AABB(zone.center, zone.center).inflate(radius);
                    for (LivingEntity target : zone.level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> SkillActiveExecutor.canAffect(owner, e))) {
                        target.hurt(zone.level.damageSources().indirectMagic(owner, owner),
                                SkillActiveExecutor.pctDamage(target, 6.0F, 0.04F, 0.0F, 0.0F));
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, true, true));
                    }
                }
            }
            THUNDER_ZONES.removeAll(finished);
        }

        // 3. 圣域穹顶
        if (!SANCTUARIES.isEmpty()) {
            List<SanctuaryDome> expired = new ArrayList<>();
            for (SanctuaryDome dome : SANCTUARIES) {
                long t = dome.level().getGameTime();
                ServerPlayer owner = server.getPlayerList().getPlayer(dome.ownerId());
                if (t >= dome.expiresAt() || owner == null) {
                    expired.add(dome);
                    dome.fadeVisual();
                    if (dome.skillLevel() >= 3 && owner != null) {
                        AABB box = new AABB(dome.center(), dome.center()).inflate(dome.radius() + 2.0);
                        for (LivingEntity enemy : dome.level().getEntitiesOfClass(LivingEntity.class, box,
                                e -> SkillActiveExecutor.canAffect(owner, e))) {
                            Vec3 diff = enemy.position().subtract(dome.center());
                            Vec3 push = diff.lengthSqr() > 1.0E-4 ? diff.normalize().scale(1.4) : new Vec3(0, 0, 1.4);
                            enemy.setDeltaMovement(push.x, 0.45, push.z);
                            enemy.hurtMarked = true;
                            enemy.igniteForSeconds(4);
                            enemy.hurt(dome.level().damageSources().indirectMagic(owner, owner),
                                    SkillActiveExecutor.pctDamage(enemy, 8.0F, 0.08F, 0.0F, 0.0F));
                        }
                        Skill3dEffectPayload.spawn(dome.level(), EffectKind.SANCTUARY_BLAST, dome.center(), Vec3.ZERO,
                                (float) dome.radius() + 1.5F, 16, 0x4FEAC0);
                    }
                    continue;
                }
                AABB box = new AABB(dome.center(), dome.center()).inflate(dome.radius());
                double radiusSqr = dome.radius() * dome.radius();
                for (Projectile proj : dome.level().getEntitiesOfClass(Projectile.class, box,
                        p -> p.isAlive()
                                && p.position().distanceToSqr(dome.center()) <= radiusSqr
                                && (p.getOwner() == null || (!p.getOwner().getUUID().equals(dome.ownerId())
                                && !p.getOwner().isAlliedTo(owner))))) {
                    Vec3 away = proj.position().subtract(dome.center());
                    if (away.lengthSqr() > 0.01) {
                        proj.setDeltaMovement(away.normalize().scale(1.2));
                        proj.hurtMarked = true;
                    }
                }
                if (t % 20 == 0) {
                    for (Player ally : dome.level().getEntitiesOfClass(Player.class, box,
                            p -> p.isAlive()
                                    && p.position().distanceToSqr(dome.center()) <= radiusSqr
                                    && (p.getUUID().equals(dome.ownerId()) || p.isAlliedTo(owner)))) {
                        ally.heal(1.5F + dome.skillLevel());
                        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> toPurify = new ArrayList<>();
                        for (MobEffectInstance inst : ally.getActiveEffects()) {
                            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                                toPurify.add(inst.getEffect());
                            }
                        }
                        toPurify.forEach(ally::removeEffect);
                    }
                }
                if (t % 10 == 0) {
                    SkillVfx.canopy(dome.level(), dome.center(), dome.radius(), t * 0.035, SkillVfx.Tint.SANCTUARY);
                }
            }
            SANCTUARIES.removeAll(expired);
        }

        // 4. 虚空折跃 10 秒返回锚点过期 -> 关闭 3D 特效并触发冷却
        if (!BLINK_ANCHORS.isEmpty()) {
            for (Map.Entry<UUID, BlinkAnchor> entry : BLINK_ANCHORS.entrySet()) {
                BlinkAnchor anchor = entry.getValue();
                if (anchor.level().getGameTime() >= anchor.expiresAt()) {
                    if (BLINK_ANCHORS.remove(entry.getKey(), anchor)) {
                        anchor.fadeVisual();
                        ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                        if (player != null) {
                            SkillManager.data(player).startCooldown(ModSkills.VOID_BLINK, anchor.cooldownTicks());
                            SkillManager.sync(player);
                        }
                    }
                }
            }
        }
    }
}
