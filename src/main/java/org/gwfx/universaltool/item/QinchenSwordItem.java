package org.gwfx.universaltool.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.phainon.PhainonCounter;
import org.gwfx.universaltool.phainon.PhainonMeteor;
import org.gwfx.universaltool.phainon.PhainonNormalSkillHandler;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;
import org.gwfx.universaltool.phainon.PhainonWeaponState;

import java.util.List;

/**
 * 白厄的武器（侵晨 / Dawnmaker）。
 * <p>普通状态下右键可释放战技「黎明创世，地辟天开」，造成扩散物理伤害并积攒【火种】；
 * 集齐 12 点火种后可释放终结技「永劫燔世，其将背负」觉醒，解锁全套觉醒技能；
 * 每击杀 12 个生物永久 +1 基础攻击伤害并同步强化所有技能伤害。</p>
 */
public final class QinchenSwordItem extends SwordItem {
    /** 侵晨基础武器攻击加成（与下界合金 4 及玩家基础 1 合计 7 点初始攻击伤害） */
    public static final int BASE_SWORD_DAMAGE_BONUS = 2;
    /** 写入主手攻击伤害属性修饰符的基础值（4.0 + 2 = 6.0，叠加玩家 1.0 后总计 7.0） */
    public static final double BASE_DAMAGE_MODIFIER = Tiers.NETHERITE.getAttackDamageBonus() + BASE_SWORD_DAMAGE_BONUS;

    public QinchenSwordItem() {
        super(Tiers.NETHERITE, new Properties()
                .rarity(ModRarities.MYTHIC.getValue())
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true))
                // 玩家基础 1 + 下界合金 4 + 武器 2 = 7 攻击伤害；玩家基础 4 + 武器 3 = 7 攻击速度
                .attributes(SwordItem.createAttributes(Tiers.NETHERITE, BASE_SWORD_DAMAGE_BONUS, 3.0F)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var state = ModDataComponents.getPhainonWeaponState(player.getItemInHand(hand));
        if (state.transformed()) return player.isShiftKeyDown()
                ? PhainonCounter.use(player, hand) : PhainonMeteor.use(level, player, hand);
        if (state.canAwaken()) {
            return PhainonUltimateHandler.tryStart(level, player, hand);
        }
        return PhainonNormalSkillHandler.tryStartCast(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (entity instanceof ServerPlayer owner && ModDataComponents.getPhainonWeaponState(stack).transformed()) {
            PhainonUltimateHandler.clearOrphanedForm(owner, stack);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (!slotChanged && oldStack.is(this) && newStack.is(this)
                && ModDataComponents.getPhainonWeaponState(oldStack).transformed()
                   != ModDataComponents.getPhainonWeaponState(newStack).transformed()) {
            return false;
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        PhainonWeaponState state = ModDataComponents.getPhainonWeaponState(stack);

        // 当前形态
        tooltipComponents.add(Component.translatable(state.transformed()
                ? "tooltip.universal_tool.qinchen_sword.form_awakened"
                : "tooltip.universal_tool.qinchen_sword.form_normal"));

        // 普通战技（蓝金）
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.normal_skill_title"));
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.normal_skill_usage"));

        // 终结技（炽金）
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.ultimate_title"));
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.ultimate_usage"));

        // 觉醒三技能（绯红普攻 / 琥珀金陨石 / 紫红金弹反）
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.enhanced_basic_usage"));
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.meteor_usage"));
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.counter_usage"));

        // 当前火种进度
        tooltipComponents.add(Component.translatable(
                state.canAwaken()
                        ? "tooltip.universal_tool.qinchen_sword.coreflame_ready"
                        : "tooltip.universal_tool.qinchen_sword.coreflame",
                state.coreflame(),
                PhainonWeaponState.MAX_COREFLAME
        ));

        // 击杀成长（每 12 杀 +1 攻击伤害，同步强化全技能）
        tooltipComponents.add(Component.translatable(
                "tooltip.universal_tool.qinchen_sword.kill_growth",
                state.kills(),
                state.bonusDamage(),
                state.killsInCurrentStage(),
                PhainonWeaponState.KILLS_PER_DAMAGE
        ));
    }
}
