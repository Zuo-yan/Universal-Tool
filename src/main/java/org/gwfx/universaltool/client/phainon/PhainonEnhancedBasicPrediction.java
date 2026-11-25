package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonEnhancedBasic;
import org.gwfx.universaltool.phainon.PhainonEnhancedBasicMotion;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.SLASH_TICK;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonEnhancedBasicPrediction {
    private record Dash(LocalPlayer player, long startTick, Vec3 origin, float yaw, float distance) {
        float age(float partialTick) {
            return player.level().getGameTime() - startTick + partialTick;
        }
    }

    private static Dash active;
    private static Vec3 lastPosition;
    private static int lastHurtTime;

    private PhainonEnhancedBasicPrediction() {}

    static void start(LocalPlayer player, PhainonSkillEffectPayload payload, Vec3 origin) {
        clear();
        if (payload.dashDistance() <= 0 || player.position().distanceToSqr(origin) > 0.64) return;
        active = new Dash(player, player.level().getGameTime(), origin, payload.yawDeg(), payload.dashDistance());
        lastPosition = player.position();
        lastHurtTime = player.hurtTime;
    }

    static void clear() {
        active = null;
        lastPosition = null;
    }

    private static boolean canContinue(Minecraft game) {
        return active != null && game.player == active.player() && game.level == active.player().level()
                && PhainonEnhancedBasic.isInForm(active.player()) && PhainonEnhancedBasicMotion.canMove(active.player())
                && active.player().hurtTime <= lastHurtTime
                && lastPosition.distanceToSqr(active.player().position()) < 0.18;
    }

    @SubscribeEvent
    public static void updateInput(MovementInputUpdateEvent event) {
        Minecraft game = Minecraft.getInstance();
        if (event.getEntity() != game.player || !canContinue(game)) return;
        var input = event.getInput();
        input.leftImpulse = input.forwardImpulse = 0.0F;
        input.up = input.down = input.left = input.right = input.jumping = false;
        Vec3 velocity = event.getEntity().getDeltaMovement();
        event.getEntity().setDeltaMovement(0, velocity.y, 0);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (active == null) return;
        if (!canContinue(Minecraft.getInstance())) {
            clear();
            return;
        }
        float age = active.age(0);
        Vec3 wanted = PhainonEnhancedBasicMotion.positionAt(active.origin(), active.yaw(), active.distance(), age);
        lastPosition = PhainonEnhancedBasicMotion.moveTo(active.player(), wanted);
        lastHurtTime = active.player().hurtTime;
        if (age >= PhainonEnhancedBasicMotion.END_TICK) clear();
    }

    @SubscribeEvent
    public static void accentFov(ViewportEvent.ComputeFov event) {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || !event.usedConfiguredFov() || event.getCamera().getEntity() != game.player) return;
        float age = PhainonEnhancedBasicEffects.poseAge(game.player, (float) event.getPartialTick());
        if (age < 0) return;
        float focus = smooth(age / 3) * (1 - smooth((age - 3.5F) / 2));
        float strike = age - SLASH_TICK;
        float punch = smooth((strike + 0.35F) / 1.6F) * (1 - smooth((strike - 1.6F) / 7));
        float distance = canContinue(game) ? active.distance() : 0;
        double scale = game.options.fovEffectScale().get();
        event.setFOV(event.getFOV() + ((3.2F + 2.9F * distance / PhainonEnhancedBasicMotion.MAX_DISTANCE) * punch
                - 2.4F * focus) * scale);
    }

    @SubscribeEvent
    public static void accentStrike(ViewportEvent.ComputeCameraAngles event) {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || event.getCamera().getEntity() != game.player) return;
        float age = PhainonEnhancedBasicEffects.poseAge(game.player, (float) event.getPartialTick());
        float strike = age - SLASH_TICK;
        if (strike < 0 || strike >= 5) return;
        float decay = 1 - smooth(strike / 5);
        float side = game.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float scale = game.options.fovEffectScale().get().floatValue();
        event.setRoll(event.getRoll() + side * scale * 0.94F * Mth.sin(strike * 1.5F) * decay);
        event.setPitch(event.getPitch() + scale * 0.42F * Mth.sin(strike * 2.9F) * decay);
    }
}
