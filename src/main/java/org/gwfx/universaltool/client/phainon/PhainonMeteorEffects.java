package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.MeteorShower;
import org.gwfx.universaltool.phainon.MeteorShowerPayload;

import java.util.HashMap;
import java.util.Map;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonMeteor.*;

/**
 * 觉醒战技「支柱·死星天裁」客户端总控调度器。
 * <p>负责同步服务端陨石雨状态、驱动电影级音效层次、施法起手姿态与手持动画、
 * 克制的 FOV / 阻尼镜头微震反馈、柔和白金瞬闪以及世界空间多 Pass 渲染调度。</p>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonMeteorEffects {
    private static final Map<Integer, MeteorShowerPayload> casts = new HashMap<>();
    private static ClientLevel level;

    private PhainonMeteorEffects() {}

    private static void syncLevel() {
        if (level != Minecraft.getInstance().level) {
            casts.clear();
            level = Minecraft.getInstance().level;
        }
    }

    public static void start(MeteorShowerPayload payload) {
        syncLevel();
        if (level == null) {
            return;
        }
        if (payload.cancelled()) {
            casts.computeIfPresent(payload.casterId(), (id, prior) -> prior.start() == payload.start() ? null : prior);
            return;
        }
        casts.put(payload.casterId(), payload);
        var caster = level.getEntity(payload.casterId());
        if (caster != null && level.getGameTime() - payload.start() < 5) {
            play(caster.position(), SoundEvents.BEACON_POWER_SELECT, 0.65F, 0.74F);
            play(caster.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.45F, 0.62F);
        }
    }

    static PhainonMeteorAtmosphere.Scene scene(Vec3 camera, float partial) {
        if (level == null || level != Minecraft.getInstance().level || casts.isEmpty()) {
            return PhainonMeteorAtmosphere.CLEAR;
        }
        float strongest = 0.0F;
        float selectedAge = 0.0F;
        for (var cast : casts.values()) {
            float age = level.getGameTime() - cast.start() + partial;
            float distance = (float) camera.distanceTo(cast.shower().center());
            float weight = smooth((MeteorShower.VISIBLE_RADIUS - distance) / 48.0F)
                    * smooth(age / 19.0F)
                    * smooth((DURATION - age) / 18.0F);
            if (weight > strongest) {
                selectedAge = age;
                strongest = weight;
            }
        }
        return PhainonMeteorAtmosphere.sample(selectedAge, strongest);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        syncLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        casts.values().removeIf(c -> now - c.start() >= DURATION);
        for (MeteorShowerPayload cast : casts.values()) {
            long age = now - cast.start();
            Vec3 center = cast.shower().center();
            if (age == RELEASE) {
                play(center.add(0, 14, 0), SoundEvents.TRIDENT_THUNDER.value(), 0.85F, 1.32F);
                play(center.add(0, 12, 0), SoundEvents.BEACON_ACTIVATE, 0.60F, 1.18F);
            }
            // 小陨石雨错落落地轰鸣（节制采样，避免音频过载）
            if (age >= RELEASE + MeteorShower.FLIGHT && age < IMPACT) {
                for (int i = 0; i < MeteorShower.COUNT; i += 3) {
                    if (age == MeteorShower.impactTick(i)) {
                        play(cast.shower().landings().get(i), SoundEvents.FIRECHARGE_USE, 0.72F, 0.76F + (i % 4) * 0.08F);
                    }
                }
            }
            if (age == GIANT) {
                play(center.add(0, 16, 0), SoundEvents.FIRECHARGE_USE, 1.45F, 0.52F);
                play(center.add(0, 10, 0), SoundEvents.WITHER_SPAWN, 0.52F, 0.58F);
            }
            if (age == IMPACT) {
                play(center, SoundEvents.GENERIC_EXPLODE.value(), 2.2F, 0.58F);
                play(center, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.35F, 0.72F);
            }
        }
    }

    private static void play(Vec3 point, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(point.x, point.y, point.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static float gesture(Player player, InteractionHand hand, float partial) {
        MeteorShowerPayload cast = casts.get(player.getId());
        if (cast == null || level != player.level() || cast.hand() != hand) {
            return 0.0F;
        }
        float age = level.getGameTime() - cast.start() + partial;
        return smooth(age / 14.0F) * (1.0F - smooth((age - RELEASE) / 8.0F));
    }

    /**
     * 第一人称施法手持动作：举剑高指蓄力后张臂召陨，严格补齐原版默认手部基座偏移。
     */
    static boolean applyHand(PoseStack pose, Player player, InteractionHand hand, HumanoidArm arm,
                             float partial, float equip) {
        float weight = gesture(player, hand, partial);
        if (weight <= 0.001F) {
            return false;
        }
        int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(sign * (0.56 + weight * 0.14), -0.52 - equip * 0.6 + 0.25 * weight, -0.72 - 0.12 * weight);
        pose.mulPose(Axis.ZP.rotationDegrees(sign * -34.0F * weight));
        pose.mulPose(Axis.XP.rotationDegrees(36.0F * weight));
        return true;
    }

    /**
     * 第三人称施法姿态：还原关键帧 01-02 从右手指天到双臂舒展俯瞰战场的神明裁决姿态。
     */
    public static void applyPose(PlayerModel<?> model, Player player) {
        MeteorShowerPayload cast = casts.get(player.getId());
        if (cast == null || level != player.level()) {
            return;
        }
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float age = level.getGameTime() - cast.start() + partial;
        float spread = smooth((age - 8.0F) / 16.0F) * (1.0F - smooth((age - RELEASE) / 8.0F));
        float initialPoint = smooth(age / 10.0F) * (1.0F - spread * 0.65F) * (1.0F - smooth((age - RELEASE) / 8.0F));

        // 阶段 01 右臂高举指天 + 阶段 02 双臂宽广张开
        model.rightArm.xRot -= 1.25F * initialPoint + 0.56F * spread;
        model.rightArm.zRot += 0.22F * initialPoint + 1.15F * spread;
        model.leftArm.xRot -= 0.52F * spread;
        model.leftArm.zRot -= 1.15F * spread;
    }

    /**
     * 计算本地观察者距离最强陨石撞击点的权重与当前 age（用于克制镜头反馈与柔和闪屏）。
     */
    private static ImpactFeedback sampleImpactFeedback(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level != mc.level || casts.isEmpty()) {
            return ImpactFeedback.NONE;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        float bestWeight = 0.0F;
        float bestAge = 0.0F;
        boolean isCaster = false;
        for (var cast : casts.values()) {
            float age = level.getGameTime() - cast.start() + partial;
            float dist = (float) camPos.distanceTo(cast.shower().center());
            float prox = smooth((MeteorShower.GIANT_RADIUS * 1.85F - dist) / (MeteorShower.GIANT_RADIUS * 0.95F));
            boolean self = cast.casterId() == mc.player.getId();
            float w = self ? Math.max(prox, 0.72F) : prox;
            if (w > bestWeight) {
                bestWeight = w;
                bestAge = age;
                isCaster = self;
            }
        }
        return bestWeight > 0.01F ? new ImpactFeedback(bestAge, bestWeight, isCaster) : ImpactFeedback.NONE;
    }

    private record ImpactFeedback(float age, float weight, boolean isCaster) {
        static final ImpactFeedback NONE = new ImpactFeedback(0.0F, 0.0F, false);
    }

    /**
     * 克制的 FOV 镜头反馈：巨型死星压境时微微收紧蓄势（-3.5°），命中瞬间爆发拉宽（+7.8°）并三次缓出回正。
     */
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        var fb = sampleImpactFeedback((float) event.getPartialTick());
        if (fb.weight <= 0.01F) {
            return;
        }
        double fovScale = mc.options.fovEffectScale().get();
        if (fovScale <= 0.001) {
            return;
        }
        float delta = 0.0F;
        if (fb.age >= GIANT && fb.age < IMPACT) {
            float pull = smooth((fb.age - GIANT) / (IMPACT - GIANT));
            delta -= 3.5F * pull;
        } else if (fb.age >= IMPACT && fb.age < IMPACT + 13.0F) {
            float decay = 1.0F - (fb.age - IMPACT) / 13.0F;
            delta += 7.8F * decay * decay * decay;
        }
        if (Math.abs(delta) > 0.01F) {
            event.setFOV(event.getFOV() + delta * fb.weight * fovScale);
        }
    }

    /**
     * 命中瞬间阻尼镜头微震（严格控制 <= 7 ticks、指数衰减，既有地震级冲击力又不眩晕）。
     */
    @SubscribeEvent
    public static void cameraShake(ViewportEvent.ComputeCameraAngles event) {
        var fb = sampleImpactFeedback((float) event.getPartialTick());
        if (fb.weight <= 0.01F || fb.age < IMPACT || fb.age >= IMPACT + 7.0F) {
            return;
        }
        float dt = fb.age - IMPACT;
        float env = (1.0F - dt / 7.0F);
        float amp = env * env * fb.weight * 2.15F;
        event.setYaw(event.getYaw() + Mth.sin(dt * 11.4F) * amp);
        event.setPitch(event.getPitch() + Mth.cos(dt * 14.2F) * amp * 0.82F);
        event.setRoll(event.getRoll() + Mth.sin(dt * 8.6F + 1.1F) * amp * 0.90F);
    }

    /**
     * 命中瞬间的柔和白金全屏瞬闪（严格遵守 <= 6 ticks、最大 Alpha <= 82/255 铁律，不刺眼不糊脸）。
     */
    @SubscribeEvent
    public static void impactFlash(RenderGuiEvent.Post event) {
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var fb = sampleImpactFeedback(partial);
        if (fb.weight <= 0.01F || fb.age < IMPACT - 0.5F || fb.age >= IMPACT + 6.0F) {
            return;
        }
        float dt = Math.max(0.0F, fb.age - IMPACT);
        float fade = 1.0F - Mth.clamp(dt / 6.0F, 0.0F, 1.0F);
        int alpha = (int) Mth.clamp(fade * fade * fb.weight * 82.0F, 0.0F, 82.0F);
        if (alpha <= 1) {
            return;
        }
        int color = (alpha << 24) | 0xFFF3D4;
        var gui = event.getGuiGraphics();
        gui.fill(0, 0, gui.guiWidth(), gui.guiHeight(), color);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level != mc.level || casts.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        var buffers = mc.renderBuffers().bufferSource();

        for (var entry : casts.entrySet()) {
            MeteorShowerPayload cast = entry.getValue();
            float age = level.getGameTime() - cast.start() + partial;
            var caster = level.getEntity(entry.getKey());
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            if (caster != null && age < RELEASE + 10.0F) {
                Vec3 head = caster.getPosition(partial).add(0.0, caster.getEyeHeight() + 0.48, 0.0);
                boolean firstPerson = caster == camera.getEntity() && mc.options.getCameraType().isFirstPerson();
                PhainonMeteorVisuals.corona(pose, buffers, head, caster.getYRot(), age, firstPerson);
            }

            Vec3 center = cast.shower().center();
            pose.translate(center.x, center.y, center.z);
            PhainonMeteorVisuals.draw(pose, buffers, cast.shower(), age);
            pose.popPose();
        }
    }
}
