package org.gwfx.universaltool.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * 万能工具（Universal Tool）—— 镐 · 斧 · 铲 · 锄 · 剑 五合一工具。
 */
public class UniversalToolItem extends TieredItem {

    public UniversalToolItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static Properties properties(Tier tier, int durability, float damageBonus, float speedModifier) {
        return new Properties()
                .durability(durability)
                .attributes(toolAttributes(damageBonus, speedModifier))
                .component(DataComponents.TOOL, toolRules(tier));
    }

    private static Tool toolRules(Tier tier) {
        List<Tool.Rule> rules = new java.util.ArrayList<>();
        // 1. 采掘等级限制：不满足材质等级的方块不掉落
        rules.add(Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops()));
        // 2. 镐/斧/铲/锄四张标签方块按材质挖掘速度挖掘并掉落
        for (var tag : List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE,
                BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE)) {
            rules.add(Tool.Rule.minesAndDrops(tag, tier.getSpeed()));
        }
        // 3. 剑规则：蛛网 15 倍速、sword_efficient 方块 1.5 倍速
        rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB), 15.0F));
        rules.add(Tool.Rule.overrideSpeed(BlockTags.SWORD_EFFICIENT, 1.5F));

        return new Tool(rules, 1.0F, 1);
    }

    private static ItemAttributeModifiers toolAttributes(float damageBonus, float speedModifier) {
        return ItemAttributeModifiers.builder()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_ID, damageBonus,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                Item.BASE_ATTACK_SPEED_ID, speedModifier,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);

        if (context.getClickedFace() == net.minecraft.core.Direction.DOWN) {
            return super.useOn(context);
        }

        // 1. 斧：剥树皮 → 刮铜 → 除蜡
        BlockState modified = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
        if (modified != null) {
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            modified = state.getToolModifiedState(context, ItemAbilities.AXE_SCRAPE, false);
            if (modified != null) {
                level.playSound(player, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.levelEvent(player, 3005, pos, 0);
            } else {
                modified = state.getToolModifiedState(context, ItemAbilities.AXE_WAX_OFF, false);
                if (modified != null) {
                    level.playSound(player, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.levelEvent(player, 3004, pos, 0);
                }
            }
        }
        if (modified != null) {
            return finishTransformation(context, modified);
        }

        // 2. 锄：耕地 / 除根
        modified = state.getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
        if (modified != null) {
            level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return finishTransformation(context, modified);
        }

        // 3. 铲：铲土径 / 熄灭营火
        BlockState flattened = state.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, false);
        if (flattened != null && level.getBlockState(pos.above()).isAir()) {
            level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            return finishTransformation(context, flattened);
        }
        modified = state.getToolModifiedState(context, ItemAbilities.SHOVEL_DOUSE, false);
        if (modified != null) {
            if (!level.isClientSide()) {
                level.levelEvent(null, 1009, pos, 0);
            }
            return finishTransformation(context, modified);
        }

        return super.useOn(context);
    }

    private static InteractionResult finishTransformation(UseOnContext context, BlockState newState) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!level.isClientSide) {
            level.setBlock(pos, newState, Block.UPDATE_ALL_IMMEDIATE);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player, newState));
            ItemStack stack = context.getItemInHand();
            if (player != null) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(itemAbility);
    }
}
