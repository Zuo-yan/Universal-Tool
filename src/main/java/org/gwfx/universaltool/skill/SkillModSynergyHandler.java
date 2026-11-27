package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.item.UniversalToolItem;

/**
 * 模组七大玩法与技能系统的深度联动入口（聚合台、抽奖机、克莱因瓶、时空法典、口袋界域、凤凰培养仓、万能工具）。
 *
 * <p>当技能模块被管理员关闭或玩家未装配对应技能时，所有方法均保持零副作用与原逻辑一致。
 */
public final class SkillModSynergyHandler {
    private SkillModSynergyHandler() {}

    // ===== 1. 万能工具与侵晨联动（兵刃共鸣 tool_resonance） =====

    /**
     * 获取手持万能工具时的材质阶梯（0 = 非万能工具；1=木, 2=石, 3=铁, 4=金, 5=钻, 6=下界合金）。
     */
    public static int universalToolTierRank(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof UniversalToolItem)) {
            return 0;
        }
        if (stack.is(ModItems.NETHERITE_UNIVERSAL_TOOL.get())) return 6;
        if (stack.is(ModItems.DIAMOND_UNIVERSAL_TOOL.get())) return 5;
        if (stack.is(ModItems.GOLDEN_UNIVERSAL_TOOL.get())) return 4;
        if (stack.is(ModItems.IRON_UNIVERSAL_TOOL.get())) return 3;
        if (stack.is(ModItems.STONE_UNIVERSAL_TOOL.get())) return 2;
        return 1;
    }

    /**
     * 获取手持本模组武器/工具时的综合材质阶梯（万能工具 1~6 阶、侵晨剑 7 阶、其余模组可耐久装备 5 阶）。
     */
    public static int modWeaponTierRank(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int toolRank = universalToolTierRank(stack);
        if (toolRank > 0) return toolRank;
        if (stack.is(ModItems.QINCHEN_SWORD.get())) return 7;
        var key = stack.getItemHolder().unwrapKey().orElse(null);
        if (key != null && UniversalToolMod.MODID.equals(key.location().getNamespace()) && stack.isDamageableItem()) {
            return 5;
        }
        return 0;
    }

    public static boolean isHoldingUniversalTool(ServerPlayer player) {
        return modWeaponTierRank(player.getMainHandItem()) > 0
                || modWeaponTierRank(player.getOffhandItem()) > 0;
    }

    // ===== 2. 凤凰计划联动（phoenix_ember） =====

    /** 当生化培养仓完成量子意识传输复活时调用。 */
    public static void onPhoenixRebirth(ServerPlayer player) {
        if (player == null || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        data.incrementStat("phoenix_rebirths", 1, skill -> SkillManager.notifySkillUnlocked(player, skill));
        SkillCombatTracker.award(player, 200, true);

        int emberLevel = SkillPassiveHandler.equippedLevel(player, ModSkills.PHOENIX_EMBER);
        if (emberLevel > 0) {
            data.clearAllCooldowns();
            float shield = 6.0F + 4.0F * emberLevel;
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shield));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true, true));
            player.serverLevel().sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.7, 0.8, 0.7, 0.08);
            SkillManager.sync(player);
        }
    }

    // ===== 3. 时空法典 & 口袋界域联动（spatial_attunement） =====

    public static boolean isInPocketOrRealmDimension(ServerPlayer player) {
        if (player == null) return false;
        String path = player.serverLevel().dimension().location().getPath();
        return UniversalToolMod.MODID.equals(player.serverLevel().dimension().location().getNamespace())
                && (path.startsWith("realm/") || path.startsWith("private_pocket/"));
    }

    /** 时空法典/卷轴蓄力吟唱缩短（返回调整后 tick 数）。 */
    public static int adjustTeleportChargeTicks(ServerPlayer player, int defaultTicks) {
        if (player == null || !SkillManager.isEnabled(player.serverLevel())) {
            return defaultTicks;
        }
        int lv = SkillPassiveHandler.equippedLevel(player, ModSkills.SPATIAL_ATTUNEMENT);
        if (lv <= 0) return defaultTicks;
        float mult = switch (lv) {
            case 1 -> 0.75F;
            case 2 -> 0.55F;
            default -> 0.40F;
        };
        return Math.max(5, Math.round(defaultTicks * mult));
    }

    /** 时空法典冷却缩短（返回调整后 tick 数）。 */
    public static int adjustTeleportCooldownTicks(ServerPlayer player, int defaultTicks) {
        if (player == null || !SkillManager.isEnabled(player.serverLevel())) {
            return defaultTicks;
        }
        int lv = SkillPassiveHandler.equippedLevel(player, ModSkills.SPATIAL_ATTUNEMENT);
        if (lv <= 0) return defaultTicks;
        float mult = switch (lv) {
            case 1 -> 0.80F;
            case 2 -> 0.65F;
            default -> 0.50F;
        };
        return Math.max(10, Math.round(defaultTicks * mult));
    }

    /** 时空法典/卷轴传送完成回调。 */
    public static void onTeleportCompleted(ServerPlayer player) {
        if (player == null || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        data.incrementStat("teleports", 1, skill -> SkillManager.notifySkillUnlocked(player, skill));
        SkillCombatTracker.award(player, 15, true);

        int lv = SkillPassiveHandler.equippedLevel(player, ModSkills.SPATIAL_ATTUNEMENT);
        if (lv > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false, true));
            if (lv >= 3) {
                data.reduceAllCooldownsByRatio(0.50F);
                player.serverLevel().sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.7, 0.5, 0.15);
            }
            SkillManager.sync(player);
        }
    }

    /** 玩家踏入星门（口袋界域/私人空间）时触发。 */
    public static void onPocketRealmEntered(ServerPlayer player) {
        if (player == null || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        data.incrementStat("pocket_realm_visits", 1, skill -> SkillManager.notifySkillUnlocked(player, skill));
        if (data.addDiscovery("synergy:first_pocket_realm")) {
            SkillCombatTracker.award(player, 150, true);
        } else {
            SkillManager.sync(player);
        }
    }

    // ===== 4. 战利品与星锻时运（hyper_conduit 已重做，保留克莱因拾取接口兼容） =====

    public static int hyperConduitLevel(ServerPlayer player) {
        // 原「超维直达」已彻底重做为「星锻时运」；返回 0 以保持克莱因瓶原拾取逻辑完全一致
        return 0;
    }

    public static void onKleinBottlePickup(ServerPlayer player) {
        if (player == null || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        SkillManager.data(player).incrementStat("klein_pickups", 1,
                skill -> SkillManager.notifySkillUnlocked(player, skill));
        SkillCombatTracker.markDirty(player);
    }

    static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        SkillPassiveHandler.onBlockDrops(event);
        SkillResonanceHandler.onBlockDrops(event);
    }

    static void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        SkillPassiveHandler.onLivingDrops(event);
    }

    static void onLivingExperienceDrop(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        SkillPassiveHandler.onLivingExperienceDrop(event);
    }

    // ===== 5. 万能聚合台联动（void_fortune + 巧匠 6 星共鸣） =====

    /** 查询聚合台附近玩家「虚空眷顾」及「天工开物(巧匠6星)」带来的每 tick 额外加速步数。 */
    public static int polymerizerExtraProgressTicks(ServerLevel level, BlockPos pos) {
        if (level == null || !SkillManager.isEnabled(level)) {
            return 0;
        }
        int maxLv = 0;
        boolean craftsman6 = false;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            double distSqr = player.distanceToSqr(cx, cy, cz);
            if (distSqr <= 64.0) {
                int lv = SkillPassiveHandler.equippedLevel(player, ModSkills.VOID_FORTUNE);
                if (lv > maxLv) maxLv = lv;
            }
            if (distSqr <= 144.0 && SkillManager.data(player).resonanceTier(SkillConstellation.QIAOJIANG) >= 3) {
                craftsman6 = true;
            }
        }
        long t = level.getGameTime();
        int extra = 0;
        if (maxLv > 0) {
            extra += switch (maxLv) {
                case 1 -> (t % 4 == 0) ? 1 : 0;   // +25%
                case 2 -> (t % 2 == 0) ? 1 : 0;   // +50%
                default -> (t % 5 == 0) ? 0 : 1;  // +80%
            };
            if (t % 10 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, cx, cy + 0.7, cz, 2, 0.32, 0.18, 0.32, 0.02);
            }
        }
        if (craftsman6) {
            extra += 1;
        }
        return extra;
    }

    public static void onPolymerizerFinished(ServerLevel level, BlockPos pos) {
        if (level == null || !SkillManager.isEnabled(level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                SkillManager.data(player).incrementStat("polymerizer_crafts", 1,
                        skill -> SkillManager.notifySkillUnlocked(player, skill));
                SkillCombatTracker.award(player, 25, true);
                break;
            }
        }
    }

    // ===== 6. 万能抽奖机联动（void_fortune） =====

    /** 为献祭抽奖提供幸运估值加成（Lv.1 +12% / Lv.2 +24% / Lv.3 +40%）。 */
    public static long adjustLotteryPoints(ServerPlayer player, long basePoints) {
        if (player == null || basePoints <= 0 || !SkillManager.isEnabled(player.serverLevel())) {
            return basePoints;
        }
        int lv = SkillPassiveHandler.equippedLevel(player, ModSkills.VOID_FORTUNE);
        if (lv <= 0) return basePoints;
        double bonus = switch (lv) {
            case 1 -> 1.12;
            case 2 -> 1.24;
            default -> 1.40;
        };
        return Math.max(basePoints, Math.round(basePoints * bonus));
    }

    /** 抽奖开始/开奖时记录试炼与经验；Lv.3 觉醒额外赠送星运暴击反馈。 */
    public static void onLotteryRollCompleted(ServerLevel level, BlockPos pos, ServerPlayer opener) {
        if (level == null || !SkillManager.isEnabled(level)) {
            return;
        }
        ServerPlayer target = opener;
        if (target == null) {
            for (ServerPlayer candidate : level.players()) {
                if (candidate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    target = candidate;
                    break;
                }
            }
        }
        if (target == null) return;
        ServerPlayer player = target;
        SkillManager.data(player).incrementStat("lottery_rolls", 1,
                skill -> SkillManager.notifySkillUnlocked(player, skill));
        SkillCombatTracker.award(player, 20, true);

        if (SkillPassiveHandler.equippedLevel(player, ModSkills.VOID_FORTUNE) >= 3
                && level.random.nextFloat() < 0.15F) {
            SkillCombatTracker.award(player, 60, true);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 18, 0.35, 0.35, 0.35, 0.12);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 1.5F);
        }
    }
}
