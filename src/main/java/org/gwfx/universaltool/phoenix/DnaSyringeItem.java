package org.gwfx.universaltool.phoenix;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.gwfx.universaltool.init.ModItems;

import java.util.List;

public class DnaSyringeItem extends Item {

    public DnaSyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 无论何种模式，给予 1 秒操作冷却防止手抖连点
        player.getCooldowns().addCooldown(this, 20);

        if (!level.isClientSide) {
            // 安全稳定扣除 1 点生命值 (0.5 颗心，确保生存模式不被无敌帧阻挡)
            float newHealth = Math.max(1.0F, player.getHealth() - 1.0F);
            player.setHealth(newHealth);

            // 生成带有玩家完整身份信息的 DNA 样本
            ItemStack sample = new ItemStack(ModItems.DNA_SAMPLE.get());
            CustomData.update(DataComponents.CUSTOM_DATA, sample, tag -> {
                tag.putUUID("OwnerUUID", player.getUUID());
                tag.putString("OwnerName", player.getGameProfile().getName());
            });

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6F, 1.4F);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }

            if (!player.getInventory().add(sample)) {
                player.drop(sample, false);
            }

            player.displayClientMessage(Component.translatable("message.universal_tool.dna_extracted", player.getGameProfile().getName()), true);
        }

        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.universal_tool.dna_syringe.desc"));
    }
}
