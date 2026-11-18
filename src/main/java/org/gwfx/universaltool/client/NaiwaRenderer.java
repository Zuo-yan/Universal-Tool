package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.naiwa.NaiwaEntity;
import org.joml.Matrix4f;

/**
 * Renders {@link NaiwaEntity} with its hierarchical Blockbench bone mesh, keyframe
 * belly-laugh / breathing idle animations, procedural walk &amp; head tracking,
 * hurt flash overlay, and expanding golden-cyan sonic shockwave rings while laughing.
 */
public final class NaiwaRenderer extends EntityRenderer<NaiwaEntity> {

    private static final int RING_SEGMENTS = 28;

    public NaiwaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.65F;
    }

    @Override
    public ResourceLocation getTextureLocation(NaiwaEntity entity) {
        return NaiwaModelData.TEXTURE;
    }

    @Override
    public void render(NaiwaEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {
        NaiwaModelData model = NaiwaModelData.getOrLoad();
        if (model == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        float limbSwingAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTick));
        float limbSwing = entity.walkAnimation.position(partialTick);

        boolean laughing = entity.isLaughing();
        float animTime = entity.getLaughAnimTimeSeconds(partialTick);
        int packedOverlay = OverlayTexture.pack(
                OverlayTexture.u(0.0F),
                OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));

        poseStack.pushPose();
        // Model front is -Z in Blockbench; rotating (180 - bodyYaw) aligns -Z with Minecraft's yaw look vector.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

        if (entity.deathTime > 0) {
            float fall = ((float) entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            fall = Math.min(1.0F, Mth.sqrt(Math.max(0.0F, fall)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(fall * 90.0F));
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(NaiwaModelData.TEXTURE));
        model.render(poseStack, consumer, packedLight, packedOverlay,
                animTime, laughing, netHeadYaw, headPitch, limbSwing, limbSwingAmount);

        if (laughing) {
            renderSonicShockwaves(poseStack, bufferSource, animTime, headPitch);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void renderSonicShockwaves(PoseStack poseStack,
                                              MultiBufferSource bufferSource,
                                              float animTime,
                                              float headPitch) {
        VertexConsumer ringBuffer = bufferSource.getBuffer(RenderType.lightning());

        // 3 staggered golden-cyan sonic rings radiating forward from Naiwa's mouth (-Z)
        poseStack.pushPose();
        poseStack.translate(0.0F, 1.45F, -0.42F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.clamp(headPitch, -35.0F, 35.0F)));

        Matrix4f forwardMat = poseStack.last().pose();
        for (int i = 0; i < 3; i++) {
            float phase = (animTime * 1.65F + i / 3.0F) % 1.0F;
            float zOffset = -phase * 4.8F;
            float innerR = 0.28F + phase * 1.35F;
            float outerR = innerR + 0.16F * (1.0F - phase * 0.45F);
            float alpha = (1.0F - phase) * 0.85F;

            int aInner = (int) (alpha * 220.0F);
            int aOuter = (int) (alpha * 170.0F);
            if (aInner <= 4) {
                continue;
            }
            emitVerticalRing(forwardMat, ringBuffer, zOffset, innerR, outerR,
                    70, 235, 255, aInner,
                    255, 200, 60, aOuter);
        }
        poseStack.popPose();

        // Outward horizontal ground/torso shockwave pulse around Naiwa
        float pulsePhase = (animTime * 1.25F) % 1.0F;
        float hInner = 0.65F + pulsePhase * 2.2F;
        float hOuter = hInner + 0.22F * (1.0F - pulsePhase * 0.4F);
        int hAlpha = (int) ((1.0F - pulsePhase) * 185.0F);
        if (hAlpha > 4) {
            emitHorizontalRing(poseStack.last().pose(), ringBuffer, 0.92F, hInner, hOuter,
                    255, 210, 75, hAlpha,
                    65, 230, 255, (int) (hAlpha * 0.6F));
        }
    }

    private static void emitVerticalRing(Matrix4f mat,
                                         VertexConsumer buffer,
                                         float z,
                                         float innerR,
                                         float outerR,
                                         int rIn, int gIn, int bIn, int aIn,
                                         int rOut, int gOut, int bOut, int aOut) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = (float) (2.0 * Math.PI * i / RING_SEGMENTS);
            float a1 = (float) (2.0 * Math.PI * (i + 1) / RING_SEGMENTS);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);

            float ix0 = c0 * innerR;
            float iy0 = s0 * innerR;
            float ox0 = c0 * outerR;
            float oy0 = s0 * outerR;
            float ix1 = c1 * innerR;
            float iy1 = s1 * innerR;
            float ox1 = c1 * outerR;
            float oy1 = s1 * outerR;

            // Front-facing & back-facing quads
            buffer.addVertex(mat, ix0, iy0, z).setColor(rIn, gIn, bIn, aIn);
            buffer.addVertex(mat, ox0, oy0, z).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ox1, oy1, z).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ix1, iy1, z).setColor(rIn, gIn, bIn, aIn);

            buffer.addVertex(mat, ix1, iy1, z).setColor(rIn, gIn, bIn, aIn);
            buffer.addVertex(mat, ox1, oy1, z).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ox0, oy0, z).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ix0, iy0, z).setColor(rIn, gIn, bIn, aIn);
        }
    }

    private static void emitHorizontalRing(Matrix4f mat,
                                           VertexConsumer buffer,
                                           float y,
                                           float innerR,
                                           float outerR,
                                           int rIn, int gIn, int bIn, int aIn,
                                           int rOut, int gOut, int bOut, int aOut) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = (float) (2.0 * Math.PI * i / RING_SEGMENTS);
            float a1 = (float) (2.0 * Math.PI * (i + 1) / RING_SEGMENTS);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);

            float ix0 = c0 * innerR;
            float iz0 = s0 * innerR;
            float ox0 = c0 * outerR;
            float oz0 = s0 * outerR;
            float ix1 = c1 * innerR;
            float iz1 = s1 * innerR;
            float ox1 = c1 * outerR;
            float oz1 = s1 * outerR;

            buffer.addVertex(mat, ix0, y, iz0).setColor(rIn, gIn, bIn, aIn);
            buffer.addVertex(mat, ox0, y, oz0).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ox1, y, oz1).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ix1, y, iz1).setColor(rIn, gIn, bIn, aIn);

            buffer.addVertex(mat, ix1, y, iz1).setColor(rIn, gIn, bIn, aIn);
            buffer.addVertex(mat, ox1, y, oz1).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ox0, y, oz0).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ix0, y, iz0).setColor(rIn, gIn, bIn, aIn);
        }
    }
}
