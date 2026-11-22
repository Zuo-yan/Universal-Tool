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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海湾 GT 跑车客户端总控：采集驾驶输入发包、自动切换与恢复第三人称视角、
 * 7 格第三人称视距与第一人称座舱视角对齐、氮气动态 FOV、ActionBar 极简状态提示（含 [HYDRO]）、
 * 陆地轮胎/尾焰粒子与水上多层次破浪/V字尾迹/甩尾水幕/氮气喷水柱特效，以及双座乘客姿态渲染变换。
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
    private static final Map<Integer, WaterParticleState> WATER_STATES = new ConcurrentHashMap<>();

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

        // 为附近及当前乘坐的所有跑车更新二阶悬架弹簧、循环音效与陆地/水上特效粒子
        List<SportsCarEntity> nearbyCars = mc.level.getEntitiesOfClass(
                SportsCarEntity.class, player.getBoundingBox().inflate(PARTICLE_RANGE));
        for (SportsCarEntity nearbyCar : nearbyCars) {
            SportsCarRenderer.tickSpringWithClock(nearbyCar, clientTickCounter);
            if (!mc.isPaused()) {
                spawnCarParticles(mc.level, nearbyCar, clientTickCounter);
            }
        }
        if (clientTickCounter % 40 == 0) {
            SportsCarRenderer.pruneStaleSprings(clientTickCounter);
            WATER_STATES.entrySet().removeIf(e -> clientTickCounter - e.getValue().lastSeenTick > 40);
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

        // 平移至主/副驾座椅精确世界坐标，同步车身姿态、悬架倾侧与水上微抬
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

        float morph = car.getWaterMorphProgress(partialTick);
        if (morph > 1.0E-3F) {
            float morphLift = SportsCarModelData.computeWaterMorphLift(morph);
            poseStack.translate(0.0F, morphLift, 0.0F);
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
        WATER_STATES.clear();
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

        if (car.isInWaterMode() || car.isWaterSurfaceActive() || car.getWaterMorphProgress() > 0.35F) {
            msg.append(Component.literal("  [HYDRO]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }

        if (car.isNitroActive()) {
            msg.append(Component.literal("  [NITRO]").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
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

        float morph = car.getWaterMorphProgress(partialTick);
        float morphLift = SportsCarModelData.computeWaterMorphLift(morph);

        Vector3f localEye = new Vector3f((float) seatLocal.x, COCKPIT_EYE_HEIGHT + morphLift, (float) seatLocal.z);
        orient.transform(localEye);
        Vec3 eyePos = base.add(localEye.x, localEye.y, localEye.z);

        if (level != null) {
            Vector3f localSafe = new Vector3f(0.0F, 0.95F + morphLift, 0.0F);
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

    private static void spawnCarParticles(Level level, SportsCarEntity car, int clientTick) {
        RandomSource rand = level.getRandom();
        Quaternionf orient = car.getRenderOrientation(1.0F);
        Vec3 base = car.getCarBasePosition(1.0F);
        float speed = car.getForwardSpeed();
        float absSpeed = Math.abs(speed);
        float morph = car.getWaterMorphProgress();
        boolean waterSurface = car.isWaterSurfaceActive();
        boolean hydroActive = waterSurface || morph > 0.35F;

        WaterParticleState wState = WATER_STATES.computeIfAbsent(car.getId(), id -> new WaterParticleState());
        wState.lastSeenTick = clientTick;

        double rawWaterY = car.getWaterSurfaceY();
        double waterY = Double.isFinite(rawWaterY) ? rawWaterY : (base.y + 0.08D);

        // 入水或水翼变形展开瞬间，在四轮周围迸发一圈清澈水花
        boolean enteredWater = !wState.wasWaterActive && (waterSurface || car.isInWaterMode());
        boolean morphBurst = wState.lastMorph < 0.25F && morph >= 0.25F && Double.isFinite(rawWaterY);
        if (enteredWater || morphBurst) {
            spawnEntrySplashRing(level, rand, orient, base, waterY);
        }
        wState.wasWaterActive = waterSurface || car.isInWaterMode();
        wState.lastMorph = morph;

        // 1. 氮气加速时双排气管始终生成蓝色与火焰尾焰粒子（水上与水雾交融）
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

        if (hydroActive) {
            // 水上模式禁止陆地橡胶黑烟与泥土碎屑，转为多层次破浪与尾迹浪花特效
            spawnWaterHydroParticles(level, rand, car, orient, base, waterY, speed, absSpeed);
            return;
        }

        // 2. 陆地漂移时左右后轮生成黑烟与橡胶尘雾
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

        // 3. 陆地高速行驶时地面扬起轻量碎屑粒子
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

    private static void spawnEntrySplashRing(Level level,
                                             RandomSource rand,
                                             Quaternionf orient,
                                             Vec3 base,
                                             double waterY) {
        float[] xs = {-1.18F, 1.18F, -1.18F, 1.18F};
        float[] zs = {SportsCarModelData.FRONT_WHEEL_Z, SportsCarModelData.FRONT_WHEEL_Z,
                SportsCarModelData.REAR_WHEEL_Z, SportsCarModelData.REAR_WHEEL_Z};
        for (int i = 0; i < 4; i++) {
            Vector3f p = orient.transform(new Vector3f(xs[i], 0.0F, zs[i]));
            double wx = base.x + p.x;
            double wz = base.z + p.z;
            for (int k = 0; k < 5; k++) {
                double angle = rand.nextDouble() * Math.PI * 2.0D;
                double radius = 0.35D + rand.nextDouble() * 0.28D;
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;
                double vx = Math.cos(angle) * (0.08D + rand.nextDouble() * 0.10D) + Math.signum(xs[i]) * 0.06D;
                double vy = 0.14D + rand.nextDouble() * 0.16D;
                double vz = Math.sin(angle) * (0.08D + rand.nextDouble() * 0.10D);
                level.addParticle(ParticleTypes.SPLASH, wx + ox, waterY + 0.05D, wz + oz, vx, vy, vz);
            }
            level.addParticle(ParticleTypes.CLOUD, wx, waterY + 0.08D, wz,
                    Math.signum(xs[i]) * 0.06D, 0.04D, (rand.nextDouble() - 0.5D) * 0.06D);
            level.addParticle(ParticleTypes.BUBBLE_POP, wx, waterY + 0.02D, wz, 0.0D, 0.05D, 0.0D);
        }
    }

    private static void spawnWaterHydroParticles(Level level,
                                                 RandomSource rand,
                                                 SportsCarEntity car,
                                                 Quaternionf orient,
                                                 Vec3 base,
                                                 double waterY,
                                                 float speed,
                                                 float absSpeed) {
        float speedRatio = Mth.clamp(absSpeed / 0.88F, 0.0F, 1.5F);
        float dirSign = speed >= 0.0F ? 1.0F : -1.0F;

        // A. 船首破浪：前涵道推进舱外缘切水点向斜上方激起飞溅浪花与白色水沫
        if (absSpeed > 0.08F) {
            int bowCount = Math.max(1, Math.round(1.0F + 2.5F * speedRatio));
            for (int side = -1; side <= 1; side += 2) {
                Vector3f bowPos = orient.transform(new Vector3f(side * 1.48F, 0.0F, -2.18F));
                double bx = base.x + bowPos.x;
                double bz = base.z + bowPos.z;
                for (int i = 0; i < bowCount; i++) {
                    Vector3f sprayVel = orient.transform(new Vector3f(
                            side * (0.10F + 0.18F * speedRatio) + (rand.nextFloat() - 0.5F) * 0.05F,
                            0.08F + 0.16F * speedRatio + rand.nextFloat() * 0.06F,
                            dirSign * (0.04F + 0.06F * speedRatio)));
                    level.addParticle(ParticleTypes.SPLASH,
                            bx + (rand.nextDouble() - 0.5D) * 0.16D,
                            waterY + 0.04D,
                            bz + (rand.nextDouble() - 0.5D) * 0.16D,
                            sprayVel.x, sprayVel.y, sprayVel.z);
                }
                if (rand.nextFloat() < 0.55F + 0.35F * speedRatio) {
                    Vector3f mistVel = orient.transform(new Vector3f(
                            side * (0.06F + 0.09F * speedRatio),
                            0.03F + 0.05F * speedRatio,
                            dirSign * 0.05F));
                    level.addParticle(ParticleTypes.CLOUD, bx, waterY + 0.06D, bz,
                            mistVel.x, mistVel.y, mistVel.z);
                }
                level.addParticle(ParticleTypes.BUBBLE_POP, bx, waterY + 0.02D, bz,
                        0.0D, 0.04D, 0.0D);
            }

            // B. V 字尾迹双浪：后涵道推进舱与车尾拖出双道白色泡沫与水花尾迹
            for (int side = -1; side <= 1; side += 2) {
                Vector3f wakePos = orient.transform(new Vector3f(side * 1.46F, 0.0F, 2.42F));
                double wx = base.x + wakePos.x;
                double wz = base.z + wakePos.z;
                Vector3f wakeVel = orient.transform(new Vector3f(
                        side * (0.05F + 0.07F * speedRatio),
                        0.03F + 0.06F * speedRatio,
                        dirSign * (0.08F + 0.10F * speedRatio)));
                level.addParticle(ParticleTypes.SPLASH, wx, waterY + 0.03D, wz,
                        wakeVel.x, wakeVel.y, wakeVel.z);
                level.addParticle(ParticleTypes.FISHING, wx, waterY + 0.02D, wz,
                        wakeVel.x * 0.7D, 0.02D, wakeVel.z * 0.7D);

                if (rand.nextFloat() < 0.45F + 0.40F * speedRatio) {
                    Vector3f foamPos = orient.transform(new Vector3f(side * 1.18F, 0.0F, 3.12F));
                    level.addParticle(ParticleTypes.CLOUD,
                            base.x + foamPos.x + (rand.nextDouble() - 0.5D) * 0.20D,
                            waterY + 0.04D,
                            base.z + foamPos.z + (rand.nextDouble() - 0.5D) * 0.20D,
                            wakeVel.x * 0.5D, 0.015D, wakeVel.z * 0.5D);
                }
            }
        }

        // C. 水上甩尾或急转时外侧涵道与导流裙掀起高抛扇形水幕浪花
        float steer = car.getSteerAngle();
        if ((car.isDrifting() || Math.abs(steer) > 0.24F) && absSpeed > 0.18F) {
            int outerSide = steer >= 0.0F ? 1 : -1;
            int curtainCount = car.isDrifting() ? 5 : 3;
            for (int i = 0; i < curtainCount; i++) {
                float zAlong = -1.10F + rand.nextFloat() * 3.10F;
                Vector3f arcPos = orient.transform(new Vector3f(outerSide * 1.56F, 0.0F, zAlong));
                Vector3f arcVel = orient.transform(new Vector3f(
                        outerSide * (0.18F + 0.22F * speedRatio),
                        0.16F + 0.18F * speedRatio + rand.nextFloat() * 0.08F,
                        (rand.nextFloat() - 0.3F) * 0.12F));
                level.addParticle(ParticleTypes.SPLASH,
                        base.x + arcPos.x, waterY + 0.05D, base.z + arcPos.z,
                        arcVel.x, arcVel.y, arcVel.z);
                if (i % 2 == 0) {
                    level.addParticle(ParticleTypes.CLOUD,
                            base.x + arcPos.x, waterY + 0.08D, base.z + arcPos.z,
                            arcVel.x * 0.65D, arcVel.y * 0.45D, arcVel.z * 0.65D);
                }
            }
        }

        // D. 水上氮气加速时车尾扩散器下方喷射高压白色水柱与水雾
        if (car.isNitroActive()) {
            for (int side = -1; side <= 1; side += 2) {
                Vector3f jetPos = orient.transform(new Vector3f(side * 0.36F, 0.0F, 3.38F));
                double jx = base.x + jetPos.x;
                double jz = base.z + jetPos.z;
                for (int k = 0; k < 3; k++) {
                    Vector3f jetVel = orient.transform(new Vector3f(
                            side * 0.05F + (rand.nextFloat() - 0.5F) * 0.08F,
                            0.12F + rand.nextFloat() * 0.12F,
                            0.38F + rand.nextFloat() * 0.14F));
                    level.addParticle(ParticleTypes.SPLASH, jx, waterY + 0.06D, jz,
                            jetVel.x, jetVel.y, jetVel.z);
                }
                Vector3f mistVel = orient.transform(new Vector3f(
                        (rand.nextFloat() - 0.5F) * 0.08F, 0.08F, 0.28F));
                level.addParticle(ParticleTypes.CLOUD, jx, waterY + 0.10D, jz,
                        mistVel.x, mistVel.y, mistVel.z);
                level.addParticle(ParticleTypes.BUBBLE_POP, jx, waterY + 0.04D, jz,
                        mistVel.x * 0.5D, 0.06D, mistVel.z * 0.5D);
            }
        }
    }

    private static final class WaterParticleState {
        boolean wasWaterActive;
        float lastMorph;
        int lastSeenTick;
    }
}
