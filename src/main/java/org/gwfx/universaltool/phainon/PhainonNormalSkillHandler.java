package org.gwfx.universaltool.phainon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModSounds;
import org.gwfx.universaltool.item.QinchenSwordItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 白厄（侵晨）普通状态右键战技「黎明创世，地辟天开 / Let There Be Light」服务端状态机与结算逻辑。
 * <p>流程：起手日轮圣印 -> 举剑蓄力 -> 日轮绽放 -> 疾步前冲 -> 扩散蓝金能量波横扫结算并积攒 2 点【火种】。</p>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonNormalSkillHandler {

    /** 战技全流程持续刻数（2.50 秒，偏原作华丽演出节奏） */
    public static final int TOTAL_CAST_TICKS = 50;
    /** 日轮光环绽放节点刻数（0.50 秒） */
    public static final int SUNBURST_TICK = 10;
    /** 疾步前冲与挥剑启动刻数（1.25 秒） */
    public static final int LUNGE_TICK = 25;
    /** 扩散能量波命中与火种结算刻数（1.40 秒） */
    public static final int IMPACT_TICK = 28;
    /** 普通战技冷却刻数（5.5 秒） */
    public static final int COOLDOWN_TICKS = 110;

    /** 主目标搜索最远距离 */
    public static final double PRIMARY_RANGE = 9.5D;
    /** 蓝金能量波前方扇区横扫半径 */
    public static final double SWEEP_RANGE = 9.5D;
    /** 相邻目标扩散半径（围绕主目标中心） */
    public static final double BLAST_ADJACENT_RADIUS = 4.0D;
    /** 横扫扇区半角（度） */
    public static final double SWEEP_HALF_ANGLE_DEGREES = 65.0D;

    /** 主目标物理伤害 */
    public static final float PRIMARY_DAMAGE = 14.0F;
    /** 相邻扩散目标物理伤害 */
    public static final float ADJACENT_DAMAGE = 10.5F;

    private static final Map<UUID, ActiveCast> ACTIVE_CASTS = new ConcurrentHashMap<>();

    private static final class ActiveCast {
        private final InteractionHand hand;
        private final long startTick;
        private boolean lunged;
        private boolean impacted;

        private ActiveCast(InteractionHand hand, long startTick) {
            this.hand = hand;
            this.startTick = startTick;
        }
    }

    private PhainonNormalSkillHandler() {
    }

    /**
     * 判断指定玩家是否正在执行普通战技动画。
     */
    public static boolean isCasting(UUID playerId) {
        return playerId != null && ACTIVE_CASTS.containsKey(playerId);
    }

    /**
     * 尝试在普通状态下触发侵晨右键战技「黎明创世，地辟天开」。
     */
    public static InteractionResultHolder<ItemStack> tryStartCast(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof QinchenSwordItem)) {
            return InteractionResultHolder.pass(stack);
        }

        PhainonWeaponState state = ModDataComponents.getPhainonWeaponState(stack);
        if (!state.isNormalForm()) {
            // 非普通形态（变身状态）不触发普通战技，交由后续扩展逻辑处理
            return InteractionResultHolder.pass(stack);
        }

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (isCasting(serverPlayer.getUUID()) || PhainonUltimateHandler.isActive(serverPlayer.getUUID())) {
            return InteractionResultHolder.fail(stack);
        }

        ServerLevel serverLevel = serverPlayer.serverLevel();
        ACTIVE_CASTS.put(serverPlayer.getUUID(), new ActiveCast(hand, serverLevel.getGameTime()));
        serverPlayer.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);

        broadcastEffect(serverPlayer, PhainonSkillEffectPayload.Action.START, hand);

        // 播放白厄普通战技随机台词语音（原声不变调），并辅以柔和的圣印与点燃音效
        serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                ModSounds.PHAINON_NORMAL_SKILL_VOICE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.45F, 1.35F);
        serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, 1.25F);
        serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.30F, 1.3F);

        // 起手脚下与身周金色星辉粒子
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                serverPlayer.getX(), serverPlayer.getY() + 0.15, serverPlayer.getZ(),
                18, 0.85, 0.15, 0.85, 0.03);
        serverLevel.sendParticles(ParticleTypes.WAX_ON,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                12, 0.45, 0.55, 0.45, 0.04);

        return InteractionResultHolder.consume(stack);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ActiveCast cast = ACTIVE_CASTS.get(serverPlayer.getUUID());
        if (cast == null) {
            return;
        }

        ItemStack stack = serverPlayer.getItemInHand(cast.hand);
        PhainonWeaponState weaponState = ModDataComponents.getPhainonWeaponState(stack);
        if (!serverPlayer.isAlive()
                || serverPlayer.isRemoved()
                || !(stack.getItem() instanceof QinchenSwordItem)
                || !weaponState.isNormalForm()) {
            ACTIVE_CASTS.remove(serverPlayer.getUUID());
            broadcastEffect(serverPlayer, PhainonSkillEffectPayload.Action.CANCEL, cast.hand);
            return;
        }

        ServerLevel level = serverPlayer.serverLevel();
        long elapsed = level.getGameTime() - cast.startTick;

        // 前摇蓄力阶段：柔和阻尼移动并生成金色蓄能粒子
        if (elapsed > 0 && elapsed < IMPACT_TICK) {
            Vec3 velocity = serverPlayer.getDeltaMovement();
            serverPlayer.setDeltaMovement(velocity.x * 0.78D, velocity.y, velocity.z * 0.78D);
            serverPlayer.hurtMarked = true;

            if (elapsed % 2 == 0) {
                int density = elapsed >= SUNBURST_TICK ? 6 : 3;
                level.sendParticles(ParticleTypes.END_ROD,
                        serverPlayer.getX(), serverPlayer.getY() + 1.85, serverPlayer.getZ(),
                        density, 0.45, 0.55, 0.45, 0.03);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        serverPlayer.getX(), serverPlayer.getY() + 1.35, serverPlayer.getZ(),
                        density, 0.48, 0.45, 0.48, 0.04);
                level.sendParticles(ParticleTypes.GLOW,
                        serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                        3, 0.5, 0.5, 0.5, 0.015);
            }
        }

        // 日轮光环爆发节点
        if (elapsed == SUNBURST_TICK) {
            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.85F, 1.45F);
            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.35F, 1.6F);

            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2.0D * i) / 16.0D;
                double px = serverPlayer.getX() + Math.cos(angle) * 1.15D;
                double pz = serverPlayer.getZ() + Math.sin(angle) * 1.15D;
                level.sendParticles(ParticleTypes.END_ROD, px, serverPlayer.getY() + 0.95D, pz,
                        1, 0.05, 0.25, 0.05, 0.04);
            }
        }

        // 疾步前冲与挥剑动作启动
        if (elapsed >= LUNGE_TICK && !cast.lunged) {
            cast.lunged = true;
            Vec3 forward = horizontalForward(serverPlayer);
            double lungeSpeed = isForwardBlocked(level, serverPlayer, forward) ? 0.25D : 0.85D;
            Vec3 currentMotion = serverPlayer.getDeltaMovement();
            serverPlayer.setDeltaMovement(
                    forward.x * lungeSpeed,
                    Math.max(currentMotion.y, 0.08D),
                    forward.z * lungeSpeed
            );
            serverPlayer.hurtMarked = true;
            serverPlayer.swing(cast.hand, true);
        }

        // 扩散能量波横扫结算与火种积攒
        if (elapsed >= IMPACT_TICK && !cast.impacted) {
            cast.impacted = true;
            executeBlastSlash(serverPlayer, cast.hand, stack);
        }

        if (elapsed >= TOTAL_CAST_TICKS) {
            ACTIVE_CASTS.remove(serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ACTIVE_CASTS.remove(serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_CASTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE_CASTS.clear();
    }

    private static void executeBlastSlash(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        broadcastEffect(player, PhainonSkillEffectPayload.Action.SLASH, hand);

        // 凌厉横扫组合音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.85F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.55F, 1.65F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.9F, 1.15F);

        Vec3 look = horizontalForward(player);
        Vec3 eyePos = player.getEyePosition();

        // 扫描前方可能受影响的存活实体
        double searchRadius = SWEEP_RANGE + BLAST_ADJACENT_RADIUS;
        AABB searchBounds = player.getBoundingBox().inflate(searchRadius, 4.5D, searchRadius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                searchBounds,
                entity -> canHitTarget(player, entity)
        );

        // 1. 选择主目标（优先贴近准星射线且在前方扇区内）
        LivingEntity primaryTarget = selectPrimaryTarget(player, eyePos, look, candidates);

        // 2. 筛选相邻扩散目标（主目标周边 BLAST_ADJACENT_RADIUS 格，或位于前方横扫扇区内）
        List<LivingEntity> adjacentTargets = new ArrayList<>();
        Vec3 primaryCenter = primaryTarget != null ? primaryTarget.getBoundingBox().getCenter() : null;
        for (LivingEntity candidate : candidates) {
            if (candidate == primaryTarget) {
                continue;
            }
            boolean nearPrimary = primaryCenter != null
                    && candidate.getBoundingBox().getCenter().distanceToSqr(primaryCenter)
                    <= BLAST_ADJACENT_RADIUS * BLAST_ADJACENT_RADIUS;
            boolean inSweepCone = isWithinHorizontalSweepCone(player, look, candidate, SWEEP_RANGE);
            if (nearPrimary || inSweepCone) {
                adjacentTargets.add(candidate);
            }
        }

        int hitCount = 0;
        if (primaryTarget != null) {
            boolean hurt = primaryTarget.hurt(player.damageSources().playerAttack(player), PRIMARY_DAMAGE);
            if (hurt) {
                hitCount++;
            }
            applyKnockback(primaryTarget, look, 0.55D, 0.22D);
            spawnPrimaryHitParticles(level, primaryTarget);
        }

        for (LivingEntity adjacent : adjacentTargets) {
            boolean hurt = adjacent.hurt(player.damageSources().playerAttack(player), ADJACENT_DAMAGE);
            if (hurt) {
                hitCount++;
            }
            Vec3 away = adjacent.position().subtract(player.position());
            Vec3 flatAway = new Vec3(away.x, 0.0D, away.z);
            Vec3 pushDir = flatAway.lengthSqr() > 1.0E-4D ? flatAway.normalize() : look;
            applyKnockback(adjacent, pushDir, 0.42D, 0.16D);
            spawnAdjacentHitParticles(level, adjacent);
        }

        // 沿 130° 前方弧形横扫轨迹撒布蓝金弧光粒子带
        spawnSlashArcParticles(level, player, look);

        // 积攒 2 点【火种】（上限 12）
        PhainonWeaponState currentState = ModDataComponents.getPhainonWeaponState(stack);
        PhainonWeaponState updatedState = currentState.withAddedCoreflame(PhainonWeaponState.NORMAL_SKILL_COREFLAME_GAIN);
        ModDataComponents.setPhainonWeaponState(stack, updatedState);

        if (updatedState.coreflame() == PhainonWeaponState.MAX_COREFLAME) {
            player.displayClientMessage(Component.translatable("message.universal_tool.phainon.ultimate_ready"), true);
        } else if (hitCount > 0) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.universal_tool.phainon.normal_skill_hit",
                            hitCount,
                            PhainonWeaponState.NORMAL_SKILL_COREFLAME_GAIN,
                            updatedState.coreflame(),
                            PhainonWeaponState.MAX_COREFLAME
                    ),
                    true
            );
        } else {
            player.displayClientMessage(
                    Component.translatable(
                            "message.universal_tool.phainon.normal_skill_cast",
                            PhainonWeaponState.NORMAL_SKILL_COREFLAME_GAIN,
                            updatedState.coreflame(),
                            PhainonWeaponState.MAX_COREFLAME
                    ),
                    true
            );
        }
    }

    @Nullable
    private static LivingEntity selectPrimaryTarget(ServerPlayer player,
                                                    Vec3 eyePos,
                                                    Vec3 forward,
                                                    List<LivingEntity> candidates) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (!isWithinHorizontalSweepCone(player, forward, candidate, PRIMARY_RANGE)) {
                continue;
            }
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 toCandidate = center.subtract(eyePos);
            double forwardDist = Math.max(0.1D, toCandidate.dot(forward));
            Vec3 rayPoint = eyePos.add(forward.scale(forwardDist));
            double distToRay = center.distanceTo(rayPoint);
            double score = distToRay * 2.2D + forwardDist * 0.25D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isWithinHorizontalSweepCone(ServerPlayer player,
                                                       Vec3 forward,
                                                       LivingEntity target,
                                                       double maxRange) {
        double dy = target.getY() - player.getY();
        if (dy < -2.0D || dy > 3.5D) {
            return false;
        }
        Vec3 delta = new Vec3(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
        double horizontalDist = delta.length();
        // 目标与玩家极其贴近时直接视作处于正面覆盖范围内
        if (horizontalDist <= 1.2D) {
            return true;
        }
        if (horizontalDist > maxRange) {
            return false;
        }
        double cosHalfAngle = Math.cos(Math.toRadians(SWEEP_HALF_ANGLE_DEGREES));
        double dot = delta.normalize().dot(forward);
        return dot >= cosHalfAngle;
    }

    private static boolean canHitTarget(ServerPlayer source, LivingEntity target) {
        if (target == source || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof ArmorStand || target instanceof Villager) {
            return false;
        }
        if (target instanceof IronGolem golem && !golem.isAngryAt(source)) {
            return false;
        }
        if (target instanceof TamableAnimal tameable && tameable.getOwner() instanceof Player owner) {
            if (owner == source || owner.getUUID().equals(source.getUUID())) {
                return false;
            }
            return source.server.isPvpAllowed() && !source.isAlliedTo(owner);
        }
        if (target instanceof Player other) {
            return source.server.isPvpAllowed() && !source.isAlliedTo(other) && !other.isSpectator();
        }
        return true;
    }

    private static void applyKnockback(LivingEntity target, Vec3 dir, double horizontalStrength, double verticalLift) {
        Vec3 existing = target.getDeltaMovement();
        target.setDeltaMovement(
                existing.x * 0.3D + dir.x * horizontalStrength,
                Math.max(existing.y, verticalLift),
                existing.z * 0.3D + dir.z * horizontalStrength
        );
        target.hurtMarked = true;
    }

    private static void spawnPrimaryHitParticles(ServerLevel level, LivingEntity target) {
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() * 0.55D;
        double cz = target.getZ();
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 2, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 18, 0.45, 0.45, 0.45, 0.22);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, cx, cy, cz, 20, 0.5, 0.5, 0.5, 0.18);
        level.sendParticles(ParticleTypes.FLAME, cx, cy, cz, 14, 0.35, 0.35, 0.35, 0.08);
    }

    private static void spawnAdjacentHitParticles(ServerLevel level, LivingEntity target) {
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() * 0.55D;
        double cz = target.getZ();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 14, 0.4, 0.4, 0.4, 0.08);
        level.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 12, 0.35, 0.35, 0.35, 0.16);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 10, 0.4, 0.4, 0.4, 0.06);
    }

    private static void spawnSlashArcParticles(ServerLevel level, ServerPlayer player, Vec3 forward) {
        double baseYaw = Math.atan2(forward.z, forward.x);
        double baseY = player.getY() + 1.1D;
        int steps = 22;
        double halfArcRad = Math.toRadians(65.0D);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = baseYaw + Mth.lerp(t, -halfArcRad, halfArcRad);
            double slantY = baseY + Mth.lerp(t, -0.35D, 0.45D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            for (double radius : new double[]{3.5D, 6.2D, 8.6D}) {
                double px = player.getX() + cos * radius;
                double pz = player.getZ() + sin * radius;
                if (i % 2 == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, slantY, pz, 1, 0.08, 0.08, 0.08, 0.02);
                }
                if (radius > 5.0D) {
                    level.sendParticles(ParticleTypes.END_ROD, px, slantY + 0.08D, pz, 1, 0.05, 0.05, 0.05, 0.03);
                }
            }
            if (i % 5 == 2) {
                double px = player.getX() + cos * 5.0D;
                double pz = player.getZ() + sin * 5.0D;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, slantY, pz, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.CLOUD, px, slantY - 0.5D, pz, 2, 0.25, 0.05, 0.25, 0.03);
            }
        }
    }

    private static Vec3 horizontalForward(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-5D) {
            float yawRad = (float) Math.toRadians(player.getYRot());
            return new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad)).normalize();
        }
        return flat.normalize();
    }

    private static boolean isForwardBlocked(ServerLevel level, ServerPlayer player, Vec3 forward) {
        BlockPos feetCheck = BlockPos.containing(
                player.getX() + forward.x * 1.0D,
                player.getY() + 0.2D,
                player.getZ() + forward.z * 1.0D
        );
        BlockPos headCheck = feetCheck.above();
        return !level.getBlockState(feetCheck).getCollisionShape(level, feetCheck).isEmpty()
                || !level.getBlockState(headCheck).getCollisionShape(level, headCheck).isEmpty();
    }

    private static void broadcastEffect(ServerPlayer player,
                                        PhainonSkillEffectPayload.Action action,
                                        InteractionHand hand) {
        PhainonSkillEffectPayload.broadcast(player, action, hand, TOTAL_CAST_TICKS);
    }
}
