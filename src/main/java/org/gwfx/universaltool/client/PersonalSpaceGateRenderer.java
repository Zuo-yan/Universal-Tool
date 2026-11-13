package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.space.PersonalSpaceGateBlock;
import org.gwfx.universaltool.space.PersonalSpaceGateBlockEntity;
import org.joml.Matrix4f;

/**
 * 星门动态能量漩涡渲染器：平时保持中空苔石符文门框；开启后在两格高尖拱内以旋转UV贴合椭圆门洞渲染电青色螺旋漩涡。
 */
public class PersonalSpaceGateRenderer implements BlockEntityRenderer<PersonalSpaceGateBlockEntity> {
    private static final ResourceLocation VORTEX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/stargate_portal_vortex.png");
    private static final int SEGMENTS = 24;

    public PersonalSpaceGateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(PersonalSpaceGateBlockEntity gate) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PersonalSpaceGateBlockEntity gate) {
        return new AABB(gate.getBlockPos()).inflate(1.25D, 1.8D, 1.25D);
    }

    @Override
    public void render(PersonalSpaceGateBlockEntity gate, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = gate.getBlockState();
        if (!state.hasProperty(PersonalSpaceGateBlock.OPEN) || !state.getValue(PersonalSpaceGateBlock.OPEN)) {
            return;
        }

        float time = gate.getLevel() != null ? gate.getLevel().getGameTime() + partialTick : partialTick;
        Direction facing = state.getValue(PersonalSpaceGateBlock.FACING);

        poseStack.pushPose();
        // 锚定在两格高尖拱门洞中心 (y = 0.28 .. 2.28, width = 1.12)
        poseStack.translate(0.5D, 1.28D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawForFacing(facing)));

        VertexConsumer vortexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(VORTEX_TEXTURE));
        Matrix4f mat = poseStack.last().pose();
        float rx = 0.56F;
        float ry = 1.00F;
        float angle = time * 0.075F;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        renderOvalVortexSide(mat, vortexConsumer, rx, ry, 0.015F, 1.0F, cos, sin, 235, 255, 255, 240);
        renderOvalVortexSide(mat, vortexConsumer, rx, ry, -0.015F, -1.0F, cos, sin, 235, 255, 255, 240);

        poseStack.popPose();
    }

    private static void renderOvalVortexSide(Matrix4f mat, VertexConsumer consumer,
                                             float rx, float ry, float z, float nz,
                                             float cos, float sin, int r, int g, int b, int a) {
        for (int i = 0; i < SEGMENTS; i++) {
            int i0 = nz > 0 ? i : i + 1;
            int i1 = nz > 0 ? i + 1 : i;
            float a0 = (float) (i0 * Math.PI * 2.0D / SEGMENTS);
            float a1 = (float) (i1 * Math.PI * 2.0D / SEGMENTS);

            float nx0 = Mth.cos(a0);
            float ny0 = Mth.sin(a0);
            float nx1 = Mth.cos(a1);
            float ny1 = Mth.sin(a1);

            float u0 = 0.5F + (nx0 * cos - ny0 * sin) * 0.47F;
            float v0 = 0.5F + (nx0 * sin + ny0 * cos) * 0.47F;
            float u1 = 0.5F + (nx1 * cos - ny1 * sin) * 0.47F;
            float v1 = 0.5F + (nx1 * sin + ny1 * cos) * 0.47F;

            // 使用退化四边形 (center, center, p0, p1) 在 QUADS 模式下无缝绘制椭圆扇形，避免方角超出门框
            consumer.addVertex(mat, 0.0F, 0.0F, z).setColor(r, g, b, a)
                    .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, nz);
            consumer.addVertex(mat, 0.0F, 0.0F, z).setColor(r, g, b, a)
                    .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, nz);
            consumer.addVertex(mat, nx0 * rx, ny0 * ry, z).setColor(r, g, b, a)
                    .setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, nz);
            consumer.addVertex(mat, nx1 * rx, ny1 * ry, z).setColor(r, g, b, a)
                    .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, nz);
        }
    }

    private static float yawForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
