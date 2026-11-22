package org.gwfx.universaltool.client.sportscar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.sportscar.SportsCarEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海湾 GT 跑车实体渲染器：负责车体地形贴合 + 二阶弹簧悬架惯性倾侧、四轮转向滚动、
 * 水陆两栖 90° 收轮涵道推进舱变形动画（含前后轮制动同步折叠、全程防透光轮舱、
 * 双叉臂液压悬架、环形涵道护罩、紧凑破浪导流裙与矢量推力微倾）、
 * 方向盘联动、迎宾门开合、智能车灯自发光、氮气尾焰及最后绘制的半透明玻璃层。
 */
public final class SportsCarRenderer extends EntityRenderer<SportsCarEntity> {

    /** 弹簧阻尼物理常数（固有频率 ω = 2π * 2.1 rad/s，阻尼比 ζ = 0.58，步长 1 tick = 0.05s） */
    private static final float SPRING_OMEGA = (float) (2.0D * Math.PI * 2.1D);
    private static final float SPRING_OMEGA_SQ = SPRING_OMEGA * SPRING_OMEGA;
    private static final float SPRING_ZETA = 0.58F;
    private static final float SPRING_DT = 0.05F;
    private static final float SUSPENSION_PIVOT_Y = 0.54F;

    /** 双侧钛合金排气管尾部中心局部坐标（模型 -Z 为车头） */
    private static final float EXHAUST_X = 0.256F;
    private static final float EXHAUST_Y = 0.693F;
    private static final float EXHAUST_Z = 3.255F;

    /** gulf_gt_car.png 合图中内置纯色色块中心 UV（深碳黑、深灰装甲、银灰金属、海湾橙、海湾蓝、浅灰高光） */
    private static final float UV_SWATCH_V = 0.62656F;
    private static final float UV_CARBON_U = 0.50195F;
    private static final float UV_DARK_GREY_U = 0.50977F;
    private static final float UV_SILVER_U = 0.51367F;
    private static final float UV_ORANGE_U = 0.53711F;
    private static final float UV_BLUE_U = 0.54102F;
    private static final float UV_LIGHT_GREY_U = 0.54492F;

    private static final int HOVER_RING_SEGMENTS = 20;
    private static final int DUCT_SEGMENTS = 16;

    private static final Map<Integer, SpringState> SPRING_STATES = new ConcurrentHashMap<>();

