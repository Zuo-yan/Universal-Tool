package org.gwfx.universaltool.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all 12 passive skills: attribute modifiers, damage reduction/clamping,
 * combat momentum, lifesteal, item magnet, and out-of-combat survival flow.
 */
final class SkillPassiveHandler {
    private static final ResourceLocation IRON_PHYSIQUE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "iron_physique");
    private static final ResourceLocation SWIFT_PACE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "swift_pace");
    private static final ResourceLocation BULWARK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "diamond_bulwark");

    private static final Map<UUID, Momentum> MOMENTUM = new ConcurrentHashMap<>();

    private record Momentum(int stacks, long lastHitGameTime) {}

    private SkillPassiveHandler() {}

    static void clearAll() {
        MOMENTUM.clear();
    }

    static void clearMomentum(UUID playerId) {
        MOMENTUM.remove(playerId);
    }

    static int equippedLevel(ServerPlayer player, String id) {
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
        updateBulwark(player, equippedLevel(player, ModSkills.DIAMOND_BULWARK));

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

    static void clearAllPassives(ServerPlayer player) {
        updateBulwark(player, 0);
        MOMENTUM.remove(player.getUUID());
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(IRON_PHYSIQUE_MODIFIER_ID);
        }
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SWIFT_PACE_MODIFIER_ID);
        }
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

    static void clampFinalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
                || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        int rank = equippedLevel(player, ModSkills.DAMAGE_CLAMP);
        if (rank > 0) {
            event.setNewDamage(Math.min(event.getNewDamage(), player.getMaxHealth() * damageClampRatio(rank)));
        }
    }

    static void applyIncomingVictimPassives(ServerPlayer victim, LivingIncomingDamageEvent event) {
        applyLastStand(victim, event);
        applyLightFeather(victim, event);
    }

    static void applyIncomingAttackerPassives(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        applyMomentum(attacker, event);
        applyArmorPiercing(attacker, event);
    }

    static void trackMomentum(ServerPlayer player, LivingEntity target, long gameTime) {
        if (target == player || equippedLevel(player, ModSkills.BATTLE_MOMENTUM) <= 0) {
            return;
        }
        Momentum previous = MOMENTUM.get(player.getUUID());
        int stacks = previous != null && gameTime - previous.lastHitGameTime() <= SkillManager.MOMENTUM_WINDOW_TICKS
                ? Math.min(SkillManager.MOMENTUM_MAX_STACKS, previous.stacks() + 1)
                : 1;
        MOMENTUM.put(player.getUUID(), new Momentum(stacks, gameTime));
    }

    static void applyBloodthirst(ServerPlayer player, LivingEntity target, float damageDealt) {
        int blood = equippedLevel(player, ModSkills.BLOODTHIRST);
        if (blood <= 0 || damageDealt <= 0) {
            return;
        }
        float heal = Math.max(0.2F, damageDealt * (blood * 0.05F));
        player.heal(heal);
    }

    static void tickPassives(ServerPlayer player, long gameTime) {
        if (gameTime % 2 == 0) {
            pullNearbyItems(player, equippedLevel(player, ModSkills.ITEM_MAGNET));
        }
        if (player.tickCount % 20 == 0) {
            applyPassiveEffects(player);
        }
        if (player.tickCount % 60 == 0) {
            int flow = equippedLevel(player, ModSkills.SURVIVAL_FLOW);
            PlayerSkillData data = SkillManager.data(player);
            if (flow > 0 && !data.inCombat(gameTime) && player.getHealth() < player.getMaxHealth()) {
                player.heal(0.5F * flow);
            }
        }
    }

    private static void applyLastStand(ServerPlayer victim, LivingIncomingDamageEvent event) {
        int level = equippedLevel(victim, ModSkills.LAST_STAND);
        if (level <= 0) {
            return;
        }
        float max = victim.getMaxHealth();
        if (max <= 0 || victim.getHealth() / max >= 0.35F) {
            return;
        }
        float reduction = Math.min(0.6F, 0.15F * level);
        event.setAmount(event.getAmount() * (1.0F - reduction));
    }

    private static void applyLightFeather(ServerPlayer player, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.FALL)) {
            return;
        }
        int feather = equippedLevel(player, ModSkills.LIGHT_FEATHER);
        if (feather <= 0) {
            return;
        }
        if (feather >= 3) {
            event.setCanceled(true);
            player.fallDistance = 0.0F;
        } else {
            float reduction = feather == 1 ? 0.40F : 0.75F;
            event.setAmount(Math.max(0.0F, event.getAmount() * (1.0F - reduction)));
        }
    }

    private static void applyMomentum(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int level = equippedLevel(attacker, ModSkills.BATTLE_MOMENTUM);
        if (level <= 0) {
            return;
        }
        Momentum momentum = MOMENTUM.get(attacker.getUUID());
        if (momentum == null) {
            return;
        }
        long gameTime = attacker.serverLevel().getGameTime();
        if (gameTime - momentum.lastHitGameTime() > SkillManager.MOMENTUM_WINDOW_TICKS) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + 0.04F * momentum.stacks() * level));
    }

    private static void applyArmorPiercing(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int pierce = equippedLevel(attacker, ModSkills.ARMOR_PIERCING);
        if (pierce <= 0) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + pierce * 0.15F));
    }

    private static void pullNearbyItems(ServerPlayer player, int magnetLevel) {
        if (magnetLevel <= 0) {
            return;
        }
        double radius = 5.0 + 2.0 * magnetLevel;
        AABB box = player.getBoundingBox().inflate(radius);
        Vec3 target = player.position().add(0, 0.5, 0);
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(
                ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            Vec3 delta = target.subtract(item.position());
            if (delta.length() > 0.4) {
                item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.35)));
                item.hasImpulse = true;
            }
        }
        for (ExperienceOrb orb : player.serverLevel().getEntitiesOfClass(
                ExperienceOrb.class, box, e -> e.isAlive())) {
            Vec3 delta = target.subtract(orb.position());
            if (delta.length() > 0.4) {
                orb.setDeltaMovement(orb.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.35)));
                orb.hasImpulse = true;
            }
        }
    }
}
