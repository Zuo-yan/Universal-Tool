package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phoenix.PhoenixPodBlock;
import org.gwfx.universaltool.phoenix.PhoenixPodBlockEntity;
import org.gwfx.universaltool.phoenix.PodState;

public class PhoenixPodRenderer implements BlockEntityRenderer<PhoenixPodBlockEntity> {

    // 绑定独立生化玻璃门材质 (单张实体纹理)
    private static final ResourceLocation GLASS_DOOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/block/phoenix_pod_glass_liquid.png");

    private final PlayerModel<?> playerModel;

    public PhoenixPodRenderer(BlockEntityRendererProvider.Context context) {
        this.playerModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.playerModel.young = false;
    }

    @Override
    public void render(PhoenixPodBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        PodState state = be.getPodState();
        Direction facing = be.getBlockState().getValue(PhoenixPodBlock.FACING);
        float rotation = -facing.toYRot();
        float door = be.getDoorProgress();

        // 1. 严格使用单缓冲绘制左右对开生化大门 (从 y=0.20 到 y=1.78 一体通高，中间无任何断层横梁)
        VertexConsumer doorBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(GLASS_DOOR_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation));
        poseStack.translate(-0.5, 0.0, -0.5);

        // --- 左半扇对开门 (向左平滑滑移与外旋微推) ---
        poseStack.pushPose();
        float leftSlide = -door * 0.32F;
        poseStack.translate(0.18 + leftSlide, 0.0, 0.16);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-door * 15.0F));
        poseStack.translate(-0.18, 0.0, -0.16);
        renderDoorPanel(poseStack, doorBuffer, 0.18F, 0.50F, packedLight, packedOverlay);
        poseStack.popPose();

        // --- 右半扇对开门 (向右平滑滑移与外旋微推) ---
        poseStack.pushPose();
        float rightSlide = door * 0.32F;
        poseStack.translate(0.82 + rightSlide, 0.0, 0.16);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(door * 15.0F));
        poseStack.translate(-0.82, 0.0, -0.16);
        renderDoorPanel(poseStack, doorBuffer, 0.50F, 0.82F, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();

        if (state == PodState.EMPTY) {
            return;
        }

        // 2. 门绘制完成后，再获取克隆体皮肤缓冲，单线程流水线无交替，彻底杜绝 Not building 异常！
        ResourceLocation skinTexture = DefaultPlayerSkin.getDefaultTexture();
        if (be.getOwnerUUID() != null) {
            skinTexture = Minecraft.getInstance().getSkinManager().getInsecureSkin(
                    new com.mojang.authlib.GameProfile(be.getOwnerUUID(), be.getOwnerName())).texture();
        }
        VertexConsumer playerConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(skinTexture));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.4, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation));

        if (door > 0.0F) {
            // 开门出舱阶段：克隆体向前滑行并迈步踏出底盘
            float forward = door * 0.60F;
            float stepDown = door * 0.15F;
            poseStack.translate(0, -stepDown, forward);
        } else {
            // 待命悬浮微动
            float time = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + partialTick;
            float bobbing = Mth.sin(time * 0.05F) * 0.04F;
            poseStack.translate(0, bobbing, 0);
        }

        if (state == PodState.CULTIVATING) {
            float scale = 0.5F + 0.4F * ((float) be.getProgress() / PhoenixPodBlockEntity.MAX_PROGRESS);
            poseStack.scale(scale, scale, scale);
        } else {
            poseStack.scale(0.85F, 0.85F, 0.85F);
        }

        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        poseStack.translate(0, -1.3, 0);

        playerModel.setAllVisible(true);
        playerModel.crouching = false;
        if (door > 0.1F) {
            float walkSwing = (float) Math.sin(door * Math.PI) * 0.35F;
            playerModel.leftArm.xRot = walkSwing;
            playerModel.rightArm.xRot = -walkSwing;
            playerModel.leftLeg.xRot = -walkSwing;
            playerModel.rightLeg.xRot = walkSwing;
            playerModel.head.xRot = 0.0F;
        } else {
            playerModel.leftArm.xRot = 0.1F;
            playerModel.rightArm.xRot = 0.1F;
            playerModel.leftLeg.xRot = 0.05F;
            playerModel.rightLeg.xRot = 0.05F;
            playerModel.head.xRot = 0.15F;
        }

        int alpha = (int) ((1.0F - door * 0.6F) * 220);
        int color = (alpha << 24) | 0x00FFFFFF;

        playerModel.renderToBuffer(poseStack, playerConsumer, packedLight, packedOverlay, color);

        poseStack.popPose();
    }

    private void renderDoorPanel(PoseStack poseStack, VertexConsumer buffer, float minX, float maxX, int packedLight, int packedOverlay) {
        float minY = 0.20F, maxY = 1.78F;
        float z = 0.16F;

        var mat = poseStack.last().pose();

        // 正面双面高透玻璃门，带有生化微光 (一次性顺畅送点，严格遵循 BufferBuilder 状态)
        buffer.addVertex(mat, minX, minY, z).setColor(255, 255, 255, 130).setUv(0.0F, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        buffer.addVertex(mat, maxX, minY, z).setColor(255, 255, 255, 130).setUv(1.0F, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        buffer.addVertex(mat, maxX, maxY, z).setColor(255, 255, 255, 130).setUv(1.0F, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        buffer.addVertex(mat, minX, maxY, z).setColor(255, 255, 255, 130).setUv(0.0F, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);

        buffer.addVertex(mat, minX, maxY, z).setColor(255, 255, 255, 130).setUv(0.0F, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        buffer.addVertex(mat, maxX, maxY, z).setColor(255, 255, 255, 130).setUv(1.0F, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        buffer.addVertex(mat, maxX, minY, z).setColor(255, 255, 255, 130).setUv(1.0F, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        buffer.addVertex(mat, minX, minY, z).setColor(255, 255, 255, 130).setUv(0.0F, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
    }
}
