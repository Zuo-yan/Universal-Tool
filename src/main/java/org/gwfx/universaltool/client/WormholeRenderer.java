package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.waypoint.WormholeEntity;
import org.joml.Matrix4f;

/**
 * 空间虫洞渲染器：在玩家前方悬浮渲染双层反向旋转的星渊虫洞漩涡，带平滑张开与坍缩缩放动效。
 */
public class WormholeRenderer extends EntityRenderer<WormholeEntity> {
    private static final ResourceLocation VORTEX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/stargate_portal_vortex.png");
    private static final int SEGMENTS = 24;

    public WormholeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WormholeEntity entity) {
        return VORTEX_TEXTURE;
    }

    @Override
    public void render(WormholeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float age = entity.tickCount + partialTick;
        float openT = Mth.clamp(age / (float) WormholeEntity.OPEN_ANIM_TICKS, 0.0F, 1.0F);
        float scale = easeOutBack(openT);
        if (entity.isClosing()) {
            scale *= 0.45F;
        }
        float pulse = 1.0F + 0.03F * Mth.sin(age * 0.22F);

        poseStack.pushPose();
        poseStack.translate(0.0D, 1.12D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(VORTEX_TEXTURE));
        Matrix4f mat = poseStack.last().pose();

        float outerRx = 0.68F * scale * pulse;
        float outerRy = 1.02F * scale * pulse;
        float outerAngle = age * 0.095F;
        renderDiscSide(mat, consumer, outerRx, outerRy, 0.015F, 1.0F, outerAngle, 165, 195, 255, 225);
        renderDiscSide(mat, consumer, outerRx, outerRy, -0.015F, -1.0F, outerAngle, 165, 195, 255, 225);

        float innerRx = 0.42F * scale * pulse;
        float innerRy = 0.64F * scale * pulse;
        float innerAngle = -age * 0.14F;
        renderDiscSide(mat, consumer, innerRx, innerRy, 0.025F, 1.0F, innerAngle, 220, 255, 255, 240);
        renderDiscSide(mat, consumer, innerRx, innerRy, -0.025F, -1.0F, innerAngle, 220, 255, 255, 240);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void renderDiscSide(Matrix4f mat, VertexConsumer consumer,
                                       float rx, float ry, float z, float nz,
                                       float angle, int r, int g, int b, int a) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
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

    private static float easeOutBack(float t) {
        float c1 = 1.35F;
        float c3 = c1 + 1.0F;
        float x = t - 1.0F;
        return 1.0F + c3 * x * x * x + c1 * x * x;
    }
}
