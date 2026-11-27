package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonEmptySwingPayload;
import org.gwfx.universaltool.phainon.PhainonEnhancedBasic;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.EFFECT_TICKS;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.POSE_TICKS;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.SLASH_TICK;

/** World-space afterimages stay at the point of the cut while the player retreats. */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonEnhancedBasicEffects {
    private static final Map<Integer, Deque<Swing>> SWINGS = new HashMap<>();
    private static ClientLevel level;

    private record Swing(long startTick, Vec3 origin, float yaw, float pitch, float reach, float travel, int side) {
        float age(long now, float partialTick) {
            return now - startTick + partialTick;
        }
    }

    private PhainonEnhancedBasicEffects() {}

    @SubscribeEvent
    public static void onEmptySwing(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (player == Minecraft.getInstance().player && PhainonEnhancedBasic.isInForm(player)
                && PhainonUltimateEffectRenderer.openingAge(player.getId(), InteractionHand.MAIN_HAND, 0) < 0) {
            PacketDistributor.sendToServer(PhainonEmptySwingPayload.INSTANCE);
        }
    }

    static void apply(PhainonSkillEffectPayload payload) {
        updateLevel();
        if (level != null && level.getEntity(payload.casterId()) instanceof Player player) {
            Vec3 origin = new Vec3(payload.originX(), payload.originY(), payload.originZ());
            var swing = new Swing(level.getGameTime(), origin,
                    payload.yawDeg(), Mth.clamp(payload.pitchDeg(), -48.0F, 48.0F),
                    payload.strikeReach(), payload.dashDistance(),
                    player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
            SWINGS.computeIfAbsent(player.getId(), id -> new ArrayDeque<>()).addLast(swing);
            if (player instanceof LocalPlayer local && player == Minecraft.getInstance().player) {
                PhainonEnhancedBasicPrediction.start(local, payload, origin);
            }
        }
    }

    public static boolean isBladeLit(Player player, ItemStack stack, float partialTick) {
        if (stack != player.getMainHandItem()) return false;
        float age = poseAge(player, partialTick);
        return age >= 1.0F && age < SLASH_TICK + 5.0F;
    }

    static float poseAge(Player player, float partialTick) {
        if (level == null || level != player.level() || !PhainonEnhancedBasic.isInForm(player)) return -1;
        var swings = SWINGS.get(player.getId());
        if (swings == null) return -1;
        float age = swings.getLast().age(level.getGameTime(), partialTick);
        return age >= 0 && age < POSE_TICKS ? age : -1;
    }

    static void clear() {
        SWINGS.clear();
        level = null;
        PhainonEnhancedBasicPrediction.clear();
    }

    private static void updateLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            SWINGS.clear();
            level = current;
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        updateLevel();
        if (level == null) return;
        long now = level.getGameTime();
        SWINGS.values().removeIf(swings -> {
            swings.removeIf(swing -> swing.age(now, 0) >= EFFECT_TICKS);
            return swings.isEmpty();
        });
    }

    static boolean hasWorldEffects() {
        return !SWINGS.isEmpty();
    }

    static void renderWorld(RenderLevelStageEvent event) {
        Minecraft game = Minecraft.getInstance();
        if (SWINGS.isEmpty()
                || level == null || level != game.level) return;

        List<PhainonBloodthornVisuals.Frame> frames = new ArrayList<>();
        long now = level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera();
        for (var entry : SWINGS.entrySet()) {
            boolean firstPerson = game.options.getCameraType().isFirstPerson()
                    && camera.getEntity().getId() == entry.getKey();
            for (Swing swing : entry.getValue()) {
                float age = swing.age(now, partial);
                if (age >= 0 && age < EFFECT_TICKS) {
                    frames.add(new PhainonBloodthornVisuals.Frame(swing.origin(), swing.yaw(), swing.pitch(),
                            swing.reach(), swing.travel(), age, swing.side(), firstPerson));
                }
            }
        }
        PhainonBloodthornVisuals.render(frames, event.getPoseStack(), camera.getPosition(), game.renderBuffers().bufferSource());
    }

    static float smooth(float progress) {
        float t = Mth.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
