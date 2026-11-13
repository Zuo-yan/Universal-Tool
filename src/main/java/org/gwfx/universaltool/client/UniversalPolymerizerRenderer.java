package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.gwfx.universaltool.block.UniversalPolymerizerBlockEntity;

/**
 * Renders the 12 astrolabe ring inputs and central result item hovering above the
 * Universal Polymerizer, with converging orbital motion and arcane particles while crafting.
 */
public class UniversalPolymerizerRenderer implements BlockEntityRenderer<UniversalPolymerizerBlockEntity> {

    private static final int RING_SLOTS = 12;
    private static final float BASE_RADIUS = 0.36F;
    private static final float CONVERGE_RADIUS = 0.10F;

    private final ItemRenderer itemRenderer;

    public UniversalPolymerizerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            UniversalPolymerizerBlockEntity be,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {

        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        int itemLight = level != null ? LevelRenderer.getLightColor(level, pos.above()) : packedLight;
        float time = (level != null ? level.getGameTime() : 0L) + partialTick;

        int progress = be.getCraftingProgress();
        int duration = Math.max(1, be.getCraftingDuration());
        boolean crafting = progress > 0;
        // craftingProgress counts down from duration -> 0, so elapsed ratio rises 0 -> 1
        float craftRatio = crafting ? Mth.clamp(1.0F - (float) progress / duration, 0.0F, 1.0F) : 0.0F;
        float easeIn = craftRatio * craftRatio;

        float radius = crafting ? Mth.lerp(easeIn, BASE_RADIUS, CONVERGE_RADIUS) : BASE_RADIUS;
        float orbitSpin = crafting ? easeIn * Mth.TWO_PI * 1.5F : 0.0F;
        int seedBase = (int) pos.asLong();

        for (int i = 0; i < RING_SLOTS; i++) {
            ItemStack stack = be.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            float baseAngle = i * (Mth.TWO_PI / RING_SLOTS) - Mth.HALF_PI;
            float angle = baseAngle + orbitSpin;
            float x = 0.5F + Mth.cos(angle) * radius;
            float z = 0.5F + Mth.sin(angle) * radius;
            float bob = Mth.sin(time * 0.08F + i * 0.52F) * 0.025F;
            float y = (crafting ? Mth.lerp(easeIn, 0.90F, 0.96F) : 0.91F) + bob;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (crafting ? 4.5F : 1.4F) + i * 30.0F));
            float scale = crafting ? Mth.lerp(easeIn, 0.24F, 0.18F) : 0.24F;
            poseStack.scale(scale, scale, scale);
            itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    itemLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    level,
                    seedBase + i);
            poseStack.popPose();
        }

        ItemStack centerStack = be.getItem(UniversalPolymerizerBlockEntity.SLOT_RESULT);
        if (!centerStack.isEmpty()) {
            float centerBob = Mth.sin(time * 0.1F) * 0.03F;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.98F + centerBob, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.0F));
            poseStack.scale(0.38F, 0.38F, 0.38F);
            itemRenderer.renderStatic(
                    centerStack,
                    ItemDisplayContext.FIXED,
                    itemLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    level,
                    seedBase + RING_SLOTS);
            poseStack.popPose();
        }

        if (crafting && level != null && !Minecraft.getInstance().isPaused()) {
            spawnCraftingParticles(level, pos, craftRatio, radius, orbitSpin);
        }
    }

    private static void spawnCraftingParticles(
            Level level, BlockPos pos, float craftRatio, float radius, float orbitSpin) {
        if (level.random.nextFloat() < 0.28F) {
            float angle = level.random.nextFloat() * Mth.TWO_PI + orbitSpin;
            double px = pos.getX() + 0.5 + Mth.cos(angle) * radius;
            double pz = pos.getZ() + 0.5 + Mth.sin(angle) * radius;
            double py = pos.getY() + 0.90 + level.random.nextDouble() * 0.08;
            double vx = (pos.getX() + 0.5 - px) * 0.08;
            double vz = (pos.getZ() + 0.5 - pz) * 0.08;
            level.addParticle(ParticleTypes.END_ROD, px, py, pz, vx, 0.02 + craftRatio * 0.02, vz);
        }
        if (level.random.nextFloat() < 0.35F) {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 1.05;
            double cz = pos.getZ() + 0.5;
            double ox = (level.random.nextDouble() - 0.5) * 0.5;
            double oz = (level.random.nextDouble() - 0.5) * 0.5;
            level.addParticle(ParticleTypes.ENCHANT, cx, cy, cz, ox, 0.15, oz);
        }
    }
}
