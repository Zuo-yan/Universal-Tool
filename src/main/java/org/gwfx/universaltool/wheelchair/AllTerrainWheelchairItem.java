package org.gwfx.universaltool.wheelchair;

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
 * Deployable item for the All-Terrain Wheelchair (Roadster omni-magnetic vehicle).
 */
public class AllTerrainWheelchairItem extends Item {

    public AllTerrainWheelchairItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        Direction attachedFace = clickedFace.getOpposite();

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        AllTerrainWheelchairEntity wheelchair = ModEntities.ALL_TERRAIN_WHEELCHAIR.get().create(serverLevel);
        if (wheelchair == null) {
            return InteractionResult.FAIL;
        }

        Vec3 faceNormal = Vec3.atLowerCornerOf(clickedFace.getNormal());
        // Place center flush against the clicked block face without embedding into ceiling/walls
        double halfExtent = clickedFace.getAxis() == Direction.Axis.Y
                ? wheelchair.getBbHeight() * 0.5D
                : wheelchair.getBbWidth() * 0.5D;
        Vec3 faceCenter = Vec3.atCenterOf(clickedPos).add(faceNormal.scale(0.5D));
        Vec3 spawnCenter = faceCenter.add(faceNormal.scale(halfExtent + 0.01D));
        Vec3 spawnPos = spawnCenter.subtract(0.0D, wheelchair.getBbHeight() * 0.5D, 0.0D);

        wheelchair.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        wheelchair.setAttachedFace(attachedFace);

        Player player = context.getPlayer();
        if (player != null) {
            Vec3 look = attachedFace.getAxis() == Direction.Axis.Y
                    ? Vec3.directionFromRotation(0.0F, player.getYRot())
                    : player.getLookAngle();
            wheelchair.setWheelchairYaw(wheelchair.yawForForward(attachedFace, look));
        }

        if (!serverLevel.noCollision(wheelchair, wheelchair.getBoundingBox())) {
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(wheelchair);
        serverLevel.playSound(null, wheelchair.getX(), wheelchair.getY(), wheelchair.getZ(),
                SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.PLAYERS, 0.85F, 1.2F);

        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.universal_tool.all_terrain_wheelchair.desc")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.universal_tool.all_terrain_wheelchair.controls")
                .withStyle(ChatFormatting.GRAY));
    }
}
