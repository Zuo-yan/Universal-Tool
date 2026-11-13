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
 * 星门展开与漩涡渲染器：
 * 闭合时保持半高基座静态外观；激活后上半截尖拱石柱、发光符文节点与拱顶逐层展开升起，并在完整门洞内绽放旋转的电青色螺旋漩涡。
 */
public class PersonalSpaceGateRenderer implements BlockEntityRenderer<PersonalSpaceGateBlockEntity> {
    private static final ResourceLocation ARCH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/stargate_arch.png");
    private static final ResourceLocation VORTEX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/stargate_portal_vortex.png");
    private static final int VORTEX_SEGMENTS = 24;

    private record ArchPart(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                            int tileX, int tileY, boolean emissive) {
    }

    // 与 personal_space_gate_upper.json 保持一致的分层石拱组件（单位：1/16 方块，相对 pos.above() 原点）
    private static final ArchPart[] TIER_1_PILLARS = {
            new ArchPart(-5, 0, 5, 0, 8, 11, 0, 0, false),
            new ArchPart(16, 0, 5, 21, 8, 11, 0, 0, false),
            new ArchPart(-0.6F, 0, 6.5F, 0.6F, 8, 9.5F, 0, 1, true),
            new ArchPart(15.4F, 0, 6.5F, 16.6F, 8, 9.5F, 0, 1, true),
            new ArchPart(-6, 1.5F, 4, -0.5F, 7, 12, 1, 1, true),
            new ArchPart(16.5F, 1.5F, 4, 22, 7, 12, 1, 1, true)
    };
    private static final ArchPart[] TIER_2_LOWER_CURVE = {
            new ArchPart(-4, 8, 5, 1, 14, 11, 1, 0, false),
            new ArchPart(15, 8, 5, 20, 14, 11, 0, 0, false),
            new ArchPart(0.5F, 8, 6.5F, 1.7F, 14, 9.5F, 0, 1, true),
            new ArchPart(14.3F, 8, 6.5F, 15.5F, 14, 9.5F, 0, 1, true)
    };
    private static final ArchPart[] TIER_3_UPPER_CURVE = {
            new ArchPart(-2, 14, 5, 3, 18, 11, 0, 0, false),
            new ArchPart(13, 14, 5, 18, 18, 11, 1, 0, false),
            new ArchPart(2.5F, 14, 6.5F, 3.7F, 18, 9.5F, 0, 1, true),
            new ArchPart(12.3F, 14, 6.5F, 13.5F, 18, 9.5F, 0, 1, true)
    };
    private static final ArchPart[] TIER_4_CROWN = {
            new ArchPart(1, 18, 5, 6, 21, 11, 0, 0, false),
            new ArchPart(10, 18, 5, 15, 21, 11, 0, 0, false),
            new ArchPart(5, 20, 5, 11, 23.5F, 11, 0, 0, false),
            new ArchPart(5, 19.5F, 4, 11, 24.5F, 12, 1, 1, true)
    };

