package org.gwfx.universaltool.phainon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.item.QinchenSwordItem;

import javax.annotation.Nullable;

/**
 * 白厄武器「侵晨」的击杀成长处理器：
 * <ul>
 *     <li>手持「侵晨」通过普攻或任意侵晨技能击杀生物时，累计击杀数 +1；</li>
 *     <li>每累计击杀 12 个生物，动态提升主手基础攻击伤害 +1（7 → 8 → 9…），并保持原攻击速度 7 不变；</li>
 *     <li>全技能伤害统一通过 {@link PhainonWeaponState#scaleSkillDamage(float)} 同步获得对应成长加成。</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonKillGrowthHandler {

    private PhainonKillGrowthHandler() {
    }

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.QINCHEN_SWORD.get())) {
            return;
        }
        int bonus = ModDataComponents.getPhainonWeaponState(stack).bonusDamage();
        if (bonus <= 0) {
            return;
        }
        // 使用原版 BASE_ATTACK_DAMAGE_ID 替换主手基础伤害，确保面板绿色总伤害与实际普攻同步成长且保留 7 攻速
        event.replaceModifier(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(
                        Item.BASE_ATTACK_DAMAGE_ID,
                        QinchenSwordItem.BASE_DAMAGE_MODIFIER + bonus,
                        AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
        );
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof ArmorStand) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim) {
            return;
        }
        ItemStack sword = findHeldSword(killer);
        if (sword == null) {
            return;
        }
        recordKill(killer, sword, 1);
    }

    /**
     * 为指定侵晨剑记录击杀数，并在突破 12 杀阶梯时提示玩家。
     */
    public static PhainonWeaponState recordKill(@Nullable ServerPlayer killer, ItemStack sword, int delta) {
        if (sword.isEmpty() || !sword.is(ModItems.QINCHEN_SWORD.get()) || delta <= 0) {
            return ModDataComponents.getPhainonWeaponState(sword);
        }
        PhainonWeaponState before = ModDataComponents.getPhainonWeaponState(sword);
        PhainonWeaponState after = before.withAddedKills(delta);
        ModDataComponents.setPhainonWeaponState(sword, after);

        if (killer != null && after.bonusDamage() > before.bonusDamage()) {
            int totalAttack = (int) PhainonWeaponState.BASE_ATTACK_DAMAGE + after.bonusDamage();
            killer.displayClientMessage(
                    Component.translatable(
                            "message.universal_tool.phainon.kill_growth_levelup",
                            after.kills(),
                            after.bonusDamage(),
                            totalAttack
                    ),
                    true
            );
            killer.serverLevel().playSound(
                    null,
                    killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.85F,
                    1.25F
            );
        }
        return after;
    }

    @Nullable
    private static ItemStack findHeldSword(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.QINCHEN_SWORD.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.QINCHEN_SWORD.get())) {
            return off;
        }
        return null;
    }
}