    public SportsCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.35F;
    }

    /**
     * 每游戏刻更新指定跑车实体的客户端二阶弹簧悬架姿态与水翼涡轮旋角。
     */
    public static void tickSpring(SportsCarEntity car) {
        SpringState state = SPRING_STATES.computeIfAbsent(car.getId(), id -> new SpringState(car));
        state.step(car);
    }

    /**
     * 更新客户端悬架步进并记录客户端 tick 序号供过期清理使用。
     */
    public static void tickSpringWithClock(SportsCarEntity car, int clientTick) {
        SpringState state = SPRING_STATES.computeIfAbsent(car.getId(), id -> new SpringState(car));
        if (state.lastUpdateClientTick != clientTick) {
            state.lastUpdateClientTick = clientTick;
            state.step(car);
        }
    }

    /**
     * 获取帧间插值后的悬架俯仰倾侧角（度，正值=抬头）。
     */
    public static float getInterpolatedFakePitch(SportsCarEntity car, float partialTick) {
        SpringState state = SPRING_STATES.get(car.getId());
        return state == null ? 0.0F : Mth.lerp(partialTick, state.prevPitch, state.pitch);
    }

    /**
     * 获取帧间插值后的悬架横向侧倾角（度，正值=向右倾）。
     */
    public static float getInterpolatedFakeRoll(SportsCarEntity car, float partialTick) {
        SpringState state = SPRING_STATES.get(car.getId());
        return state == null ? 0.0F : Mth.lerp(partialTick, state.prevRoll, state.roll);
    }

    /**
     * 清理不再存在的跑车弹簧缓存，避免长期挂机积累旧实体 ID。
     */
    public static void pruneStaleSprings(int currentClientTick) {
        SPRING_STATES.entrySet().removeIf(e -> currentClientTick - e.getValue().lastUpdateClientTick > 40);
    }

    /**
     * 在退出世界/断开连接时重置全部悬架弹簧状态。
     */
    public static void clearSprings() {
        SPRING_STATES.clear();
    }

    @Override
    public boolean shouldRender(SportsCarEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        return entity.noCulling || camera.isVisible(entity.getBoundingBoxForCulling().inflate(2.4D));
    }

    @Override
    public ResourceLocation getTextureLocation(SportsCarEntity entity) {
        SportsCarModelData model = SportsCarModelData.getOrLoad();
        return model != null ? model.mainTexture() : SportsCarModelData.DEFAULT_MAIN_TEXTURE;
    }

    @Override
    public void render(SportsCarEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {
        SportsCarModelData model = SportsCarModelData.getOrLoad();
        if (model == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // 若实体未在客户端附近循环中更新到当前 tick，兜底推进一次弹簧与涡轮状态
        SpringState spring = SPRING_STATES.computeIfAbsent(entity.getId(), id -> new SpringState(entity));
        if (spring.lastEntityTick != entity.tickCount) {
            spring.step(entity);
        }

        float fakePitch = Mth.lerp(partialTick, spring.prevPitch, spring.pitch);
        float fakeRoll = Mth.lerp(partialTick, spring.prevRoll, spring.roll);
        float hoverTurbineSpin = Mth.lerp(partialTick, spring.prevTurbineSpin, spring.turbineSpin);
        float vectorPitch = Mth.lerp(partialTick, spring.prevVectorPitch, spring.vectorPitch);
        float vectorRoll = Mth.lerp(partialTick, spring.prevVectorRoll, spring.vectorRoll);
        float steerAngle = entity.getSteerAngle(partialTick);
        float wheelSpin = entity.getWheelSpin(partialTick);
        float doorProgress = entity.getDoorOpenProgress(partialTick);
        float waterMorph = entity.getWaterMorphProgress(partialTick);

        // 智能车灯判定：暗处/夜间或行驶/载客时自动点亮暖白前大灯；刹车/倒车时亮红尾灯，行驶时微亮
        int skyLight = LightTexture.sky(packedLight);
        int blockLight = LightTexture.block(packedLight);
        boolean isDark = entity.level().getSkyDarken() > 4 || (skyLight < 9 && blockLight < 8);
        boolean isActive = !entity.getPassengers().isEmpty()
                || Math.abs(entity.getForwardSpeed()) > 0.03F
                || Math.abs(entity.getThrottle()) > 0.05F;
        boolean headlightsOn = isDark || isActive;
        boolean braking = entity.isBraking();

        int headlightLight = headlightsOn ? LightTexture.FULL_BRIGHT : packedLight;
        int headlightTint = headlightsOn ? 0xFFFFF7E1 : 0xFFFFFFFF;

        int taillightLight;
        int taillightTint;
        if (braking) {
            taillightLight = LightTexture.FULL_BRIGHT;
            taillightTint = 0xFFFF4D4D;
        } else if (isActive || isDark) {
            taillightLight = LightTexture.pack(Math.max(8, blockLight), skyLight);
            taillightTint = 0xFFE89B9B;
        } else {
            taillightLight = packedLight;
            taillightTint = 0xFFFFFFFF;
        }

        poseStack.pushPose();

        // 1. 应用服务端同步的航向与地形拟合旋转矩阵
        Quaternionf baseOrient = entity.getRenderOrientation(partialTick);
        poseStack.mulPose(baseOrient);

        // 2. 围绕悬架轴心叠加客户端二阶弹簧阻尼惯性倾侧
        if (Math.abs(fakePitch) > 1.0E-3F || Math.abs(fakeRoll) > 1.0E-3F) {
            poseStack.translate(0.0F, SUSPENSION_PIVOT_Y, 0.0F);
            if (Math.abs(fakePitch) > 1.0E-3F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(fakePitch));
            }
            if (Math.abs(fakeRoll) > 1.0E-3F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-fakeRoll));
            }
            poseStack.translate(0.0F, -SUSPENSION_PIVOT_Y, 0.0F);
        }

        // 3. 水上模式变形平滑微抬（+0.34 格避开折平涡轮顶面）及低速轻柔波浪呼吸浮动
        if (waterMorph > 1.0E-3F) {
            float speedRatio = Mth.clamp(Math.abs(entity.getForwardSpeed()) / 0.88F, 0.0F, 1.0F);
            float morphLift = SportsCarModelData.computeWaterMorphLift(waterMorph);
            float waveAmp = smoothstep(0.18F, 0.85F, waterMorph) * (0.024F * (1.0F - 0.68F * speedRatio));
            float waveFloat = waveAmp * Mth.sin((entity.tickCount + partialTick) * 0.14F);
            poseStack.translate(0.0F, morphLift + waveFloat, 0.0F);
        }

        // 4. 绘制跑车主体、四轮折转涡轮、前后同步折叠制动系统及矢量推力偏转
        VertexConsumer mainConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(model.mainTexture()));
        model.renderOpaqueParts(
                poseStack,
                mainConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                steerAngle,
                wheelSpin,
                doorProgress,
                waterMorph,
                hoverTurbineSpin,
                vectorPitch,
                vectorRoll,
                headlightLight,
                headlightTint,
                taillightLight,
                taillightTint
        );

        // 5. 全程绘制轮舱防透光内衬；水上展开机械装甲百叶、双叉臂液压折转悬架、环形涵道护罩与紧凑破浪导流裙
        renderWheelWellBulkheads(poseStack, mainConsumer, waterMorph, packedLight, OverlayTexture.NO_OVERLAY);
        if (waterMorph > 0.05F) {
            renderSuspensionArms(poseStack, mainConsumer, waterMorph, vectorPitch, vectorRoll,
                    packedLight, OverlayTexture.NO_OVERLAY);
        }
        if (waterMorph > 0.25F) {
            renderHoverDuctedShrouds(poseStack, mainConsumer, waterMorph, vectorPitch, vectorRoll,
                    packedLight, OverlayTexture.NO_OVERLAY);
        }
        if (waterMorph > 0.28F) {
            renderCenterHydroSkirts(poseStack, mainConsumer, waterMorph, packedLight, OverlayTexture.NO_OVERLAY);
        }

        // 6. 绘制水上矢量气垫推进发光环与氮气排气管尾焰（使用 additive lightning 层）
        if (waterMorph > 0.35F) {
            renderHoverThrusterGlow(entity, partialTick, poseStack, bufferSource,
                    waterMorph, vectorPitch, vectorRoll);
        }
        if (entity.isNitroActive()) {
            renderNitroPlumes(entity, partialTick, poseStack, bufferSource);
        }

        // 7. 最后统一绘制半透明前风挡与车门侧窗玻璃，避免遮挡座舱内饰与乘客
        VertexConsumer glassConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(model.glassTexture()));
        model.renderTranslucentGlass(
                poseStack,
                glassConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                doorProgress
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 在四处轮拱内部全程绘制碳纤维防透光内衬背板与顶/端封板；水上变形时展开深灰机械装甲、散热百叶与液压基座。
     */
    private static void renderWheelWellBulkheads(PoseStack poseStack,
                                                 VertexConsumer consumer,
                                                 float waterMorph,
                                                 int packedLight,
                                                 int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        float armorDeploy = smoothstep(0.05F, 0.85F, waterMorph);

        float[] wheelZs = {SportsCarModelData.FRONT_WHEEL_Z, SportsCarModelData.REAR_WHEEL_Z};
        for (int side = -1; side <= 1; side += 2) {
            for (float wz : wheelZs) {
                float wallInnerX = side * 0.855F;
                float wallOuterX = side * 0.890F;
                float minX = Math.min(wallInnerX, wallOuterX);
                float maxX = Math.max(wallInnerX, wallOuterX);

                // 1. 全程轮舱内侧碳纤维防透光主背板（位于陆地轮胎内缘 0.912 内侧，绝不穿模）
                emitTexturedBox(mat, pose, consumer,
                        minX, 0.16F, wz - 0.56F,
                        maxX, 1.08F, wz + 0.56F,
                        UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 2. 前后轮拱端壁封板与顶部防透光拱顶（严格位于陆地轮胎包络半径 0.536 之外）
                float endMinX = side < 0 ? -1.04F : 0.86F;
                float endMaxX = side < 0 ? -0.86F : 1.04F;
                emitTexturedBox(mat, pose, consumer,
                        endMinX, 0.24F, wz - 0.59F,
                        endMaxX, 1.07F, wz - 0.55F,
                        UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);
                emitTexturedBox(mat, pose, consumer,
                        endMinX, 0.24F, wz + 0.55F,
                        endMaxX, 1.07F, wz + 0.59F,
                        UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);
                emitTexturedBox(mat, pose, consumer,
                        endMinX, 1.082F, wz - 0.56F,
                        endMaxX, 1.118F, wz + 0.56F,
                        UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 3. 水上模式展开深灰机械装甲面板、散热百叶与液压伺服锚定基座
                if (armorDeploy > 0.01F) {
                    float panelX0 = side * 0.888F;
                    float panelX1 = side * (0.892F + 0.026F * armorDeploy);
                    float pMinX = Math.min(panelX0, panelX1);
                    float pMaxX = Math.max(panelX0, panelX1);
                    emitTexturedBox(mat, pose, consumer,
                            pMinX, 0.34F, wz - 0.45F,
                            pMaxX, 1.02F, wz + 0.45F,
                            UV_DARK_GREY_U, UV_SWATCH_V, packedLight, packedOverlay);

                    // 三道海湾蓝/橙横向散热百叶筋条
                    float finX0 = side * (0.912F + 0.010F * armorDeploy);
                    float finX1 = side * (0.922F + 0.022F * armorDeploy);
                    float fMinX = Math.min(finX0, finX1);
                    float fMaxX = Math.max(finX0, finX1);
                    for (int i = 0; i < 3; i++) {
                        float fy = 0.84F + i * 0.055F;
                        float uTint = (i == 1) ? UV_ORANGE_U : UV_BLUE_U;
                        emitTexturedBox(mat, pose, consumer,
                                fMinX, fy, wz - 0.36F,
                                fMaxX, fy + 0.022F, wz + 0.36F,
                                uTint, UV_SWATCH_V, packedLight, packedOverlay);
                    }

                    // 中心液压伺服底座
                    float baseX0 = side * 0.890F;
                    float baseX1 = side * (0.900F + 0.055F * armorDeploy);
                    emitTexturedBox(mat, pose, consumer,
                            Math.min(baseX0, baseX1), 0.58F, wz - 0.16F,
                            Math.max(baseX0, baseX1), 0.80F, wz + 0.16F,
                            UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);
                }
            }
        }
    }

    /**
     * 在四个轮舱内绘制随变形实时伸展的双叉臂碳纤维液压折转悬架机构与万向关节盖。
     */
    private static void renderSuspensionArms(PoseStack poseStack,
                                             VertexConsumer consumer,
                                             float waterMorph,
                                             float vectorPitchDeg,
                                             float vectorRollDeg,
                                             int packedLight,
                                             int packedOverlay) {
        float armDeploy = smoothstep(0.05F, 0.72F, waterMorph);
        if (armDeploy <= 0.01F) {
            return;
        }

        float[] wheelZs = {SportsCarModelData.FRONT_WHEEL_Z, SportsCarModelData.REAR_WHEEL_Z};
        for (int side = -1; side <= 1; side += 2) {
            boolean isLeft = side < 0;
            for (float wz : wheelZs) {
                SportsCarModelData.HoverPodTransform pod = SportsCarModelData.computeWheelPodTransform(
                        side * SportsCarModelData.WHEEL_PIVOT_X,
                        SportsCarModelData.WHEEL_PIVOT_Y,
                        wz,
                        isLeft,
                        waterMorph,
                        vectorPitchDeg,
                        vectorRollDeg
                );

                // 折平后轮毂中心上表面万向连接座坐标
                float hubX = pod.tx();
                float hubY = pod.ty() + 0.175F * pod.foldPhase();
                float hubZ = pod.tz();

                float anchorX = side * 0.905F;
                // 上 V 形碳纤维控制臂（2 根）
                emitConnectingStrut(poseStack, consumer,
                        anchorX, 0.80F, wz - 0.26F,
                        hubX, hubY + 0.025F, hubZ - 0.075F,
                        0.024F, 0.020F, UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);
                emitConnectingStrut(poseStack, consumer,
                        anchorX, 0.80F, wz + 0.26F,
                        hubX, hubY + 0.025F, hubZ + 0.075F,
                        0.024F, 0.020F, UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 下 V 形碳纤维控制臂（2 根）
                emitConnectingStrut(poseStack, consumer,
                        anchorX, 0.45F, wz - 0.22F,
                        hubX, hubY - 0.025F, hubZ - 0.075F,
                        0.026F, 0.022F, UV_DARK_GREY_U, UV_SWATCH_V, packedLight, packedOverlay);
                emitConnectingStrut(poseStack, consumer,
                        anchorX, 0.45F, wz + 0.22F,
                        hubX, hubY - 0.025F, hubZ + 0.075F,
                        0.026F, 0.022F, UV_DARK_GREY_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 中央两段式液压伸缩推杆：上外缸(深灰) + 下活塞杆(浅灰高亮) + 橙色锁紧套环
                float p0x = side * 0.925F;
                float p0y = 0.96F;
                float p0z = wz;
                float p1x = hubX;
                float p1y = hubY + 0.035F;
                float p1z = hubZ;
                float midX = Mth.lerp(0.56F, p0x, p1x);
                float midY = Mth.lerp(0.56F, p0y, p1y);
                float midZ = Mth.lerp(0.56F, p0z, p1z);

                emitConnectingStrut(poseStack, consumer, p0x, p0y, p0z, midX, midY, midZ,
                        0.034F, 0.034F, UV_DARK_GREY_U, UV_SWATCH_V, packedLight, packedOverlay);
                emitConnectingStrut(poseStack, consumer,
                        Mth.lerp(0.45F, p0x, p1x), Mth.lerp(0.45F, p0y, p1y), Mth.lerp(0.45F, p0z, p1z),
                        p1x, p1y, p1z,
                        0.020F, 0.020F, UV_LIGHT_GREY_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 海湾橙液压锁环与轮盘中央万向关节盖
                PoseStack.Pose collarPose = poseStack.last();
                Matrix4f collarMat = collarPose.pose();
                emitTexturedBox(collarMat, collarPose, consumer,
                        midX - 0.042F, midY - 0.042F, midZ - 0.042F,
                        midX + 0.042F, midY + 0.042F, midZ + 0.042F,
                        UV_ORANGE_U, UV_SWATCH_V, packedLight, packedOverlay);

                poseStack.pushPose();
                poseStack.translate(hubX, hubY, hubZ);
                if (Math.abs(pod.vectorPitchDeg()) > 1.0E-3F) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(pod.vectorPitchDeg()));
                }
                if (Math.abs(pod.vectorRollDeg()) > 1.0E-3F) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-pod.vectorRollDeg()));
                }
                PoseStack.Pose hubPose = poseStack.last();
                Matrix4f hubMat = hubPose.pose();
                emitTexturedBox(hubMat, hubPose, consumer,
                        -0.13F, -0.045F, -0.13F,
                        0.13F, 0.055F, 0.13F,
                        UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);
                emitTexturedBox(hubMat, hubPose, consumer,
                        -0.085F, 0.045F, -0.085F,
                        0.085F, 0.075F, 0.085F,
                        UV_BLUE_U, UV_SWATCH_V, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }
    }

    /**
     * 在四个折平推进轮外圈展开加厚的碳纤维 + 海湾橙/蓝环形涵道护罩，并同步应用矢量推进倾角。
     */
    private static void renderHoverDuctedShrouds(PoseStack poseStack,
                                                 VertexConsumer consumer,
                                                 float waterMorph,
                                                 float vectorPitchDeg,
                                                 float vectorRollDeg,
                                                 int packedLight,
                                                 int packedOverlay) {
        float deploy = smoothstep(0.28F, 1.00F, waterMorph);
        if (deploy <= 0.01F) {
            return;
        }
        float radialScale = 0.72F + 0.28F * deploy;

        float[] wheelZs = {SportsCarModelData.FRONT_WHEEL_Z, SportsCarModelData.REAR_WHEEL_Z};
        for (int side = -1; side <= 1; side += 2) {
            boolean isLeft = side < 0;
            for (float wz : wheelZs) {
                SportsCarModelData.HoverPodTransform pod = SportsCarModelData.computeWheelPodTransform(
                        side * SportsCarModelData.WHEEL_PIVOT_X,
                        SportsCarModelData.WHEEL_PIVOT_Y,
                        wz,
                        isLeft,
                        waterMorph,
                        vectorPitchDeg,
                        vectorRollDeg
                );

                poseStack.pushPose();
                poseStack.translate(pod.tx(), pod.ty(), pod.tz());
                if (Math.abs(pod.vectorPitchDeg()) > 1.0E-3F) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(pod.vectorPitchDeg()));
                }
                if (Math.abs(pod.vectorRollDeg()) > 1.0E-3F) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-pod.vectorRollDeg()));
                }
                poseStack.scale(radialScale, deploy, radialScale);

                PoseStack.Pose pose = poseStack.last();
                Matrix4f mat = pose.pose();

                // 碳黑主环形涵道外壳（内径 0.555 > 轮胎半径 0.536，外径 0.635，厚实高度 -0.145..+0.155）
                emitOctagonalOrPolygonRing(mat, pose, consumer,
                        0.555F, 0.635F, -0.145F, 0.155F,
                        UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 外环中段海湾橙空气动力装饰腰带
                emitOctagonalOrPolygonRing(mat, pose, consumer,
                        0.632F, 0.648F, -0.038F, 0.048F,
                        UV_ORANGE_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 底缘海湾蓝切水导流唇口
                emitOctagonalOrPolygonRing(mat, pose, consumer,
                        0.548F, 0.642F, -0.168F, -0.136F,
                        UV_BLUE_U, UV_SWATCH_V, packedLight, packedOverlay);

                // 顶部四向深灰金属导流定子筋条（位于折平轮胎顶面 +0.191 之外的安全层，连接外环上沿至中央关节盖）
                for (int k = 0; k < 2; k++) {
                    poseStack.pushPose();
                    if (k == 1) {
                        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                    }
                    PoseStack.Pose vanePose = poseStack.last();
                    Matrix4f vaneMat = vanePose.pose();
                    emitTexturedBox(vaneMat, vanePose, consumer,
                            -0.575F, 0.145F, -0.026F,
                            -0.115F, 0.212F, 0.026F,
                            UV_SILVER_U, UV_SWATCH_V, packedLight, packedOverlay);
                    emitTexturedBox(vaneMat, vanePose, consumer,
                            0.115F, 0.145F, -0.026F,
                            0.575F, 0.212F, 0.026F,
                            UV_SILVER_U, UV_SWATCH_V, packedLight, packedOverlay);
                    poseStack.popPose();
                }

                poseStack.popPose();
            }
        }
    }

    /**
     * 在前后轮之间的侧裙下方展开紧凑厚实的碳纤维破浪导流裙（与前后涵道外径留出干净机械间隙）。
     */
    private static void renderCenterHydroSkirts(PoseStack poseStack,
                                                VertexConsumer consumer,
                                                float waterMorph,
                                                int packedLight,
                                                int packedOverlay) {
        float deploy = smoothstep(0.28F, 1.00F, waterMorph);
        if (deploy <= 0.01F) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        float bottomY = 0.08F - 0.30F * deploy;
        float topY = 0.135F;

        for (int side = -1; side <= 1; side += 2) {
            float innerX = side * 1.06F;
            float outerX = side * (1.16F + 0.14F * deploy);
            float minX = Math.min(innerX, outerX);
            float maxX = Math.max(innerX, outerX);

            // 主碳纤维紧凑导流裙（Z ∈ [-1.14, +1.26]，避开前涵道 -1.240 与后涵道 +1.365）
            emitTexturedBox(mat, pose, consumer,
                    minX, bottomY, -1.14F,
                    maxX, topY, 1.26F,
                    UV_CARBON_U, UV_SWATCH_V, packedLight, packedOverlay);

            // 外侧海湾橙腰线饰条
            float stripeMinX = side < 0 ? minX - 0.010F : maxX - 0.012F;
            float stripeMaxX = side < 0 ? minX + 0.012F : maxX + 0.010F;
            emitTexturedBox(mat, pose, consumer,
                    stripeMinX, bottomY + 0.075F, -1.04F,
                    stripeMaxX, topY - 0.025F, 1.16F,
                    UV_ORANGE_U, UV_SWATCH_V, packedLight, packedOverlay);

            // 底缘海湾蓝切水导流刃
            emitTexturedBox(mat, pose, consumer,
                    minX - (side < 0 ? 0.008F : 0.0F), bottomY - 0.024F, -1.10F,
                    maxX + (side > 0 ? 0.008F : 0.0F), bottomY + 0.014F, 1.22F,
                    UV_BLUE_U, UV_SWATCH_V, packedLight, packedOverlay);

            // 前后 22° 切水倒角导流端块
            for (int end = -1; end <= 1; end += 2) {
                poseStack.pushPose();
                float edgeZ = end < 0 ? -1.14F : 1.26F;
                poseStack.translate((minX + maxX) * 0.5F, (bottomY + topY) * 0.5F, edgeZ);
                poseStack.mulPose(Axis.XP.rotationDegrees(end < 0 ? -22.0F : 22.0F));
                PoseStack.Pose capPose = poseStack.last();
                Matrix4f capMat = capPose.pose();
                float halfW = (maxX - minX) * 0.46F;
                float halfH = (topY - bottomY) * 0.34F;
                float zStart = end < 0 ? -0.14F : 0.0F;
                float zEnd = end < 0 ? 0.0F : 0.14F;
                emitTexturedBox(capMat, capPose, consumer,
                        -halfW, -halfH, zStart,
                        halfW, halfH, zEnd,
                        UV_DARK_GREY_U, UV_SWATCH_V, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }
    }

    /**
     * 在 4 个水平折叠涵道推进舱下方绘制脉动海湾青蓝推进光环，并与推进舱保持同步矢量倾角。
     */
    private static void renderHoverThrusterGlow(SportsCarEntity entity,
                                                float partialTick,
                                                PoseStack poseStack,
                                                MultiBufferSource bufferSource,
                                                float waterMorph,
                                                float vectorPitchDeg,
                                                float vectorRollDeg) {
        float glowStrength = smoothstep(0.35F, 0.95F, waterMorph);
        if (glowStrength <= 0.01F) {
            return;
        }

        boolean nitro = entity.isNitroActive();
        float speedRatio = Mth.clamp(Math.abs(entity.getForwardSpeed()) / 0.90F, 0.0F, 1.2F);
        float time = entity.tickCount + partialTick;
        float pulse = 0.86F + 0.14F * Mth.sin(time * 0.45F);
        float intensity = glowStrength * pulse * (nitro ? 1.28F : (0.78F + 0.30F * speedRatio));

        int rIn = nitro ? 190 : 110;
        int gIn = nitro ? 248 : 230;
        int bIn = 255;
        int aIn = Mth.clamp((int) (185 * intensity), 10, 240);

        int rOut = 25;
        int gOut = nitro ? 185 : 155;
        int bOut = 255;
        int aOut = Mth.clamp((int) (45 * intensity), 0, 120);

        float[] wheelZs = {SportsCarModelData.FRONT_WHEEL_Z, SportsCarModelData.REAR_WHEEL_Z};
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (int side = -1; side <= 1; side += 2) {
            boolean isLeft = side < 0;
            for (float cz : wheelZs) {
                SportsCarModelData.HoverPodTransform pod = SportsCarModelData.computeWheelPodTransform(
                        side * SportsCarModelData.WHEEL_PIVOT_X,
                        SportsCarModelData.WHEEL_PIVOT_Y,
                        cz,
                        isLeft,
                        waterMorph,
                        vectorPitchDeg,
                        vectorRollDeg
                );

                poseStack.pushPose();
                poseStack.translate(pod.tx(), pod.ty(), pod.tz());
                if (Math.abs(pod.vectorPitchDeg()) > 1.0E-3F) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(pod.vectorPitchDeg()));
                }
                if (Math.abs(pod.vectorRollDeg()) > 1.0E-3F) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-pod.vectorRollDeg()));
                }
                Matrix4f mat = poseStack.last().pose();

                float innerR = 0.15F;
                float outerR = (nitro ? 0.62F : 0.52F) * (0.76F + 0.24F * glowStrength);
                emitHorizontalHoverRing(mat, buffer, 0.0F, -0.175F, 0.0F,
                        innerR, outerR, rIn, gIn, bIn, aIn, rOut, gOut, bOut, aOut);
                emitHorizontalHoverRing(mat, buffer, 0.0F, -0.275F, 0.0F,
                        0.06F, outerR * 0.64F, rIn, gIn, bIn, (int) (aIn * 0.68F), rOut, gOut, bOut, 0);

                poseStack.popPose();
            }
        }
    }

    /**
     * 绘制连接任意两三维点的方杆连杆，使用正交基矩阵对齐两点方向。
     */
    private static void emitConnectingStrut(PoseStack poseStack,
                                            VertexConsumer consumer,
                                            float x0, float y0, float z0,
                                            float x1, float y1, float z1,
                                            float halfWidth,
                                            float halfHeight,
                                            float u,
                                            float v,
                                            int packedLight,
                                            int packedOverlay) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        float invLen = 1.0F / len;
        float ux = dx * invLen;
        float uy = dy * invLen;
        float uz = dz * invLen;

        // 构造右手正交基：+X 沿连杆方向，r = normalize(u x ref)，v = r x u，确保 det=+1 且法线朝外
        float refX = 0.0F;
        float refY = Math.abs(uy) > 0.92F ? 0.0F : 1.0F;
        float refZ = Math.abs(uy) > 0.92F ? 1.0F : 0.0F;

        float rx = uy * refZ - uz * refY;
        float ry = uz * refX - ux * refZ;
        float rz = ux * refY - uy * refX;
        float rLen = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen < 1.0E-5F) {
            return;
        }
        rx /= rLen;
        ry /= rLen;
        rz /= rLen;

        float vx = ry * uz - rz * uy;
        float vy = rz * ux - rx * uz;
        float vz = rx * uy - ry * ux;

        Matrix4f basis = new Matrix4f(
                ux, uy, uz, 0.0F,
                vx, vy, vz, 0.0F,
                rx, ry, rz, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F
        );
        Quaternionf rot = new Quaternionf().setFromNormalized(basis);

        poseStack.pushPose();
        poseStack.translate(x0, y0, z0);
        poseStack.mulPose(rot);
        PoseStack.Pose pose = poseStack.last();
        emitTexturedBox(pose.pose(), pose, consumer,
                0.0F, -halfHeight, -halfWidth,
                len, halfHeight, halfWidth,
                u, v, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * 绘制加厚多边形环形涵道壁（包含外圆柱壁、内圆柱壁、顶环面与底环面）。
     */
    private static void emitOctagonalOrPolygonRing(Matrix4f mat,
                                                   PoseStack.Pose pose,
                                                   VertexConsumer consumer,
                                                   float rIn,
                                                   float rOut,
                                                   float yBottom,
                                                   float yTop,
                                                   float u,
                                                   float v,
                                                   int packedLight,
                                                   int packedOverlay) {
        for (int i = 0; i < DUCT_SEGMENTS; i++) {
            float a0 = (float) (2.0D * Math.PI * i / DUCT_SEGMENTS);
            float a1 = (float) (2.0D * Math.PI * (i + 1) / DUCT_SEGMENTS);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float cMid = Mth.cos((a0 + a1) * 0.5F);
            float sMid = Mth.sin((a0 + a1) * 0.5F);

            float ix0 = c0 * rIn;
            float iz0 = s0 * rIn;
            float ix1 = c1 * rIn;
            float iz1 = s1 * rIn;
            float ox0 = c0 * rOut;
            float oz0 = s0 * rOut;
            float ox1 = c1 * rOut;
            float oz1 = s1 * rOut;

            // 外环壁（法线朝外）
            emitTexturedQuad(mat, pose, consumer,
                    ox0, yBottom, oz0, ox0, yTop, oz0, ox1, yTop, oz1, ox1, yBottom, oz1,
                    cMid, 0.0F, sMid, u, v, packedLight, packedOverlay);
            // 内环壁（法线朝内）
            emitTexturedQuad(mat, pose, consumer,
                    ix1, yBottom, iz1, ix1, yTop, iz1, ix0, yTop, iz0, ix0, yBottom, iz0,
                    -cMid, 0.0F, -sMid, u, v, packedLight, packedOverlay);
            // 顶环面（+Y）
            emitTexturedQuad(mat, pose, consumer,
                    ix0, yTop, iz0, ix1, yTop, iz1, ox1, yTop, oz1, ox0, yTop, oz0,
                    0.0F, 1.0F, 0.0F, u, v, packedLight, packedOverlay);
            // 底环面（-Y）
            emitTexturedQuad(mat, pose, consumer,
                    ox0, yBottom, oz0, ox1, yBottom, oz1, ix1, yBottom, iz1, ix0, yBottom, iz0,
                    0.0F, -1.0F, 0.0F, u, v, packedLight, packedOverlay);
        }
    }

    private static void emitHorizontalHoverRing(Matrix4f mat,
                                                VertexConsumer buffer,
                                                float cx, float y, float cz,
                                                float innerR, float outerR,
                                                int rIn, int gIn, int bIn, int aIn,
                                                int rOut, int gOut, int bOut, int aOut) {
        for (int i = 0; i < HOVER_RING_SEGMENTS; i++) {
            float a0 = (float) (2.0D * Math.PI * i / HOVER_RING_SEGMENTS);
            float a1 = (float) (2.0D * Math.PI * (i + 1) / HOVER_RING_SEGMENTS);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);

            float ix0 = cx + c0 * innerR;
            float iz0 = cz + s0 * innerR;
            float ox0 = cx + c0 * outerR;
            float oz0 = cz + s0 * outerR;
            float ix1 = cx + c1 * innerR;
            float iz1 = cz + s1 * innerR;
            float ox1 = cx + c1 * outerR;
            float oz1 = cz + s1 * outerR;

            buffer.addVertex(mat, ix0, y, iz0).setColor(rIn, gIn, bIn, aIn);
            buffer.addVertex(mat, ox0, y, oz0).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ox1, y, oz1).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ix1, y, iz1).setColor(rIn, gIn, bIn, aIn);

            buffer.addVertex(mat, ix1, y, iz1).setColor(rIn, gIn, bIn, aIn);
            buffer.addVertex(mat, ox1, y, oz1).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ox0, y, oz0).setColor(rOut, gOut, bOut, aOut);
            buffer.addVertex(mat, ix0, y, iz0).setColor(rIn, gIn, bIn, aIn);
        }
    }

    private static void emitTexturedBox(Matrix4f mat,
                                        PoseStack.Pose pose,
                                        VertexConsumer consumer,
                                        float x0, float y0, float z0,
                                        float x1, float y1, float z1,
                                        float u, float v,
                                        int packedLight,
                                        int packedOverlay) {
        // 顶面 (+Y)
        emitTexturedQuad(mat, pose, consumer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                0.0F, 1.0F, 0.0F, u, v, packedLight, packedOverlay);
        // 底面 (-Y)
        emitTexturedQuad(mat, pose, consumer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                0.0F, -1.0F, 0.0F, u, v, packedLight, packedOverlay);
        // 前面 (-Z)
        emitTexturedQuad(mat, pose, consumer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
                0.0F, 0.0F, -1.0F, u, v, packedLight, packedOverlay);
        // 后面 (+Z)
        emitTexturedQuad(mat, pose, consumer, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1,
                0.0F, 0.0F, 1.0F, u, v, packedLight, packedOverlay);
        // 左面 (-X)
        emitTexturedQuad(mat, pose, consumer, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0,
                -1.0F, 0.0F, 0.0F, u, v, packedLight, packedOverlay);
        // 右面 (+X)
        emitTexturedQuad(mat, pose, consumer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
                1.0F, 0.0F, 0.0F, u, v, packedLight, packedOverlay);
    }

    private static void emitTexturedQuad(Matrix4f mat,
                                         PoseStack.Pose pose,
                                         VertexConsumer consumer,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float x3, float y3, float z3,
                                         float nx, float ny, float nz,
                                         float u, float v,
                                         int packedLight,
                                         int packedOverlay) {
        consumer.addVertex(mat, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
    }

    private static void renderNitroPlumes(SportsCarEntity entity,
                                          float partialTick,
                                          PoseStack poseStack,
                                          MultiBufferSource bufferSource) {
        float time = entity.tickCount + partialTick;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.translate(side * EXHAUST_X, EXHAUST_Y, EXHAUST_Z);
            Matrix4f mat = poseStack.last().pose();

            float flicker = 0.88F + 0.14F * Mth.sin(time * 2.4F + side * 1.3F)
                    + 0.07F * Mth.cos(time * 4.1F);

            // 外层海湾青蓝火焰
            emitCrossedPlume(mat, buffer, 0.82F * flicker, 0.085F, 0.020F,
                    55, 195, 255, 215,
                    20, 100, 255, 35);
            // 内层高热白芯
            emitCrossedPlume(mat, buffer, 0.46F * flicker, 0.048F, 0.010F,
                    235, 250, 255, 255,
                    125, 225, 255, 165);

            poseStack.popPose();
        }
    }

    private static void emitCrossedPlume(Matrix4f mat,
                                         VertexConsumer buffer,
                                         float length,
                                         float rStart,
                                         float rEnd,
                                         int r0, int g0, int b0, int a0,
                                         int r1, int g1, int b1, int a1) {
        float d0 = rStart * 0.7071F;
        float d1 = rEnd * 0.7071F;

        // +45° 与 -45° 正交十字面片，确保各视角均可见尾焰
        emitDoubleQuad(mat, buffer,
                -d0, -d0, 0.0F, d0, d0, 0.0F,
                d1, d1, length, -d1, -d1, length,
                r0, g0, b0, a0, r1, g1, b1, a1);
        emitDoubleQuad(mat, buffer,
                -d0, d0, 0.0F, d0, -d0, 0.0F,
                d1, -d1, length, -d1, d1, length,
                r0, g0, b0, a0, r1, g1, b1, a1);
    }

    private static void emitDoubleQuad(Matrix4f mat,
                                       VertexConsumer buffer,
                                       float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3,
                                       int r0, int g0, int b0, int a0,
                                       int r1, int g1, int b1, int a1) {
        buffer.addVertex(mat, x0, y0, z0).setColor(r0, g0, b0, a0);
        buffer.addVertex(mat, x1, y1, z1).setColor(r0, g0, b0, a0);
        buffer.addVertex(mat, x2, y2, z2).setColor(r1, g1, b1, a1);
        buffer.addVertex(mat, x3, y3, z3).setColor(r1, g1, b1, a1);

        buffer.addVertex(mat, x3, y3, z3).setColor(r1, g1, b1, a1);
        buffer.addVertex(mat, x2, y2, z2).setColor(r1, g1, b1, a1);
        buffer.addVertex(mat, x1, y1, z1).setColor(r0, g0, b0, a0);
        buffer.addVertex(mat, x0, y0, z0).setColor(r0, g0, b0, a0);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * 每台跑车的二阶欠阻尼悬架弹簧与水上气垫涡轮自转状态。
     */
    private static final class SpringState {
        double lastX;
        double lastZ;
        double lastVx;
        double lastVz;
        int lastEntityTick;
        int lastUpdateClientTick;

        float prevPitch;
        float pitch;
        float pitchVel;

        float prevRoll;
        float roll;
        float rollVel;

        float prevTurbineSpin;
        float turbineSpin;

        float prevVectorPitch;
        float vectorPitch;
        float prevVectorRoll;
        float vectorRoll;

        SpringState(SportsCarEntity car) {
            this.lastX = car.getX();
            this.lastZ = car.getZ();
            this.lastEntityTick = car.tickCount;
        }

        void step(SportsCarEntity car) {
            this.prevPitch = this.pitch;
            this.prevRoll = this.roll;
            this.prevTurbineSpin = this.turbineSpin;
            this.prevVectorPitch = this.vectorPitch;
            this.prevVectorRoll = this.vectorRoll;
            this.lastEntityTick = car.tickCount;

            float morph = car.getWaterMorphProgress();
            if (morph > 0.25F) {
                float speedRatio = Mth.clamp(Math.abs(car.getForwardSpeed()) / 0.88F, 0.0F, 1.4F);
                float rate = (0.28F + 0.56F * speedRatio + (car.isNitroActive() ? 0.55F : 0.0F))
                        * smoothstep(0.25F, 0.85F, morph);
                this.turbineSpin = (this.turbineSpin + rate) % ((float) (Math.PI * 200.0D));
            }

            // 计算水上涵道推进舱矢量推进倾斜目标角（前进向后下方喷气取负俯仰，转向取横向外喷角）
            float targetVPitch = 0.0F;
            float targetVRoll = 0.0F;
            if (morph > 0.15F) {
                float throttle = car.getThrottle();
                if (car.isBraking() || throttle < -0.05F) {
                    targetVPitch = 7.5F;
                } else {
                    float fwdBoost = Math.max(0.0F, throttle);
                    float spd = Mth.clamp(car.getForwardSpeed() * 3.0F, 0.0F, 2.5F);
                    targetVPitch = -8.0F * fwdBoost - (car.isNitroActive() ? 4.0F : 0.0F) - spd;
                }
                targetVPitch = Mth.clamp(targetVPitch, -12.0F, 9.0F);
                targetVRoll = Mth.clamp(
                        -car.getSteerAngle() * 14.0F * (car.isDrifting() ? 1.35F : 1.0F),
                        -10.0F, 10.0F);
            }
            this.vectorPitch = Mth.lerp(0.24F, this.vectorPitch, targetVPitch);
            this.vectorRoll = Mth.lerp(0.24F, this.vectorRoll, targetVRoll);

            double x = car.getX();
            double z = car.getZ();
            double vx = x - this.lastX;
            double vz = z - this.lastZ;
            this.lastX = x;
            this.lastZ = z;

            // 若发生传送或刚载入导致瞬移，重置速度避免悬架瞬间抽搐
            if (vx * vx + vz * vz > 12.0D) {
                this.lastVx = 0.0D;
                this.lastVz = 0.0D;
                return;
            }

            double ax = vx - this.lastVx;
            double az = vz - this.lastVz;
            this.lastVx = vx;
            this.lastVz = vz;

            Vec3 forward = car.getWorldForward();
            Vec3 right = car.getWorldRight();
            float accelFwd = Mth.clamp((float) (ax * forward.x + az * forward.z), -0.09F, 0.09F);
            float accelRight = Mth.clamp((float) (ax * right.x + az * right.z), -0.09F, 0.09F);

            // 加速抬头（正 Pitch）、刹车点头（负 Pitch），过弯离心力使车身向外侧倾
            float waterDamp = car.isInWaterMode() ? 0.65F : 1.0F;
            float targetPitch = Mth.clamp(
                    (accelFwd * 75.0F + (car.isNitroActive() ? 1.8F : 0.0F)) * waterDamp,
                    -5.5F, 5.5F);
            float targetRoll = Mth.clamp(-accelRight * 85.0F * waterDamp, -6.5F, 6.5F);

            float pitchAccel = (targetPitch - this.pitch) * SPRING_OMEGA_SQ
                    - 2.0F * SPRING_ZETA * SPRING_OMEGA * this.pitchVel;
            this.pitchVel += pitchAccel * SPRING_DT;
            this.pitch = Mth.clamp(this.pitch + this.pitchVel * SPRING_DT, -9.0F, 9.0F);

            float rollAccel = (targetRoll - this.roll) * SPRING_OMEGA_SQ
                    - 2.0F * SPRING_ZETA * SPRING_OMEGA * this.rollVel;
            this.rollVel += rollAccel * SPRING_DT;
            this.roll = Mth.clamp(this.roll + this.rollVel * SPRING_DT, -10.0F, 10.0F);
        }
    }
}
