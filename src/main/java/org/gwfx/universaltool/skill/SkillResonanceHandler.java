package org.gwfx.universaltool.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 四大星座 2 件 / 4 件套装共鸣处理器。
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

    private SkillResonanceHandler() {}

    static void clearAll() {
        TIANSHU_EVASION_UNTIL.clear();
    }

    static void clearTransient(UUID playerId) {
        TIANSHU_EVASION_UNTIL.remove(playerId);
    }

    /** 应用或清理共鸣属性修饰符。 */
    static void applyResonanceAttributes(ServerPlayer player) {
        PlayerSkillData data = SkillManager.data(player);
        boolean systemOn = SkillManager.isEnabled(player.serverLevel());

        int xuanwu = systemOn ? data.resonanceTier(SkillConstellation.XUANWU) : 0;
        var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(XUANWU_KB_ID);
            if (xuanwu >= 1) {
                kb.addTransientModifier(new AttributeModifier(
                        XUANWU_KB_ID, 0.40, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        int tianshu = systemOn ? data.resonanceTier(SkillConstellation.TIANSHU) : 0;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(TIANSHU_SPEED_ID);
            if (tianshu >= 1) {
                speed.addTransientModifier(new AttributeModifier(
                        TIANSHU_SPEED_ID, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }

        int qiaojiang = systemOn ? data.resonanceTier(SkillConstellation.QIAOJIANG) : 0;
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null) {
            reach.removeModifier(QIAOJIANG_REACH_ID);
            if (qiaojiang >= 1) {
                reach.addTransientModifier(new AttributeModifier(
                        QIAOJIANG_REACH_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        var mineSpeed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (mineSpeed != null) {
            mineSpeed.removeModifier(QIAOJIANG_MINE_ID);
            if (qiaojiang >= 1) {
                mineSpeed.addTransientModifier(new AttributeModifier(
                        QIAOJIANG_MINE_ID, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    static void clearAllResonances(ServerPlayer player) {
        TIANSHU_EVASION_UNTIL.remove(player.getUUID());
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
        if (player.tickCount % 160 == 0) {
            PlayerSkillData data = SkillManager.data(player);
            if (data.resonanceTier(SkillConstellation.XUANWU) >= 2 && !data.inCombat(gameTime)) {
                float current = player.getAbsorptionAmount();
                if (current < 6.0F) {
                    player.setAbsorptionAmount(Math.min(6.0F, current + 2.0F));
                }
            }
        }
    }

    /** 破军 2 件：连击窗口由 60t 延长至 100t。 */
    static int momentumWindowTicks(ServerPlayer player) {
        return SkillManager.data(player).resonanceTier(SkillConstellation.POJUN) >= 1 ? 100 : SkillManager.MOMENTUM_WINDOW_TICKS;
    }

    /** 天枢 2 件：主动技能冷却缩短 12%。 */
    static int adjustCooldownTicks(ServerPlayer player, int baseTicks) {
        if (baseTicks <= 0) return 0;
        if (SkillManager.data(player).resonanceTier(SkillConstellation.TIANSHU) >= 1) {
            return Math.max(10, Math.round(baseTicks * 0.88F));
        }
        return baseTicks;
    }

    /** 天枢 4 件：施放主动技能后获 3 秒迅捷与 15% 闪避减伤。 */
    static void onActiveSkillCast(ServerPlayer player) {
        if (SkillManager.data(player).resonanceTier(SkillConstellation.TIANSHU) >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false, true));
            TIANSHU_EVASION_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + 60);
        }
    }

    /** 破军 4 件：击杀敌怪缩短所有主动冷却 1.5s 并触发星辉斩击波。 */
    static void onHostileKill(ServerPlayer player, LivingEntity killed) {
        PlayerSkillData data = SkillManager.data(player);
        if (data.resonanceTier(SkillConstellation.POJUN) < 2) {
            return;
        }
        data.reduceAllCooldownsMillis(1500L);
        var world = player.serverLevel();
        world.sendParticles(ParticleTypes.SWEEP_ATTACK, killed.getX(), killed.getY() + 0.8, killed.getZ(),
                4, 0.4, 0.2, 0.4, 0.0);
        for (LivingEntity nearby : world.getEntitiesOfClass(LivingEntity.class,
                killed.getBoundingBox().inflate(3.5), e -> SkillActiveExecutor.canAffect(player, e))) {
            nearby.hurt(world.damageSources().indirectMagic(player, player), 4.0F);
        }
    }

    static void onIncomingAttacker(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        if (SkillManager.data(attacker).resonanceTier(SkillConstellation.POJUN) >= 1) {
            // 破军 2 件：暴击或近战增伤 10%
            event.setAmount(event.getAmount() * 1.10F);
        }
    }

    static void onIncomingVictim(ServerPlayer victim, LivingIncomingDamageEvent event) {
        Long until = TIANSHU_EVASION_UNTIL.get(victim.getUUID());
        if (until != null) {
            if (victim.serverLevel().getGameTime() < until) {
                event.setAmount(event.getAmount() * 0.85F);
            } else {
                TIANSHU_EVASION_UNTIL.remove(victim.getUUID());
            }
        }
    }

    static void onLivingHeal(ServerPlayer player, LivingHealEvent event) {
        if (SkillManager.data(player).resonanceTier(SkillConstellation.XUANWU) >= 1) {
            event.setAmount(event.getAmount() * 1.15F);
        }
    }

    /** 巧匠 4 件：挖掘矿石/收获成熟作物 15% 概率额外经验与星辉反馈。 */
    static void onCraftsmanHarvest(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (SkillManager.data(player).resonanceTier(SkillConstellation.QIAOJIANG) >= 2
                && player.getRandom().nextFloat() < 0.15F) {
            SkillCombatTracker.award(player, 12, false);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.04);
            player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }
}
