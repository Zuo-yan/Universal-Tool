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
 * 星门动态能量漩涡渲染器：平时保持中空苔石符文门框；开启后在两格高尖拱内生成电青色像素螺旋能量漩涡，玩家走入即传送。
 */
public class PersonalSpaceGateRenderer implements BlockEntityRenderer<PersonalSpaceGateBlockEntity> {
    private static final ResourceLocation VORTEX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/stargate_portal_vortex.png");
    private static final ResourceLocation ENERGY_RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/personal_space_gate_energy.png");

    public PersonalSpaceGateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(PersonalSpaceGateBlockEntity gate) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PersonalSpaceGateBlockEntity gate) {
        return new AABB(gate.getBlockPos()).inflate(1.25D, 1.8D, 1.25D);
    }

    @Override
    public void render(PersonalSpaceGateBlockEntity gate, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = gate.getBlockState();
        if (!state.hasProperty(PersonalSpaceGateBlock.OPEN) || !state.getValue(PersonalSpaceGateBlock.OPEN)) {
            return;
        }

        float time = gate.getLevel() != null ? gate.getLevel().getGameTime() + partialTick : partialTick;
        Direction facing = state.getValue(PersonalSpaceGateBlock.FACING);
        int accent = accentColorForMode(gate.getPortalMode(), gate.getPortalEnvironment());
        int ar = (accent >> 16) & 0xFF;
        int ag = (accent >> 8) & 0xFF;
        int ab = accent & 0xFF;

        poseStack.pushPose();
        // 锚定在两格高尖拱门洞中心 (y = 0.25 .. 2.38, width = 1.18)
        poseStack.translate(0.5D, 1.32D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawForFacing(facing)));
        float pulse = 1.0F + 0.025F * Mth.sin(time * 0.16F);
        poseStack.scale(0.62F * pulse, 1.08F * pulse, 0.62F);

        VertexConsumer vortexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(VORTEX_TEXTURE));
        // 1. 主电青像素螺旋漩涡（双面可见）
        renderSpinningQuad(poseStack, vortexConsumer, 0.98F, 0.014F, time * 0.065F, 220, 255, 255, 245);
        // 2. 反向内层深空漩涡（营造层次感与淡淡界域色调）
        int ir = Math.min(255, (140 + ar) / 2);
        int ig = Math.min(255, (235 + ag) / 2);
        int ib = Math.min(255, (255 + ab) / 2);
        renderSpinningQuad(poseStack, vortexConsumer, 0.82F, -0.014F, -time * 0.095F, ir, ig, ib, 175);

        // 3. 外圈微弱能量辉光环
        VertexConsumer ringConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(ENERGY_RING_TEXTURE));
        renderSpinningQuad(poseStack, ringConsumer, 1.02F, 0.004F, -time * 0.03F, 110, 245, 255, 115);

        poseStack.popPose();
    }

    private static void renderSpinningQuad(PoseStack poseStack, VertexConsumer consumer, float radius,
                                         float zOffset, float angleRad, int r, int g, int b, int a) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(angleRad));
        Matrix4f mat = poseStack.last().pose();

        // 正面
        consumer.addVertex(mat, -radius, -radius, zOffset).setColor(r, g, b, a)
                .setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(mat, radius, -radius, zOffset).setColor(r, g, b, a)
                .setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(mat, radius, radius, zOffset).setColor(r, g, b, a)
                .setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(mat, -radius, radius, zOffset).setColor(r, g, b, a)
                .setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);

        // 背面
        consumer.addVertex(mat, -radius, radius, -zOffset).setColor(r, g, b, a)
                .setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        consumer.addVertex(mat, radius, radius, -zOffset).setColor(r, g, b, a)
                .setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        consumer.addVertex(mat, radius, -radius, -zOffset).setColor(r, g, b, a)
                .setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);
        consumer.addVertex(mat, -radius, -radius, -zOffset).setColor(r, g, b, a)
                .setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, -1.0F);

        poseStack.popPose();
    }

    private static float yawForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    private static int accentColorForMode(int mode, String envId) {
        if (mode == PersonalSpaceGateBlockEntity.MODE_PERSONAL) {
            return 0x6FF7FF;
        }
        return switch (envId) {
            case "deep_dark" -> 0x45E5D8;
            case "ancient_city" -> 0x5CC8FF;
            case "nether_fortress" -> 0x62E2FF;
            case "crimson_forest" -> 0x76D6FF;
            case "end_city" -> 0x9C8CFF;
            case "trial_chambers" -> 0x56E8D8;
            default -> 0x4BE8FF;
        };
    }
}
