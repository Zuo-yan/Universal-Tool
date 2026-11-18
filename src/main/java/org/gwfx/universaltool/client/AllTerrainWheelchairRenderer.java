package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.wheelchair.AllTerrainWheelchairEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the All-Terrain Wheelchair entity oriented via {@link AllTerrainWheelchairEntity#getRenderOrientation(float)},
 * with articulated wheels, swiveling front casters, animated joystick, and SpaceX boost thruster plumes.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class AllTerrainWheelchairRenderer extends EntityRenderer<AllTerrainWheelchairEntity> {

    // Bright near-white swatch (tile 3,0) in all_terrain_wheelchair.png for additive emissive tinting.
    private static final float FLAME_U0 = 0.78F;
    private static final float FLAME_V0 = 0.03F;
    private static final float FLAME_U1 = 0.97F;
    private static final float FLAME_V1 = 0.22F;

    public AllTerrainWheelchairRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.55F;
    }

    @Override
    protected float getShadowRadius(AllTerrainWheelchairEntity entity) {
        return entity.getAttachedFace() == Direction.DOWN ? 0.55F : 0.0F;
    }

    @Override
    public void render(AllTerrainWheelchairEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {
        WheelchairModelData model = WheelchairModelData.getOrLoad();
        if (model == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        poseStack.pushPose();
        // Center pivot matches AllTerrainWheelchairEntity#getWheelchairCenter (entity origin + half BbHeight).
        float halfHeight = entity.getBbHeight() * 0.5F;
        poseStack.translate(0.0F, halfHeight, 0.0F);
        Quaternionf orientation = entity.getRenderOrientation(partialTick);
        Quaternionf nozzleRotation = nozzleRotation(entity, orientation);
        poseStack.mulPose(orientation);

        poseStack.pushPose();
        // Shift model base (Y=0) down by halfHeight (-0.625) so tyres sit flush on attached surface.
        poseStack.translate(0.0F, -halfHeight, 0.0F);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(WheelchairModelData.TEXTURE));
        model.render(
                poseStack,
                consumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                -entity.getLeftWheelSpin(partialTick),
                -entity.getRightWheelSpin(partialTick),
                entity.getCasterYaw(partialTick),
                -entity.getCasterSpin(partialTick),
                -entity.getJoystickPitch(partialTick),
                entity.getJoystickRoll(partialTick),
                nozzleRotation
        );
        if (hasExhaust(entity)) {
            renderThrusterPlumes(entity, partialTick, poseStack, bufferSource, model, nozzleRotation);
        }
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static boolean hasExhaust(AllTerrainWheelchairEntity entity) {
        return entity.getJumpExhaust() != null || entity.isBoosting();
    }

    private static Quaternionf nozzleRotation(AllTerrainWheelchairEntity entity, Quaternionf orientation) {
        Direction exhaust = entity.getJumpExhaust();
        if (exhaust == null) {
            return new Quaternionf();
        }
        Vector3f local = orientation.transformInverse(new Vector3f(exhaust.getStepX(), exhaust.getStepY(), exhaust.getStepZ()));
        return new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, local.x, local.y, local.z);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AllTerrainWheelchairEntity chair)
                || !chair.level().isClientSide || !hasExhaust(chair)) {
            return;
        }
        WheelchairModelData model = WheelchairModelData.getOrLoad();
        if (model == null) {
            return;
        }
        Quaternionf orientation = chair.getRenderOrientation(1.0F);
        Quaternionf nozzleRotation = nozzleRotation(chair, orientation);
        Vector3f velocity = orientation.transform(nozzleRotation.transform(new Vector3f(0, 0, 0.28F)));
        Vec3 center = chair.getWheelchairCenter(1.0F);
        for (WheelchairModelData.Nozzle nozzle : model.nozzles()) {
            Vector3f outlet = nozzle.outlet(nozzleRotation).sub(0.0F, chair.getBbHeight() * 0.5F, 0.0F);
            orientation.transform(outlet);
            double x = center.x + outlet.x;
            double y = center.y + outlet.y;
            double z = center.z + outlet.z;
            chair.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, velocity.x, velocity.y, velocity.z);
            chair.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    velocity.x * 1.1, velocity.y * 1.1, velocity.z * 1.1);
        }
    }

    private static void renderThrusterPlumes(AllTerrainWheelchairEntity entity, float partialTick,
                                             PoseStack poseStack, MultiBufferSource bufferSource,
                                             WheelchairModelData model, Quaternionf nozzleRotation) {
        float time = entity.tickCount + partialTick;
        VertexConsumer flameBuffer = bufferSource.getBuffer(RenderType.eyes(WheelchairModelData.TEXTURE));
        float power = entity.getJumpExhaust() == null ? 1.0F : 0.65F;
        int index = 0;
        for (WheelchairModelData.Nozzle nozzle : model.nozzles()) {
            poseStack.pushPose();
            nozzle.applyPose(poseStack, nozzleRotation);
            PoseStack.Pose pose = poseStack.last();
            Matrix4f mat = pose.pose();
            float startZ = nozzle.outletZ();
            float flicker = power * (0.88F + 0.14F * Mth.sin(time * 2.1F + index++ * 1.7F)
                    + 0.07F * Mth.cos(time * 3.7F));

            // Outer glowing cobalt-cyan exhaust plume (crossed diagonal planes for 360-degree visibility)
            emitCrossedPlume(mat, pose, flameBuffer, 0.0F, 0.0F, startZ, 0.92F * flicker,
                    0.075F, 0.022F,
                    70, 200, 255, 220,
                    30, 125, 255, 35);

            // Inner white-hot SpaceX Mach core
            emitCrossedPlume(mat, pose, flameBuffer, 0.0F, 0.0F, startZ, 0.54F * flicker,
                    0.048F, 0.012F,
                    235, 250, 255, 255,
                    135, 225, 255, 175);
            poseStack.popPose();
        }
    }

    private static void emitCrossedPlume(Matrix4f mat,
                                         PoseStack.Pose pose,
                                         VertexConsumer buffer,
                                         float cx, float cy, float z0,
                                         float length,
                                         float rStart, float rEnd,
                                         int r0, int g0, int b0, int a0,
                                         int r1, int g1, int b1, int a1) {
        float z1 = z0 + length;
        float d0 = rStart * 0.7071F;
        float d1 = rEnd * 0.7071F;

        // Plane A (+45 deg diagonal)
        emitDoubleSidedTaperedQuad(mat, pose, buffer,
                cx - d0, cy - d0, z0,
                cx + d0, cy + d0, z0,
                cx + d1, cy + d1, z1,
                cx - d1, cy - d1, z1,
                -0.7071F, 0.7071F, 0.0F,
                r0, g0, b0, a0, r1, g1, b1, a1);

        // Plane B (-45 deg diagonal)
        emitDoubleSidedTaperedQuad(mat, pose, buffer,
                cx - d0, cy + d0, z0,
                cx + d0, cy - d0, z0,
                cx + d1, cy - d1, z1,
                cx - d1, cy + d1, z1,
                0.7071F, 0.7071F, 0.0F,
                r0, g0, b0, a0, r1, g1, b1, a1);
    }

    private static void emitDoubleSidedTaperedQuad(Matrix4f mat,
                                                   PoseStack.Pose pose,
                                                   VertexConsumer buffer,
                                                   float x0, float y0, float z0,
                                                   float x1, float y1, float z1,
                                                   float x2, float y2, float z2,
                                                   float x3, float y3, float z3,
                                                   float nx, float ny, float nz,
                                                   int r0, int g0, int b0, int a0,
                                                   int r1, int g1, int b1, int a1) {
        // Front face
        buffer.addVertex(mat, x0, y0, z0).setColor(r0, g0, b0, a0).setUv(FLAME_U0, FLAME_V0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        buffer.addVertex(mat, x1, y1, z1).setColor(r0, g0, b0, a0).setUv(FLAME_U1, FLAME_V0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        buffer.addVertex(mat, x2, y2, z2).setColor(r1, g1, b1, a1).setUv(FLAME_U1, FLAME_V1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        buffer.addVertex(mat, x3, y3, z3).setColor(r1, g1, b1, a1).setUv(FLAME_U0, FLAME_V1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);

        // Back face (RenderType.eyes uses backface culling)
        buffer.addVertex(mat, x3, y3, z3).setColor(r1, g1, b1, a1).setUv(FLAME_U0, FLAME_V1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, -nx, -ny, -nz);
        buffer.addVertex(mat, x2, y2, z2).setColor(r1, g1, b1, a1).setUv(FLAME_U1, FLAME_V1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, -nx, -ny, -nz);
        buffer.addVertex(mat, x1, y1, z1).setColor(r0, g0, b0, a0).setUv(FLAME_U1, FLAME_V0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, -nx, -ny, -nz);
        buffer.addVertex(mat, x0, y0, z0).setColor(r0, g0, b0, a0).setUv(FLAME_U0, FLAME_V0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, -nx, -ny, -nz);
    }

    @Override
    public ResourceLocation getTextureLocation(AllTerrainWheelchairEntity entity) {
        return WheelchairModelData.TEXTURE;
    }
}
