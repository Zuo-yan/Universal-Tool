package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloud-free cosmic sky effects for the 25x25 Personal Starry Space:
 * deep indigo-midnight gradient, subtle galactic nebula band, and a dense multi-tiered twinkling starfield.
 */
public final class StarryPocketDimensionEffects extends DimensionSpecialEffects {
    private static final float SKY_RADIUS = 96.0F;
    private static final int STAR_COUNT = 2800;
    private static final int NEBULA_PUFF_COUNT = 48;

    private final List<StarVertexQuad> stars;
    private final List<NebulaFan> nebulaPuffs;

    public StarryPocketDimensionEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
        RandomSource random = RandomSource.create(20261113L);
        this.stars = buildStarfield(random);
        this.nebulaPuffs = buildGalacticBand(random);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return new Vec3(0.035, 0.045, 0.105);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        // Suppress vanilla clouds completely in the personal starry pocket.
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        renderSkyDome(tesselator, modelViewMatrix);
        renderGalacticBand(tesselator, modelViewMatrix);
        renderStars(tesselator, modelViewMatrix, ticks + partialTick);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        return true;
    }

    private static void renderSkyDome(Tesselator tesselator, Matrix4f pose) {
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] elevations = {-90F, -35F, -5F, 8F, 28F, 58F, 90F};
        int[][] colors = {
                {3, 5, 14},     // deep nadir
                {6, 10, 24},    // lower void
                {11, 18, 42},   // horizon band
                {10, 16, 38},   // just above horizon
                {6, 10, 26},    // mid sky
                {3, 5, 16},     // upper sky
                {2, 3, 10}      // zenith
        };
        int slices = 24;
        float r = 108.0F;
        for (int ring = 0; ring < elevations.length - 1; ring++) {
            float phi0 = elevations[ring] * Mth.DEG_TO_RAD;
            float phi1 = elevations[ring + 1] * Mth.DEG_TO_RAD;
            float y0 = Mth.sin(phi0) * r;
            float rad0 = Mth.cos(phi0) * r;
            float y1 = Mth.sin(phi1) * r;
            float rad1 = Mth.cos(phi1) * r;

            int[] c0 = colors[ring];
            int[] c1 = colors[ring + 1];

            for (int s = 0; s < slices; s++) {
                float theta0 = (float) (s * 2.0 * Math.PI / slices);
                float theta1 = (float) ((s + 1) * 2.0 * Math.PI / slices);
                float x00 = Mth.cos(theta0) * rad0;
                float z00 = Mth.sin(theta0) * rad0;
                float x01 = Mth.cos(theta1) * rad0;
                float z01 = Mth.sin(theta1) * rad0;
                float x11 = Mth.cos(theta1) * rad1;
                float z11 = Mth.sin(theta1) * rad1;
                float x10 = Mth.cos(theta0) * rad1;
                float z10 = Mth.sin(theta0) * rad1;

                builder.addVertex(pose, x00, y0, z00).setColor(c0[0], c0[1], c0[2], 255);
                builder.addVertex(pose, x01, y0, z01).setColor(c0[0], c0[1], c0[2], 255);
                builder.addVertex(pose, x11, y1, z11).setColor(c1[0], c1[1], c1[2], 255);
                builder.addVertex(pose, x10, y1, z10).setColor(c1[0], c1[1], c1[2], 255);
            }
        }
        drawMesh(builder);
    }

    private void renderGalacticBand(Tesselator tesselator, Matrix4f pose) {
        for (NebulaFan puff : nebulaPuffs) {
            BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(pose, puff.center.x, puff.center.y, puff.center.z)
                    .setColor(puff.r, puff.g, puff.b, puff.a);
            for (Vector3f rimVertex : puff.rim) {
                builder.addVertex(pose, rimVertex.x, rimVertex.y, rimVertex.z)
                        .setColor(puff.r, puff.g, puff.b, 0);
            }
            drawMesh(builder);
        }
    }

    private void renderStars(Tesselator tesselator, Matrix4f pose, float time) {
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (StarVertexQuad star : stars) {
            float twinkle = 0.72F + 0.28F * Mth.sin(time * star.twinkleSpeed + star.twinklePhase);
            int alpha = Mth.clamp(Math.round(star.baseAlpha * twinkle), 45, 255);
            emitQuad(builder, pose, star.corners, star.r, star.g, star.b, alpha);
        }
        drawMesh(builder);
    }

    private static void emitQuad(BufferBuilder builder, Matrix4f pose, Vector3f[] corners, int r, int g, int b, int a) {
        builder.addVertex(pose, corners[0].x, corners[0].y, corners[0].z).setColor(r, g, b, a);
        builder.addVertex(pose, corners[1].x, corners[1].y, corners[1].z).setColor(r, g, b, a);
        builder.addVertex(pose, corners[2].x, corners[2].y, corners[2].z).setColor(r, g, b, a);
        builder.addVertex(pose, corners[3].x, corners[3].y, corners[3].z).setColor(r, g, b, a);
    }

    private static void drawMesh(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static List<StarVertexQuad> buildStarfield(RandomSource random) {
        List<StarVertexQuad> result = new ArrayList<>(STAR_COUNT);
        // Tilted galactic plane normal so roughly 45% of stars concentrate along a natural Milky Way arc.
        Vector3f bandNormal = new Vector3f(0.42F, 0.72F, -0.55F).normalize();

        for (int i = 0; i < STAR_COUNT; i++) {
            Vector3f dir;
            if (i < STAR_COUNT * 45 / 100) {
                dir = sampleGalacticBandDirection(random, bandNormal, 0.26F);
            } else {
                double u = random.nextDouble() * 2.0 - 1.0;
                double phi = random.nextDouble() * Math.PI * 2.0;
                double s = Math.sqrt(1.0 - u * u);
                dir = new Vector3f((float) (s * Math.cos(phi)), (float) u, (float) (s * Math.sin(phi)));
            }

            // Keep stars visible mostly above and around the horizon (-0.25 .. +1.0)
            if (dir.y < -0.28F) {
                dir.y = Math.abs(dir.y);
            }
            dir.normalize();

            float tier = random.nextFloat();
            float size;
            int baseAlpha;
            if (tier < 0.76F) {
                size = 0.13F + random.nextFloat() * 0.11F;
                baseAlpha = 145 + random.nextInt(85);
            } else if (tier < 0.95F) {
                size = 0.25F + random.nextFloat() * 0.15F;
                baseAlpha = 200 + random.nextInt(55);
            } else {
                size = 0.42F + random.nextFloat() * 0.18F;
                baseAlpha = 235 + random.nextInt(20);
            }

            int[] tint = pickStarTint(random.nextFloat());
            float twinkleSpeed = 0.04F + random.nextFloat() * 0.11F;
            float twinklePhase = random.nextFloat() * Mth.TWO_PI;
            result.add(new StarVertexQuad(
                    buildBillboardQuad(dir, SKY_RADIUS, size),
                    tint[0], tint[1], tint[2], baseAlpha, twinkleSpeed, twinklePhase
            ));
        }
        return result;
    }

    private static List<NebulaFan> buildGalacticBand(RandomSource random) {
        List<NebulaFan> result = new ArrayList<>(NEBULA_PUFF_COUNT);
        Vector3f bandNormal = new Vector3f(0.42F, 0.72F, -0.55F).normalize();
        for (int i = 0; i < NEBULA_PUFF_COUNT; i++) {
            Vector3f dir = sampleGalacticBandDirection(random, bandNormal, 0.20F);
            if (dir.y < -0.1F) {
                dir.y = Math.abs(dir.y);
                dir.normalize();
            }
            float radius = 7.0F + random.nextFloat() * 8.0F;
            boolean cyanTone = random.nextBoolean();
            int r = cyanTone ? 36 : 64;
            int g = cyanTone ? 74 : 48;
            int b = cyanTone ? 138 : 132;
            int a = 18 + random.nextInt(14);
            result.add(buildRadialFan(dir, SKY_RADIUS + 4.0F, radius, r, g, b, a));
        }
        return result;
    }

    private static NebulaFan buildRadialFan(Vector3f dir, float distance, float radius, int r, int g, int b, int a) {
        Vector3f center = new Vector3f(dir).mul(distance);
        Vector3f ref = Math.abs(dir.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f right = new Vector3f(dir).cross(ref).normalize().mul(radius);
        Vector3f up = new Vector3f(right).cross(dir).normalize().mul(radius);
        int segments = 12;
        Vector3f[] rim = new Vector3f[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            rim[i] = new Vector3f(center)
                    .add(new Vector3f(right).mul(Mth.cos(angle)))
                    .add(new Vector3f(up).mul(Mth.sin(angle)));
        }
        return new NebulaFan(center, rim, r, g, b, a);
    }

    private static Vector3f sampleGalacticBandDirection(RandomSource random, Vector3f bandNormal, float spread) {
        Vector3f ref = Math.abs(bandNormal.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(bandNormal).cross(ref).normalize();
        Vector3f v = new Vector3f(bandNormal).cross(u).normalize();
        float angle = random.nextFloat() * Mth.TWO_PI;
        float offset = (float) (random.nextGaussian() * spread);
        return new Vector3f(u).mul(Mth.cos(angle))
                .add(new Vector3f(v).mul(Mth.sin(angle)))
                .add(new Vector3f(bandNormal).mul(offset))
                .normalize();
    }

    private static Vector3f[] buildBillboardQuad(Vector3f dir, float distance, float halfSize) {
        Vector3f center = new Vector3f(dir).mul(distance);
        Vector3f ref = Math.abs(dir.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f right = new Vector3f(dir).cross(ref).normalize().mul(halfSize);
        Vector3f up = new Vector3f(right).cross(dir).normalize().mul(halfSize);
        return new Vector3f[] {
                new Vector3f(center).sub(right).sub(up),
                new Vector3f(center).add(right).sub(up),
                new Vector3f(center).add(right).add(up),
                new Vector3f(center).sub(right).add(up)
        };
    }

    private static int[] pickStarTint(float roll) {
        if (roll < 0.48F) return new int[]{240, 245, 255};      // starlight white
        if (roll < 0.72F) return new int[]{168, 224, 255};      // ice cyan
        if (roll < 0.88F) return new int[]{212, 188, 255};      // pale astral violet
        return new int[]{255, 234, 184};                         // warm amber-gold
    }

    private record StarVertexQuad(Vector3f[] corners, int r, int g, int b, int baseAlpha,
                                  float twinkleSpeed, float twinklePhase) {}

    private record NebulaFan(Vector3f center, Vector3f[] rim, int r, int g, int b, int a) {}
}
