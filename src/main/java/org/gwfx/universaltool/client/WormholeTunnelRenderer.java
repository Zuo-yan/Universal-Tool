package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.waypoint.WormholeCorridorManager;
import org.gwfx.universaltool.waypoint.WormholeEntity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Custom 3D flowing wormhole tunnel renderer for {@code universal_tool:wormhole_transit}.
 * All tunnel tubes, circular pulse hoops, and 3D light ribbons are strictly aligned with the
 * circular exit wormhole aperture ({@code r = 2.45}, {@code centerY = 1.65}) and funnel outward
 * strictly in front of the portal plane ({@code z <= portalZ - 0.12}) so the destination vista
 * in the center remains completely clear and unobstructed.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class WormholeTunnelRenderer {
    private static final ResourceLocation TUNNEL_WALL_TEX =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/misc/wormhole_tunnel_wall.png");
    private static final ResourceLocation TUNNEL_RING_TEX =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/misc/wormhole_tunnel_ring.png");
    private static final ResourceLocation TUNNEL_STREAK_TEX =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/misc/wormhole_tunnel_streak.png");

    private static final RenderType TUNNEL_WALL_LAYER = RenderType.entityTranslucentEmissive(TUNNEL_WALL_TEX);
    private static final RenderType TUNNEL_RING_LAYER = RenderType.entityTranslucentEmissive(TUNNEL_RING_TEX);
    private static final RenderType TUNNEL_STREAK_LAYER = RenderType.entityTranslucentEmissive(TUNNEL_STREAK_TEX);

    private static final float PORTAL_RADIUS = 2.45F;
    private static final float Z_MIN = -5.5F;
    private static final float DEFAULT_EXIT_Z = 12.5F;
    private static final int TUBE_SLICES_Z = 14;
    private static final int RADIAL_SEGMENTS = 48;
    private static final int STREAK_COUNT = 96;
    private static final int RING_COUNT = 5;

    private WormholeTunnelRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!mc.level.dimension().location().equals(WormholeCorridorManager.TRANSIT_DIM_ID)) {
            return;
        }

        int laneX = (Math.abs(mc.player.getUUID().hashCode()) % 512) * 32;
        double axisX = laneX + 0.5D;
        double axisY = 64.0D + 1.65D;
        float portalZ = DEFAULT_EXIT_Z;

        List<WormholeEntity> exits = mc.level.getEntitiesOfClass(
                WormholeEntity.class,
                mc.player.getBoundingBox().inflate(32.0D),
                WormholeEntity::isCorridorExit
        );
        if (!exits.isEmpty()) {
            WormholeEntity exit = exits.get(0);
            axisX = exit.getX();
            axisY = exit.getY() + 1.65D;
            portalZ = (float) exit.getZ();
        }

        float zMax = portalZ - 0.12F;
        if (zMax <= Z_MIN + 1.0F) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = mc.level.getGameTime() + partialTick;
        Vec3 cam = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(axisX - cam.x, axisY - cam.y, -cam.z);
        Matrix4f pose = poseStack.last().pose();

        var bufferSource = mc.renderBuffers().bufferSource();

        // 1. Concentric circular spiraling tunnel membranes funneling out from the exit rim
        VertexConsumer wallVc = bufferSource.getBuffer(TUNNEL_WALL_LAYER);
        renderTunnelTube(wallVc, pose, age, zMax, PORTAL_RADIUS + 0.03F, 3.20F, 0.090F, 0.026F, 64, 120, 235, 255);
        renderTunnelTube(wallVc, pose, age, zMax, 3.85F, 5.15F, 0.055F, -0.020F, 36, 150, 115, 255);
        bufferSource.endBatch(TUNNEL_WALL_LAYER);

        // 2. Smooth 48-segment circular pulse rings detaching from the portal rim and sweeping past the player
        VertexConsumer ringVc = bufferSource.getBuffer(TUNNEL_RING_LAYER);
        renderFlowingRings(ringVc, pose, age, zMax);
        bufferSource.endBatch(TUNNEL_RING_LAYER);

        // 3. 96 high-speed 3D energy ribbons pouring along the tunnel walls and outer cosmic void
        VertexConsumer streakVc = bufferSource.getBuffer(TUNNEL_STREAK_LAYER);
        renderFlowingRibbons(streakVc, pose, age, zMax);
        bufferSource.endBatch(TUNNEL_STREAK_LAYER);

        poseStack.popPose();
    }

    private static void renderTunnelTube(VertexConsumer vc, Matrix4f pose, float age, float zMax,
                                         float radiusAtPortal, float radiusAtBack,
                                         float flowSpeed, float twistRate,
                                         int maxAlpha, int tintR, int tintG, int tintB) {
        float zSpan = zMax - Z_MIN;
        float dz = zSpan / TUBE_SLICES_Z;
        for (int iz = 0; iz < TUBE_SLICES_Z; iz++) {
            float z0 = Z_MIN + iz * dz;
            float z1 = z0 + dz;

            // t = 0 at zMax (portal), 1 at Z_MIN (behind player)
            float t0 = (zMax - z0) / zSpan;
            float t1 = (zMax - z1) / zSpan;
            float r0 = Mth.lerp(t0, radiusAtPortal, radiusAtBack);
            float r1 = Mth.lerp(t1, radiusAtPortal, radiusAtBack);

            int a0 = (int) computeTubeAlpha(1.0F - t0, maxAlpha);
            int a1 = (int) computeTubeAlpha(1.0F - t1, maxAlpha);
            if (a0 <= 1 && a1 <= 1) {
                continue;
            }

            float v0 = z0 * 0.16F + age * flowSpeed;
            float v1 = z1 * 0.16F + age * flowSpeed;

            for (int seg = 0; seg < RADIAL_SEGMENTS; seg++) {
                float ang0 = (float) (seg * Math.PI * 2.0D / RADIAL_SEGMENTS);
                float ang1 = (float) ((seg + 1) * Math.PI * 2.0D / RADIAL_SEGMENTS);
                float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0);
                float c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);

                float u00 = (float) seg / RADIAL_SEGMENTS + z0 * twistRate + age * 0.004F;
                float u10 = (float) (seg + 1) / RADIAL_SEGMENTS + z0 * twistRate + age * 0.004F;
                float u01 = (float) seg / RADIAL_SEGMENTS + z1 * twistRate + age * 0.004F;
                float u11 = (float) (seg + 1) / RADIAL_SEGMENTS + z1 * twistRate + age * 0.004F;

                emitDoubleSidedQuad(vc, pose,
                        c0 * r0, s0 * r0, z0, u00, v0, tintR, tintG, tintB, a0,
                        c1 * r0, s1 * r0, z0, u10, v0, tintR, tintG, tintB, a0,
                        c1 * r1, s1 * r1, z1, u11, v1, tintR, tintG, tintB, a1,
                        c0 * r1, s0 * r1, z1, u01, v1, tintR, tintG, tintB, a1);
            }
        }
    }

    private static void renderFlowingRings(VertexConsumer vc, Matrix4f pose, float age, float zMax) {
        float ringSpan = zMax - Z_MIN;
        for (int r = 0; r < RING_COUNT; r++) {
            float phase = ((age * 0.42F / ringSpan) + (float) r / RING_COUNT) % 1.0F;
            float zFront = zMax - phase * ringSpan;
            float zBack = Math.min(zMax, zFront + 0.45F);

            float fade = Mth.clamp(phase / 0.12F, 0.0F, 1.0F) * Mth.clamp((1.0F - phase) / 0.16F, 0.0F, 1.0F);
            int alpha = (int) (185.0F * fade);
            if (alpha <= 2) {
                continue;
            }

            // Funnel radius matches exact portal rim at phase=0 and widens cleanly as it rushes toward the player
            float baseRadius = Mth.lerp(phase, PORTAL_RADIUS, 3.12F);
            float rInner = baseRadius * 0.94F;
            float rOuter = baseRadius * 1.07F;

            int cr = (r % 2 == 0) ? 135 : 195;
            int cg = (r % 2 == 0) ? 245 : 165;
            int cb = 255;

            for (int seg = 0; seg < RADIAL_SEGMENTS; seg++) {
                float ang0 = (float) (seg * Math.PI * 2.0D / RADIAL_SEGMENTS);
                float ang1 = (float) ((seg + 1) * Math.PI * 2.0D / RADIAL_SEGMENTS);
                float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0);
                float c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);

                float u0 = (float) seg / RADIAL_SEGMENTS;
                float u1 = (float) (seg + 1) / RADIAL_SEGMENTS;

                emitDoubleSidedQuad(vc, pose,
                        c0 * rInner, s0 * rInner, zFront, u0, 0.0F, cr, cg, cb, alpha,
                        c1 * rInner, s1 * rInner, zFront, u1, 0.0F, cr, cg, cb, alpha,
                        c1 * rOuter, s1 * rOuter, zBack, u1, 1.0F, cr, cg, cb, alpha / 3,
                        c0 * rOuter, s0 * rOuter, zBack, u0, 1.0F, cr, cg, cb, alpha / 3);
            }
        }
    }

    private static void renderFlowingRibbons(VertexConsumer vc, Matrix4f pose, float age, float zMax) {
        float zSpan = zMax - Z_MIN;
        for (int i = 0; i < STREAK_COUNT; i++) {
            float h1 = hash(i * 17 + 3);
            float h2 = hash(i * 31 + 11);
            float h3 = hash(i * 53 + 19);
            float h4 = hash(i * 79 + 29);

            boolean outer = i >= 60;
            float speed = outer ? (0.85F + h1 * 0.55F) : (0.58F + h1 * 0.45F);
            float phase = h2 * zSpan;
            float progress = ((age * speed + phase) % zSpan) / zSpan;

            float zHead = zMax - progress * zSpan;
            float length = outer ? (2.0F + h3 * 2.2F) : (1.35F + h3 * 1.65F);
            float zTail = Math.min(zMax, zHead + length);
            if (zTail - zHead <= 0.15F) {
                continue;
            }

            float fadeIn = Mth.clamp((zMax - zHead) / 1.8F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((zHead - Z_MIN) / 2.2F, 0.0F, 1.0F);
            int alpha = (int) (235.0F * fadeIn * fadeOut);
            if (alpha <= 4) {
                continue;
            }

            float baseAngle = h4 * (Mth.PI * 2.0F);
            float swirlHead = baseAngle + zHead * 0.045F + age * 0.015F;
            float swirlTail = baseAngle + zTail * 0.045F + age * 0.015F;

            // Keep all ribbons on or outside the portal rim (r >= 2.38 at portal) and flare outward toward -Z
            float rPortal = outer ? (3.25F + h3 * 4.50F) : (2.38F + h3 * 0.72F);
            float rHead = rPortal * (1.0F + ((zMax - zHead) / zSpan) * 0.28F);
            float rTail = rPortal * (1.0F + ((zMax - zTail) / zSpan) * 0.28F);

            float hx = Mth.cos(swirlHead) * rHead;
            float hy = Mth.sin(swirlHead) * rHead;
            float txPos = Mth.cos(swirlTail) * rTail;
            float tyPos = Mth.sin(swirlTail) * rTail;

            int cr, cg, cb;
            int colorPick = i % 3;
            if (colorPick == 0) {
                cr = 135; cg = 248; cb = 255;
            } else if (colorPick == 1) {
                cr = 205; cg = 148; cb = 255;
            } else {
                cr = 242; cg = 250; cb = 255;
            }

            float halfW = outer ? (0.070F + h1 * 0.050F) : (0.045F + h1 * 0.032F);
            float tanHx = -Mth.sin(swirlHead) * halfW;
            float tanHy = Mth.cos(swirlHead) * halfW;
            float tanTx = -Mth.sin(swirlTail) * halfW;
            float tanTy = Mth.cos(swirlTail) * halfW;

            emitDoubleSidedQuad(vc, pose,
                    hx - tanHx, hy - tanHy, zHead, 0.0F, 0.0F, cr, cg, cb, alpha,
                    hx + tanHx, hy + tanHy, zHead, 1.0F, 0.0F, cr, cg, cb, alpha,
                    txPos + tanTx, tyPos + tanTy, zTail, 1.0F, 1.0F, cr, cg, cb, alpha / 4,
                    txPos - tanTx, tyPos - tanTy, zTail, 0.0F, 1.0F, cr, cg, cb, alpha / 4);

            float normHx = Mth.cos(swirlHead) * halfW;
            float normHy = Mth.sin(swirlHead) * halfW;
            float normTx = Mth.cos(swirlTail) * halfW;
            float normTy = Mth.sin(swirlTail) * halfW;

            emitDoubleSidedQuad(vc, pose,
                    hx - normHx, hy - normHy, zHead, 0.0F, 0.0F, cr, cg, cb, alpha,
                    hx + normHx, hy + normHy, zHead, 1.0F, 0.0F, cr, cg, cb, alpha,
                    txPos + normTx, tyPos + normTy, zTail, 1.0F, 1.0F, cr, cg, cb, alpha / 4,
                    txPos - normTx, tyPos - normTy, zTail, 0.0F, 1.0F, cr, cg, cb, alpha / 4);
        }
    }

    private static float computeTubeAlpha(float tFromBackToPortal, int maxAlpha) {
        if (tFromBackToPortal <= 0.0F || tFromBackToPortal >= 1.0F) {
            return 0.0F;
        }
        float edgeFade = Math.min(
                Mth.clamp(tFromBackToPortal / 0.16F, 0.0F, 1.0F),
                Mth.clamp((1.0F - tFromBackToPortal) / 0.18F, 0.0F, 1.0F)
        );
        return maxAlpha * edgeFade;
    }

    private static float hash(int seed) {
        int x = seed * 0x27d4eb2d;
        x = (x ^ (x >>> 15)) * 0x85ebca6b;
        x = (x ^ (x >>> 13));
        return (x & 0xFFFF) / 65535.0F;
    }

    private static void emitDoubleSidedQuad(VertexConsumer vc, Matrix4f pose,
                                            float x0, float y0, float z0, float u0, float v0, int r0, int g0, int b0, int a0,
                                            float x1, float y1, float z1, float u1, float v1, int r1, int g1, int b1, int a1,
                                            float x2, float y2, float z2, float u2, float v2, int r2, int g2, int b2, int a2,
                                            float x3, float y3, float z3, float u3, float v3, int r3, int g3, int b3, int a3) {
        vc.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a0).setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3).setUv(u3, v3)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);

        vc.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3).setUv(u3, v3)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a0).setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
    }
}
