package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.CounterGuardEffectPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the three Counter Guard ward shields around the protected player.
 *
 * <p>Gameplay remains server-authoritative. This renderer receives lifecycle packets and only
 * animates formation expansion, the hit trigger, and cleanup.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class CounterGuardEffectRenderer {
    private static final int SHIELD_COUNT = 3;
    private static final int EXPAND_TICKS = 14;
    private static final int TRIGGER_TICKS = 20;
    private static final Map<Integer, GuardVisual> ACTIVE = new HashMap<>();
    private static final ResourceLocation SHIELD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                    "textures/item/counter_guard_shield.png");
    private static final RenderType SHIELD_LAYER =
            RenderType.entityTranslucent(SHIELD_TEXTURE);
    private static long nextSequence;

    public static void apply(CounterGuardEffectPayload payload) {
        if (payload.sequence() < nextSequence) return;
        nextSequence = payload.sequence();
        if (payload.action() == CounterGuardEffectPayload.Action.CLEAR) {
            ACTIVE.remove(payload.playerId());
        } else {
            ACTIVE.put(payload.playerId(), new GuardVisual(
                    payload.action(), Minecraft.getInstance().level == null
                    ? 0L : Minecraft.getInstance().level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            nextSequence = 0L;
            return;
        }
        long now = minecraft.level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            GuardVisual visual = entry.getValue();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            return entity == null || !entity.isAlive()
                    || visual.action() == CounterGuardEffectPayload.Action.TRIGGER
                    && now - visual.startedAt() > TRIGGER_TICKS;
        });
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || ACTIVE.isEmpty()
                || Minecraft.getInstance().level == null
                || Minecraft.getInstance().player == null
                || Minecraft.getInstance().options.hideGui) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = Minecraft.getInstance().level.getGameTime();
        double cameraX = event.getCamera().getPosition().x;
        double cameraY = event.getCamera().getPosition().y;
        double cameraZ = event.getCamera().getPosition().z;
        var vertices = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(SHIELD_LAYER);

        pose.pushPose();
        for (Map.Entry<Integer, GuardVisual> entry : ACTIVE.entrySet()) {
            Entity entity = Minecraft.getInstance().level.getEntity(entry.getKey());
            if (!(entity instanceof Player owner) || !owner.isAlive()) continue;

            GuardVisual visual = entry.getValue();
            long age = gameTime - visual.startedAt();
            float triggerAge = visual.action() == CounterGuardEffectPayload.Action.TRIGGER
                    ? Math.max(0.0F, age + partialTick) : -1.0F;
            float expand = visual.action() == CounterGuardEffectPayload.Action.EXPAND
                    ? Math.min(1.0F, age / (float) EXPAND_TICKS) : 1.0F;
            float open = easeOutBack(expand);
            float trigger = triggerAge < 0.0F
                    ? 0.0F : Math.min(1.0F, triggerAge / TRIGGER_TICKS);
            float contract = trigger < 0.28F
                    ? 1.0F - 0.45F * easeOutCubic(trigger / 0.28F)
                    : 0.55F + 0.95F * easeOutCubic((trigger - 0.28F) / 0.72F);
            float fade = visual.action() == CounterGuardEffectPayload.Action.TRIGGER
                    ? Math.max(0.15F, 1.0F - trigger * 0.65F) : 1.0F;

            for (int index = 0; index < SHIELD_COUNT; index++) {
                float phase = owner.getYRot() * Mth.DEG_TO_RAD
                        + index * Mth.TWO_PI / SHIELD_COUNT
                        + (gameTime + partialTick) * 0.028F;
                float orbit = (1.30F + 0.06F * index) * open * contract;
                float shieldScale = 0.68F * (0.94F + 0.06F
                        * Mth.sin((gameTime + partialTick) * 0.14F + index));

                pose.pushPose();
                pose.translate(
                        owner.getX() - cameraX + Mth.cos(phase) * orbit,
                        owner.getY() + 0.95 - cameraY
                                + Mth.sin((gameTime + partialTick) * 0.045F
                                + index * 2.1F) * 0.12F
                                + (index - 1) * 0.22F,
                        owner.getZ() - cameraZ + Mth.sin(phase) * orbit);
                pose.mulPose(Axis.YP.rotationDegrees(
                        -phase * Mth.RAD_TO_DEG + Mth.cos(phase) * 13.0F));
                pose.scale(shieldScale, shieldScale, shieldScale);
                renderShieldQuad(pose.last().pose(), vertices, fade);
                pose.popPose();
            }
        }
        pose.popPose();
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(SHIELD_LAYER);
    }

    private static void renderShieldQuad(org.joml.Matrix4f matrix,
                                         VertexConsumer buffer, float fade) {
        int color = ((int) (fade * 255.0F) << 24) | 0x00FFFFFF;
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        buffer.addVertex(matrix, -0.78F, -0.78F, 0.0F)
                .setColor(color).setUv(0.0F, 1.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(matrix, 0.78F, -0.78F, 0.0F)
                .setColor(color).setUv(1.0F, 1.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(matrix, 0.78F, 0.78F, 0.0F)
                .setColor(color).setUv(1.0F, 0.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(matrix, -0.78F, 0.78F, 0.0F)
                .setColor(color).setUv(0.0F, 0.0F).setOverlay(overlay)
                .setLight(light).setNormal(0.0F, 0.0F, 1.0F);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeOutBack(float value) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float shifted = value - 1.0F;
        return 1.0F + c3 * shifted * shifted * shifted + c1 * shifted * shifted;
    }

    private record GuardVisual(CounterGuardEffectPayload.Action action, long startedAt) {
    }

    private CounterGuardEffectRenderer() {
    }
}
