package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public class PhoenixCinematicController {

    private static boolean active = false;
    private static int currentTick = 0;
    private static int totalDuration = 240; // 翻倍延长为 12 秒 (240 ticks) 电影级长运镜

    private static BlockPos targetPodPos = BlockPos.ZERO;
    private static Direction targetFacing = Direction.NORTH;
    private static String targetDim = "";
    private static Vec3 deathOrigin = Vec3.ZERO;

    public static void startCinematic(BlockPos podPos, Direction facing, String dimId, int duration) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        active = true;
        currentTick = 0;
        totalDuration = duration > 0 ? duration : 240;
        targetPodPos = podPos;
        targetFacing = facing;
        targetDim = dimId;
        deathOrigin = player.position();

        // 阶段 1：起搏器高频电声与量子解离升调
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.0F));
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.6F));
    }

    public static boolean isActive() {
        return active;
    }

    public static float getProgressRatio(float partialTick) {
        if (!active) return 0.0F;
        return Mth.clamp((currentTick + partialTick) / (float) totalDuration, 0.0F, 1.0F);
    }

    public static int getCurrentTick() {
        return currentTick;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            active = false;
            return;
        }

        currentTick++;

        // 翻倍时间轴 (240 ticks) 电影音效调度
        if (currentTick == 60) {
            // 阶段 2：量子超空间跃迁星云风暴
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PORTAL_TRIGGER, 1.0F, 1.8F));
        } else if (currentTick == 110) {
            // 阶段 3：高空垂直俯冲风切破音障
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.5F, 0.9F));
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ELYTRA_FLYING, 1.5F, 1.0F));
        } else if (currentTick == 160) {
            // 阶段 4：扎入生化液体水花与消音回响
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.2F, 1.0F));
        } else if (currentTick == 166 || currentTick == 174 || currentTick == 182) {
            // 阶段 4：连续震撼心跳起搏音 (咚！咚！咚！)
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.3F));
        } else if (currentTick == 190) {
            // 阶段 5：高压排气泄压白烟与液压大门升起
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.FIRE_EXTINGUISH, 1.6F, 0.7F));
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.IRON_DOOR_OPEN, 1.0F, 0.7F));
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PISTON_EXTEND, 1.0F, 1.1F));
        }

        if (currentTick >= totalDuration) {
            active = false;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;

        float p = (float) currentTick / totalDuration;

        if (p < 0.25F) {
            // 0 ~ 60 ticks: 灵魂出窍回眸俯瞰地面，缓慢螺旋升空
            float sub = p / 0.25F;
            float spiralYaw = sub * 120.0F;
            float pitch = Mth.lerp(sub, 25.0F, -85.0F);
            event.setPitch(pitch);
            event.setYaw(event.getYaw() + spiralYaw * 0.25F);
        } else if (p < 0.46F) {
            // 60 ~ 110 ticks: 量子超空间跳跃，剧烈镜头抖动
            float shake = (float) Math.sin(currentTick * 2.0F) * 3.5F;
            event.setPitch(-85.0F + shake);
            event.setRoll(shake * 1.8F);
        } else if (p < 0.67F) {
            // 110 ~ 160 ticks: 垂直向下极速俯冲入仓 (+85度极速俯视扎入)
            float sub = (p - 0.46F) / 0.21F;
            float pitch = Mth.lerp(sub, -85.0F, 85.0F);
            event.setPitch(pitch);
        } else if (p < 0.79F) {
            // 160 ~ 190 ticks: 仓内苏醒，回正视角面对前门
            float sub = (p - 0.67F) / 0.12F;
            float facingYaw = targetFacing.toYRot();
            event.setPitch(Mth.lerp(sub, 85.0F, 0.0F));
            event.setYaw(facingYaw);
        } else {
            // 190 ~ 240 ticks: 泄压开门、迈步走出舱外
            float facingYaw = targetFacing.toYRot();
            float stepBob = (float) Math.sin((p - 0.79F) * 8.0F) * 1.5F;
            event.setPitch(stepBob);
            event.setYaw(facingYaw);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!active) return;

        float p = (float) currentTick / totalDuration;
        if (p >= 0.25F && p < 0.67F) {
            float fovBoost = 28.0F * (float) Math.sin((p - 0.25F) / 0.42F * Math.PI);
            event.setFOV(event.getFOV() + fovBoost);
        }
    }

    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        if (active) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }
}
