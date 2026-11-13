package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineBlock;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineBlockEntity;

public class UniversalLotteryMachineRenderer implements BlockEntityRenderer<UniversalLotteryMachineBlockEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/block/universal_lottery_machine.png");
    private static final float U0_METAL = 0.035F, V0_METAL = 0.004F, U1_METAL = 0.058F, V1_METAL = 0.027F;
    private static final float U0_SOLID = 0.004F, V0_SOLID = 0.004F, U1_SOLID = 0.027F, V1_SOLID = 0.027F;

    public UniversalLotteryMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(UniversalLotteryMachineBlockEntity be) {
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
    }

    @Override
    public void render(UniversalLotteryMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (be.getLevel() == null) {
            return;
        }

        Direction facing = be.getBlockState().getValue(UniversalLotteryMachineBlock.FACING);
        float rotation = switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        float time = be.getLevel().getGameTime() + partialTick;
        boolean rolling = be.isRolling();
        float speedMultiplier = rolling ? 7.0F : 1.0F;
        float animTime = time * speedMultiplier;

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int glowLight = 0xF000F0; // 满级发光 (不受环境光线影响的秘法辉光)

        poseStack.pushPose();

        // 移动到方块中心，根据方块朝向旋转
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        // 移动到上层观测舱中央 (Y = 1.34375, Z = -0.275 贴近朝北前脸舷窗)
        poseStack.pushPose();
        poseStack.translate(0.0, 1.34375, -0.275);

        // 零重力漂浮微动
        float bob = Mth.sin(animTime * 0.06F) * 0.025F;
        poseStack.translate(0.0, bob, 0.0);

        // ==========================================
        // 根据当前模式渲染对应的独立全息核心
        // ==========================================
        switch (be.getActiveMode()) {
            case UniversalLotteryMachineBlockEntity.MODE_WHEEL ->
                renderWheelChamber(be, poseStack, buffer, animTime, rolling, glowLight, packedOverlay);
            case UniversalLotteryMachineBlockEntity.MODE_SLOT ->
                renderSlotChamber(be, poseStack, buffer, animTime, rolling, packedLight, glowLight, packedOverlay);
            default ->
                renderDiceChamber(be, poseStack, buffer, animTime, rolling, packedLight, glowLight, packedOverlay);
        }

        poseStack.popPose(); // end of chamber

        // ==========================================
        // 4. 钟表拱门精密钟摆 / 机械摆针 (Swinging Pendulum Animation)
        // ==========================================
        poseStack.pushPose();
        // 移动到钟表拱门上部悬挂支点 (Y = 1.74, Z = -0.425 贴合拱门前表盘)
        poseStack.translate(0.0, 1.74, -0.425);

        // 摆钟自然摆动角度：待机时精密摆钟节律 (0.08F)，抽奖中高频剧烈偏转 (0.5F)
        float swingAngle;
        if (rolling) {
            swingAngle = Mth.sin(animTime * 0.5F) * 32.0F;
        } else {
            swingAngle = Mth.sin(animTime * 0.08F) * 18.0F;
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(swingAngle));

        // 钟摆金属连杆 (从支点垂直向下悬挂长度 ~0.095F)
        float rodW = 0.008F;
        float rodLen = 0.095F;
        float rodThick = 0.008F;
        renderBox(poseStack, buffer, -rodW / 2, -rodLen, -rodThick / 2,
                rodW / 2, 0.0F, rodThick / 2,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                240, 195, 65, 255, packedLight, packedOverlay);

        // 钟摆末端金色重锤圆盘 (Pendulum Bob)
        float bobSize = 0.024F;
        float bobThick = 0.012F;
        renderBox(poseStack, buffer, -bobSize / 2, -rodLen - bobSize / 2, -bobThick / 2,
                bobSize / 2, -rodLen + bobSize / 2, bobThick / 2,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                255, 218, 75, 255, packedLight, packedOverlay);

        // 支点黄铜固定螺栓
        float pivotSize = 0.014F;
        renderBox(poseStack, buffer, -pivotSize / 2, -pivotSize / 2, -bobThick / 2,
                pivotSize / 2, pivotSize / 2, bobThick / 2,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                220, 170, 50, 255, packedLight, packedOverlay);

        poseStack.popPose(); // end of pendulum

        poseStack.popPose(); // end of root pose
    }

    private static final int[][] WHEEL_COLORS_RGB = new int[][]{
            {217, 119, 6},   // 0: Gold
            {51, 65, 85},    // 1: Netherite
            {2, 132, 199},   // 2: Diamond
            {124, 58, 237},  // 3: Mythic Purple
            {5, 150, 105},   // 4: Emerald
            {219, 39, 119},  // 5: Book Magenta
            {100, 116, 139}, // 6: Iron Silver
            {234, 88, 12}    // 7: Mystery Orange
    };

    /**
     * 模式 0: 3D 命运天体骰子 (带 6 面点数金边与自转定格)
     */
    private void renderDiceChamber(UniversalLotteryMachineBlockEntity be, PoseStack poseStack, VertexConsumer buffer,
                                   float animTime, boolean rolling, int packedLight, int glowLight, int packedOverlay) {
        poseStack.pushPose();

        if (rolling) {
            int rollTicks = be.getRollTicks();
            if (rollTicks > 15) {
                poseStack.mulPose(Axis.YP.rotationDegrees(animTime * 14.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(animTime * 10.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(animTime * 6.0F));
            } else {
                float p = Mth.clamp((15.0F - rollTicks) / 15.0F, 0.0F, 1.0F);
                float eased = easeOutBack(p);
                float[] targetAngles = getBERDiceEulerAngles(be.getDiceTargetNumber());
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(eased, 720.0F, targetAngles[0])));
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(eased, 1080.0F, targetAngles[1])));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(eased, 360.0F, targetAngles[2])));
            }
        } else {
            int targetPip = be.getDiceTargetNumber();
            float[] targetAngles = getBERDiceEulerAngles(targetPip);
            poseStack.mulPose(Axis.XP.rotationDegrees(targetAngles[0] + Mth.sin(animTime * 0.04F) * 3.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(targetAngles[1] + Mth.cos(animTime * 0.03F) * 3.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(targetAngles[2]));
        }

        float s = 0.058F;
        // 骰子黑曜石本体 (使用环境光照)
        renderBox(poseStack, buffer, -s, -s, -s, s, s, s,
                U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID,
                24, 16, 40, 255, packedLight, packedOverlay);

        // 骰子六面微凸边缘与金色镶边
        float sf = s * 0.90F;
        renderBox(poseStack, buffer, -sf, -sf, -s - 0.001F, sf, sf, -s + 0.001F,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                210, 165, 55, 255, glowLight, packedOverlay); // -Z (Face 1: 对应正面舷窗)
        renderBox(poseStack, buffer, -sf, -sf, s - 0.001F, sf, sf, s + 0.001F,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                210, 165, 55, 255, glowLight, packedOverlay); // +Z (Face 6)
        renderBox(poseStack, buffer, -s - 0.001F, -sf, -sf, -s + 0.001F, sf, sf,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                210, 165, 55, 255, glowLight, packedOverlay); // -X (Face 2)
        renderBox(poseStack, buffer, s - 0.001F, -sf, -sf, s + 0.001F, sf, sf,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                210, 165, 55, 255, glowLight, packedOverlay); // +X (Face 5)
        renderBox(poseStack, buffer, -sf, -s - 0.001F, -sf, sf, -s + 0.001F, sf,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                210, 165, 55, 255, glowLight, packedOverlay); // -Y (Face 3)
        renderBox(poseStack, buffer, -sf, s - 0.001F, -sf, sf, s + 0.001F, sf,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                210, 165, 55, 255, glowLight, packedOverlay); // +Y (Face 4)

        // 骰子六面立体点数镶嵌 (Face 1..6)
        renderDicePips(poseStack, buffer, s, glowLight, packedOverlay);

        // 围绕骰子的金色星轨倾斜光环
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-animTime * 3.0F));
        renderStarRing(poseStack, buffer, 0.135F, 0.110F, 0.010F, glowLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    private void renderDicePips(PoseStack poseStack, VertexConsumer buffer, float s,
                                int glowLight, int packedOverlay) {
        float off = s * 0.44F;
        float r = 0.0075F;
        float d = 0.0025F;
        // Face 1 (-Z): single glowing cyan/crimson core pip
        renderBox(poseStack, buffer, -r * 1.3F, -r * 1.3F, -s - d, r * 1.3F, r * 1.3F, -s + 0.0005F,
                U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID, 156, 245, 240, 255, glowLight, packedOverlay);
        // Face 6 (+Z): 6 pips
        float[][] f6 = {{-off, -off}, {off, -off}, {-off, 0}, {off, 0}, {-off, off}, {off, off}};
        for (float[] p : f6) {
            renderBox(poseStack, buffer, p[0] - r, p[1] - r, s - 0.0005F, p[0] + r, p[1] + r, s + d,
                    U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID, 245, 214, 120, 255, glowLight, packedOverlay);
        }
        // Face 2 (-X): 2 pips
        float[][] f2 = {{-off, -off}, {off, off}};
        for (float[] p : f2) {
            renderBox(poseStack, buffer, -s - d, p[0] - r, p[1] - r, -s + 0.0005F, p[0] + r, p[1] + r,
                    U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID, 245, 214, 120, 255, glowLight, packedOverlay);
        }
        // Face 5 (+X): 5 pips
        float[][] f5 = {{0, 0}, {-off, -off}, {off, -off}, {-off, off}, {off, off}};
        for (float[] p : f5) {
            renderBox(poseStack, buffer, s - 0.0005F, p[0] - r, p[1] - r, s + d, p[0] + r, p[1] + r,
                    U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID, 245, 214, 120, 255, glowLight, packedOverlay);
        }
        // Face 3 (-Y): 3 pips
        float[][] f3 = {{-off, -off}, {0, 0}, {off, off}};
        for (float[] p : f3) {
            renderBox(poseStack, buffer, p[0] - r, -s - d, p[1] - r, p[0] + r, -s + 0.0005F, p[1] + r,
                    U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID, 216, 168, 255, 255, glowLight, packedOverlay);
        }
        // Face 4 (+Y): 4 pips
        float[][] f4 = {{-off, -off}, {off, -off}, {-off, off}, {off, off}};
        for (float[] p : f4) {
            renderBox(poseStack, buffer, p[0] - r, s - 0.0005F, p[1] - r, p[0] + r, s + d, p[1] + r,
                    U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID, 216, 168, 255, 255, glowLight, packedOverlay);
        }
    }

    /**
     * 模式 1: 竖直星轨罗盘 (8 扇区彩色符文 + 顶部指向指针)
     */
    private void renderWheelChamber(UniversalLotteryMachineBlockEntity be, PoseStack poseStack, VertexConsumer buffer,
                                    float animTime, boolean rolling, int glowLight, int packedOverlay) {
        poseStack.pushPose();

        float wheelAngle;
        if (rolling) {
            float progress = Mth.clamp(1.0F - (float) be.getRollTicks() / be.getTotalRollDurationTicks(), 0.0F, 1.0F);
            float targetAngle = (270.0F - be.getWheelTargetSlice() * 45.0F + 3600.0F) % 360.0F;
            wheelAngle = 1800.0F * easeOutCubic(progress) + targetAngle * progress;
        } else {
            wheelAngle = animTime * 1.5F;
        }

        // 竖直面对舷窗自转
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(wheelAngle));

        // 1. 中央金黄轴心
        float hubS = 0.025F;
        renderBox(poseStack, buffer, -hubS, -hubS, -0.015F, hubS, hubS, 0.015F,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                255, 215, 60, 255, glowLight, packedOverlay);

        // 2. 8 个彩色发光扇区辐条与外沿宝石
        float radius = 0.125F;
        float spokeW = 0.012F;
        float spokeLen = 0.095F;
        for (int i = 0; i < 8; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * 45.0F));
            int[] rgb = WHEEL_COLORS_RGB[i];

            // 辐条 (从轴心向外辐射)
            renderBox(poseStack, buffer, -spokeW / 2, 0.02F, -0.008F, spokeW / 2, 0.02F + spokeLen, 0.008F,
                    U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                    rgb[0], rgb[1], rgb[2], 255, glowLight, packedOverlay);

            // 辐条顶端宝石色块
            float gemS = 0.018F;
            renderBox(poseStack, buffer, -gemS / 2, radius - gemS / 2, -0.012F, gemS / 2, radius + gemS / 2, 0.012F,
                    U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID,
                    rgb[0], rgb[1], rgb[2], 255, glowLight, packedOverlay);
            poseStack.popPose();
        }

        // 3. 外圈金色锁环
        renderStarRing(poseStack, buffer, radius + 0.012F, 0.105F, 0.010F, glowLight, packedOverlay);

        poseStack.popPose(); // end of rotating wheel

        // 4. 12 点钟方向固定金色指针 (向下指)
        poseStack.pushPose();
        poseStack.translate(0.0, 0.135, -0.020);
        if (rolling) {
            float jiggle = Mth.sin(animTime * 1.8F) * 8.0F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(jiggle));
        }
        renderBox(poseStack, buffer, -0.008F, 0.0F, -0.006F, 0.008F, 0.025F, 0.006F,
                U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                240, 190, 60, 255, glowLight, packedOverlay);
        renderBox(poseStack, buffer, -0.005F, -0.014F, -0.008F, 0.005F, 0.0F, 0.008F,
                U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID,
                239, 68, 68, 255, glowLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    /**
     * 模式 2: 三联微缩全息滚轮 (水平排列，支持分段急刹车与听牌悬念)
     */
    private void renderSlotChamber(UniversalLotteryMachineBlockEntity be, PoseStack poseStack, VertexConsumer buffer,
                                   float animTime, boolean rolling, int packedLight, int glowLight, int packedOverlay) {
        poseStack.pushPose();

        boolean extended = (be.getTotalRollDurationTicks() == UniversalLotteryMachineBlockEntity.EXTENDED_ROLL_DURATION_TICKS);
        int r1Stop = extended ? 48 : 25;
        int r2Stop = extended ? 32 : 12;

        float[] reelX = new float[]{-0.075F, 0.0F, 0.075F};
        int[] targetSymbols = new int[]{be.getSlotReel1(), be.getSlotReel2(), be.getSlotReel3()};

        for (int r = 0; r < 3; r++) {
            poseStack.pushPose();
            poseStack.translate(reelX[r], 0.0, 0.0);

            float reelRot;
            if (rolling) {
                int rollTicks = be.getRollTicks();
                if (r == 0) {
                    if (rollTicks > r1Stop) {
                        reelRot = animTime * 22.0F;
                    } else {
                        float p = Mth.clamp((float) (r1Stop - rollTicks) / 10.0F, 0.0F, 1.0F);
                        reelRot = Mth.lerp(easeOutBack(p), (r1Stop + 10) * 22.0F, targetSymbols[0] * (360.0F / 7.0F));
                    }
                } else if (r == 1) {
                    if (rollTicks > r2Stop) {
                        reelRot = animTime * 26.0F + 45.0F;
                    } else {
                        float p = Mth.clamp((float) (r2Stop - rollTicks) / 10.0F, 0.0F, 1.0F);
                        reelRot = Mth.lerp(easeOutBack(p), (r2Stop + 10) * 26.0F, targetSymbols[1] * (360.0F / 7.0F));
                    }
                } else {
                    if (extended && rollTicks <= r2Stop && rollTicks > 0) {
                        float suspense = (float) (r2Stop - rollTicks) / r2Stop;
                        float speed = Mth.lerp(suspense, 16.0F, 3.0F);
                        reelRot = (r2Stop - rollTicks) * speed + targetSymbols[2] * (360.0F / 7.0F);
                    } else if (rollTicks > 0) {
                        reelRot = animTime * 30.0F + 90.0F;
                    } else {
                        reelRot = targetSymbols[2] * (360.0F / 7.0F);
                    }
                }
            } else {
                reelRot = targetSymbols[r] * (360.0F / 7.0F);
            }

            poseStack.mulPose(Axis.XP.rotationDegrees(reelRot));

            // 渲染单列滚筒棱柱 (6 面棱柱柱体)
            float rW = 0.024F;
            float rRad = 0.045F;
            float rLen = 0.038F;
            for (int k = 0; k < 6; k++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(k * 60.0F));
                int[] col = WHEEL_COLORS_RGB[k % 8];
                renderBox(poseStack, buffer, -rW / 2, rRad - 0.006F, -rLen / 2, rW / 2, rRad + 0.006F, rLen / 2,
                        U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                        col[0], col[1], col[2], 255, glowLight, packedOverlay);
                poseStack.popPose();
            }

            // 滚筒金属轴心 (使用环境光照)
            renderBox(poseStack, buffer, -rW / 2 - 0.003F, -0.010F, -0.010F, rW / 2 + 0.003F, 0.010F, 0.010F,
                    U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                    220, 180, 50, 255, packedLight, packedOverlay);

            poseStack.popPose();
        }

        // 中轴发光激光赔率线 (横跨三滚轮)
        renderBox(poseStack, buffer, -0.115F, -0.003F, -0.052F, 0.115F, 0.003F, -0.048F,
                U0_SOLID, V0_SOLID, U1_SOLID, V1_SOLID,
                244, 63, 94, 230, glowLight, packedOverlay);

        poseStack.popPose();
    }

    private void renderStarRing(PoseStack poseStack, VertexConsumer buffer,
                                float ringRadius, float segLen, float barThick,
                                int glowLight, int packedOverlay) {
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 45.0F));
            renderBox(poseStack, buffer, -segLen / 2, -barThick / 2, ringRadius - barThick / 2,
                    segLen / 2, barThick / 2, ringRadius + barThick / 2,
                    U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                    255, 220, 94, 255, glowLight, packedOverlay);
            renderBox(poseStack, buffer, -segLen / 2, -barThick / 2, -ringRadius - barThick / 2,
                    segLen / 2, barThick / 2, -ringRadius + barThick / 2,
                    U0_METAL, V0_METAL, U1_METAL, V1_METAL,
                    255, 220, 94, 255, glowLight, packedOverlay);
            poseStack.popPose();
        }
    }

    private static float[] getBERDiceEulerAngles(int pip) {
        return switch (pip) {
            case 1 -> new float[]{0.0F, 0.0F, 0.0F};       // -Z 朝向舷窗正前
            case 2 -> new float[]{0.0F, 90.0F, 0.0F};      // -X 转向正前
            case 3 -> new float[]{-90.0F, 0.0F, 0.0F};     // -Y 转向正前
            case 4 -> new float[]{90.0F, 0.0F, 0.0F};      // +Y 转向正前
            case 5 -> new float[]{0.0F, -90.0F, 0.0F};     // +X 转向正前
            case 6 -> new float[]{0.0F, 180.0F, 0.0F};     // +Z 转向正前
            default -> new float[]{0.0F, 0.0F, 0.0F};
        };
    }

    private static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer buffer,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  float u0, float v0, float u1, float v1,
                                  int r, int g, int b, int a,
                                  int packedLight, int packedOverlay) {
        var mat = poseStack.last().pose();

        // North (-Z)
        buffer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        buffer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        buffer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        buffer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);

        // South (+Z)
        buffer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        buffer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        buffer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        buffer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);

        // East (+X)
        buffer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(1, 0, 0);
        buffer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(1, 0, 0);
        buffer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(1, 0, 0);
        buffer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(1, 0, 0);

        // West (-X)
        buffer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(-1, 0, 0);
        buffer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(-1, 0, 0);
        buffer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(-1, 0, 0);
        buffer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(-1, 0, 0);

        // Up (+Y)
        buffer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 1, 0);
        buffer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 1, 0);
        buffer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 1, 0);
        buffer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 1, 0);

        // Down (-Y)
        buffer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, -1, 0);
        buffer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, -1, 0);
        buffer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, -1, 0);
        buffer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, -1, 0);
    }
}
