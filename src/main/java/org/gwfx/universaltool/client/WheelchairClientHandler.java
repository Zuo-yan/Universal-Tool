package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.wheelchair.AllTerrainWheelchairEntity;
import org.gwfx.universaltool.wheelchair.WheelchairInputPayload;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Client-side driving input sender, omni-directional wall/ceiling camera controller,
 * and rider seat orientation transformer for {@link AllTerrainWheelchairEntity}.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class WheelchairClientHandler {

    private static final float DEFAULT_EYE_OFFSET = 0.88F;
    private static final float MIN_EYE_OFFSET = 0.20F;
    private static final double BOOST_FOV_BONUS = 6.0D;

    private static final Method CAMERA_SET_POSITION = resolveCameraSetPosition();
    private static final Set<Player> PUSHED_PLAYER_POSES = Collections.newSetFromMap(new IdentityHashMap<>());

    private static int trackedWheelchairId = -1;
    private static float localLookYaw = 0.0F;
    private static float lastSeenPlayerYRot = 0.0F;
    private static float prevEyeNormalOffset = DEFAULT_EYE_OFFSET;
    private static float eyeNormalOffset = DEFAULT_EYE_OFFSET;
    private static float prevBoostFovScale = 0.0F;
    private static float boostFovScale = 0.0F;

    private WheelchairClientHandler() {
    }

    private static Method resolveCameraSetPosition() {
        try {
            Method m = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            UniversalToolMod.LOGGER.warn("Unable to access Camera#setPosition(Vec3); camera normal offset disabled", e);
            return null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetState();
            return;
        }
        if (!(player.getVehicle() instanceof AllTerrainWheelchairEntity wc)) {
            resetState();
            return;
        }

        ensureTracked(player, wc);
        updateSafeEyeDistance(mc.level, wc);

        prevBoostFovScale = boostFovScale;
        boostFovScale = Mth.lerp(0.22F, boostFovScale, wc.isBoosting() ? 1.0F : 0.0F);

        // Keep player body aligned with wheelchair seat while head turns smoothly within natural limits.
        player.yBodyRot = player.getYRot() - Mth.clamp(localLookYaw, -75.0F, 75.0F);
        player.yBodyRotO = player.yBodyRot;

        float throttle = 0.0F;
        float steer = 0.0F;
        boolean jumping = false;
        boolean boosting = false;

        if (mc.screen == null && !mc.isPaused()) {
            if (player.input.up) {
                throttle += 1.0F;
            }
            if (player.input.down) {
                throttle -= 1.0F;
            }
            if (player.input.left) {
                steer += 1.0F;
            }
            if (player.input.right) {
                steer -= 1.0F;
            }
            jumping = player.input.jumping;
            boosting = mc.options.keySprint.isDown();
        }

        wc.applyClientInput(throttle, steer, jumping, boosting);
        PacketDistributor.sendToServer(new WheelchairInputPayload(throttle, steer, jumping, boosting));
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(player.getVehicle() instanceof AllTerrainWheelchairEntity wc)) {
            return;
        }
        if (event.getCamera().getEntity() != player) {
            return;
        }

        float partialTick = (float) event.getPartialTick();
        ensureTracked(player, wc);

        float curYRot = player.getYRot();
        float deltaYRot = Mth.wrapDegrees(curYRot - lastSeenPlayerYRot);
        lastSeenPlayerYRot = curYRot;
        localLookYaw = Mth.wrapDegrees(localLookYaw + deltaYRot);

        float localLookPitch = Mth.clamp(Mth.lerp(partialTick, player.xRotO, player.getXRot()), -85.0F, 85.0F);

        Quaternionf camQuat = new Quaternionf(wc.getRenderOrientation(partialTick));
        camQuat.rotateY((float) Math.toRadians(-localLookYaw));
        camQuat.rotateX((float) Math.toRadians(-localLookPitch));

        Vector3f forw = camQuat.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f up = camQuat.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f right = camQuat.transform(new Vector3f(1.0F, 0.0F, 0.0F));

        // Decompose into Camera#setRotation(yaw, pitch, roll) -> rotationYXZ(PI - yaw, -pitch, -roll).
        float angleX;
        float angleY;
        float angleZ;
        if (Math.abs(forw.y) >= 0.9999F) {
            angleX = forw.y >= 0.0F ? (float) (Math.PI * 0.5D) : (float) (-Math.PI * 0.5D);
            angleY = 0.0F;
            angleZ = forw.y >= 0.0F
                    ? (float) Math.atan2(-up.x, up.z)
                    : (float) Math.atan2(-up.x, -up.z);
        } else {
            angleX = (float) Math.asin(Mth.clamp(forw.y, -1.0F, 1.0F));
            angleY = (float) Math.atan2(-forw.x, -forw.z);
            angleZ = (float) Math.atan2(right.y, up.y);
        }

        event.setYaw(Mth.wrapDegrees(180.0F - (float) Math.toDegrees(angleY)));
        event.setPitch((float) -Math.toDegrees(angleX));
        event.setRoll(Mth.wrapDegrees((float) -Math.toDegrees(angleZ)));
    }

    @SubscribeEvent
    public static void onDetachedCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = event.getCamera();
        if (player == null || camera.getEntity() != player) {
            return;
        }
        if (!(player.getVehicle() instanceof AllTerrainWheelchairEntity wc)) {
            return;
        }
        // Reposition camera pivot along surface normal before Camera#getMaxZoom raycasts backwards.
        applyCameraPivotOffset(camera, wc, camera.getPartialTickTime());
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = event.getCamera();
        if (player == null || camera.getEntity() != player) {
            return;
        }
        if (!(player.getVehicle() instanceof AllTerrainWheelchairEntity wc)) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        if (event.usedConfiguredFov() && !camera.isDetached()) {
            applyCameraPivotOffset(camera, wc, partialTick);
        }
        if (event.usedConfiguredFov()) {
            float fovBoost = Mth.lerp(partialTick, prevBoostFovScale, boostFovScale);
            if (fovBoost > 0.005F) {
                event.setFOV(event.getFOV() + BOOST_FOV_BONUS * fovBoost);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockScreenOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK
                && event.getPlayer().getVehicle() instanceof AllTerrainWheelchairEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof AllTerrainWheelchairEntity wc)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        Vec3 seatPos = wc.getSeatPosition(partialTick);
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());

        poseStack.pushPose();
        PUSHED_PLAYER_POSES.add(player);

        // Move to rider's seat pivot, apply wheelchair orientation, and cancel vanilla bodyYaw spin.
        poseStack.translate(seatPos.x - px, seatPos.y - py, seatPos.z - pz);
        poseStack.mulPose(wc.getRenderOrientation(partialTick));
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw - 180.0F));
        poseStack.translate(0.0F, -0.60F, 0.0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (PUSHED_PLAYER_POSES.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    private static void ensureTracked(LocalPlayer player, AllTerrainWheelchairEntity wc) {
        if (trackedWheelchairId != wc.getId()) {
            trackedWheelchairId = wc.getId();
            lastSeenPlayerYRot = player.getYRot();
            if (wc.getAttachedFace() == Direction.DOWN) {
                localLookYaw = Mth.wrapDegrees(player.getYRot() - wc.getWheelchairYaw());
            } else {
                localLookYaw = 0.0F;
            }
            prevEyeNormalOffset = DEFAULT_EYE_OFFSET;
            eyeNormalOffset = DEFAULT_EYE_OFFSET;
            prevBoostFovScale = 0.0F;
            boostFovScale = 0.0F;
        }
    }

    private static void resetState() {
        trackedWheelchairId = -1;
        localLookYaw = 0.0F;
        lastSeenPlayerYRot = 0.0F;
        prevEyeNormalOffset = DEFAULT_EYE_OFFSET;
        eyeNormalOffset = DEFAULT_EYE_OFFSET;
        prevBoostFovScale = 0.0F;
        boostFovScale = 0.0F;
    }

    private static void updateSafeEyeDistance(Level level, AllTerrainWheelchairEntity wc) {
        Vec3 seatPos = wc.getSeatPosition(1.0F);
        Vector3f norm = wc.getRenderOrientation(1.0F).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vec3 normalDir = new Vec3(norm.x, norm.y, norm.z);

        Vec3 end = seatPos.add(normalDir.scale(DEFAULT_EYE_OFFSET + 0.16D));
        HitResult hit = level.clip(new ClipContext(
                seatPos, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, wc));

        float target = DEFAULT_EYE_OFFSET;
        if (hit.getType() != HitResult.Type.MISS) {
            double hitDist = hit.getLocation().distanceTo(seatPos);
            target = (float) Mth.clamp(hitDist - 0.16D, MIN_EYE_OFFSET, DEFAULT_EYE_OFFSET);
        }
        prevEyeNormalOffset = eyeNormalOffset;
        eyeNormalOffset = Mth.lerp(0.35F, eyeNormalOffset, target);
    }

    private static void applyCameraPivotOffset(Camera camera, AllTerrainWheelchairEntity wc, float partialTick) {
        if (CAMERA_SET_POSITION == null) {
            return;
        }
        Vec3 seatPos = wc.getSeatPosition(partialTick);
        Vector3f norm = wc.getRenderOrientation(partialTick).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        float dist = Mth.lerp(partialTick, prevEyeNormalOffset, eyeNormalOffset);
        Vec3 eyePos = seatPos.add(norm.x * dist, norm.y * dist, norm.z * dist);
        try {
            CAMERA_SET_POSITION.invoke(camera, eyePos);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
