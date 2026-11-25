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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.MeteorShowerPayload;
import org.gwfx.universaltool.phainon.MeteorShower;

import java.util.HashMap;
import java.util.Map;

import static org.gwfx.universaltool.phainon.PhainonMeteor.*;

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
        if (level == null) return;
        if (payload.cancelled()) {
            casts.computeIfPresent(payload.casterId(), (id, prior) -> prior.start() == payload.start() ? null : prior);
            return;
        }
        casts.put(payload.casterId(), payload);
        var caster = level.getEntity(payload.casterId());
        if (caster != null && level.getGameTime() - payload.start() < 5) {
            play(caster.position(), SoundEvents.BEACON_POWER_SELECT, .55F, .72F);
        }
    }

    static PhainonMeteorAtmosphere.Scene scene(Vec3 camera, float partial) {
        if (level == null || level != Minecraft.getInstance().level || casts.isEmpty()) return PhainonMeteorAtmosphere.CLEAR;
        float strongest = 0;
        float selectedAge = 0;
        for (var cast : casts.values()) {
            float age = level.getGameTime() - cast.start() + partial;
            float distance = (float) camera.distanceTo(cast.shower().center());
            float weight = PhainonEnhancedBasicEffects.smooth((MeteorShower.VISIBLE_RADIUS - distance) / 48)
                    * PhainonEnhancedBasicEffects.smooth(age / 19)
                    * PhainonEnhancedBasicEffects.smooth((DURATION - age) / 18);
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
        if (level == null) return;
        long now = level.getGameTime();
        casts.values().removeIf(c -> now - c.start() >= DURATION);
        for (MeteorShowerPayload cast : casts.values()) {
            long age = now - cast.start();
            if (age == RELEASE) play(cast.shower().center().add(0, 9, 0), SoundEvents.TRIDENT_THUNDER.value(), .6F, 1.45F);
            if (age == GIANT) play(cast.shower().center().add(0, 6, 0), SoundEvents.FIRECHARGE_USE, 1.1F, .58F);
            if (age == IMPACT) play(cast.shower().center(), SoundEvents.GENERIC_EXPLODE.value(), 1.8F, .66F);
        }
    }

    private static void play(Vec3 point, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(point.x, point.y, point.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static float gesture(Player player, InteractionHand hand, float partial) {
        MeteorShowerPayload cast = casts.get(player.getId());
        if (cast == null || level != player.level() || cast.hand() != hand) return 0;
        float age = level.getGameTime() - cast.start() + partial;
        return PhainonEnhancedBasicEffects.smooth(age / 15) *
                (1 - PhainonEnhancedBasicEffects.smooth((age - RELEASE) / 7));
    }

    static boolean applyHand(PoseStack pose, Player player, InteractionHand hand, HumanoidArm arm,
                             float partial, float equip) {
        float weight = gesture(player, hand, partial);
        if (weight <= 0) return false;
        int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(sign * (.56 + weight * .12), -.52 - equip * .6 + .23 * weight, -.72 - .11 * weight);
        pose.mulPose(Axis.ZP.rotationDegrees(sign * -32 * weight));
        pose.mulPose(Axis.XP.rotationDegrees(34 * weight));
        return true;
    }

    public static void applyPose(PlayerModel<?> model, Player player) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float spread = Math.max(gesture(player, InteractionHand.MAIN_HAND, partial),
                gesture(player, InteractionHand.OFF_HAND, partial));
        model.rightArm.zRot += 1.08F * spread;
        model.leftArm.zRot -= 1.08F * spread;
        model.rightArm.xRot -= .52F * spread;
        model.leftArm.xRot -= .52F * spread;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level != mc.level || casts.isEmpty()) return;
        MeteorShowerPayload cast = casts.get(mc.player.getId());
        if (cast == null) return;
        float t = (float)(level.getGameTime() - cast.start() + event.getPartialTick());
        float surge = t >= IMPACT ? Math.max(0, 1 - (t - IMPACT) / 9) : 0;
        event.setFOV(event.getFOV() + 3.8 * surge * mc.options.fovEffectScale().get());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level != mc.level || casts.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera();
        for (var entry : casts.entrySet()) {
            MeteorShowerPayload cast = entry.getValue();
            float age = level.getGameTime() - cast.start() + partial;
            var caster = level.getEntity(entry.getKey());
            var pose = event.getPoseStack();
            pose.pushPose();
            Vec3 cameraPos = camera.getPosition();
            pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            var buffers = mc.renderBuffers().bufferSource();
            if (caster != null && age < RELEASE + 8) {
                Vec3 head = caster.getPosition(partial).add(0, caster.getEyeHeight() + .5, 0);
                PhainonMeteorVisuals.corona(pose, buffers, head, caster.getYRot(), age,
                        caster == camera.getEntity() && mc.options.getCameraType().isFirstPerson());
            }
            pose.translate(cast.shower().center().x, cast.shower().center().y, cast.shower().center().z);
            PhainonMeteorVisuals.draw(pose, buffers, cast.shower(), age);
            pose.popPose();
        }
    }
}
