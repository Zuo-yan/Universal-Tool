package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.Skill3dEffectPayload;
import org.gwfx.universaltool.network.Skill3dEffectPayload.EffectKind;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 被动技能运行时处理器（涵盖全部 18 项被动：升级效果、百分比伤害、碎甲、星锻时运、凤凰涅槃与模组联动）。
 */
final class SkillPassiveHandler {
    private static final ResourceLocation IRON_PHYSIQUE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "iron_physique");
    private static final ResourceLocation SWIFT_PACE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "swift_pace");
    private static final ResourceLocation SWIFT_STEP_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "swift_pace_step");
    private static final ResourceLocation BULWARK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "diamond_bulwark");
    private static final ResourceLocation TOOL_RESONANCE_ATK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "tool_resonance_atk");
    private static final ResourceLocation TOOL_RESONANCE_SPD_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "tool_resonance_spd");
    private static final ResourceLocation SPATIAL_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "spatial_attunement_speed");
    private static final ResourceLocation SPATIAL_REACH_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "spatial_attunement_reach");
    private static final ResourceLocation VOID_FORTUNE_LUCK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "void_fortune_luck");

    private static final Map<UUID, Momentum> MOMENTUM = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> TOOL_COMBO_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_STAND_SHIELD_CD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DAMAGE_CLAMP_CD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PHOENIX_EMBER_CD = new ConcurrentHashMap<>();
    private static final Map<UUID, ShatterState> SHATTERED_TARGETS = new ConcurrentHashMap<>();
    private static final Map<UUID, EmberZone> EMBER_ZONES = new ConcurrentHashMap<>();

    private record Momentum(int stacks, long lastHitGameTime) {}
    private record ShatterState(int level, long expiresAtMillis) {}
    private record EmberZone(net.minecraft.server.level.ServerLevel level, UUID ownerId, Vec3 center, long expiresAtTick) {}

    private SkillPassiveHandler() {}

    static void clearAll() {
        MOMENTUM.clear();
        TOOL_COMBO_HITS.clear();
        LAST_STAND_SHIELD_CD.clear();
        DAMAGE_CLAMP_CD.clear();
        PHOENIX_EMBER_CD.clear();
        SHATTERED_TARGETS.clear();
        EMBER_ZONES.clear();
    }

    static void clearMomentum(UUID playerId) {
        MOMENTUM.remove(playerId);
        TOOL_COMBO_HITS.remove(playerId);
    }

    static int equippedLevel(ServerPlayer player, String id) {
        if (!SkillManager.isEnabled(player.serverLevel())) {
            return 0;
        }
        PlayerSkillData data = SkillManager.data(player);
        if (!data.passiveLoadout().contains(id)) {
            return 0;
        }
        return data.learnedLevel(id);
    }

    static double bulwarkArmor(int level) {
        return level <= 0 ? 0 : level == 1 ? 6 : level == 2 ? 12 : 20;
    }

    static double bulwarkToughness(int level) {
        return level <= 1 ? 0 : level == 2 ? 4 : 8;
    }

    static float damageClampRatio(int level) {
        return level <= 0 ? 1 : level == 1 ? 0.5F : level == 2 ? 0.4F : 0.3F;
    }

    static void applyPassiveEffects(ServerPlayer player) {
        if (!SkillManager.isEnabled(player.serverLevel())) {
            clearAllPassives(player);
            return;
        }

        updateBulwark(player, equippedLevel(player, ModSkills.DIAMOND_BULWARK));

        int mining = equippedLevel(player, ModSkills.FOCUS_MINING);
        if (mining > 0) {
            MobEffectInstance currentHaste = player.getEffect(MobEffects.DIG_SPEED);
            if (currentHaste == null || currentHaste.getAmplifier() <= mining - 1) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 45, mining - 1, true, false, true));
            }
        }

        int healthLvl = equippedLevel(player, ModSkills.IRON_PHYSIQUE);
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(IRON_PHYSIQUE_MODIFIER_ID);
            if (healthLvl > 0) {
                double bonusHp = healthLvl == 1 ? 6.0 : healthLvl == 2 ? 12.0 : 20.0;
                healthAttr.addTransientModifier(new AttributeModifier(
                        IRON_PHYSIQUE_MODIFIER_ID, bonusHp, AttributeModifier.Operation.ADD_VALUE));
            }
            if (player.isAlive() && player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
        if (equippedLevel(player, ModSkills.BATTLE_MOMENTUM) <= 0) {
            MOMENTUM.remove(player.getUUID());
        }
        if (equippedLevel(player, ModSkills.TOOL_RESONANCE) <= 0) {
            TOOL_COMBO_HITS.remove(player.getUUID());
        }

        int paceLvl = equippedLevel(player, ModSkills.SWIFT_PACE);
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SWIFT_PACE_MODIFIER_ID);
            if (paceLvl > 0) {
                double speedBonus = paceLvl == 1 ? 0.10 : paceLvl == 2 ? 0.18 : 0.25;
                speedAttr.addTransientModifier(new AttributeModifier(
                        SWIFT_PACE_MODIFIER_ID, speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
        var stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            stepAttr.removeModifier(SWIFT_STEP_MODIFIER_ID);
            if (paceLvl >= 3) {
                stepAttr.addTransientModifier(new AttributeModifier(
                        SWIFT_STEP_MODIFIER_ID, 0.45, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        updateToolResonance(player);
        updateSpatialAttunement(player);

        int fortuneLvl = equippedLevel(player, ModSkills.VOID_FORTUNE);
        var luckAttr = player.getAttribute(Attributes.LUCK);
        if (luckAttr != null) {
            luckAttr.removeModifier(VOID_FORTUNE_LUCK_ID);
            if (fortuneLvl > 0) {
                luckAttr.addTransientModifier(new AttributeModifier(
                        VOID_FORTUNE_LUCK_ID, fortuneLvl, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        SkillResonanceHandler.applyResonanceAttributes(player);
    }

    private static void updateToolResonance(ServerPlayer player) {
        int resLvl = equippedLevel(player, ModSkills.TOOL_RESONANCE);
        int tier = SkillModSynergyHandler.modWeaponTierRank(player.getMainHandItem());
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var spd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atk != null) atk.removeModifier(TOOL_RESONANCE_ATK_ID);
        if (spd != null) spd.removeModifier(TOOL_RESONANCE_SPD_ID);

        if (resLvl > 0 && tier > 0) {
            if (atk != null) {
                double dmgBonus = tier * (0.4 + 0.3 * resLvl);
                atk.addTransientModifier(new AttributeModifier(
                        TOOL_RESONANCE_ATK_ID, dmgBonus, AttributeModifier.Operation.ADD_VALUE));
            }
            if (spd != null) {
                double spdBonus = 0.06 * resLvl + 0.02 * tier;
                spd.addTransientModifier(new AttributeModifier(
                        TOOL_RESONANCE_SPD_ID, spdBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    /** 时空锚感：常驻触及 +0.5/1.0/1.5 格；在口袋界域内额外提供 8/16/24% 移速与 +1/2/3 触及。 */
    private static void updateSpatialAttunement(ServerPlayer player) {
        int lv = equippedLevel(player, ModSkills.SPATIAL_ATTUNEMENT);
        boolean inPocket = SkillModSynergyHandler.isInPocketOrRealmDimension(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (speed != null) speed.removeModifier(SPATIAL_SPEED_ID);
        if (reach != null) reach.removeModifier(SPATIAL_REACH_ID);

        if (lv > 0) {
            double reachBonus = 0.5D * lv + (inPocket ? lv : 0.0D);
            if (reach != null) {
                reach.addTransientModifier(new AttributeModifier(
                        SPATIAL_REACH_ID, reachBonus, AttributeModifier.Operation.ADD_VALUE));
            }
            if (inPocket && speed != null) {
                speed.addTransientModifier(new AttributeModifier(
                        SPATIAL_SPEED_ID, 0.08 * lv, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    static void clearAllPassives(ServerPlayer player) {
        updateBulwark(player, 0);
        MOMENTUM.remove(player.getUUID());
        TOOL_COMBO_HITS.remove(player.getUUID());
        removeAttr(player, Attributes.MAX_HEALTH, IRON_PHYSIQUE_MODIFIER_ID);
        if (player.isAlive() && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        removeAttr(player, Attributes.MOVEMENT_SPEED, SWIFT_PACE_MODIFIER_ID);
        removeAttr(player, Attributes.STEP_HEIGHT, SWIFT_STEP_MODIFIER_ID);
        removeAttr(player, Attributes.ATTACK_DAMAGE, TOOL_RESONANCE_ATK_ID);
        removeAttr(player, Attributes.ATTACK_SPEED, TOOL_RESONANCE_SPD_ID);
        removeAttr(player, Attributes.MOVEMENT_SPEED, SPATIAL_SPEED_ID);
        removeAttr(player, Attributes.BLOCK_INTERACTION_RANGE, SPATIAL_REACH_ID);
        removeAttr(player, Attributes.LUCK, VOID_FORTUNE_LUCK_ID);
        SkillResonanceHandler.clearAllResonances(player);
    }

    private static void removeAttr(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id) {
        var inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    private static void updateBulwark(ServerPlayer player, int rank) {
        var armor = player.getAttribute(Attributes.ARMOR);
        var toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armor != null) {
            armor.removeModifier(BULWARK_ID);
            if (rank > 0) {
                armor.addTransientModifier(new AttributeModifier(
                        BULWARK_ID, bulwarkArmor(rank), AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (toughness != null) {
            toughness.removeModifier(BULWARK_ID);
            if (rank > 0) {
                toughness.addTransientModifier(new AttributeModifier(
                        BULWARK_ID, bulwarkToughness(rank), AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /** 伤害钳制（新增 2 秒内置冷却防连段无限免死 + 六边形锁壁视觉音效反馈）。 */
    static void clampFinalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SkillManager.isEnabled(player.serverLevel())) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
                || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        int rank = equippedLevel(player, ModSkills.DAMAGE_CLAMP);
        if (rank <= 0) return;

        float cap = player.getMaxHealth() * damageClampRatio(rank);
        if (event.getNewDamage() > cap) {
            long now = System.currentTimeMillis();
            Long cd = DAMAGE_CLAMP_CD.get(player.getUUID());
            if (cd != null && now < cd) {
                return;
            }
            DAMAGE_CLAMP_CD.put(player.getUUID(), now + 2_000L);
            event.setNewDamage(cap);
            if (rank >= 3) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, false, true));
            }
            SkillVfx.ring(player.serverLevel(), player.position().add(0, 1.0, 0), 1.05, 0, 6, SkillVfx.Tint.GUARD);
            SkillVfx.sparks(player.serverLevel(), player.position().add(0, 1.0, 0), 0.35, 0.45, 10, SkillVfx.Tint.GUARD);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.65F, 1.5F);
        }
    }

    static void applyIncomingVictimPassives(ServerPlayer victim, LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        if (SkillNewActivesHelper.isInsideSanctuary(victim)) {
            event.setAmount(event.getAmount() * 0.85F);
        }
        applyLastStand(victim, event);
        if (event.isCanceled() || event.getAmount() <= 0) return;
        applyLightFeather(victim, event);
        if (event.isCanceled() || event.getAmount() <= 0) return;
        applyBulwarkThorns(victim, event);
        if (event.isCanceled() || event.getAmount() <= 0) return;
        SkillResonanceHandler.onIncomingVictim(victim, event);
    }

    static void applyIncomingAttackerPassives(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        applyMomentum(attacker, event);
        applyArmorPiercing(attacker, event);
        SkillActiveExecutor.applyVoidBackstab(attacker, event);
        SkillResonanceHandler.onIncomingAttacker(attacker, event);
        SkillResonanceHandler.tryCraftsmanDurableCombat(attacker);
    }

    static void onLivingHeal(ServerPlayer player, LivingHealEvent event) {
        int physique = equippedLevel(player, ModSkills.IRON_PHYSIQUE);
        if (physique >= 3) {
            event.setAmount(event.getAmount() * 1.20F);
        }
        SkillResonanceHandler.onLivingHeal(player, event);
        if (physique > 0 && event.getAmount() >= 1.0F) {
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.28, 0.35, 0.28, 0.02);
        }
    }

    /** 凤凰余烬兜底保命：3D 展翼火环 + 升腾烈焰柱 + 4 秒余烬地面灼烧区。 */
    static void tryPhoenixEmberRevive(ServerPlayer player, LivingDeathEvent event) {
        if (event.isCanceled() || !SkillManager.isEnabled(player.serverLevel())) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        int lv = equippedLevel(player, ModSkills.PHOENIX_EMBER);
        if (lv <= 0) return;
        long now = System.currentTimeMillis();
        long cdMillis = switch (lv) {
            case 1 -> 360_000L;
            case 2 -> 300_000L;
            default -> 240_000L;
        };
        Long last = PHOENIX_EMBER_CD.get(player.getUUID());
        if (last != null && now - last < cdMillis) {
            return;
        }
        PHOENIX_EMBER_CD.put(player.getUUID(), now);
        event.setCanceled(true);
        float targetHp = lv >= 3 ? Math.max(4.0F, player.getMaxHealth() * 0.30F) : 1.0F;
        player.setHealth(Math.min(player.getMaxHealth(), targetHp));
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0F + 2.0F * lv));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3, false, true, true));

        var world = player.serverLevel();
        for (LivingEntity enemy : world.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0), e -> SkillActiveExecutor.canAffect(player, e))) {
            Vec3 diff = enemy.position().subtract(player.position());
            Vec3 push = diff.lengthSqr() > 1.0E-4 ? diff.normalize().scale(1.3) : new Vec3(0, 0, 1.3);
            enemy.setDeltaMovement(push.x, 0.45, push.z);
            enemy.hurtMarked = true;
            enemy.igniteForSeconds(4);
        }
        EMBER_ZONES.put(player.getUUID(), new EmberZone(world, player.getUUID(), player.position(), world.getGameTime() + 80L));
        Skill3dEffectPayload.spawn(world, EffectKind.PHOENIX_WINGS, player.position(), player.getLookAngle(),
                5.0F, 36, 0xFF963B);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.5, 0.8, 0.5, 0.25);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 1.1F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.85F, 1.2F);
    }

    static void tickServer(net.minecraft.server.MinecraftServer server) {
        long nowMs = System.currentTimeMillis();
        SHATTERED_TARGETS.entrySet().removeIf(e -> nowMs >= e.getValue().expiresAtMillis());

        if (!EMBER_ZONES.isEmpty()) {
            for (Map.Entry<UUID, EmberZone> entry : EMBER_ZONES.entrySet()) {
                EmberZone zone = entry.getValue();
                long t = zone.level().getGameTime();
                if (t >= zone.expiresAtTick()) {
                    EMBER_ZONES.remove(entry.getKey(), zone);
                    continue;
                }
                if (t % 20 == 0) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(zone.ownerId());
                    if (owner == null) {
                        EMBER_ZONES.remove(entry.getKey(), zone);
                        continue;
                    }
                    AABB box = new AABB(zone.center(), zone.center()).inflate(5.0);
                    for (LivingEntity enemy : zone.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> SkillActiveExecutor.canAffect(owner, e))) {
                        enemy.hurt(zone.level().damageSources().inFire(),
                                SkillActiveExecutor.pctDamage(enemy, 5.0F, 0.04F, 0.0F, 0.0F));
                    }
                    SkillVfx.ring(zone.level(), zone.center().add(0, 0.15, 0), 4.6, t * 0.05, 14, SkillVfx.Tint.RAGE);
                }
            }
        }
    }

    static void trackMomentum(ServerPlayer player, LivingEntity target, long gameTime) {
        if (target == player) return;
        int momLv = equippedLevel(player, ModSkills.BATTLE_MOMENTUM);
        if (momLv > 0) {
            Momentum previous = MOMENTUM.get(player.getUUID());
            int window = SkillResonanceHandler.momentumWindowTicks(player);
            int stacks = previous != null && gameTime - previous.lastHitGameTime() <= window
                    ? Math.min(SkillManager.MOMENTUM_MAX_STACKS, previous.stacks() + 1)
                    : 1;
            MOMENTUM.put(player.getUUID(), new Momentum(stacks, gameTime));

            // 叠层战意视觉粒子
            SkillVfx.sparks(player.serverLevel(), player.position().add(0, 1.0, 0),
                    0.35, 0.35, stacks >= SkillManager.MOMENTUM_MAX_STACKS ? 10 : 3, SkillVfx.Tint.RAGE);

            // Lv.3 觉醒：叠满 5 层时触发随攻击力比例 + 4% 最大生命值星辉顺劈波
            if (momLv >= 3 && stacks >= SkillManager.MOMENTUM_MAX_STACKS) {
                float atkBase = (float) Math.max(4.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.45D);
                player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + 0.8, target.getZ(), 3, 0.35, 0.2, 0.35, 0.0);
                for (LivingEntity extra : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(2.8),
                        e -> e != target && SkillActiveExecutor.canAffect(player, e))) {
                    extra.hurt(player.serverLevel().damageSources().indirectMagic(player, player),
                            SkillActiveExecutor.pctDamage(extra, atkBase, 0.04F, 0.0F, 0.0F));
                }
            }
        }

        // 兵刃共鸣 Lv.3 觉醒：每第 4 次手持本模组武器攻击触发星辉交叉暴击斩
        int toolResLv = equippedLevel(player, ModSkills.TOOL_RESONANCE);
        int tier = SkillModSynergyHandler.modWeaponTierRank(player.getMainHandItem());
        if (toolResLv >= 3 && tier > 0) {
            int hits = TOOL_COMBO_HITS.getOrDefault(player.getUUID(), 0) + 1;
            if (hits >= 4) {
                TOOL_COMBO_HITS.put(player.getUUID(), 0);
                for (LivingEntity hit : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(2.5), e -> SkillActiveExecutor.canAffect(player, e))) {
                    hit.hurt(player.serverLevel().damageSources().indirectMagic(player, player),
                            SkillActiveExecutor.pctDamage(hit, 6.0F + 2.0F * tier, 0.06F, 0.0F, 0.0F));
                }
                Skill3dEffectPayload.spawn(player.serverLevel(), EffectKind.ASTRAL_CLEAVE,
                        player.getEyePosition().subtract(0, 0.2, 0), player.getLookAngle(),
                        4.5F, 12, 0xFFE38C);
                player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.4F);
            } else {
                TOOL_COMBO_HITS.put(player.getUUID(), hits);
            }
        }
    }

    static void applyBloodthirst(ServerPlayer player, LivingEntity target, float damageDealt) {
        int blood = equippedLevel(player, ModSkills.BLOODTHIRST);
        if (blood <= 0 || damageDealt <= 0) return;
        float heal = Math.max(0.2F, damageDealt * (blood * 0.05F));
        float missing = Math.max(0.0F, player.getMaxHealth() - player.getHealth());
        player.heal(heal);

        // 血晶粒子从目标流向玩家
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 to = player.position().add(0, 1.0, 0);
        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0;
            Vec3 p = from.lerp(to, t);
            SkillVfx.point(player.serverLevel(), p, SkillVfx.Tint.RAGE.dust);
        }

        if (blood >= 3 && heal > missing) {
            float overflow = heal - missing;
            float nextShield = Math.min(10.0F, player.getAbsorptionAmount() + overflow * 0.5F);
            player.setAbsorptionAmount(nextShield);
            SkillVfx.sparks(player.serverLevel(), player.position().add(0, 1.0, 0), 0.35, 0.35, 6, SkillVfx.Tint.ALCHEMY);
        }
    }

    static void tickPassives(ServerPlayer player, long gameTime) {
        if (!SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        if (gameTime % 2 == 0) {
            pullNearbyItems(player, equippedLevel(player, ModSkills.ITEM_MAGNET));
        }
        if (player.tickCount % 20 == 0) {
            applyPassiveEffects(player);
            tickToolRepairAndExplorer(player);

            // 生存回流：脱战状态下每秒恢复 1 / 2 / 3 生命值，Lv.3 5s 净化一次负面效果
            int flow = equippedLevel(player, ModSkills.SURVIVAL_FLOW);
            PlayerSkillData data = SkillManager.data(player);
            if (flow > 0 && !data.inCombat(gameTime)) {
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal((float) flow);
                    player.serverLevel().sendParticles(ParticleTypes.GLOW,
                            player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.45, 0.35, 0.01);
                }
                if (flow >= 3) {
                    if (player.tickCount % 60 == 0 && player.getFoodData().needsFood()) {
                        player.getFoodData().eat(1, 0.25F);
                    }
                    if (player.tickCount % 100 == 0) {
                        for (MobEffectInstance inst : List.copyOf(player.getActiveEffects())) {
                            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                                player.removeEffect(inst.getEffect());
                                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45F, 1.7F);
                                break;
                            }
                        }
                    }
                }
            }

            int paceLvl = equippedLevel(player, ModSkills.SWIFT_PACE);
            if (paceLvl > 0 && (player.isSprinting() || !player.onGround())) {
                float reduction = paceLvl == 1 ? 0.25F : paceLvl == 2 ? 0.45F : 0.65F;
                float ex = player.getFoodData().getExhaustionLevel();
                if (ex > 0.0F) {
                    player.getFoodData().setExhaustion(ex * (1.0F - reduction));
                }
            }
        }
        SkillResonanceHandler.tickResonances(player, gameTime);
    }

    private static void tickToolRepairAndExplorer(ServerPlayer player) {
        int toolRes = equippedLevel(player, ModSkills.TOOL_RESONANCE);
        if (toolRes > 0) {
            int interval = toolRes == 1 ? 60 : toolRes == 2 ? 40 : 20;
            if (player.tickCount % interval == 0) {
                ItemStack main = player.getMainHandItem();
                if (SkillModSynergyHandler.modWeaponTierRank(main) > 0 && main.isDamaged()) {
                    main.setDamageValue(Math.max(0, main.getDamageValue() - 1));
                }
            }
        }

        // 秘境直觉：地下夜视 + Lv.2 起清除失明/黑暗 + Lv.3 水下呼吸与海豚恩惠
        int expEye = equippedLevel(player, ModSkills.EXPLORERS_EYE);
        if (expEye > 0) {
            BlockPos pos = player.blockPosition();
            var level = player.serverLevel();
            int sky = level.getBrightness(LightLayer.SKY, pos);
            int block = level.getBrightness(LightLayer.BLOCK, pos);
            if (!level.canSeeSky(pos) && sky <= 2 && block <= 6) {
                MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
                if (nv == null || nv.getDuration() < 240) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
                }
            }
            if (expEye >= 2) {
                player.removeEffect(MobEffects.BLINDNESS);
                player.removeEffect(MobEffects.DARKNESS);
            }
            if (expEye >= 3 && player.isUnderWater()) {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, false));
            }
        }
    }

    private static void applyLastStand(ServerPlayer victim, LivingIncomingDamageEvent event) {
        int level = equippedLevel(victim, ModSkills.LAST_STAND);
        if (level <= 0) return;
        float max = victim.getMaxHealth();
        if (max <= 0 || victim.getHealth() / max >= 0.40F) return;
        float reduction = Math.min(0.6F, 0.15F * level);
        event.setAmount(event.getAmount() * (1.0F - reduction));

        if (level >= 3) {
            long now = System.currentTimeMillis();
            Long cd = LAST_STAND_SHIELD_CD.get(victim.getUUID());
            if (cd == null || now >= cd) {
                LAST_STAND_SHIELD_CD.put(victim.getUUID(), now + 20_000L);
                victim.setAbsorptionAmount(Math.max(victim.getAbsorptionAmount(), 8.0F));
                SkillVfx.ring(victim.serverLevel(), victim.position().add(0, 0.4, 0), 1.35, 0, 12, SkillVfx.Tint.RAGE);
                victim.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.55F, 1.4F);
            }
        }
    }

    private static void applyLightFeather(ServerPlayer player, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.FALL)) return;
        int feather = equippedLevel(player, ModSkills.LIGHT_FEATHER);
        if (feather <= 0) return;
        if (feather >= 3) {
            event.setCanceled(true);
            player.fallDistance = 0.0F;
            if (event.getAmount() >= 4.0F) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false, true));
            }
        } else {
            float reduction = feather == 1 ? 0.40F : 0.75F;
            event.setAmount(Math.max(0.0F, event.getAmount() * (1.0F - reduction)));
        }
    }

    private static void applyBulwarkThorns(ServerPlayer victim, LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        int lv = equippedLevel(victim, ModSkills.DIAMOND_BULWARK);
        if (lv < 3) return;
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && SkillActiveExecutor.canAffect(victim, living)) {
            float reflect = Math.max(1.0F, event.getAmount() * 0.15F);
            living.hurt(victim.damageSources().thorns(victim), reflect);
        }
    }

    private static void applyMomentum(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int level = equippedLevel(attacker, ModSkills.BATTLE_MOMENTUM);
        if (level <= 0) return;
        Momentum momentum = MOMENTUM.get(attacker.getUUID());
        if (momentum == null) return;
        long gameTime = attacker.serverLevel().getGameTime();
        if (gameTime - momentum.lastHitGameTime() > SkillResonanceHandler.momentumWindowTicks(attacker)) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + 0.04F * momentum.stacks() * level));
    }

    /** 锋刃破甲：+15/30/45% 增伤 + 挂 4 秒碎甲易伤（10/18/25%）+ 金属脆响火花。 */
    private static void applyArmorPiercing(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int pierce = equippedLevel(attacker, ModSkills.ARMOR_PIERCING);
        long nowMs = System.currentTimeMillis();
        float shatterMult = 1.0F;
        if (event.getEntity() instanceof LivingEntity target) {
            ShatterState shattered = SHATTERED_TARGETS.get(target.getUUID());
            if (shattered != null && nowMs < shattered.expiresAtMillis()) {
                shatterMult += shattered.level() == 1 ? 0.10F : (shattered.level() == 2 ? 0.18F : 0.25F);
            }
            if (pierce > 0) {
                SHATTERED_TARGETS.put(target.getUUID(), new ShatterState(pierce, nowMs + 4_000L));
            }
        }
        if (pierce <= 0) {
            if (shatterMult > 1.0F) event.setAmount(event.getAmount() * shatterMult);
            return;
        }
        float mult = (1.0F + pierce * 0.15F) * shatterMult;
        if (event.getEntity() instanceof LivingEntity target) {
            if (target.getArmorValue() > 0) {
                attacker.serverLevel().sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        6, 0.25, 0.25, 0.25, 0.08);
            }
            float armorBonus = pierce >= 3 ? (target.getArmorValue() * 0.15F) : 0.0F;
            event.setAmount(event.getAmount() * mult + armorBonus);
        } else {
            event.setAmount(event.getAmount() * mult);
        }
    }

    // ===== 星锻时运（原 hyper_conduit 位置） =====

    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        int lv = equippedLevel(player, ModSkills.HYPER_CONDUIT);
        if (lv <= 0) return;

        // 精准采集保护：不翻倍带精准采集的原矿方块
        var silkReg = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean silkTouch = event.getTool().getEnchantmentLevel(silkReg.getOrThrow(Enchantments.SILK_TOUCH)) > 0;
        boolean validFortuneTarget = SkillCombatTracker.isOre(event.getState())
                || event.getState().getBlock() instanceof CropBlock;

        if (!silkTouch && validFortuneTarget && !event.getDrops().isEmpty()) {
            for (ItemEntity entity : event.getDrops()) {
                ItemStack stack = entity.getItem();
                if (!stack.isEmpty() && !stack.is(event.getState().getBlock().asItem())) {
                    int bonusCount = rollFortuneBonus(player.getRandom(), lv);
                    if (bonusCount > 0) {
                        stack.grow(Math.min(stack.getMaxStackSize() - stack.getCount(), bonusCount * stack.getCount()));
                    }
                }
            }
        }
        // 工具耐久减耗（20% / 35% / 50%，仅对消耗耐久的非零硬度方块生效）
        float saveChance = lv == 1 ? 0.20F : (lv == 2 ? 0.35F : 0.50F);
        ItemStack tool = player.getMainHandItem();
        if (event.getState().getDestroySpeed(event.getLevel(), event.getPos()) > 0.0F
                && tool.isDamageableItem() && tool.isDamaged() && player.getRandom().nextFloat() < saveChance) {
            tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
        }
        if (lv >= 3 && event.getDroppedExperience() > 0) {
            event.setDroppedExperience(Math.max(1, Math.round(event.getDroppedExperience() * 1.30F)));
        }
    }

    static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled() || event.getEntity() instanceof Player) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        int lv = equippedLevel(player, ModSkills.HYPER_CONDUIT);
        if (lv <= 0) return;
        for (ItemEntity entity : event.getDrops()) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty() && stack.isStackable()) {
                int extra = player.getRandom().nextInt(lv + 1);
                if (extra > 0) {
                    stack.grow(Math.min(stack.getMaxStackSize() - stack.getCount(), extra));
                }
            }
        }
    }

    static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getAttackingPlayer() instanceof ServerPlayer player) || !SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        int lv = equippedLevel(player, ModSkills.HYPER_CONDUIT);
        if (lv >= 3 && event.getDroppedExperience() > 0) {
            event.setDroppedExperience(Math.max(1, Math.round(event.getDroppedExperience() * 1.30F)));
        }
    }

    private static int rollFortuneBonus(net.minecraft.util.RandomSource rand, int fortuneLevel) {
        int roll = rand.nextInt(fortuneLevel + 2) - 1;
        return Math.max(0, roll);
    }

    /** 攻击触发星锻时运耐久保护。 */
    static void tryFortuneToolSave(ServerPlayer player) {
        int lv = equippedLevel(player, ModSkills.HYPER_CONDUIT);
        if (lv <= 0) return;
        float saveChance = lv == 1 ? 0.20F : (lv == 2 ? 0.35F : 0.50F);
        ItemStack held = player.getMainHandItem();
        if (held.isDamageableItem() && held.isDamaged() && player.getRandom().nextFloat() < saveChance) {
            held.setDamageValue(Math.max(0, held.getDamageValue() - 1));
        }
    }

    private static void pullNearbyItems(ServerPlayer player, int magnetLevel) {
        if (magnetLevel <= 0) return;
        double radius = magnetLevel == 1 ? 8.0 : (magnetLevel == 2 ? 12.0 : 16.0);
        AABB box = player.getBoundingBox().inflate(radius);
        Vec3 target = player.position().add(0, 0.5, 0);
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(
                ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            Vec3 delta = target.subtract(item.position());
            if (delta.length() > 0.4) {
                item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.42)));
                item.hasImpulse = true;
            }
        }
        for (ExperienceOrb orb : player.serverLevel().getEntitiesOfClass(
                ExperienceOrb.class, box, e -> e.isAlive())) {
            if (magnetLevel >= 3 && orb.distanceToSqr(player) <= 36.0D) {
                orb.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                continue;
            }
            Vec3 delta = target.subtract(orb.position());
            if (delta.length() > 0.4) {
                double pullSpeed = magnetLevel >= 3 ? 0.65 : 0.40;
                orb.setDeltaMovement(orb.getDeltaMovement().scale(0.5).add(delta.normalize().scale(pullSpeed)));
                orb.hasImpulse = true;
            }
        }
    }
}
