package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.PersonalSpaceActionPayload;
import org.gwfx.universaltool.space.PersonalSpaceGateBlock;
import org.gwfx.universaltool.space.PersonalSpaceGateBlockEntity;
import org.joml.Matrix4f;

/**
 * Renders a sci-fi animated holographic event-horizon portal inside an opened {@link PersonalSpaceGateBlock}.
 */
public class PersonalSpaceGateRenderer implements BlockEntityRenderer<PersonalSpaceGateBlockEntity> {
    private static final ResourceLocation FLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/block/starry_flow_overlay.png");
    private static final ResourceLocation WHITE_PIXEL =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public PersonalSpaceGateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PersonalSpaceGateBlockEntity gate, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (gate.getLevel() == null || !gate.getBlockState().getValue(PersonalSpaceGateBlock.OPEN)) {
            return;
        }

        float time = (gate.getLevel().getGameTime() + partialTick);
        float age = gate.getOpenedGameTime() > 0L ? Math.max(0.0F, time - gate.getOpenedGameTime()) : 10.0F;
        float openT = Mth.clamp(age / 10.0F, 0.08F, 1.0F);
        float irisScale = 1.0F - (float) Math.pow(1.0F - openT, 3.0);

        int[] colors = resolvePortalColors(gate.getPortalMode(), gate.getPortalEnvironment());
        int primary = colors[0];
        int secondary = colors[1];

        Direction facing = gate.getBlockState().getValue(PersonalSpaceGateBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.54, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingYaw(facing)));
        poseStack.scale(irisScale, irisScale * 1.16F, irisScale);

        // 1. Primary swirling event-horizon nebula disc (placed cleanly outside +/-0.03125 gate lens)
        renderSwirlDisc(poseStack, buffer, FLOW_TEXTURE, 0.255F, 0.040F, time * 0.048F, primary, 220);
        // 2. Counter-rotating inner astral vortex disc
        renderSwirlDisc(poseStack, buffer, FLOW_TEXTURE, 0.205F, 0.044F, -time * 0.078F, secondary, 195);

        // 3. Outer segmented sci-fi holo-ring (rotating clockwise)
        renderHoloRing(poseStack, buffer, 0.258F, 0.286F, 12, 0.65F, time * 0.055F, 0.048F, primary, 240);
        // 4. Inner accelerator ring (counter-rotating)
        renderHoloRing(poseStack, buffer, 0.145F, 0.168F, 6, 0.55F, -time * 0.095F, 0.052F, secondary, 235);

        // 5. Pulsing quantum core flare
        float corePulse = 0.045F + 0.014F * Mth.sin(time * 0.22F);
        renderSwirlDisc(poseStack, buffer, WHITE_PIXEL, corePulse, 0.056F, time * 0.12F, 0xFFEFFFFF, 220);

        poseStack.popPose();
    }

    private static void renderSwirlDisc(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture,
                                        float radius, float zOffset, float angle, int argb, int alpha) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(angle));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(texture));
        Matrix4f mat = poseStack.last().pose();

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segments);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segments);
            float x0 = Mth.cos(a0) * radius;
            float y0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float y1 = Mth.sin(a1) * radius;

            float u0 = 0.5F + 0.45F * Mth.cos(a0);
            float v0 = 0.5F + 0.45F * Mth.sin(a0);
            float u1 = 0.5F + 0.45F * Mth.cos(a1);
            float v1 = 0.5F + 0.45F * Mth.sin(a1);

            // Front face + back face as degenerate quads from center
            emitTriangleQuad(consumer, mat, 0, 0, zOffset, x0, y0, zOffset, x1, y1, zOffset,
                    0.5F, 0.5F, u0, v0, u1, v1, r, g, b, alpha, 0, 0, 1);
            emitTriangleQuad(consumer, mat, 0, 0, -zOffset, x1, y1, -zOffset, x0, y0, -zOffset,
                    0.5F, 0.5F, u1, v1, u0, v0, r, g, b, alpha, 0, 0, -1);
        }
        poseStack.popPose();
    }

    private static void renderHoloRing(PoseStack poseStack, MultiBufferSource buffer,
                                       float innerR, float outerR, int segments, float fillRatio,
                                       float angleOffset, float zOffset, int argb, int alpha) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(angleOffset));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE_PIXEL));
        Matrix4f mat = poseStack.last().pose();

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        float step = (float) (2.0 * Math.PI / segments);
        float arc = step * fillRatio;
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = a0 + arc;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            emitArcQuad(consumer, mat,
                    c0 * innerR, s0 * innerR,
                    c0 * outerR, s0 * outerR,
                    c1 * outerR, s1 * outerR,
                    c1 * innerR, s1 * innerR,
                    zOffset, r, g, b, alpha, 1);
            emitArcQuad(consumer, mat,
                    c1 * innerR, s1 * innerR,
                    c1 * outerR, s1 * outerR,
                    c0 * outerR, s0 * outerR,
                    c0 * innerR, s0 * innerR,
                    -zOffset, r, g, b, alpha, -1);
        }
        poseStack.popPose();
    }

    private static void emitTriangleQuad(VertexConsumer c, Matrix4f mat,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float u0, float v0, float u1, float v1, float u2, float v2,
                                         int r, int g, int b, int a, float nx, float ny, float nz) {
        c.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
        c.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
        c.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
        c.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
    }

    private static void emitArcQuad(VertexConsumer c, Matrix4f mat,
                                    float x0, float y0, float x1, float y1,
                                    float x2, float y2, float x3, float y3,
                                    float z, int r, int g, int b, int a, float nz) {
        c.addVertex(mat, x0, y0, z).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 0, nz);
        c.addVertex(mat, x1, y1, z).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 0, nz);
        c.addVertex(mat, x2, y2, z).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 0, nz);
        c.addVertex(mat, x3, y3, z).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 0, nz);
    }

    private static float facingYaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    private static int[] resolvePortalColors(int mode, String envId) {
        if (mode == PersonalSpaceActionPayload.ACTION_ENTER_PRIVATE_SPACE) {
            return new int[]{0xFF3BF5E8, 0xFF9B6BFF};
        }
        return switch (envId) {
            case "cherry_grove" -> new int[]{0xFFFF94D4, 0xFFFFD8F0};
            case "obsidian_forge" -> new int[]{0xFFFF7A28, 0xFFFFD066};
            case "amethyst_starfall" -> new int[]{0xFFC878FF, 0xFF84F2FF};
            case "aurora_glacier" -> new int[]{0xFF5AF5D8, 0xFF78B4FF};
            case "sculk_sanctum" -> new int[]{0xFF1AD6D0, 0xFF3B62FF};
            case "mushroom_wonderland" -> new int[]{0xFFFF6EA8, 0xFFB97BFF};
            case "sky_islands" -> new int[]{0xFF7BE3FF, 0xFFF0FAFF};
            default -> new int[]{0xFF74E8FF, 0xFFFFE680};
        };
    }
}
