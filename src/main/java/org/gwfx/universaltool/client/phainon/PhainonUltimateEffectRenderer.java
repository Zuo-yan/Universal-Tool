package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;
import org.gwfx.universaltool.phainon.PhainonUltimateMotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 白厄终结技「永劫燔世，其将背负」三幕电影级 3D 视觉渲染主控：
 * <ol>
 *   <li>第一幕（0–38 ticks，帧 02）：白蓝圣洁天翼展开 + 头顶黄金日轮 + 环绕金光弧 + 脚下圣光爆裂；</li>
 *   <li>第二幕（38–82 ticks，帧 05）：26 格暗沉苍穹穹顶 + 双层旋转金色焚世茧球 + 超长双彗尾环 + 32 块黑褐碎石风暴；</li>
 *   <li>第三幕（82–112 ticks 高潮 + 觉醒 20 秒持续，帧 08）：破茧爆发瞬闪 + 左金右黑金不对称巨翼（实体黑曜石+Additive金焰双层混绘）+ 头后烈日圣环 + 12 枚黄金泰坦神徽悬浮环绕。</li>
 * </ol>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonUltimateEffectRenderer {

    private static final class Visual {
        private final boolean mainHand;
        private final long start;
        private final int duration;
        private final Vec3 origin;
        private final float maxLift;

        private Visual(PhainonSkillEffectPayload payload, long start) {
            this.mainHand = payload.mainHand();
            this.start = start;
            this.duration = payload.totalTicks();
            this.origin = new Vec3(payload.originX(), payload.originY(), payload.originZ());
            this.maxLift = payload.hoverHeight();
        }
    }

    private record Actor(Player player, float age, int duration, float hover, boolean firstPerson,
                         Vec3 position, Vec3 burstOrigin) {
    }

    private static final Map<Integer, Visual> ACTIVE = new HashMap<>();
    private static ClientLevel level;

    private PhainonUltimateEffectRenderer() {
    }

    static void apply(PhainonSkillEffectPayload payload) {
        updateLevel();
        if (level == null) {
            return;
        }
        if (payload.action() == PhainonSkillEffectPayload.Action.ULTIMATE_CANCEL) {
            Visual previous = ACTIVE.remove(payload.casterId());
            if (isLocalCaster(payload.casterId())) {
                var player = Minecraft.getInstance().player;
                if (previous != null && openingAge(previous, 0) >= 0 && player != null
                        && player.isAlive() && !player.isPassenger()) {
                    player.setPos(payload.originX(), payload.originY(), payload.originZ());
                }
                PhainonUltimateCamera.finish();
            }
        } else {
            ACTIVE.put(payload.casterId(), new Visual(payload, level.getGameTime()));
            if (isLocalCaster(payload.casterId())) {
                PhainonUltimateCamera.begin();
            }
        }
    }

    private static boolean isLocalCaster(int casterId) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getId() == casterId;
    }

    public static void clear() {
        PhainonUltimateCamera.finish();
        ACTIVE.clear();
        level = null;
    }

    static float openingAge(int entityId, InteractionHand hand, float partialTick) {
        if (level == null || level != Minecraft.getInstance().level) {
            return -1.0F;
        }
        Visual visual = ACTIVE.get(entityId);
        if (visual == null || visual.mainHand != (hand == InteractionHand.MAIN_HAND)) {
            return -1.0F;
        }
        return openingAge(visual, partialTick);
    }

    private static float openingAge(Visual visual, float partialTick) {
        float age = level.getGameTime() - visual.start + partialTick;
        return age >= 0.0F && age < PhainonUltimateHandler.OPENING_TICKS ? age : -1.0F;
    }

    static float localOpeningAge(float partialTick) {
        Minecraft game = Minecraft.getInstance();
        if (level == null || level != game.level || game.player == null || game.getCameraEntity() != game.player) {
            return -1.0F;
        }
        Visual visual = ACTIVE.get(game.player.getId());
        return visual == null ? -1.0F : openingAge(visual, partialTick);
    }

    private static Visual localSuspension(Player player) {
        if (player == null || level == null || level != player.level() || player.isPassenger()
                || !player.isAlive() || player.isFallFlying() || player.isSpectator()) {
            return null;
        }
        Visual visual = ACTIVE.get(player.getId());
        if (visual == null || openingAge(visual, 0) < 0
                || player.position().distanceToSqr(visual.origin) > 6.25) {
            return null;
        }
        return visual;
    }

    @SubscribeEvent
    public static void lockMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() != Minecraft.getInstance().player || localSuspension(event.getEntity()) == null) {
            return;
        }
        var input = event.getInput();
        input.forwardImpulse = input.leftImpulse = 0.0F;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
        event.getEntity().setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void frameHud(RenderGuiEvent.Pre event) {
        float age = localOpeningAge(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (age < 0.0F) {
            return;
        }
        float close = ease(age / 14.0F) * (1.0F - ease((age - PhainonUltimateHandler.AWAKEN_TICK - 14.0F)
                / (PhainonUltimateHandler.OPENING_TICKS - PhainonUltimateHandler.AWAKEN_TICK - 14.0F)));
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        int band = Math.round(height * 0.13F * close);
        if (band > 0) {
            int shade = ((int) (close * 175) << 24) | 0x090612;
            gui.fillGradient(0, 0, width, band, shade, 0x00090612);
            gui.fillGradient(0, height - band, width, height, 0x00090612, shade);
        }

        // 破茧爆发瞬间（82 ~ 89 ticks）叠加短暂柔和白金圣光瞬闪
        float burst = age - PhainonUltimateHandler.AWAKEN_TICK;
        if (burst >= 0.0F && burst <= 7.0F) {
            float flash = (1.0F - burst / 7.0F);
            int alpha = (int) (flash * flash * 96.0F);
            if (alpha > 1) {
                gui.fill(0, 0, width, height, (alpha << 24) | 0x00FFF0C8);
            }
        }
    }

    @SubscribeEvent
    public static void accentFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        float age = localOpeningAge((float) event.getPartialTick());
        if (age < 0.0F) {
            return;
        }
        float release = age - PhainonUltimateHandler.AWAKEN_TICK;
        float focus = ease((age - 30.0F) / 38.0F) * (1.0F - ease((release + 1.0F) / 4.0F));
        float recoil = ease(release / 3.5F) * (1.0F - ease((release - 4.0F) / 16.0F));
        double scale = Minecraft.getInstance().options.fovEffectScale().get();
        event.setFOV(event.getFOV() + (11.0F * recoil - 6.0F * focus) * scale);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        updateLevel();
        if (level != null) {
            long now = level.getGameTime();
            ACTIVE.entrySet().removeIf(entry -> {
                Visual visual = entry.getValue();
                long age = now - visual.start;
                return age >= visual.duration
                        || !(level.getEntity(entry.getKey()) instanceof Player owner)
                        || !owner.isAlive();
            });
            var player = Minecraft.getInstance().player;
            Visual suspension = localSuspension(player);
            if (suspension != null) {
                float hover = PhainonUltimateMotion.liftAt(now - suspension.start, suspension.maxLift);
                player.setPos(suspension.origin.x, suspension.origin.y + hover, suspension.origin.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            }
        }
        PhainonUltimateCamera.tick();
    }

    private static void updateLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            clear();
            level = current;
        }
    }

    static boolean hasWorldEffects() {
        return !ACTIVE.isEmpty();
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (level != game.level) {
            return;
        }
        PhainonUltimateMeshes ultMeshes = PhainonUltimateMeshes.current();
        PhainonVfxModelData vfxModels = PhainonVfxModelData.getOrLoad();
        if (ultMeshes == null || vfxModels == null) {
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<Actor> actors = collectActors(event, game, partial);
        if (actors.isEmpty()) {
            return;
        }

        var pose = event.getPoseStack();
        var buffers = game.renderBuffers().bufferSource();
        var camera = event.getCamera();

        // Pass 0：世界空间暗化苍穹穹顶（压暗四周环境，令中心焚世金光与双翼极为震撼）
        RenderType skyLayer = PhainonVfxRenderTypes.translucentTriangles(PhainonUltimateMeshes.SKY_VEIL_TEX);
        VertexConsumer skyBuf = buffers.getBuffer(skyLayer);
        for (Actor a : actors) {
            PhainonUltimateAtmosphere.renderSkyVeilDome(pose, skyBuf, ultMeshes, a.burstOrigin(), a.age());
        }
        buffers.endBatch(skyLayer);

        // Pass 1：32 块不透明受光黑褐碎石风暴
        RenderType rockLayer = PhainonVfxRenderTypes.solidTriangles(PhainonUltimateAtmosphere.ROCK);
        VertexConsumer rockBuf = buffers.getBuffer(rockLayer);
        for (Actor a : actors) {
            PhainonUltimateAtmosphere.renderDebrisStorm(pose, rockBuf, vfxModels.debrisShard(),
                    a.burstOrigin(), a.age(), a.firstPerson());
        }
        buffers.endBatch(rockLayer);

        // Pass 2：右翼「黑曜石暗紫魔翼」实体剪影（translucentTriangles 压暗背景）
        RenderType obsidianBodyLayer = PhainonVfxRenderTypes.translucentTriangles(PhainonUltimateMeshes.WING_OBSIDIAN_BODY_TEX);
        VertexConsumer obsBodyBuf = buffers.getBuffer(obsidianBodyLayer);
        for (Actor a : actors) {
            float burst = a.age() - PhainonUltimateHandler.AWAKEN_TICK;
            if (a.firstPerson() || burst <= 0.0F) {
                continue;
            }
            pushBodyAlignedPose(pose, a, partial);
            PhainonAwakenedMantle.drawObsidianWingBody(pose, obsBodyBuf, ultMeshes, burst, fadeTail(a));
            pose.popPose();
        }
        buffers.endBatch(obsidianBodyLayer);

        // Pass 3A：第一幕白蓝圣洁天翼（Additive）
        RenderType seraphLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.SERAPH_WING_TEX);
        VertexConsumer seraphBuf = buffers.getBuffer(seraphLayer);
        for (Actor a : actors) {
            if (a.firstPerson() || a.age() > 44.0F) {
                continue;
            }
            pushBodyAlignedPose(pose, a, partial);
            PhainonAwakenedMantle.drawSeraphWings(pose, seraphBuf, ultMeshes, a.age(), 1.0F);
            pose.popPose();
        }
        buffers.endBatch(seraphLayer);

        // Pass 3B：第二幕双层旋转金色焚世茧球 + 超长双彗尾环 + 第一幕金色星轨（Additive SOLAR_COCOON_TEX）
        RenderType cocoonLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.SOLAR_COCOON_TEX);
        VertexConsumer cocoonBuf = buffers.getBuffer(cocoonLayer);
        for (Actor a : actors) {
            renderCocoonAndCometRings(pose, cocoonBuf, ultMeshes, a);
        }
        buffers.endBatch(cocoonLayer);

        // Pass 3C：第三幕左翼「炽烈黄金神翼」（Additive WING_GOLD_TEX）
        RenderType goldWingLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.WING_GOLD_TEX);
        VertexConsumer goldWingBuf = buffers.getBuffer(goldWingLayer);
        for (Actor a : actors) {
            float burst = a.age() - PhainonUltimateHandler.AWAKEN_TICK;
            if (a.firstPerson() || burst <= 0.0F) {
                continue;
            }
            pushBodyAlignedPose(pose, a, partial);
            PhainonAwakenedMantle.drawGoldWingGlow(pose, goldWingBuf, ultMeshes, burst, fadeTail(a));
            pose.popPose();
        }
        buffers.endBatch(goldWingLayer);

        // Pass 3D：第三幕右翼「炽白金焰羽缘」叠亮（Additive WING_OBSIDIAN_GLOW_TEX）
        RenderType obsGlowLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.WING_OBSIDIAN_GLOW_TEX);
        VertexConsumer obsGlowBuf = buffers.getBuffer(obsGlowLayer);
        for (Actor a : actors) {
            float burst = a.age() - PhainonUltimateHandler.AWAKEN_TICK;
            if (a.firstPerson() || burst <= 0.0F) {
                continue;
            }
            pushBodyAlignedPose(pose, a, partial);
            PhainonAwakenedMantle.drawObsidianWingFlameGlow(pose, obsGlowBuf, ultMeshes, burst, fadeTail(a));
            pose.popPose();
        }
        buffers.endBatch(obsGlowLayer);

        // Pass 3E：第一幕头顶日轮 & 脚下圣光柱 + 第三幕头后巨型烈日圣环 & 金红背光云海（Additive SUNBURST_TEX）
        RenderType sunburstLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.SUNBURST_TEX);
        VertexConsumer sunburstBuf = buffers.getBuffer(sunburstLayer);
        for (Actor a : actors) {
            renderAct1SunAndPillar(pose, sunburstBuf, vfxModels, a, partial);
            PhainonUltimateAtmosphere.renderBacklightFlare(pose, sunburstBuf, camera, a.position(), a.age(), a.firstPerson());
            float burst = a.age() - PhainonUltimateHandler.AWAKEN_TICK;
            if (!a.firstPerson() && burst > 0.0F) {
                pushBodyAlignedPose(pose, a, partial);
                PhainonAwakenedMantle.drawAwakenedHeadHalo(pose, sunburstBuf, vfxModels, burst, fadeTail(a));
                pose.popPose();
            }
        }
        buffers.endBatch(sunburstLayer);

        // Pass 3F：破茧瞬间巨型蓝金/烈金水平冲击波环（Additive SLASH_ARC_TEX）
        RenderType shockwaveLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.SLASH_ARC_TEX);
        VertexConsumer shockBuf = buffers.getBuffer(shockwaveLayer);
        for (Actor a : actors) {
            renderAwakenShockwave(pose, shockBuf, vfxModels, a);
        }
        buffers.endBatch(shockwaveLayer);

        // Pass 3G：第三幕环绕全身的 12 枚黄金泰坦神徽纹章（Additive TITAN_RUNES_TEX）
        RenderType runesLayer = PhainonVfxRenderTypes.additiveGlow(PhainonUltimateMeshes.TITAN_RUNES_TEX);
        VertexConsumer runesBuf = buffers.getBuffer(runesLayer);
        for (Actor a : actors) {
            float burst = a.age() - PhainonUltimateHandler.AWAKEN_TICK;
            if (burst >= 0.0F && burst <= 62.0F) {
                PhainonUltimateAtmosphere.renderTitanRunes(pose, runesBuf, camera, a.position(), burst, a.firstPerson());
            }
        }
        buffers.endBatch(runesLayer);
    }

    private static void renderAct1SunAndPillar(PoseStack pose, VertexConsumer buffer,
                                               PhainonVfxModelData vfxModels, Actor a, float partial) {
        if (a.age() > 42.0F) {
            return;
        }
        float fade = ease(a.age() / 8.0F) * (1.0F - ease((a.age() - 30.0F) / 12.0F));
        if (fade <= 0.01F) {
            return;
        }
        float viewScale = a.firstPerson() ? 0.35F : 1.0F;

        // 脚下向上冲起的圣光柱
        pose.pushPose();
        pose.translate(a.burstOrigin().x, a.burstOrigin().y + 0.06D, a.burstOrigin().z);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(a.age() * 9.0F));
        pose.scale(1.85F, 1.85F, 4.6F * fade);
        vfxModels.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.94F, 0.72F, 0.58F * fade * viewScale, -a.age() * 0.05F, 0.0F);
        pose.popPose();

        // 头顶黄金锯齿圣日轮（还原帧 02 头顶金环）
        if (!a.firstPerson()) {
            pushBodyAlignedPose(pose, a, partial);
            pose.translate(0.0F, 2.35F, -0.18F);
            pose.mulPose(Axis.ZP.rotationDegrees(a.age() * 3.5F));
            float r = 1.45F * ease(a.age() / 12.0F);
            pose.scale(r, r, r * 0.55F);
            vfxModels.sunburstWheel().renderAdditive(pose, buffer, 1.0F, 0.88F, 0.42F, 0.90F * fade, 0.0F, 0.0F);
            pose.popPose();
        }
    }

    private static void renderCocoonAndCometRings(PoseStack pose, VertexConsumer buffer,
                                                  PhainonUltimateMeshes meshes, Actor a) {
        float age = a.age();
        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float viewAlpha = a.firstPerson() ? 0.38F : 1.0F;

        // 第一幕周身旋转金色星轨光弧（0 ~ 42 ticks）
        if (age < 42.0F) {
            float fade = ease(age / 9.0F) * (1.0F - ease((age - 30.0F) / 12.0F));
            if (fade > 0.01F) {
                pose.pushPose();
                pose.translate(a.position().x, a.position().y + 1.15D, a.position().z);
                pose.mulPose(Axis.YP.rotationDegrees(age * 7.5F));
                pose.scale(0.82F, 0.75F, 0.82F);
                meshes.cocoonRings().renderAdditive(pose, buffer, 1.0F, 0.88F, 0.42F,
                        0.72F * fade * viewAlpha, -age * 0.04F, 0.0F);
                pose.popPose();
            }
        }

        // 第二幕核心名场面：巨型旋转金色焚世茧球 + 超长双彗尾环（34 ~ 87 ticks，还原帧 05）
        if (age >= 34.0F && age <= awaken + 6.0F) {
            float form = ease((age - 34.0F) / 12.0F);
            float crush = ease((age - (awaken - 8.0F)) / 8.0F);
            float explodeFade = age <= awaken ? 1.0F : 1.0F - ease((age - awaken) / 6.0F);
            float alpha = form * explodeFade * viewAlpha;
            if (alpha <= 0.01F) {
                return;
            }

            float sphereRadius = Mth.lerp(crush, 2.35F * form, 1.15F)
                    + (age > awaken ? (age - awaken) * 0.95F : 0.0F);
            double cy = a.position().y + 1.08D;

            // 外层高速旋转燃烧金橙太阳球壳
            pose.pushPose();
            pose.translate(a.position().x, cy, a.position().z);
            pose.mulPose(Axis.YP.rotationDegrees(age * 9.5F + crush * 45.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(18.0F));
            pose.scale(sphereRadius, sphereRadius, sphereRadius);
            meshes.solarCocoon().renderAdditive(pose, buffer, 1.0F, 0.74F, 0.18F,
                    0.92F * alpha, -age * 0.032F, 0.0F);
            pose.popPose();

            // 内层反向高速旋转炽白金阳核
            pose.pushPose();
            pose.translate(a.position().x, cy, a.position().z);
            pose.mulPose(Axis.YP.rotationDegrees(-age * 13.0F));
            pose.mulPose(Axis.XP.rotationDegrees(-24.0F));
            float innerR = sphereRadius * 0.82F;
            pose.scale(innerR, innerR, innerR);
            meshes.solarCocoon().renderAdditive(pose, buffer, 1.0F, 0.94F, 0.62F,
                    0.78F * alpha, age * 0.044F, 0.0F);
            pose.popPose();

            // 双赤道超长撕裂金焰彗尾环（横贯画面，还原帧 05）
            float ringScale = Mth.lerp(crush, 1.65F * form, 1.10F)
                    + (age > awaken ? (age - awaken) * 0.65F : 0.0F);
            pose.pushPose();
            pose.translate(a.position().x, cy, a.position().z);
            pose.mulPose(Axis.YP.rotationDegrees(age * 11.5F));
            pose.mulPose(Axis.ZP.rotationDegrees(12.0F));
            pose.scale(ringScale, ringScale * 0.82F, ringScale);
            meshes.cocoonRings().renderAdditive(pose, buffer, 1.0F, 0.86F, 0.34F,
                    0.95F * alpha, -age * 0.055F, 0.0F);
            pose.popPose();
        }
    }

    private static void renderAwakenShockwave(PoseStack pose, VertexConsumer buffer,
                                              PhainonVfxModelData vfxModels, Actor a) {
        float burst = a.age() - PhainonUltimateHandler.AWAKEN_TICK;
        if (burst < 0.0F || burst > 22.0F) {
            return;
        }
        float t = burst / 22.0F;
        float spread = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        float alpha = (1.0F - t) * (a.firstPerson() ? 0.45F : 0.92F);
        float radius = 2.2F + 11.5F * spread;

        // 对称拼合两段 156° 弯月波形成 360° 破茧烈金冲击环
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            pose.translate(a.position().x, a.position().y + 1.05D, a.position().z);
            pose.mulPose(Axis.YP.rotationDegrees(side * 180.0F + burst * 4.5F));
            pose.mulPose(Axis.ZP.rotationDegrees(side == 0 ? 10.0F : -10.0F));
            pose.scale(radius, 1.4F + spread * 1.8F, radius);
            vfxModels.crescentWave().renderAdditive(pose, buffer, 1.0F, 0.84F, 0.36F, alpha, -t * 0.4F, 0.0F);
            pose.popPose();
        }
    }

    private static void pushBodyAlignedPose(PoseStack pose, Actor a, float partial) {
        Player player = a.player();
        pose.pushPose();
        pose.translate(a.position().x, a.position().y, a.position().z);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot)));
        if (player.isCrouching()) {
            pose.translate(0.0F, -0.14F, 0.09F);
        }
    }

    private static float fadeTail(Actor a) {
        return ease((a.duration() - a.age()) / 20.0F);
    }

    private static List<Actor> collectActors(RenderLevelStageEvent event, Minecraft game, float partial) {
        List<Actor> actors = new ArrayList<>();
        var cameraPos = event.getCamera().getPosition();
        for (var entry : ACTIVE.entrySet()) {
            if (!(level.getEntity(entry.getKey()) instanceof Player player)) {
                continue;
            }
            Visual visual = entry.getValue();
            float age = level.getGameTime() - visual.start + partial;
            if (age < 0.0F || age >= visual.duration) {
                continue;
            }
            boolean firstPerson = player == game.getCameraEntity() && game.options.getCameraType().isFirstPerson();
            var position = player.getPosition(partial);
            float hover = Mth.clamp((float) (position.y - visual.origin.y), 0.0F, visual.maxLift);
            actors.add(new Actor(player, age, visual.duration, hover, firstPerson,
                    position.subtract(cameraPos), visual.origin.subtract(cameraPos)));
        }
        return actors;
    }

    static float ease(float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
