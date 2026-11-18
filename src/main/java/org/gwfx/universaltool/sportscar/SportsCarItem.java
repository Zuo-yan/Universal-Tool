package org.gwfx.universaltool.sportscar;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.init.ModEntities;

import java.util.List;

/**
 * 海湾 GT 跑车放置物品（右键按玩家朝向放置，检查碰撞空间后生成实体）。
 */
public class SportsCarItem extends Item {

    public SportsCarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        SportsCarEntity car = ModEntities.GULF_GT_CAR.get().create(serverLevel);
        if (car == null) {
            return InteractionResult.FAIL;
        }

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        Vec3 spawnPos = computeSpawnPosition(clickedPos, clickedFace, context.getClickLocation(), car);

        car.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        Player player = context.getPlayer();
        if (player != null) {
            car.setCarYaw(player.getYRot());
        }

        // 严格检查放置空间是否存在碰撞阻塞，避免跑车卡入墙体
        if (!serverLevel.noCollision(car, car.getBoundingBox())) {
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(car);
        serverLevel.playSound(null, car.getX(), car.getY(), car.getZ(),
                SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.PLAYERS, 0.85F, 1.1F);

        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    private static Vec3 computeSpawnPosition(BlockPos clickedPos, Direction clickedFace,
                                            Vec3 clickLocation, SportsCarEntity car) {
        double cx = clickedPos.getX() + 0.5D;
        double cz = clickedPos.getZ() + 0.5D;
        if (clickedFace == Direction.UP) {
            return new Vec3(cx, clickLocation.y + 0.01D, cz);
        }
        if (clickedFace == Direction.DOWN) {
            return new Vec3(cx, clickedPos.getY() - car.getBbHeight() - 0.02D, cz);
        }
        double offset = 0.5D + car.getBbWidth() * 0.5D + 0.02D;
        return new Vec3(
                cx + clickedFace.getStepX() * offset,
                clickedPos.getY(),
                cz + clickedFace.getStepZ() * offset
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.universal_tool.gulf_gt_car.desc")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.universal_tool.gulf_gt_car.controls")
                .withStyle(ChatFormatting.GRAY));
    }
}
