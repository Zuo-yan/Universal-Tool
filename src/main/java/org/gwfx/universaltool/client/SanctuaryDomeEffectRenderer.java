package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.phainon.PhainonVfxRenderTypes;
import org.gwfx.universaltool.network.SanctuaryDomeEffectPayload;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class SanctuaryDomeEffectRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int SEA_GLASS = 0x51ECCA;
    private static final int PALE = 0xBFFFE9;
    private static final int CHAMPAGNE = 0xFCE9A2;
    private static final int RIPPLE = 0x67B6E2;
    private static final int OPEN_TICKS = 20;
    private static final int FADE_TICKS = 14;
    private static final int RIBS = 8;
    private static final int MAX_ACTIVE = 24;
    private static final int MAX_DRAWN = 6;
    private static final double DRAW_DISTANCE_SQR = 72.0 * 72.0;
    private static final Map<Long, Dome> DOMES = new LinkedHashMap<>();
    private static ClientLevel level;

    private SanctuaryDomeEffectRenderer() {}

    private static final class Dome {
        final Vec3 center;
        final float radius;
        final long born;
        long fadeAt;

        Dome(Vec3 center, float radius, long born, int duration) {
            this.center = center;
            this.radius = radius;
            this.born = born;
            this.fadeAt = born + duration;
        }
    }

    public static void apply(SanctuaryDomeEffectPayload cue) {
        ClientLevel current = updateLevel();
        if (current == null) return;
        long now = current.getGameTime();
        if (cue.duration() == 0) {
            Dome dome = DOMES.get(cue.id());
            if (dome != null) dome.fadeAt = Math.min(dome.fadeAt, now);
            return;
        }
        if (!Double.isFinite(cue.x()) || !Double.isFinite(cue.y()) || !Double.isFinite(cue.z())
                || !Float.isFinite(cue.radius()) || cue.radius() < 1 || cue.radius() > 16
                || cue.duration() < OPEN_TICKS || cue.duration() > 400 || DOMES.containsKey(cue.id())) return;
        if (DOMES.size() >= MAX_ACTIVE) DOMES.remove(DOMES.keySet().iterator().next());
        DOMES.put(cue.id(), new Dome(new Vec3(cue.x(), cue.y(), cue.z()), cue.radius(), now, cue.duration()));
    }

    private static ClientLevel updateLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            DOMES.clear();
            level = current;
        }
        return current;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel current = updateLevel();
        if (current != null) {
            long now = current.getGameTime();
            DOMES.values().removeIf(dome -> now >= dome.fadeAt + FADE_TICKS);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || DOMES.isEmpty()
                || level != Minecraft.getInstance().level) return;
        var pose = event.getPoseStack();
        Vec3 eye = event.getCamera().getPosition();
        long now = level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var type = PhainonVfxRenderTypes.additiveGlow(WHITE);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = null;
        int rendered = 0;
        for (Dome dome : DOMES.values().stream()
                .sorted(java.util.Comparator.comparingDouble(d -> d.center.distanceToSqr(eye))).toList()) {
            if (rendered >= MAX_DRAWN || dome.center.distanceToSqr(eye) > DRAW_DISTANCE_SQR) break;
            float age = (now - dome.born) + partial;
            float fadeAge = (now - dome.fadeAt) + partial;
            if (age < 0 || fadeAge >= FADE_TICKS) continue;
            if (buffer == null) buffer = buffers.getBuffer(type);
            Vec3 relative = dome.center.subtract(eye);
            boolean inside = relative.lengthSqr() < dome.radius * dome.radius;
            pose.pushPose();
            pose.translate(relative.x, relative.y + 0.10, relative.z);
            draw(pose.last().pose(), buffer, dome.radius, age, fadeAge, inside);
            pose.popPose();
            rendered++;
        }
        if (buffer != null) buffers.endBatch(type);
    }

    private static void draw(Matrix4f matrix, VertexConsumer buffer, float radius, float age,
                             float fadeAge, boolean inside) {
        float close = ease(fadeAge / FADE_TICKS);
        float visibility = (1 - close) * (inside ? 0.72F : 1.0F);
        float opening = ease((age - 2) / (OPEN_TICKS - 2.0F));
        float pulse = 0.92F + 0.08F * Mth.sin(age * 0.11F);
        float rim = radius * Mth.lerp(ease(age / 8), 0.40F, 1.0F);
        ring(matrix, buffer, rim, 0.015F, 0.14F, 64, SEA_GLASS, 0.36F * visibility);
        ring(matrix, buffer, rim * 0.94F, 0.055F, 0.057F, 64, CHAMPAGNE, 0.23F * visibility);
        float shellHeight = radius * 0.84F * Mth.lerp(close, 1.0F, 0.50F);
        float angleMax = Mth.HALF_PI * opening;
        if (angleMax > 0.015F) {
            for (int i = 0; i < RIBS; i++) {
                float bearing = i * Mth.TWO_PI / RIBS;
                float flicker = 0.84F + 0.16F * Mth.sin(age * 0.16F + i * 1.7F);
                meridian(matrix, buffer, bearing, radius, shellHeight, angleMax,
                        0.14F, SEA_GLASS, 0.15F * visibility * flicker);
                meridian(matrix, buffer, bearing, radius, shellHeight, angleMax,
                        0.052F, i % 2 == 0 ? CHAMPAGNE : PALE, 0.40F * visibility * flicker);
            }
        }

        // A thin latitude climbs as the shell forms; no filled surface or full-screen flash.
        if (opening > 0.01F && opening < 0.99F) {
            float leadingAngle = angleMax * 0.97F;
            ring(matrix, buffer, Math.max(0.14F, radius * Mth.cos(leadingAngle)),
                    shellHeight * Mth.sin(leadingAngle), 0.10F, 48, PALE, 0.39F * visibility);
        }

        float settled = ease((age - 13) / 11);
        if (settled > 0) {
            float bandAngle = 0.43F + 0.035F * Mth.sin(age * 0.095F);
            ring(matrix, buffer, radius * Mth.cos(bandAngle), shellHeight * Mth.sin(bandAngle),
                    0.059F, 64, RIPPLE, 0.22F * visibility * settled * pulse);
            float crown = shellHeight * 0.955F;
            ring(matrix, buffer, radius * 0.26F, crown, 0.09F, 40, CHAMPAGNE,
                    0.38F * visibility * settled * pulse);
        }
        if (fadeAge >= 0) {
            float waveRadius = radius * (1.0F + 0.21F * close);
            ring(matrix, buffer, waveRadius, 0.06F + 0.25F * close, 0.14F * (1 - close), 64,
                    CHAMPAGNE, 0.50F * visibility);
        }
    }

    private static void meridian(Matrix4f matrix, VertexConsumer buffer, float bearing, float radius, float height,
                                 float angleMax, float width, int color, float alpha) {
        for (int i = 0; i < 16; i++) {
            float a = angleMax * i / 16.0F;
            float b = angleMax * (i + 1) / 16.0F;
            float r0 = radius * Mth.cos(a);
            float r1 = radius * Mth.cos(b);
            float y0 = height * Mth.sin(a);
            float y1 = height * Mth.sin(b);
            float taper = 1.0F - 0.60F * ease(b / Mth.HALF_PI);
            float east = -Mth.sin(bearing) * width;
            float north = Mth.cos(bearing) * width;
            float x0 = Mth.cos(bearing) * r0, z0 = Mth.sin(bearing) * r0;
            float x1 = Mth.cos(bearing) * r1, z1 = Mth.sin(bearing) * r1;
            quad(matrix, buffer, x0 - east, y0, z0 - north, x0 + east, y0, z0 + north,
                    x1 + east, y1, z1 + north, x1 - east, y1, z1 - north, color, alpha * taper);
        }
    }

    private static void ring(Matrix4f matrix, VertexConsumer buffer, float r, float y, float w, int steps,
                             int color, float alpha) {
        if (alpha <= 0.009F || w <= 0.004F) return;
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
            float ca = Mth.cos(a), sa = Mth.sin(a), cb = Mth.cos(b), sb = Mth.sin(b);
            float inner = Math.max(0, r - w), outer = r + w;
            quad(matrix, buffer, ca * inner, y, sa * inner, ca * outer, y, sa * outer,
                    cb * outer, y, sb * outer, cb * inner, y, sb * inner, color, alpha);
        }
    }

    private static void quad(Matrix4f matrix, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int tint, float alpha) {
        int rgba = (Math.round(alpha * 255) << 24) | tint;
        vertex(matrix, buffer, ax, ay, az, rgba);
        vertex(matrix, buffer, bx, by, bz, rgba);
        vertex(matrix, buffer, cx, cy, cz, rgba);
        vertex(matrix, buffer, ax, ay, az, rgba);
        vertex(matrix, buffer, cx, cy, cz, rgba);
        vertex(matrix, buffer, dx, dy, dz, rgba);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z, int rgba) {
        buffer.addVertex(matrix, x, y, z).setColor(rgba).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static float ease(float x) {
        float t = Mth.clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
