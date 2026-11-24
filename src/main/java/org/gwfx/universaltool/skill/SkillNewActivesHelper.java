package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.network.SanctuaryDomeEffectPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 执行新增 6 项主动技能的释放与持续态调度：
 * 裂空星斩、天罚雷域、圣域穹顶、虚空折跃、星脉连锁、炼金脉冲。
 */
final class SkillNewActivesHelper {
    private static final List<CleaveEcho> CLEAVE_ECHOES = new CopyOnWriteArrayList<>();
    private static final List<ThunderZone> THUNDER_ZONES = new CopyOnWriteArrayList<>();
    private static final List<SanctuaryDome> SANCTUARIES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, BlinkAnchor> BLINK_ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, VeinState> VEIN_STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> VEIN_REENTRANCY_GUARD = ConcurrentHashMap.newKeySet();

    private record CleaveEcho(ServerLevel level, UUID ownerId, Vec3 origin, Vec3 direction,
                              float damage, long fireAtGameTime) {}

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

    private record BlinkAnchor(ServerLevel level, Vec3 originPos, float originHealth,
                               long expiresAt, int cooldownTicks) {}

    private record VeinState(int skillLevel, long expiresAt) {}

    private SkillNewActivesHelper() {}

    static void clearAll() {
        CLEAVE_ECHOES.clear();
        THUNDER_ZONES.clear();
        SANCTUARIES.forEach(SanctuaryDome::fadeVisual);
        SANCTUARIES.clear();
        BLINK_ANCHORS.clear();
        VEIN_STATES.clear();
        VEIN_REENTRANCY_GUARD.clear();
    }