    public PersonalSpaceGateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(PersonalSpaceGateBlockEntity gate) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PersonalSpaceGateBlockEntity gate) {
        return new AABB(gate.getBlockPos()).inflate(1.25D, 2.0D, 1.25D);
    }

    @Override
    public void render(PersonalSpaceGateBlockEntity gate, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = gate.getBlockState();
        boolean open = state.hasProperty(PersonalSpaceGateBlock.OPEN) && state.getValue(PersonalSpaceGateBlock.OPEN);
        float progress = gate.getDeployProgress(partialTick);
        if (!open && progress <= 0.005F) {
            return;
        }

        float time = gate.getLevel() != null ? gate.getLevel().getGameTime() + partialTick : partialTick;
        Direction facing = state.getValue(PersonalSpaceGateBlock.FACING);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawForFacing(facing)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        // 阶段一：上半截符文石拱从半高基座逐层缓缓升起展开 (0.00 -> 0.66)
        renderUnfoldingArch(poseStack, bufferSource, packedLight, progress);

        // 阶段二：拱顶合拢时在门洞中心绽放旋转能量漩涡 (0.52 -> 1.00)
        float vortexProgress = segmentProgress(progress, 0.52F, 1.00F);
        if (vortexProgress > 0.01F) {
            float easeVortex = easeOutBack(vortexProgress);
            int alpha = Math.min(240, (int) (vortexProgress * 240.0F));
            renderOvalVortex(poseStack, bufferSource, time, easeVortex, alpha);
        }

        poseStack.popPose();
    }

    private static void renderUnfoldingArch(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float progress) {
        VertexConsumer archConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(ARCH_TEXTURE));

        float t1 = easeOutCubic(segmentProgress(progress, 0.00F, 0.30F));
        renderArchTier(poseStack, archConsumer, TIER_1_PILLARS, packedLight, t1, 1.0F, -0.32F, 0.84F);

        float t2 = easeOutCubic(segmentProgress(progress, 0.14F, 0.44F));
        renderArchTier(poseStack, archConsumer, TIER_2_LOWER_CURVE, packedLight, t2, 1.50F, -0.52F, 0.80F);

        float t3 = easeOutCubic(segmentProgress(progress, 0.28F, 0.56F));
        renderArchTier(poseStack, archConsumer, TIER_3_UPPER_CURVE, packedLight, t3, 1.875F, -0.72F, 0.76F);

        float rawT4 = segmentProgress(progress, 0.40F, 0.66F);
        float t4 = rawT4 >= 1.0F ? 1.0F : easeOutBack(rawT4);
        renderArchTier(poseStack, archConsumer, TIER_4_CROWN, packedLight, Math.max(0.0F, t4), 2.125F, -0.88F, 0.72F);
    }

    private static void renderArchTier(PoseStack poseStack, VertexConsumer consumer, ArchPart[] parts,
                                       int packedLight, float tierT, float pivotY, float hiddenOffsetY, float startScaleX) {
        if (tierT <= 0.01F) {
            return;
        }
        float scaleX = Mth.lerp(Math.min(1.0F, tierT), startScaleX, 1.0F);
        float scaleY = Math.max(0.04F, tierT);
        float shiftY = Mth.lerp(Math.min(1.0F, tierT), hiddenOffsetY, 0.0F);

        poseStack.pushPose();
        poseStack.translate(0.5D, pivotY + shiftY, 0.5D);
        poseStack.scale(scaleX, scaleY, 0.85F + 0.15F * Math.min(1.0F, tierT));
        poseStack.translate(-0.5D, -pivotY, -0.5D);

        Matrix4f mat = poseStack.last().pose();
        for (ArchPart part : parts) {
            int light = part.emissive ? LightTexture.FULL_BRIGHT : packedLight;
            renderTexturedBox(mat, consumer, part, light);
        }
        poseStack.popPose();
    }

    private static void renderTexturedBox(Matrix4f mat, VertexConsumer consumer, ArchPart part, int light) {
        float x0 = part.minX / 16.0F;
        float y0 = 1.0F + part.minY / 16.0F;
        float z0 = part.minZ / 16.0F;
        float x1 = part.maxX / 16.0F;
        float y1 = 1.0F + part.maxY / 16.0F;
        float z1 = part.maxZ / 16.0F;

        float u0 = part.tileX * 0.5F + 0.002F;
        float v0 = part.tileY * 0.5F + 0.002F;
        float u1 = (part.tileX + 1) * 0.5F - 0.002F;
        float v1 = (part.tileY + 1) * 0.5F - 0.002F;

        // North (-Z)
        emitQuad(mat, consumer, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v1, u1, v0, 224, 224, 224, 0, 0, -1, light);
        // South (+Z)
        emitQuad(mat, consumer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v1, u1, v0, 224, 224, 224, 0, 0, 1, light);
        // West (-X)
        emitQuad(mat, consumer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v1, u1, v0, 194, 194, 194, -1, 0, 0, light);
        // East (+X)
        emitQuad(mat, consumer, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v1, u1, v0, 194, 194, 194, 1, 0, 0, light);
        // Up (+Y)
        emitQuad(mat, consumer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v1, u1, v0, 255, 255, 255, 0, 1, 0, light);
        // Down (-Y)
        emitQuad(mat, consumer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v1, u1, v0, 160, 160, 160, 0, -1, 0, light);
    }

    private static void emitQuad(Matrix4f mat, VertexConsumer consumer,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float u0, float vBottom, float u1, float vTop,
                                 int r, int g, int b, float nx, float ny, float nz, int light) {
        consumer.addVertex(mat, x0, y0, z0).setColor(r, g, b, 255).setUv(u0, vBottom).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(mat, x1, y1, z1).setColor(r, g, b, 255).setUv(u1, vBottom).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(mat, x2, y2, z2).setColor(r, g, b, 255).setUv(u1, vTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(mat, x3, y3, z3).setColor(r, g, b, 255).setUv(u0, vTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }

    private static void renderOvalVortex(PoseStack poseStack, MultiBufferSource bufferSource, float time, float scale, int alpha) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.28D, 0.5D);
        VertexConsumer vortexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(VORTEX_TEXTURE));
        Matrix4f mat = poseStack.last().pose();
        float rx = 0.56F * scale;
        float ry = 1.00F * scale;
        float angle = time * 0.08F;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        renderOvalVortexSide(mat, vortexConsumer, rx, ry, 0.015F, 1.0F, cos, sin, 235, 255, 255, alpha);
        renderOvalVortexSide(mat, vortexConsumer, rx, ry, -0.015F, -1.0F, cos, sin, 235, 255, 255, alpha);
        poseStack.popPose();
    }

    private static void renderOvalVortexSide(Matrix4f mat, VertexConsumer consumer,
                                             float rx, float ry, float z, float nz,
                                             float cos, float sin, int r, int g, int b, int a) {
        for (int i = 0; i < VORTEX_SEGMENTS; i++) {
            int i0 = nz > 0 ? i : i + 1;
            int i1 = nz > 0 ? i + 1 : i;
            float a0 = (float) (i0 * Math.PI * 2.0D / VORTEX_SEGMENTS);
            float a1 = (float) (i1 * Math.PI * 2.0D / VORTEX_SEGMENTS);

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

    private static float segmentProgress(float total, float start, float end) {
        return Mth.clamp((total - start) / (end - start), 0.0F, 1.0F);
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.35F;
        float c3 = c1 + 1.0F;
        float x = t - 1.0F;
        return 1.0F + c3 * x * x * x + c1 * x * x;
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
