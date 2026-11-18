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

    private static final Map<Integer, SpringState> SPRING_STATES = new ConcurrentHashMap<>();

    public SportsCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.35F;
    }

    /**
     * 每游戏刻更新指定跑车实体的客户端二阶弹簧悬架姿态（加速抬头、刹车点头、过弯侧倾）。
     */
    public static void tickSpring(SportsCarEntity car) {
        SpringState state = SPRING_STATES.computeIfAbsent(car.getId(), id -> new SpringState(car));
        state.step(car);
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

        // 若实体未在客户端附近循环中更新到当前 tick，兜底推进一次弹簧状态
        SpringState spring = SPRING_STATES.computeIfAbsent(entity.getId(), id -> new SpringState(entity));
        if (spring.lastEntityTick != entity.tickCount) {
            spring.step(entity);
        }

        float fakePitch = Mth.lerp(partialTick, spring.prevPitch, spring.pitch);
        float fakeRoll = Mth.lerp(partialTick, spring.prevRoll, spring.roll);
        float steerAngle = entity.getSteerAngle(partialTick);
        float wheelSpin = entity.getWheelSpin(partialTick);
        float doorProgress = entity.getDoorOpenProgress(partialTick);

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

        // 1. 应用服务端同步的航向与四轮地形拟合旋转矩阵
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

        // 3. 绘制所有不透明部件（车身、四轮、制动系统、方向盘、迎宾门、智能车灯）
        VertexConsumer mainConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(model.mainTexture()));
        model.renderOpaqueParts(
                poseStack,
                mainConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                steerAngle,
                wheelSpin,
                doorProgress,
                headlightLight,
                headlightTint,
                taillightLight,
                taillightTint
        );

        // 4. 氮气加速时于双侧排气管口绘制发光蓝焰
        if (entity.isNitroActive()) {
            renderNitroPlumes(entity, partialTick, poseStack, bufferSource);
        }

        // 5. 最后统一绘制半透明前风挡与车门侧窗玻璃，避免遮挡座舱内饰与乘客
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

    /**
     * 每台跑车的二阶欠阻尼悬架弹簧状态。
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

        SpringState(SportsCarEntity car) {
            this.lastX = car.getX();
            this.lastZ = car.getZ();
            this.lastEntityTick = car.tickCount;
        }

        void step(SportsCarEntity car) {
            this.prevPitch = this.pitch;
            this.prevRoll = this.roll;
            this.lastEntityTick = car.tickCount;

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
            float targetPitch = Mth.clamp(
                    accelFwd * 75.0F + (car.isNitroActive() ? 1.8F : 0.0F),
                    -5.5F, 5.5F);
            float targetRoll = Mth.clamp(-accelRight * 85.0F, -6.5F, 6.5F);

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
}