    static void clearPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        CLEAVE_ECHOES.removeIf(e -> e.ownerId().equals(id));
        THUNDER_ZONES.removeIf(z -> z.ownerId.equals(id));
        removeSanctuaries(id);
        BLINK_ANCHORS.remove(id);
        VEIN_STATES.remove(id);
        VEIN_REENTRANCY_GUARD.remove(id);
    }

    static void clearSingle(ServerPlayer player, String skillId) {
        UUID id = player.getUUID();
        switch (skillId) {
            case ModSkills.ASTRAL_CLEAVE -> CLEAVE_ECHOES.removeIf(e -> e.ownerId().equals(id));
            case ModSkills.THUNDER_JUDGMENT -> THUNDER_ZONES.removeIf(z -> z.ownerId.equals(id));
            case ModSkills.STARLIGHT_SANCTUARY -> removeSanctuaries(id);
            case ModSkills.VOID_BLINK -> BLINK_ANCHORS.remove(id);
            case ModSkills.VEIN_RESONANCE -> VEIN_STATES.remove(id);
            default -> {}
        }
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

    static boolean isVeinActive(UUID playerId, long gameTime) {
        VeinState state = VEIN_STATES.get(playerId);
        return state != null && gameTime < state.expiresAt();
    }

    // ===== 1. 裂空星斩 (astral_cleave) =====

    static boolean castAstralCleave(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 origin = player.getEyePosition().subtract(0, 0.2, 0);
        Vec3 dir = player.getLookAngle().normalize();
        boolean holdingTool = SkillModSynergyHandler.isHoldingUniversalTool(player);
        float baseDamage = (10.0F + 5.5F * (level - 1)) * (holdingTool ? 1.25F : 1.0F);
        double range = holdingTool ? 12.0 : 10.0;
        double width = holdingTool ? 2.6 : 2.1;

        boolean killed = sweepCleaveBeam(world, player, origin, dir, range, width, baseDamage, false);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.35F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.6F);

        if (level >= 3) {
            CLEAVE_ECHOES.add(new CleaveEcho(world, player.getUUID(),
                    origin.add(dir.scale(range)), dir.scale(-1.0), baseDamage * 0.65F,
                    world.getGameTime() + 8));
            if (killed) {
                // 延迟于常规 CD 启动后由调用方扣 CD，这里用标识或下一帧减少；直接用标记稍后回调不如先调度
                CLEAVE_ECHOES.add(new CleaveEcho(world, player.getUUID(), origin, dir, -1.0F, world.getGameTime() + 1));
            }
        }
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.astral_cleave"), true);
        return true;
    }

    private static boolean sweepCleaveBeam(ServerLevel world, ServerPlayer player, Vec3 origin, Vec3 dir,
                                           double range, double width, float damage, boolean echo) {
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
                target.hurt(world.damageSources().indirectMagic(player, player), damage);
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

        long now = world.getGameTime();
        long strikeAt = now + 16;
        long expiresAt = level >= 3 ? strikeAt + 100 : strikeAt + 2;
        THUNDER_ZONES.add(new ThunderZone(world, player.getUUID(), center, level, strikeAt, expiresAt));

        SkillVfx.ring(world, center.add(0, 0.22, 0), 3.5 + level, 0, 16, SkillVfx.Tint.THUNDER);
        SkillVfx.spiral(world, center, 1.2, 1.7, 0, SkillVfx.Tint.THUNDER);
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

    // ===== 4. 虚空折跃 (void_blink) =====

    static boolean handleVoidBlink(ServerPlayer player, PlayerSkillData data, int level, int adjustedCooldown) {
        ServerLevel world = player.serverLevel();
        long now = world.getGameTime();
        BlinkAnchor anchor = BLINK_ANCHORS.remove(player.getUUID());
        if (level >= 3 && anchor != null && now < anchor.expiresAt() && anchor.level() == world) {
            // 触发二段时空回溯
            Vec3 cur = player.position();
            SkillVfx.portal(world, cur, false);
            player.teleportTo(anchor.originPos().x, anchor.originPos().y, anchor.originPos().z);
            player.resetFallDistance();
            if (player.getHealth() < anchor.originHealth()) {
                player.setHealth(Math.min(player.getMaxHealth(), anchor.originHealth()));
            }
            SkillVfx.portal(world, anchor.originPos(), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.4F);
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.void_blink_recall"), true);
            data.startCooldown(ModSkills.VOID_BLINK, adjustedCooldown);
            SkillManager.sync(player);
            return false; // 冷却已手动结算
        }

        double maxDist = 10.0 + (level - 1) * 4.0;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 dest = findSafeBlinkSpot(world, player, eye, look, maxDist);

        Vec3 oldPos = player.position();
        float oldHealth = player.getHealth();

        SkillVfx.portal(world, oldPos, false);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, true));
        SkillVfx.portal(world, dest, true);
        world.playSound(null, dest.x, dest.y, dest.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.15F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.void_blink"), true);

        if (level >= 3) {
            BLINK_ANCHORS.put(player.getUUID(), new BlinkAnchor(world, oldPos, oldHealth, now + 60, adjustedCooldown));
            return false; // 3 秒回溯窗结束后再开启冷却
        }
        return true;
    }

    private static Vec3 findSafeBlinkSpot(ServerLevel world, ServerPlayer player, Vec3 eye, Vec3 look, double maxDist) {
        Vec3 targetEnd = eye.add(look.scale(maxDist));
        BlockHitResult hit = world.clip(new ClipContext(eye, targetEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = hit.getType() == BlockHitResult.Type.MISS ? maxDist : Math.max(0.5, hit.getLocation().distanceTo(eye) - 0.8);
        for (double d = reach; d >= 1.0; d -= 0.6) {
            Vec3 candidate = eye.add(look.scale(d));
            BlockPos feet = BlockPos.containing(candidate.x, candidate.y - 0.5, candidate.z);
            BlockPos head = feet.above();
            if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                    && world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
                return new Vec3(candidate.x, feet.getY(), candidate.z);
            }
        }
        return player.position();
    }

    // ===== 5. 星脉连锁 (vein_resonance) =====

    static boolean castVeinResonance(ServerPlayer player, int level) {
        long duration = 200L + level * 60L;
        VEIN_STATES.put(player.getUUID(), new VeinState(level, player.serverLevel().getGameTime() + duration));
        SkillVfx.spiral(player.serverLevel(), player.position().add(0, 0.22, 0), 1.0, 1.08, 0, SkillVfx.Tint.VEIN);
        SkillVfx.sparks(player.serverLevel(), player.position().add(0, 0.7, 0), 0.52, 0.40, 14, SkillVfx.Tint.VEIN);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.3F);
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.vein_resonance",
                duration / 20), true);
        return true;
    }

    /** 在玩家挖掘自然矿石或原木时尝试触发星脉连锁采集。 */
    static void tryChainVeinMine(ServerPlayer player, BlockPos originPos, BlockState originState) {
        UUID id = player.getUUID();
        if (VEIN_REENTRANCY_GUARD.contains(id)) return;
        VeinState state = VEIN_STATES.get(id);
        if (state == null || player.serverLevel().getGameTime() >= state.expiresAt()) {
            VEIN_STATES.remove(id);
            return;
        }
        boolean isOre = SkillCombatTracker.isOre(originState);
        boolean isLog = originState.is(BlockTags.LOGS);
        if (!isOre && !isLog) return;

        int maxBlocks = switch (state.skillLevel()) {
            case 1 -> 12;
            case 2 -> 24;
            default -> 40;
        };

        ServerLevel world = player.serverLevel();
        ItemStack tool = player.getMainHandItem();
        Block targetBlock = originState.getBlock();

        VEIN_REENTRANCY_GUARD.add(id);
        try {
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(originPos);
            visited.add(originPos);
            int broken = 0;

            while (!queue.isEmpty() && broken < maxBlocks) {
                BlockPos current = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = current.offset(dx, dy, dz);
                            if (!visited.add(next) || !world.isLoaded(next)) continue;
                            BlockState nextState = world.getBlockState(next);
                            if (!nextState.is(targetBlock)) continue;
                            if (SkillCombatTracker.isPlayerPlaced(player, next)) continue;

                            if (!player.isCreative() && tool.isDamageableItem()
                                    && tool.getMaxDamage() - tool.getDamageValue() <= 1) {
                                return;
                            }

                            BlockEntity be = world.getBlockEntity(next);
                            List<ItemStack> drops = Block.getDrops(nextState, world, next, be, player, tool);
                            if (state.skillLevel() >= 3 && isOre && world.random.nextFloat() < 0.20F) {
                                List<ItemStack> bonus = Block.getDrops(nextState, world, next, be, player, tool);
                                drops = new ArrayList<>(drops);
                                drops.addAll(bonus);
                            }

                            world.destroyBlock(next, false, player);
                            for (ItemStack drop : drops) {
                                if (!SkillModSynergyHandler.routeDropToPlayer(player, drop, false)) {
                                    Block.popResource(world, next, drop);
                                }
                            }
                            if (!player.isCreative() && tool.isDamageableItem()) {
                                tool.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                            }
                            SkillCombatTracker.recordMinedBlock(player, nextState);
                            SkillVfx.sparks(world, Vec3.atCenterOf(next), 0.17, 0.24, 3, SkillVfx.Tint.VEIN);

                            broken++;
                            queue.add(next);
                            if (broken >= maxBlocks) break;
                        }
                        if (broken >= maxBlocks) break;
                    }
                    if (broken >= maxBlocks) break;
                }
            }
            if (state.skillLevel() >= 3 && broken > 0) {
                player.giveExperiencePoints(broken * 2);
            }
        } finally {
            VEIN_REENTRANCY_GUARD.remove(id);
        }
    }

    // ===== 6. 炼金脉冲 (arcane_transmute) =====

    static boolean castArcaneTransmute(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        int maxCount = switch (level) {
            case 1 -> 8;
            case 2 -> 16;
            default -> 32;
        };

        // 优先检查副手，其次主背包
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
            if (!SkillModSynergyHandler.routeDropToPlayer(player, produced, false)) {
                if (!player.getInventory().add(produced)) {
                    player.drop(produced, false);
                }
            }
            outCount -= batch;
        }

        // 修复主手工具耐久
        ItemStack main = player.getMainHandItem();
        if (main.isDamageableItem() && main.isDamaged()) {
            double repairRatio = level == 1 ? 0.05 : level == 2 ? 0.10 : 0.18;
            int repair = Math.max(2, (int) Math.round(main.getMaxDamage() * repairRatio));
            main.setDamageValue(Math.max(0, main.getDamageValue() - repair));
        }

        SkillVfx.ring(world, player.position().add(0, 0.20, 0), 1.0, Math.PI / 8.0, 12, SkillVfx.Tint.ALCHEMY);
        SkillVfx.spiral(world, player.position().add(0, 0.25, 0), 0.63, 1.45, 0, SkillVfx.Tint.ALCHEMY);
        SkillVfx.sparks(world, player.position().add(0, 0.85, 0), 0.34, 0.46, 14, SkillVfx.Tint.ALCHEMY);
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
        long gameTime = server.overworld().getGameTime();

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
                        if (echo.damage() < 0) {
                            PlayerSkillData data = SkillManager.data(owner);
                            long rem = data.cooldownRemainingMillis(ModSkills.ASTRAL_CLEAVE);
                            if (rem > 0) {
                                data.startCooldown(ModSkills.ASTRAL_CLEAVE, (int) (rem * 0.6 / 50L));
                                SkillManager.sync(owner);
                            }
                        } else {
                            boolean killed = sweepCleaveBeam(echo.level(), owner, echo.origin(),
                                    echo.direction(), 11.0, 2.5, echo.damage(), true);
                            if (killed) {
                                PlayerSkillData data = SkillManager.data(owner);
                                long rem = data.cooldownRemainingMillis(ModSkills.ASTRAL_CLEAVE);
                                if (rem > 0) {
                                    data.startCooldown(ModSkills.ASTRAL_CLEAVE, (int) (rem * 0.6 / 50L));
                                    SkillManager.sync(owner);
                                }
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
                    float dmg = 16.0F + (zone.skillLevel - 1) * 9.0F;
                    AABB box = new AABB(zone.center, zone.center).inflate(radius);
                    for (LivingEntity target : zone.level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> SkillActiveExecutor.canAffect(owner, e))) {
                        target.hurt(zone.level.damageSources().indirectMagic(owner, owner), dmg);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                    }
                    SkillVfx.ring(zone.level, zone.center.add(0, 0.30, 0), radius, Math.PI / 12.0, 16, SkillVfx.Tint.THUNDER);
                    SkillVfx.ring(zone.level, zone.center.add(0, 0.56, 0), radius * 0.5, 0, 8, SkillVfx.Tint.THUNDER);
                    SkillVfx.sparks(zone.level, zone.center.add(0, 0.5, 0), 1.0, 0.68, 28, SkillVfx.Tint.THUNDER);
                } else if (zone.struck && zone.skillLevel >= 3 && t % 20 == 0) {
                    SkillVfx.ring(zone.level, zone.center.add(0, 0.25, 0), radius, t * 0.04, 10, SkillVfx.Tint.THUNDER);
                    AABB box = new AABB(zone.center, zone.center).inflate(radius);
                    for (LivingEntity target : zone.level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> SkillActiveExecutor.canAffect(owner, e))) {
                        target.hurt(zone.level.damageSources().indirectMagic(owner, owner), 6.0F);
                    }
                } else if (!zone.struck && t % 4 == 0) {
                    double progress = 1.0 - Math.clamp((zone.strikeAt - t) / 16.0, 0.0, 1.0);
                    SkillVfx.ring(zone.level, zone.center.add(0, 0.25 + progress * 0.45, 0),
                            radius * (1.0 - progress * 0.56), t * 0.08, 8, SkillVfx.Tint.THUNDER);
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
                            Vec3 push = enemy.position().subtract(dome.center()).normalize().scale(1.4);
                            enemy.setDeltaMovement(push.x, 0.45, push.z);
                            enemy.hurtMarked = true;
                            enemy.igniteForSeconds(4);
                        }
                        SkillVfx.ring(dome.level(), dome.center().add(0, 0.35, 0), dome.radius() + 1, 0, 16,
                                SkillVfx.Tint.SANCTUARY);
                        SkillVfx.sparks(dome.level(), dome.center().add(0, 0.45, 0), 1.15, 0.5, 22, SkillVfx.Tint.ALCHEMY);
                    }
                    continue;
                }
                // 弹开敌方投射物
                AABB box = new AABB(dome.center(), dome.center()).inflate(dome.radius());
                for (Projectile proj : dome.level().getEntitiesOfClass(Projectile.class, box,
                        p -> p.isAlive() && (p.getOwner() == null || !p.getOwner().getUUID().equals(dome.ownerId())))) {
                    Vec3 away = proj.position().subtract(dome.center());
                    if (away.lengthSqr() > 0.01) {
                        proj.setDeltaMovement(away.normalize().scale(1.2));
                        proj.hurtMarked = true;
                    }
                }
                if (t % 20 == 0) {
                    for (Player ally : dome.level().getEntitiesOfClass(Player.class, box,
                            p -> p.isAlive() && (p.getUUID().equals(dome.ownerId()) || p.isAlliedTo(owner)))) {
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

        // 4. 虚空折跃回溯过期 -> 触发常规冷却
        if (!BLINK_ANCHORS.isEmpty()) {
            for (Map.Entry<UUID, BlinkAnchor> entry : BLINK_ANCHORS.entrySet()) {
                BlinkAnchor anchor = entry.getValue();
                if (anchor.level().getGameTime() >= anchor.expiresAt()) {
                    if (BLINK_ANCHORS.remove(entry.getKey(), anchor)) {
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
