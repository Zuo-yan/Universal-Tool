package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.waypoint.WormholeEntity;
import org.joml.Matrix4f;

/**
 * Renders a spatial wormhole with a parallax destination vista inside its oval aperture
 * framed by a hollow swirling cosmic energy rim.
 */
public class WormholeRenderer extends EntityRenderer<WormholeEntity> {
    private static final ResourceLocation VORTEX_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/entity/stargate_portal_vortex.png");
    private static final int SEGMENTS = 24;

    public WormholeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WormholeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float open = entity.getOpenProgress(partialTick);
        if (open <= 0.01F) {
            return;
        }

        float age = entity.tickCount + partialTick;
        float pulse = 1.0F + 0.03F * Mth.sin(age * 0.16F);
        float rx = 0.64F * open * pulse;
        float ry = 1.02F * open * pulse;

        poseStack.pushPose();
        poseStack.translate(0.0D, 1.08D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));

        Matrix4f pose = poseStack.last().pose();

        // 1. Inner destination vista aperture (waypoint screenshot with parallax, or dimension-themed horizon)
        ResourceLocation photoTex = PhotoManager.getOrLoadTexture(entity.getPhotoId());
        if (photoTex != null) {
            renderPhotoVista(entity, pose, bufferSource, photoTex, rx, ry, open);
        } else {
            renderFallbackDimensionVista(entity.getDestDimension(), pose, bufferSource, rx, ry, age, open);
        }

        // 2. Hollow swirling cosmic energy perimeter rim (keeps center vista unobstructed)
        renderSwirlingRim(pose, bufferSource, rx, ry, age, open);

        poseStack.popPose();
    }

    private static void renderPhotoVista(WormholeEntity entity, Matrix4f pose, MultiBufferSource bufferSource,
                                         ResourceLocation photoTex, float rx, float ry, float open) {
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 toCam = camPos.subtract(entity.getX(), entity.getY() + 1.08D, entity.getZ());
        double radYaw = Math.toRadians(entity.getYRot());
        double rightX = Math.cos(radYaw);
        double rightZ = Math.sin(radYaw);
        double dist = Math.max(0.8D, toCam.length());
        float parallaxU = Mth.clamp((float) ((toCam.x * rightX + toCam.z * rightZ) / dist) * 0.10F, -0.11F, 0.11F);
        float parallaxV = Mth.clamp((float) (toCam.y / dist) * 0.10F, -0.11F, 0.11F);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(photoTex));
        float rAper = 0.78F;
        float cxU = 0.5F + parallaxU;
        float cyV = 0.5F - parallaxV;
        int centerAlpha = (int) (245 * open);
        int edgeAlpha = (int) (205 * open);

        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2.0D / SEGMENTS);
            float a1 = (float) ((i + 1) * Math.PI * 2.0D / SEGMENTS);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            float x0 = c0 * rx * rAper, y0 = s0 * ry * rAper;
            float x1 = c1 * rx * rAper, y1 = s1 * ry * rAper;

            float u0 = Mth.clamp(cxU + c0 * 0.36F, 0.02F, 0.98F);
            float v0 = Mth.clamp(cyV - s0 * 0.36F, 0.02F, 0.98F);
            float u1 = Mth.clamp(cxU + c1 * 0.36F, 0.02F, 0.98F);
            float v1 = Mth.clamp(cyV - s1 * 0.36F, 0.02F, 0.98F);

            // Quad fan slice (duplicate center vertex for QUADS)
            emitVistaQuad(vc, pose,
                    0.0F, 0.0F, cxU, cyV, centerAlpha,
                    x0, y0, u0, v0, edgeAlpha,
                    x1, y1, u1, v1, edgeAlpha);
        }
    }

    private static void renderFallbackDimensionVista(String destDim, Matrix4f pose, MultiBufferSource bufferSource,
                                                     float rx, float ry, float age, float open) {
        int topR = 70, topG = 185, topB = 255;
        int botR = 45, botG = 195, botB = 165;
        if (destDim.contains("nether")) {
            topR = 175; topG = 42; topB = 42;
            botR = 248; botG = 142; botB = 48;
        } else if (destDim.contains("end")) {
            topR = 32; topG = 18; topB = 64;
            botR = 168; botG = 86; botB = 248;
        }

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(VORTEX_TEXTURE));
        float rot = age * 0.035F;
        float cr = Mth.cos(rot), sr = Mth.sin(rot);
        float rAper = 0.78F;
        int centerAlpha = (int) (210 * open);
        int edgeAlpha = (int) (175 * open);

        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2.0D / SEGMENTS);
            float a1 = (float) ((i + 1) * Math.PI * 2.0D / SEGMENTS);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            float t0 = 0.5F + 0.5F * s0;
            float t1 = 0.5F + 0.5F * s1;
            int r0 = (int) Mth.lerp(t0, botR, topR), g0 = (int) Mth.lerp(t0, botG, topG), b0 = (int) Mth.lerp(t0, botB, topB);
            int r1 = (int) Mth.lerp(t1, botR, topR), g1 = (int) Mth.lerp(t1, botG, topG), b1 = (int) Mth.lerp(t1, botB, topB);

            float u0 = 0.5F + (c0 * cr - s0 * sr) * 0.35F;
            float v0 = 0.5F + (c0 * sr + s0 * cr) * 0.35F;
            float u1 = 0.5F + (c1 * cr - s1 * sr) * 0.35F;
            float v1 = 0.5F + (c1 * sr + s1 * cr) * 0.35F;

            // Front and back
            vc.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor((topR + botR) / 2, (topG + botG) / 2, (topB + botB) / 2, centerAlpha)
                    .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
            vc.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor((topR + botR) / 2, (topG + botG) / 2, (topB + botB) / 2, centerAlpha)
                    .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
            vc.addVertex(pose, c0 * rx * rAper, s0 * ry * rAper, 0.0F).setColor(r0, g0, b0, edgeAlpha)
                    .setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
            vc.addVertex(pose, c1 * rx * rAper, s1 * ry * rAper, 0.0F).setColor(r1, g1, b1, edgeAlpha)
                    .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        }
    }

    private static void renderSwirlingRim(Matrix4f pose, MultiBufferSource bufferSource,
                                          float rx, float ry, float age, float open) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(VORTEX_TEXTURE));
        float rot = age * 0.085F;
        float cr = Mth.cos(rot), sr = Mth.sin(rot);
        float rIn = 0.72F;
        float rOut = 1.06F;
        int innerAlpha = (int) (235 * open);
        int outerAlpha = (int) (95 * open);

        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2.0D / SEGMENTS);
            float a1 = (float) ((i + 1) * Math.PI * 2.0D / SEGMENTS);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            float u0In = 0.5F + (c0 * cr - s0 * sr) * 0.34F;
            float v0In = 0.5F + (c0 * sr + s0 * cr) * 0.34F;
            float u1In = 0.5F + (c1 * cr - s1 * sr) * 0.34F;
            float v1In = 0.5F + (c1 * sr + s1 * cr) * 0.34F;

            float u0Out = 0.5F + (c0 * cr - s0 * sr) * 0.49F;
            float v0Out = 0.5F + (c0 * sr + s0 * cr) * 0.49F;
            float u1Out = 0.5F + (c1 * cr - s1 * sr) * 0.49F;
            float v1Out = 0.5F + (c1 * sr + s1 * cr) * 0.49F;

            // Front & back rim quad
            for (int side = -1; side <= 1; side += 2) {
                float z = side * 0.025F;
                vc.addVertex(pose, c0 * rx * rIn, s0 * ry * rIn, z).setColor(145, 245, 255, innerAlpha)
                        .setUv(u0In, v0In).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, side);
                vc.addVertex(pose, c0 * rx * rOut, s0 * ry * rOut, z).setColor(168, 120, 255, outerAlpha)
                        .setUv(u0Out, v0Out).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, side);
                vc.addVertex(pose, c1 * rx * rOut, s1 * ry * rOut, z).setColor(168, 120, 255, outerAlpha)
                        .setUv(u1Out, v1Out).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, side);
                vc.addVertex(pose, c1 * rx * rIn, s1 * ry * rIn, z).setColor(145, 245, 255, innerAlpha)
                        .setUv(u1In, v1In).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, side);
            }
        }
    }

    private static void emitVistaQuad(VertexConsumer vc, Matrix4f pose,
                                      float cx, float cy, float cu, float cv, int ca,
                                      float x0, float y0, float u0, float v0, int a0,
                                      float x1, float y1, float u1, float v1, int a1) {
        vc.addVertex(pose, cx, cy, 0.0F).setColor(255, 255, 255, ca)
                .setUv(cu, cv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, cx, cy, 0.0F).setColor(255, 255, 255, ca)
                .setUv(cu, cv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x0, y0, 0.0F).setColor(255, 255, 255, a0)
                .setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x1, y1, 0.0F).setColor(255, 255, 255, a1)
                .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);

        vc.addVertex(pose, cx, cy, 0.0F).setColor(255, 255, 255, ca)
                .setUv(cu, cv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, cx, cy, 0.0F).setColor(255, 255, 255, ca)
                .setUv(cu, cv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x1, y1, 0.0F).setColor(255, 255, 255, a1)
                .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x0, y0, 0.0F).setColor(255, 255, 255, a0)
                .setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(WormholeEntity entity) {
        return VORTEX_TEXTURE;
    }
}
