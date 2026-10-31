package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.CounterGuardEffectPayload;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * High-craft visual renderer for the Counter Guard skill.
 *
 * <p>Replaces old circling shield quads with an energy aegis dome:
 * - Idle/Expand: Translucent hexagonal forcefield sphere with breathing pulse,
 *   fresnel edge brilliance, orbiting gyro energy ring, and ground ward glyph.
 *   First-person view softly fades inner opacity to ensure zero crosshair obstruction.
 * - Trigger (Parry): Sudden impact absorption flash followed by a wide sonic shockwave ring
 *   expanding outwards, accompanied by shield crystallization burst.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class CounterGuardEffectRenderer {
    private static final ResourceLocation AEGIS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                    "textures/misc/counter_guard_aegis.png");
    private static final ResourceLocation RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                    "textures/misc/counter_guard_ring.png");

    private static final RenderType AEGIS_LAYER =
            RenderType.entityTranslucent(AEGIS_TEXTURE);
    private static final RenderType RING_LAYER =
            RenderType.entityTranslucent(RING_TEXTURE);

    private static final int EXPAND_TICKS = 10;
    private static final int TRIGGER_TICKS = 18;
    private static final float BASE_RADIUS = 1.25F;

    private static final int SPHERE_STACKS = 8;
    private static final int SPHERE_SLICES = 16;

    private static final Map<Integer, GuardVisual> ACTIVE = new HashMap<>();
    private static long nextSequence;

    public static void apply(CounterGuardEffectPayload payload) {
        if (payload.sequence() < nextSequence) return;
        nextSequence = payload.sequence();
        if (payload.action() == CounterGuardEffectPayload.Action.CLEAR) {
            ACTIVE.remove(payload.playerId());
        } else {
            ACTIVE.put(payload.playerId(), new GuardVisual(
                    payload.action(),
                    Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            nextSequence = 0L;
            return;
        }
        long now = minecraft.level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            GuardVisual visual = entry.getValue();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            return entity == null || !entity.isAlive()
                    || (visual.action() == CounterGuardEffectPayload.Action.TRIGGER
                    && now - visual.startedAt() > TRIGGER_TICKS);
        });
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || ACTIVE.isEmpty()
                || Minecraft.getInstance().level == null
                || Minecraft.getInstance().player == null
                || Minecraft.getInstance().options.hideGui) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = Minecraft.getInstance().level.getGameTime();
        double cameraX = event.getCamera().getPosition().x;
        double cameraY = event.getCamera().getPosition().y;
        double cameraZ = event.getCamera().getPosition().z;

        record ActiveRender(Player owner, GuardVisual visual, float age, boolean isFirstPerson,
                            double px, double py, double pz) {}

        java.util.List<ActiveRender> renderList = new java.util.ArrayList<>();
        for (Map.Entry<Integer, GuardVisual> entry : ACTIVE.entrySet()) {
            Entity entity = Minecraft.getInstance().level.getEntity(entry.getKey());
            if (!(entity instanceof Player owner) || !owner.isAlive()) continue;

            GuardVisual visual = entry.getValue();
            float age = Math.max(0.0F, (gameTime - visual.startedAt()) + partialTick);

            boolean isFirstPerson = (owner == Minecraft.getInstance().player
                    && Minecraft.getInstance().options.getCameraType().isFirstPerson());

            double px = Mth.lerp(partialTick, owner.xOld, owner.getX()) - cameraX;
            double py = Mth.lerp(partialTick, owner.yOld, owner.getY()) - cameraY;
            double pz = Mth.lerp(partialTick, owner.zOld, owner.getZ()) - cameraZ;

            renderList.add(new ActiveRender(owner, visual, age, isFirstPerson, px, py, pz));
        }

        if (renderList.isEmpty()) {
            return;
        }

        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        pose.pushPose();

        // Pass 1: Translucent forcefield sphere (AEGIS_LAYER)
        var aegisBuffer = bufferSource.getBuffer(AEGIS_LAYER);
        for (ActiveRender r : renderList) {
            if (r.visual.action() == CounterGuardEffectPayload.Action.EXPAND) {
                renderIdleAegisSphere(pose, aegisBuffer, r.px, r.py, r.pz, r.age, r.isFirstPerson);
            } else if (r.visual.action() == CounterGuardEffectPayload.Action.TRIGGER) {
                renderTriggerParrySphere(pose, aegisBuffer, r.px, r.py, r.pz, r.age, r.isFirstPerson);
            }
        }
        bufferSource.endBatch(AEGIS_LAYER);

        // Pass 2: Orbiting gyro rings and ground glyph (RING_LAYER)
        var ringBuffer = bufferSource.getBuffer(RING_LAYER);
        for (ActiveRender r : renderList) {
            if (r.visual.action() == CounterGuardEffectPayload.Action.EXPAND) {
                renderIdleAegisRings(pose, ringBuffer, r.px, r.py, r.pz, r.age, r.isFirstPerson);
            } else if (r.visual.action() == CounterGuardEffectPayload.Action.TRIGGER) {
                renderTriggerParryRings(pose, ringBuffer, r.px, r.py, r.pz, r.age, r.isFirstPerson);
            }
        }
        bufferSource.endBatch(RING_LAYER);

        pose.popPose();
    }

    private static void renderIdleAegisSphere(PoseStack pose, VertexConsumer aegisBuffer,
                                              double px, double py, double pz, float age, boolean isFirstPerson) {
        if (isFirstPerson) return; // Keep 1st-person clear of view-blocking sphere forcefields
        float expandRatio = Math.min(1.0F, age / (float) EXPAND_TICKS);
        float scale = easeOutBack(expandRatio);
        float pulse = 0.035F * Mth.sin(age * 0.14F);
        float radius = (BASE_RADIUS + pulse) * scale;

        float alpha = 0.65F * Math.min(1.0F, age / 4.0F);

        float uvScrollU = age * 0.008F;
        float uvScrollV = age * 0.012F;

        // Forcefield Sphere
        pose.pushPose();
        pose.translate(px, py + 0.95, pz);
        renderSphere(pose.last().pose(), aegisBuffer, radius, uvScrollU, uvScrollV,
                240, 205, 110, alpha);
        pose.popPose();
    }

    private static void renderIdleAegisRings(PoseStack pose, VertexConsumer ringBuffer,
                                             double px, double py, double pz, float age, boolean isFirstPerson) {
        float expandRatio = Math.min(1.0F, age / (float) EXPAND_TICKS);
        float scale = easeOutBack(expandRatio);
        float pulse = 0.035F * Mth.sin(age * 0.14F);
        float radius = (BASE_RADIUS + pulse) * scale;

        // 1. Orbiting Equatorial Gyro Ring (3rd person only - avoids blocking crosshair in 1st person)
        if (!isFirstPerson) {
            pose.pushPose();
            pose.translate(px, py + 0.95, pz);
            pose.mulPose(Axis.YP.rotationDegrees(age * 2.2F));
            pose.mulPose(Axis.XP.rotationDegrees(18.0F * Mth.sin(age * 0.08F)));
            float gyroRadius = radius * 1.08F;
            float gyroAlpha = 0.75F * scale;
            renderHorizontalDisk(pose.last().pose(), ringBuffer, 0.0F, gyroRadius,
                    255, 230, 140, gyroAlpha);
            pose.popPose();
        }

        // 2. Ground Ward Glyph (feet level py + 0.03: subtle indicator in 1st person without blocking sight)
        pose.pushPose();
        pose.translate(px, py + 0.03, pz);
        pose.mulPose(Axis.YP.rotationDegrees(-age * 1.2F));
        float groundRadius = radius * 1.15F;
        float groundAlpha = (isFirstPerson ? 0.45F : 0.70F) * scale;
        renderHorizontalDisk(pose.last().pose(), ringBuffer, 0.0F, groundRadius,
                230, 190, 90, groundAlpha);
        pose.popPose();
    }

    private static void renderTriggerParrySphere(PoseStack pose, VertexConsumer aegisBuffer,
                                                 double px, double py, double pz, float age, boolean isFirstPerson) {
        if (isFirstPerson) return; // Avoid blinding flash covering crosshair in 1st person
        if (age < 3.5F) {
            float flashRatio = age / 3.5F;
            float contract = 1.0F - 0.15F * Mth.sin(flashRatio * Mth.PI);
            float radius = BASE_RADIUS * contract;

            pose.pushPose();
            pose.translate(px, py + 0.95, pz);
            renderSphere(pose.last().pose(), aegisBuffer, radius, age * 0.05F, age * 0.08F,
                    255, 255, 255, 0.95F);
            pose.popPose();
        }
    }

    private static void renderTriggerParryRings(PoseStack pose, VertexConsumer ringBuffer,
                                                double px, double py, double pz, float age, boolean isFirstPerson) {
        if (age >= 1.0F) {
            float progress = Math.min(1.0F, (age - 1.0F) / (float) (TRIGGER_TICKS - 1));
            float shockRadius = BASE_RADIUS + 2.8F * easeOutCubic(progress);

            // Horizontal waist blast wave (3rd person only)
            if (!isFirstPerson) {
                float shockAlpha = Math.max(0.0F, (1.0F - progress) * 0.90F);
                pose.pushPose();
                pose.translate(px, py + 0.85, pz);
                pose.mulPose(Axis.YP.rotationDegrees(age * 5.0F));
                renderHorizontalDisk(pose.last().pose(), ringBuffer, 0.0F, shockRadius,
                        255, 245, 180, shockAlpha);
                pose.popPose();
            }

            // Ground expanding ripple (feet level py + 0.03: visible cue in both 1st and 3rd person)
            float groundAlpha = Math.max(0.0F, (1.0F - progress) * (isFirstPerson ? 0.60F : 0.85F));
            pose.pushPose();
            pose.translate(px, py + 0.03, pz);
            pose.mulPose(Axis.YP.rotationDegrees(-age * 4.0F));
            renderHorizontalDisk(pose.last().pose(), ringBuffer, 0.0F, shockRadius * 1.15F,
                    255, 215, 110, groundAlpha);
            pose.popPose();
        }
    }

    private static void renderSphere(Matrix4f matrix, VertexConsumer buffer, float radius,
                                     float uOffset, float vOffset, int r, int g, int b, float alpha) {
        if (alpha <= 0.01F) return;
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        for (int stack = 0; stack < SPHERE_STACKS; stack++) {
            float theta0 = (float) stack / SPHERE_STACKS * Mth.PI;
            float theta1 = (float) (stack + 1) / SPHERE_STACKS * Mth.PI;

            float sinT0 = Mth.sin(theta0);
            float cosT0 = Mth.cos(theta0);
            float sinT1 = Mth.sin(theta1);
            float cosT1 = Mth.cos(theta1);

            float v0 = (float) stack / SPHERE_STACKS * 2.0F + vOffset;
            float v1 = (float) (stack + 1) / SPHERE_STACKS * 2.0F + vOffset;

            // Rim alpha highlight near the equator
            float equatorialRim0 = 0.6F + 0.4F * sinT0;
            float equatorialRim1 = 0.6F + 0.4F * sinT1;

            int color0 = ((int) (Mth.clamp(alpha * equatorialRim0, 0.0F, 1.0F) * 255.0F) << 24)
                    | (r << 16) | (g << 8) | b;
            int color1 = ((int) (Mth.clamp(alpha * equatorialRim1, 0.0F, 1.0F) * 255.0F) << 24)
                    | (r << 16) | (g << 8) | b;

            for (int slice = 0; slice < SPHERE_SLICES; slice++) {
                float phi0 = (float) slice / SPHERE_SLICES * Mth.TWO_PI;
                float phi1 = (float) (slice + 1) / SPHERE_SLICES * Mth.TWO_PI;

                float sinP0 = Mth.sin(phi0);
                float cosP0 = Mth.cos(phi0);
                float sinP1 = Mth.sin(phi1);
                float cosP1 = Mth.cos(phi1);

                float u0 = (float) slice / SPHERE_SLICES * 3.0F + uOffset;
                float u1 = (float) (slice + 1) / SPHERE_SLICES * 3.0F + uOffset;

                // Four quad corners
                float x00 = radius * sinT0 * cosP0;
                float y00 = radius * cosT0;
                float z00 = radius * sinT0 * sinP0;

                float x10 = radius * sinT1 * cosP0;
                float y10 = radius * cosT1;
                float z10 = radius * sinT1 * sinP0;

                float x11 = radius * sinT1 * cosP1;
                float y11 = radius * cosT1;
                float z11 = radius * sinT1 * sinP1;

                float x01 = radius * sinT0 * cosP1;
                float y01 = radius * cosT0;
                float z01 = radius * sinT0 * sinP1;

                // Outside face (counter-clockwise)
                buffer.addVertex(matrix, x00, y00, z00).setColor(color0).setUv(u0, v0).setOverlay(overlay)
                        .setLight(light).setNormal(sinT0 * cosP0, cosT0, sinT0 * sinP0);
                buffer.addVertex(matrix, x10, y10, z10).setColor(color1).setUv(u0, v1).setOverlay(overlay)
                        .setLight(light).setNormal(sinT1 * cosP0, cosT1, sinT1 * sinP0);
                buffer.addVertex(matrix, x11, y11, z11).setColor(color1).setUv(u1, v1).setOverlay(overlay)
                        .setLight(light).setNormal(sinT1 * cosP1, cosT1, sinT1 * sinP1);
                buffer.addVertex(matrix, x01, y01, z01).setColor(color0).setUv(u1, v0).setOverlay(overlay)
                        .setLight(light).setNormal(sinT0 * cosP1, cosT0, sinT0 * sinP1);

                // Inside face (visible from inside sphere)
                buffer.addVertex(matrix, x01, y01, z01).setColor(color0).setUv(u1, v0).setOverlay(overlay)
                        .setLight(light).setNormal(-sinT0 * cosP1, -cosT0, -sinT0 * sinP1);
                buffer.addVertex(matrix, x11, y11, z11).setColor(color1).setUv(u1, v1).setOverlay(overlay)
                        .setLight(light).setNormal(-sinT1 * cosP1, -cosT1, -sinT1 * sinP1);
                buffer.addVertex(matrix, x10, y10, z10).setColor(color1).setUv(u0, v1).setOverlay(overlay)
                        .setLight(light).setNormal(-sinT1 * cosP0, -cosT1, -sinT1 * sinP0);
                buffer.addVertex(matrix, x00, y00, z00).setColor(color0).setUv(u0, v0).setOverlay(overlay)
                        .setLight(light).setNormal(-sinT0 * cosP0, -cosT0, -sinT0 * sinP0);
            }
        }
    }

    private static void renderHorizontalDisk(Matrix4f matrix, VertexConsumer buffer, float yOffset, float radius,
                                            int r, int g, int b, float alpha) {
        if (alpha <= 0.01F) return;
        int color = ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24)
                | (r << 16) | (g << 8) | b;
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        // Top face
        buffer.addVertex(matrix, -radius, yOffset, -radius).setColor(color).setUv(0.0F, 0.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, -radius, yOffset, radius).setColor(color).setUv(0.0F, 1.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, radius, yOffset, radius).setColor(color).setUv(1.0F, 1.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, radius, yOffset, -radius).setColor(color).setUv(1.0F, 0.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 1.0F, 0.0F);

        // Bottom face
        buffer.addVertex(matrix, radius, yOffset, -radius).setColor(color).setUv(1.0F, 0.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, -1.0F, 0.0F);
        buffer.addVertex(matrix, radius, yOffset, radius).setColor(color).setUv(1.0F, 1.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, -1.0F, 0.0F);
        buffer.addVertex(matrix, -radius, yOffset, radius).setColor(color).setUv(0.0F, 1.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, -1.0F, 0.0F);
        buffer.addVertex(matrix, -radius, yOffset, -radius).setColor(color).setUv(0.0F, 0.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, -1.0F, 0.0F);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
    }

    private static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3);
    }

    private record GuardVisual(CounterGuardEffectPayload.Action action, long startedAt) {
    }

    private CounterGuardEffectRenderer() {
    }
}
