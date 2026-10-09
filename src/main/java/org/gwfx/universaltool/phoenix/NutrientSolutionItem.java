package org.gwfx.universaltool.phoenix;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class NutrientSolutionItem extends Item {

    public NutrientSolutionItem(Properties properties) {
        super(properties.stacksTo(16).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 仅在潜行状态下执行战备刻印
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack targetStack = held;
                boolean split = false;

                // 若手持多瓶，拆出 1 瓶进行独立刻印
                if (held.getCount() > 1 && !player.getAbilities().instabuild) {
                    held.shrink(1);
                    targetStack = new ItemStack(this);
                    split = true;
                }

                // 捕获全身 41 格装备与物资快照
                ListTag snapshotList = new ListTag();
                Inventory inv = player.getInventory();
                int recordedCount = 0;

                // 0 ~ 35: 主背包
                for (int i = 0; i < inv.items.size(); i++) {
                    ItemStack st = inv.items.get(i);
                    // 避免快照中无限套娃记录正在使用的这瓶原液
                    if (hand == InteractionHand.MAIN_HAND && i == inv.selected) {
                        continue;
                    }
                    if (!st.isEmpty()) {
                        CompoundTag entry = new CompoundTag();
                        entry.putInt("Slot", i);
                        entry.put("Item", st.save(level.registryAccess()));
                        snapshotList.add(entry);
                        recordedCount++;
                    }
                }

                // 36 ~ 39: 盔甲栏 (36=脚, 37=腿, 38=胸, 39=头)
                for (int i = 0; i < inv.armor.size(); i++) {
                    ItemStack st = inv.armor.get(i);
                    if (!st.isEmpty()) {
                        CompoundTag entry = new CompoundTag();
                        entry.putInt("Slot", 36 + i);
                        entry.put("Item", st.save(level.registryAccess()));
                        snapshotList.add(entry);
                        recordedCount++;
                    }
                }

                // 40: 副手
                for (int i = 0; i < inv.offhand.size(); i++) {
                    ItemStack st = inv.offhand.get(i);
                    if (hand == InteractionHand.OFF_HAND) {
                        continue;
                    }
                    if (!st.isEmpty()) {
                        CompoundTag entry = new CompoundTag();
                        entry.putInt("Slot", 40 + i);
                        entry.put("Item", st.save(level.registryAccess()));
                        snapshotList.add(entry);
                        recordedCount++;
                    }
                }

                final int totalItems = recordedCount;
                CustomData.update(DataComponents.CUSTOM_DATA, targetStack, tag -> {
                    tag.putBoolean("HasSnapshot", true);
                    tag.putInt("ItemCount", totalItems);
                    tag.put("InventorySnapshot", snapshotList);
                });

                if (split) {
                    if (!player.getInventory().add(targetStack)) {
                        player.drop(targetStack, false);
                    }
                }

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);

                player.displayClientMessage(Component.translatable("message.universal_tool.snapshot_success", totalItems), true);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }

        return super.use(level, player, hand);
    }

    public static boolean hasSnapshot(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("HasSnapshot");
    }

    public static int getSnapshotItemCount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("ItemCount")) {
            return data.copyTag().getInt("ItemCount");
        }
        return 0;
    }

    public static ListTag getSnapshotTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("InventorySnapshot")) {
            return data.copyTag().getList("InventorySnapshot", Tag.TAG_COMPOUND);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (hasSnapshot(stack)) {
            int count = getSnapshotItemCount(stack);
            tooltip.add(Component.translatable("tooltip.universal_tool.nutrient.encoded").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.universal_tool.nutrient.item_count", count).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.universal_tool.nutrient.re_record_tip").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.universal_tool.nutrient_solution.desc").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.universal_tool.nutrient.record_tip").withStyle(ChatFormatting.GRAY));
        }
    }
}
