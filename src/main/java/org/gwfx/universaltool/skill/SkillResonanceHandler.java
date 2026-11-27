package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.Skill3dEffectPayload;
import org.gwfx.universaltool.network.Skill3dEffectPayload.EffectKind;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 四大星座 2 / 4 / 6 星套装共鸣处理器。
 *
 * <ul>
 *   <li>破军（2/4/6）：连击延长6s+15%伤 / 击杀减全CD 2.5s+4.5格星波 / 第6击或破军主动天降3D星刃+25%穿甲</li>
 *   <li>玄武（2/4/6）：50%击退抗性+25%受疗 / 每6s叠护盾至12 / 合计90%击退抗性+15%减伤+破盾3D震荡波回15%血</li>
 *   <li>天枢（2/4/6）：+12%移速-18%CD / 施放后4s迅捷II与20%减伤 / 再-15%CD+位移牵引后下击8%当前生命穿刺</li>
 *   <li>巧匠（2/4/6）：+1.5触及+25%掘速 / 25%额外+20经验与额外掉落 / 50%免损+40%经验+12格炉台与作物+100%加速</li>
 * </ul>
 */
public final class SkillResonanceHandler {
    private static final ResourceLocation XUANWU_KB_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "res_xuanwu_knockback");
    private static final ResourceLocation TIANSHU_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "res_tianshu_speed");
    private static final ResourceLocation QIAOJIANG_REACH_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "res_qiaojiang_reach");
    private static final ResourceLocation QIAOJIANG_MINE_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "res_qiaojiang_mine");

    private static final Map<UUID, Long> TIANSHU_EVASION_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TIANSHU_PIERCE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> POJUN_STRIKE_COUNT = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_ABSORPTION = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> XUANWU_AEGIS_CD = new ConcurrentHashMap<>();

    private SkillResonanceHandler() {}

    static void clearAll() {
        TIANSHU_EVASION_UNTIL.clear();
        TIANSHU_PIERCE_UNTIL.clear();
        POJUN_STRIKE_COUNT.clear();
        LAST_ABSORPTION.clear();
        XUANWU_AEGIS_CD.clear();
    }

    static void clearTransient(UUID playerId) {
        TIANSHU_EVASION_UNTIL.remove(playerId);
        TIANSHU_PIERCE_UNTIL.remove(playerId);
        POJUN_STRIKE_COUNT.remove(playerId);
        LAST_ABSORPTION.remove(playerId);
        XUANWU_AEGIS_CD.remove(playerId);
    }

    /** 应用或清理 2/4/6 星共鸣属性修饰符。 */
    static void applyResonanceAttributes(ServerPlayer player) {
        PlayerSkillData data = SkillManager.data(player);
        boolean systemOn = SkillManager.isEnabled(player.serverLevel());

        // 玄武 2 星 50% 击退抗性 + 玄武 6 星额外 40% 击退抗性 = 总计 90% 击退抗性
        int xuanwu = systemOn ? data.resonanceTier(SkillConstellation.XUANWU) : 0;
        var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(XUANWU_KB_ID);
            if (xuanwu >= 1) {
                double kbResist = xuanwu >= 3 ? 0.90D : 0.50D;
                kb.addTransientModifier(new AttributeModifier(
                        XUANWU_KB_ID, kbResist, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        int tianshu = systemOn ? data.resonanceTier(SkillConstellation.TIANSHU) : 0;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(TIANSHU_SPEED_ID);
            if (tianshu >= 1) {
                speed.addTransientModifier(new AttributeModifier(
                        TIANSHU_SPEED_ID, 0.12D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }

        int qiaojiang = systemOn ? data.resonanceTier(SkillConstellation.QIAOJIANG) : 0;
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null) {
            reach.removeModifier(QIAOJIANG_REACH_ID);
            if (qiaojiang >= 1) {
                reach.addTransientModifier(new AttributeModifier(
                        QIAOJIANG_REACH_ID, 1.5D, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        var mineSpeed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (mineSpeed != null) {
            mineSpeed.removeModifier(QIAOJIANG_MINE_ID);
            if (qiaojiang >= 1) {
                mineSpeed.addTransientModifier(new AttributeModifier(
                        QIAOJIANG_MINE_ID, 0.25D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    static void clearAllResonances(ServerPlayer player) {
        clearTransient(player.getUUID());
        var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(XUANWU_KB_ID);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(TIANSHU_SPEED_ID);
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null) reach.removeModifier(QIAOJIANG_REACH_ID);
        var mineSpeed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (mineSpeed != null) mineSpeed.removeModifier(QIAOJIANG_MINE_ID);
    }

    static void tickResonances(ServerPlayer player, long gameTime) {
        PlayerSkillData data = SkillManager.data(player);
        int xuanwu = data.resonanceTier(SkillConstellation.XUANWU);
        // 玄武 4 星：每 6 秒（120t）自动叠加 2 点吸收护盾至 12 点
        if (xuanwu >= 2 && player.tickCount % 120 == 0) {
            float current = player.getAbsorptionAmount();
            if (current < 12.0F) {
                player.setAbsorptionAmount(Math.min(12.0F, current + 2.0F));
            }
        }
        // 玄武 6 星：检测吸收护盾被击破瞬间
        if (xuanwu >= 3) {
            float curAbs = player.getAbsorptionAmount();
            float prevAbs = LAST_ABSORPTION.getOrDefault(player.getUUID(), 0.0F);
            LAST_ABSORPTION.put(player.getUUID(), curAbs);
            if (prevAbs > 0.5F && curAbs <= 0.01F && player.isAlive()) {
                long nowMs = System.currentTimeMillis();
                Long cdUntil = XUANWU_AEGIS_CD.get(player.getUUID());
                if (cdUntil == null || nowMs >= cdUntil) {
                    XUANWU_AEGIS_CD.put(player.getUUID(), nowMs + 12_000L);
                    triggerXuanwuAegisBurst(player);
                }
            }
        }

        // 巧匠 6 星（天工开物）：12 格内熔炉/高炉/烟熏炉/酿造台与作物加速 +100%
        if (data.resonanceTier(SkillConstellation.QIAOJIANG) >= 3) {
            tickCraftsmanWorkshop(player);
        }
    }

    private static void triggerXuanwuAegisBurst(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        player.heal(player.getMaxHealth() * 0.15F);
        for (LivingEntity enemy : world.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0D), e -> SkillActiveExecutor.canAffect(player, e))) {
            Vec3 diff = enemy.position().subtract(player.position());
            Vec3 push = diff.lengthSqr() > 1.0E-4 ? diff.normalize().scale(1.35D) : new Vec3(0, 0, 1.35D);
            enemy.setDeltaMovement(push.x, 0.42D, push.z);
            enemy.hurtMarked = true;
            enemy.hurt(world.damageSources().indirectMagic(player, player),
                    SkillActiveExecutor.pctDamage(enemy, 6.0F, 0.06F, 0.0F, 0.0F));
        }
        Skill3dEffectPayload.spawn(world, EffectKind.RESONANCE_AEGIS, player.position(), Vec3.ZERO, 5.2F, 16, 0x48E8B5);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private static void tickCraftsmanWorkshop(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        BlockPos origin = player.blockPosition();
        // 每 tick 为 12 格内熔炉/酿造台追加 1 次 tick（真实 +100% 加速），农作物每 20 tick 采样一次
        int r = 12;
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var chunk = world.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.getBlockPos().distSqr(origin) > 144.0D) continue;
                    BlockState bs = be.getBlockState();
                    if (be instanceof AbstractFurnaceBlockEntity furnace) {
                        AbstractFurnaceBlockEntity.serverTick(world, be.getBlockPos(), bs, furnace);
                    } else if (be instanceof BrewingStandBlockEntity stand) {
                        BrewingStandBlockEntity.serverTick(world, be.getBlockPos(), bs, stand);
                    }
                }
            }
        }
        if (player.tickCount % 20 == 0) {
            for (int i = 0; i < 6; i++) {
                BlockPos sample = origin.offset(
                        world.random.nextInt(r * 2 + 1) - r,
                        world.random.nextInt(7) - 3,
                        world.random.nextInt(r * 2 + 1) - r);
                if (!world.isLoaded(sample)) continue;
                BlockState state = world.getBlockState(sample);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                    state.randomTick(world, sample, world.random);
                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            sample.getX() + 0.5, sample.getY() + 0.5, sample.getZ() + 0.5,
                            2, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
    }

    /** 破军 2 星：连击窗口由 60t 延长至 120t（6 秒）。 */
    static int momentumWindowTicks(ServerPlayer player) {
        return SkillManager.data(player).resonanceTier(SkillConstellation.POJUN) >= 1 ? 120 : SkillManager.MOMENTUM_WINDOW_TICKS;
    }

    /** 天枢 2 星(-18%) + 天枢 6 星(-15%) + 时空锚感常驻减 CD（最低保留 35%）。 */
    static int adjustCooldownTicks(ServerPlayer player, int baseTicks) {
        if (baseTicks <= 0) return 0;
        PlayerSkillData data = SkillManager.data(player);
        float mult = 1.0F;
        int tianshu = data.resonanceTier(SkillConstellation.TIANSHU);
        if (tianshu >= 1) mult -= 0.18F;
        if (tianshu >= 3) mult -= 0.15F;
        int spatial = SkillPassiveHandler.equippedLevel(player, ModSkills.SPATIAL_ATTUNEMENT);
        if (spatial > 0) {
            mult -= 0.05F * spatial;
        }
        mult = Math.max(0.35F, mult);
        return Math.max(10, Math.round(baseTicks * mult));
    }

    /** 施放主动技能后触发：天枢 4/6 星与破军 6 星共鸣。 */
    static void onActiveSkillCast(ServerPlayer player, String skillId) {
        PlayerSkillData data = SkillManager.data(player);
        int tianshu = data.resonanceTier(SkillConstellation.TIANSHU);
        long now = player.serverLevel().getGameTime();
        if (tianshu >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false, true));
            TIANSHU_EVASION_UNTIL.put(player.getUUID(), now + 80);
        }
        if (tianshu >= 3 && SkillActiveExecutor.isMobilityOrPullSkill(skillId)) {
            TIANSHU_PIERCE_UNTIL.put(player.getUUID(), now + 100);
        }
        if (data.resonanceTier(SkillConstellation.POJUN) >= 3 && SkillActiveExecutor.isPojunActive(skillId)) {
            LivingEntity focus = SkillActiveExecutor.findCrosshairTarget(player, 18.0D);
            if (focus != null) {
                triggerPojunSkyBlade(player, focus);
            }
        }
    }

    /** 破军 4 星：击杀缩短全部主动 CD 2.5s + 4.5 格星辉斩波（含 6% 最大生命值伤）。 */
    static void onHostileKill(ServerPlayer player, LivingEntity killed) {
        PlayerSkillData data = SkillManager.data(player);
        if (data.resonanceTier(SkillConstellation.POJUN) < 2) {
            return;
        }
        data.reduceAllCooldownsMillis(2500L);
        ServerLevel world = player.serverLevel();
        world.sendParticles(ParticleTypes.SWEEP_ATTACK, killed.getX(), killed.getY() + 0.8, killed.getZ(),
                5, 0.45, 0.2, 0.45, 0.0);
        for (LivingEntity nearby : world.getEntitiesOfClass(LivingEntity.class,
                killed.getBoundingBox().inflate(4.5), e -> SkillActiveExecutor.canAffect(player, e))) {
            nearby.hurt(world.damageSources().indirectMagic(player, player),
                    SkillActiveExecutor.pctDamage(nearby, 4.0F, 0.06F, 0.0F, 0.0F));
        }
    }

    static void onIncomingAttacker(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        PlayerSkillData data = SkillManager.data(attacker);
        int pojun = data.resonanceTier(SkillConstellation.POJUN);
        if (pojun >= 1) {
            event.setAmount(event.getAmount() * 1.15F);
        }
        if (pojun >= 3 && event.getEntity() instanceof LivingEntity target && target != attacker) {
            // 破军 6 星：25% 护甲穿透加成 + 每第 6 击天降 3D 星刃
            float armorBonus = target.getArmorValue() * 0.25F;
            event.setAmount(event.getAmount() + armorBonus);
            int hits = POJUN_STRIKE_COUNT.getOrDefault(attacker.getUUID(), 0) + 1;
            if (hits >= 6) {
                POJUN_STRIKE_COUNT.put(attacker.getUUID(), 0);
                triggerPojunSkyBlade(attacker, target);
            } else {
                POJUN_STRIKE_COUNT.put(attacker.getUUID(), hits);
            }
        }
        // 天枢 6 星（穿梭星轨）：位移/牵引后下一次攻击附加 8% 目标当前生命值穿刺伤
        Long pierceUntil = TIANSHU_PIERCE_UNTIL.get(attacker.getUUID());
        if (pierceUntil != null && event.getEntity() instanceof LivingEntity target && target != attacker) {
            if (attacker.serverLevel().getGameTime() < pierceUntil) {
                TIANSHU_PIERCE_UNTIL.remove(attacker.getUUID());
                float bonus = SkillActiveExecutor.pctDamage(target, 4.0F, 0.0F, 0.08F, 0.0F);
                event.setAmount(event.getAmount() + bonus);
                attacker.serverLevel().sendParticles(ParticleTypes.END_ROD,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        12, 0.3, 0.3, 0.3, 0.06);
            } else {
                TIANSHU_PIERCE_UNTIL.remove(attacker.getUUID());
            }
        }
    }

    private static void triggerPojunSkyBlade(ServerPlayer attacker, LivingEntity target) {
        ServerLevel world = attacker.serverLevel();
        Vec3 pos = target.position();
        Skill3dEffectPayload.spawn(world, EffectKind.RESONANCE_BLADE, pos, Vec3.ZERO, 3.2F, 16, 0xFF6B4A);
        for (LivingEntity enemy : world.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(3.0D), e -> SkillActiveExecutor.canAffect(attacker, e))) {
            enemy.hurt(world.damageSources().indirectMagic(attacker, attacker),
                    SkillActiveExecutor.pctDamage(enemy, 8.0F, 0.0F, 0.0F, 0.10F));
        }
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    static void onIncomingVictim(ServerPlayer victim, LivingIncomingDamageEvent event) {
        PlayerSkillData data = SkillManager.data(victim);
        Long until = TIANSHU_EVASION_UNTIL.get(victim.getUUID());
        if (until != null) {
            if (victim.serverLevel().getGameTime() < until) {
                event.setAmount(event.getAmount() * 0.80F);
            } else {
                TIANSHU_EVASION_UNTIL.remove(victim.getUUID());
            }
        }
        if (data.resonanceTier(SkillConstellation.XUANWU) >= 3) {
            event.setAmount(event.getAmount() * 0.85F);
        }
    }

    static void onLivingHeal(ServerPlayer player, LivingHealEvent event) {
        if (SkillManager.data(player).resonanceTier(SkillConstellation.XUANWU) >= 1) {
            event.setAmount(event.getAmount() * 1.25F);
        }
    }

    /** 巧匠 4 星：挖掘矿石/收获成熟作物 25% 概率额外 +20 技能经验；6 星额外减免耐久损耗。 */
    static void onCraftsmanHarvest(ServerPlayer player, BlockPos pos) {
        PlayerSkillData data = SkillManager.data(player);
        int qiaojiang = data.resonanceTier(SkillConstellation.QIAOJIANG);
        if (qiaojiang >= 2 && player.getRandom().nextFloat() < 0.25F) {
            SkillCombatTracker.award(player, 20, false);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.04);
            player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }

    /** 巧匠 4 星：开采自然矿石或收获成熟作物（非精准采集且非玩家放置）25% 概率额外掉 1 份产物；6 星：破坏需耗耐久方块时 50% 概率返还 1 点耐久。 */
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        int qiaojiang = SkillManager.data(player).resonanceTier(SkillConstellation.QIAOJIANG);
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        if (qiaojiang >= 2 && !event.getDrops().isEmpty()
                && !SkillCombatTracker.wasPlayerPlacedBreak(player, pos)
                && (SkillCombatTracker.isOre(state) || SkillCombatTracker.isMatureCrop(state))) {
            var enchReg = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            boolean silkTouch = event.getTool().getEnchantmentLevel(enchReg.getOrThrow(Enchantments.SILK_TOUCH)) > 0;
            if (!silkTouch && player.getRandom().nextFloat() < 0.25F) {
                ItemEntity first = event.getDrops().get(0);
                ItemStack firstStack = first.getItem();
                if (!firstStack.isEmpty() && !firstStack.is(state.getBlock().asItem())) {
                    ItemStack bonus = firstStack.copyWithCount(1);
                    event.getDrops().add(new ItemEntity(event.getLevel(), first.getX(), first.getY(), first.getZ(), bonus));
                }
            }
        }
        if (qiaojiang >= 3
                && state.getDestroySpeed(event.getLevel(), pos) > 0.0F
                && player.getRandom().nextFloat() < 0.50F) {
            ItemStack held = player.getMainHandItem();
            if (held.isDamageableItem() && held.isDamaged()) {
                held.setDamageValue(Math.max(0, held.getDamageValue() - 1));
            }
        }
    }

    /** 巧匠 6 星武器攻击 50% 概率不耗耐久；全来源经验 +40%。 */
    static void tryCraftsmanDurableCombat(ServerPlayer player) {
        if (SkillManager.data(player).resonanceTier(SkillConstellation.QIAOJIANG) >= 3
                && player.getRandom().nextFloat() < 0.50F) {
            ItemStack held = player.getMainHandItem();
            if (held.isDamageableItem() && held.isDamaged()) {
                held.setDamageValue(Math.max(0, held.getDamageValue() - 1));
            }
        }
    }

    static float skillXpMultiplier(ServerPlayer player) {
        float mult = 1.0F;
        if (SkillManager.data(player).resonanceTier(SkillConstellation.QIAOJIANG) >= 3) {
            mult += 0.40F;
        }
        int vf = SkillPassiveHandler.equippedLevel(player, ModSkills.VOID_FORTUNE);
        if (vf > 0) {
            mult += vf == 1 ? 0.10F : (vf == 2 ? 0.20F : 0.35F);
        }
        return mult;
    }

    public static boolean isEntityValid(Entity e) {
        return e != null && e.isAlive();
    }
}
