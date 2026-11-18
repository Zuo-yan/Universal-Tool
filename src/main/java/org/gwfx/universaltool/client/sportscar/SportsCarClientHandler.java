package org.gwfx.universaltool.client.sportscar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.sportscar.SportsCarEntity;
import org.gwfx.universaltool.sportscar.SportsCarInputPayload;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 海湾 GT 跑车客户端总控：采集驾驶输入发包、自动切换与恢复第三人称视角、
 * 7 格第三人称视距与第一人称座舱视角对齐、氮气动态 FOV、ActionBar 极简状态提示、
 * 轮胎/氮气/碎屑粒子生成，以及主副驾双座乘客姿态渲染变换。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class SportsCarClientHandler {

    private static final float THIRD_PERSON_CAMERA_DISTANCE = 7.0F;
    private static final float THIRD_PERSON_PIVOT_HEIGHT = 1.55F;
    private static final float COCKPIT_EYE_HEIGHT = 1.20F;
    private static final double NITRO_FOV_BONUS = 8.0D;
    private static final double PARTICLE_RANGE = 48.0D;

    private static final Method CAMERA_SET_POSITION = resolveCameraSetPosition();
    private static final Set<Player> PUSHED_PLAYER_POSES = Collections.newSetFromMap(new IdentityHashMap<>());

    private static int clientTickCounter;
    private static int trackedCarId = -1;
    @Nullable
    private static CameraType savedCameraType;
    private static float prevNitroFovScale;
    private static float nitroFovScale;

    private SportsCarClientHandler() {
    }

    private static Method resolveCameraSetPosition() {
        try {
            Method m = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            UniversalToolMod.LOGGER.warn("无法反射访问 Camera#setPosition(Vec3)，跑车相机枢轴偏移已禁用", e);
            return null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetTracking(mc, false);
            return;
        }

        clientTickCounter++;

        // 为附近及当前乘坐的所有跑车更新二阶悬架弹簧、循环音效与轮胎/尾焰粒子
        List<SportsCarEntity> nearbyCars = mc.level.getEntitiesOfClass(
                SportsCarEntity.class, player.getBoundingBox().inflate(PARTICLE_RANGE));
        for (SportsCarEntity nearbyCar : nearbyCars) {
            SportsCarRenderer.tickSpringWithClock(nearbyCar, clientTickCounter);
            if (!mc.isPaused()) {
                spawnCarParticles(mc.level, nearbyCar);
            }
        }
        if (clientTickCounter % 40 == 0) {
            SportsCarRenderer.pruneStaleSprings(clientTickCounter);
        }
        SportsCarSoundController.updateNearbyCars(mc, nearbyCars);

        if (!(player.getVehicle() instanceof SportsCarEntity car)) {
            resetTracking(mc, true);
            return;
        }

        ensureMountedAndCameraSwitched(mc, car);

        prevNitroFovScale = nitroFovScale;
        nitroFovScale = Mth.lerp(0.18F, nitroFovScale, car.isNitroActive() ? 1.0F : 0.0F);

        // 让座舱内玩家躯干朝向随车体锁定，头部可自由环顾四周
        player.yBodyRot = car.getCarYaw();
        player.yBodyRotO = player.yBodyRot;

        if (car.isDriver(player)) {
            sendDriverInputs(mc, player, car);
        }

        displayMinimalActionBar(player, car);
    }

    @SubscribeEvent
    public static void onDetachedCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = event.getCamera();
        if (player == null || camera.getEntity() != player) {
            return;
        }
        if (!(player.getVehicle() instanceof SportsCarEntity car)) {
            return;
        }

        // 将第三人称相机追踪枢轴抬高至车顶上方，再由原生射线防穿墙向后拉远至 7 格
        float partialTick = camera.getPartialTickTime();
        Vec3 base = car.getCarBasePosition(partialTick);
        Vector3f localPivot = new Vector3f(0.0F, THIRD_PERSON_PIVOT_HEIGHT, 0.0F);
        car.getRenderOrientation(partialTick).transform(localPivot);
        setCameraPosition(camera, base.add(localPivot.x, localPivot.y, localPivot.z));

        event.setDistance(THIRD_PERSON_CAMERA_DISTANCE);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = event.getCamera();
        if (player == null || camera.getEntity() != player) {
            return;
        }
        if (!(player.getVehicle() instanceof SportsCarEntity car)) {
            return;
        }

        float partialTick = (float) event.getPartialTick();

        // 第一人称下支持左/右舵座舱真实视点（透过前风挡看方向盘与仪表台）
        if (event.usedConfiguredFov() && !camera.isDetached()) {
            applyCockpitCameraPosition(mc.level, camera, player, car, partialTick);
        }

        if (event.usedConfiguredFov()) {
            float fovScale = Mth.lerp(partialTick, prevNitroFovScale, nitroFovScale);
            if (fovScale > 0.005F) {
                event.setFOV(event.getFOV() + NITRO_FOV_BONUS * fovScale);
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = event.getCamera();
        if (player == null || camera.getEntity() != player) {
            return;
        }
        if (!(player.getVehicle() instanceof SportsCarEntity car)) {
            return;
        }

        // 第一人称座舱视角下适度融合车身侧倾，增强驾驶沉浸感
        if (!camera.isDetached()) {
            float partialTick = (float) event.getPartialTick();
            float roll = car.getTerrainRoll(partialTick)
                    + SportsCarRenderer.getInterpolatedFakeRoll(car, partialTick);
            event.setRoll(Mth.wrapDegrees(event.getRoll() + roll * 0.45F));
        }
    }

    @SubscribeEvent
    public static void onBlockScreenOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK
                && event.getPlayer().getVehicle() instanceof SportsCarEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof SportsCarEntity car)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        Vec3 seatPos = car.getSeatWorldPosition(player, partialTick);
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());

        poseStack.pushPose();
        PUSHED_PLAYER_POSES.add(player);

        // 平移至主/副驾座椅精确世界坐标，并应用车身姿态与悬架倾侧
        poseStack.translate(seatPos.x - px, seatPos.y - py, seatPos.z - pz);
        poseStack.mulPose(car.getRenderOrientation(partialTick));

        float fakePitch = SportsCarRenderer.getInterpolatedFakePitch(car, partialTick);
        float fakeRoll = SportsCarRenderer.getInterpolatedFakeRoll(car, partialTick);
        if (Math.abs(fakePitch) > 1.0E-3F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(fakePitch * 0.7F));
        }
        if (Math.abs(fakeRoll) > 1.0E-3F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-fakeRoll * 0.7F));
        }

        // 抵消原版 yBodyRot 旋转使乘客朝向车头 -Z，并略微缩沉以贴合赛车桶椅且不穿出车顶
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw - 180.0F));
        poseStack.translate(0.0F, -0.56F, 0.0F);
        poseStack.scale(0.90F, 0.90F, 0.90F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (PUSHED_PLAYER_POSES.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetTracking(Minecraft.getInstance(), false);
        PUSHED_PLAYER_POSES.clear();
        SportsCarSoundController.clearAll();
        SportsCarRenderer.clearSprings();
    }

    private static void ensureMountedAndCameraSwitched(Minecraft mc, SportsCarEntity car) {
        if (trackedCarId != car.getId()) {
            if (trackedCarId == -1) {
                savedCameraType = mc.options.getCameraType();
                if (savedCameraType != CameraType.THIRD_PERSON_BACK) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
            trackedCarId = car.getId();
            prevNitroFovScale = 0.0F;
            nitroFovScale = 0.0F;
        }
    }

    private static void resetTracking(Minecraft mc, boolean restoreCamera) {
        if (restoreCamera && savedCameraType != null && mc.options != null) {
            mc.options.setCameraType(savedCameraType);
        }
        savedCameraType = null;
        trackedCarId = -1;
        prevNitroFovScale = 0.0F;
        nitroFovScale = 0.0F;
    }

    private static void sendDriverInputs(Minecraft mc, LocalPlayer player, SportsCarEntity car) {
        float throttle = 0.0F;
        float steerOverride = 0.0F;
        boolean handbrake = false;
        boolean nitro = false;
        float targetYaw = player.getYRot();

        if (mc.screen == null && !mc.isPaused()) {
            if (player.input.up) {
                throttle += 1.0F;
            }
            if (player.input.down) {
                throttle -= 1.0F;
            }
            if (player.input.left) {
                steerOverride += 1.0F;
            }
            if (player.input.right) {
                steerOverride -= 1.0F;
            }
            handbrake = player.input.jumping;
            nitro = mc.options.keySprint.isDown();
        }

        car.applyClientPrediction(throttle, steerOverride, handbrake, nitro);
        PacketDistributor.sendToServer(new SportsCarInputPayload(
                throttle, steerOverride, targetYaw, handbrake, nitro));
    }

    private static void displayMinimalActionBar(LocalPlayer player, SportsCarEntity car) {
        float nitro = car.getNitroEnergy();
        int filledBars = Mth.clamp(Math.round(nitro / 10.0F), 0, 10);
        String bar = "|".repeat(filledBars) + ".".repeat(10 - filledBars);
        int speedKmh = Math.round(Math.abs(car.getForwardSpeed()) * 20.0F * 3.6F);

        ChatFormatting barColor = car.isNitroActive()
                ? ChatFormatting.AQUA
                : (nitro >= 25.0F ? ChatFormatting.GREEN : ChatFormatting.YELLOW);

        MutableComponent msg = Component.literal("N₂O [")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(bar).withStyle(barColor))
                .append(Component.literal("] " + Math.round(nitro) + "%  " + speedKmh + " km/h")
                        .withStyle(ChatFormatting.WHITE));

        if (car.isNitroActive()) {
            msg.append(Component.literal("  [NITRO]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        } else if (car.isDrifting()) {
            msg.append(Component.literal("  [DRIFT]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else if (car.isBraking()) {
            msg.append(Component.literal("  [BRAKE]").withStyle(ChatFormatting.RED));
        }

        player.displayClientMessage(msg, true);
    }

    private static void applyCockpitCameraPosition(@Nullable Level level,
                                                   Camera camera,
                                                   LocalPlayer player,
                                                   SportsCarEntity car,
                                                   float partialTick) {
        if (CAMERA_SET_POSITION == null) {
            return;
        }
        int seatIndex = Math.max(0, car.getSeatIndex(player));
        Vec3 seatLocal = car.getSeatLocalPosition(seatIndex);
        Quaternionf orient = car.getRenderOrientation(partialTick);
        Vec3 base = car.getCarBasePosition(partialTick);

        Vector3f localEye = new Vector3f((float) seatLocal.x, COCKPIT_EYE_HEIGHT, (float) seatLocal.z);
        orient.transform(localEye);
        Vec3 eyePos = base.add(localEye.x, localEye.y, localEye.z);

        if (level != null) {
            Vector3f localSafe = new Vector3f(0.0F, 0.95F, 0.0F);
            orient.transform(localSafe);
            Vec3 origin = base.add(localSafe.x, localSafe.y, localSafe.z);
            HitResult hit = level.clip(new ClipContext(
                    origin, eyePos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, car));
            if (hit.getType() != HitResult.Type.MISS) {
                eyePos = origin.lerp(hit.getLocation(), 0.85D);
            }
        }
        setCameraPosition(camera, eyePos);
    }

    private static void setCameraPosition(Camera camera, Vec3 pos) {
        if (CAMERA_SET_POSITION == null) {
            return;
        }
        try {
            CAMERA_SET_POSITION.invoke(camera, pos);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void spawnCarParticles(Level level, SportsCarEntity car) {
        RandomSource rand = level.getRandom();
        Quaternionf orient = car.getRenderOrientation(1.0F);
        Vec3 base = car.getCarBasePosition(1.0F);
        float speed = car.getForwardSpeed();
        float absSpeed = Math.abs(speed);

        // 1. 漂移时左右后轮生成黑烟与橡胶尘雾
        if (car.isDrifting() && car.onGround()) {
            for (int side = -1; side <= 1; side += 2) {
                Vector3f localWheel = new Vector3f(side * 1.103F, 0.08F, 2.00F);
                orient.transform(localWheel);
                double wx = base.x + localWheel.x + (rand.nextDouble() - 0.5D) * 0.18D;
                double wy = base.y + localWheel.y;
                double wz = base.z + localWheel.z + (rand.nextDouble() - 0.5D) * 0.18D;

                level.addParticle(ParticleTypes.LARGE_SMOKE, wx, wy, wz,
                        (rand.nextDouble() - 0.5D) * 0.04D,
                        0.03D + rand.nextDouble() * 0.04D,
                        (rand.nextDouble() - 0.5D) * 0.04D);
                level.addParticle(ParticleTypes.SMOKE, wx, wy + 0.05D, wz,
                        (rand.nextDouble() - 0.5D) * 0.06D,
                        0.04D + rand.nextDouble() * 0.05D,
                        (rand.nextDouble() - 0.5D) * 0.06D);
            }
        }

        // 2. 氮气加速时双排气管生成蓝色与火焰尾焰粒子
        if (car.isNitroActive()) {
            Vector3f backwardVel = orient.transform(new Vector3f(0.0F, 0.02F, 0.36F));
            for (int side = -1; side <= 1; side += 2) {
                Vector3f localExhaust = new Vector3f(side * 0.256F, 0.693F, 3.28F);
                orient.transform(localExhaust);
                double ex = base.x + localExhaust.x;
                double ey = base.y + localExhaust.y;
                double ez = base.z + localExhaust.z;

                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, ex, ey, ez,
                        backwardVel.x + (rand.nextDouble() - 0.5D) * 0.04D,
                        backwardVel.y + (rand.nextDouble() - 0.5D) * 0.03D,
                        backwardVel.z + (rand.nextDouble() - 0.5D) * 0.04D);
                if (rand.nextBoolean()) {
                    level.addParticle(ParticleTypes.FLAME, ex, ey, ez,
                            backwardVel.x * 0.75D,
                            backwardVel.y + 0.01D,
                            backwardVel.z * 0.75D);
                }
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, ex, ey, ez,
                        backwardVel.x * 1.15D + (rand.nextDouble() - 0.5D) * 0.06D,
                        backwardVel.y + (rand.nextDouble() - 0.5D) * 0.05D,
                        backwardVel.z * 1.15D + (rand.nextDouble() - 0.5D) * 0.06D);
            }
        }

        // 3. 高速行驶时地面扬起轻量碎屑粒子
        if (car.onGround() && absSpeed > 0.25F && rand.nextFloat() < Math.min(0.75F, absSpeed * 0.65F)) {
            int side = rand.nextBoolean() ? -1 : 1;
            Vector3f localRear = new Vector3f(side * 1.103F, 0.05F, 2.00F);
            orient.transform(localRear);
            double gx = base.x + localRear.x;
            double gy = base.y + localRear.y;
            double gz = base.z + localRear.z;

            BlockPos groundPos = BlockPos.containing(gx, gy - 0.18D, gz);
            BlockState groundState = level.getBlockState(groundPos);
            if (!groundState.isAir()) {
                Vector3f kick = orient.transform(new Vector3f(
                        (rand.nextFloat() - 0.5F) * 0.08F,
                        0.08F + rand.nextFloat() * 0.06F,
                        speed >= 0.0F ? 0.14F : -0.14F));
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                        gx, gy, gz, kick.x, kick.y, kick.z);
            }
        }
    }
}
