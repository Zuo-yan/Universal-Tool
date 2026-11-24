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
import org.gwfx.universaltool.phainon.PhainonNormalSkillHandler;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;
import org.gwfx.universaltool.phainon.PhainonWeaponState;

import java.util.List;

/**
 * 白厄的武器（侵晨 / Dawnmaker）。
 * <p>普通状态下右键可释放战技「黎明创世，地辟天开」，造成扩散物理伤害并积攒【火种】。</p>
 */
public final class QinchenSwordItem extends SwordItem {
    public QinchenSwordItem() {
        super(Tiers.NETHERITE, new Properties()
                .rarity(ModRarities.MYTHIC.getValue())
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true))
                // 1 player damage + 4 tier damage + 2 sword damage, and 4 player speed + 3 sword speed.
                .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 2, 3.0F)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (ModDataComponents.getPhainonWeaponState(player.getItemInHand(hand)).canAwaken()) {
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
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.normal_skill_title"));
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.normal_skill_usage"));
        tooltipComponents.add(Component.translatable("tooltip.universal_tool.qinchen_sword.ultimate_usage"));
        tooltipComponents.add(Component.translatable(
                "tooltip.universal_tool.qinchen_sword.coreflame",
                state.coreflame(),
                PhainonWeaponState.MAX_COREFLAME
        ));
    }
}
