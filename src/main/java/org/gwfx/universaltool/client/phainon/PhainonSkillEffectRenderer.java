package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 白厄（侵晨）普通战技「黎明创世，地辟天开」客户端 3D 特效、Additive 叠加发光与动感镜头渲染器。
 * <p>整合 Blockbench 3D 特效模型与 AI 关键帧发光贴图：
 * 地面双层日轮圣印阵 -> 剑锋立体金焰光锥 -> 3D 圣日轮与放射金芒 ->
 * 裂空青白光柱 -> 巨型 3D 蓝金弯月能量波（含 UV 流动）+ 贴地冲击气浪 + 14 块立体飞溅碎石 + 镜头冲击感。</p>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonSkillEffectRenderer {

    private static final ResourceLocation SUNBURST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/misc/phainon_sunburst_sigil.png");
    private static final ResourceLocation SLASH_ARC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/misc/phainon_slash_arc.png");
    private static final ResourceLocation GROUND_DUST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/misc/phainon_ground_dust.png");
    private static final ResourceLocation DEBRIS_ROCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/misc/phainon_debris_rock.png");

    private static final float SLASH_DURATION_TICKS = 22.0F;
    private static final int GROUND_ARC_SEGMENTS = 32;
    private static final float ARC_TOTAL_ANGLE_DEG = 152.0F;
    private static final int DEBRIS_COUNT = 14;

    private static final Map<Integer, CastVisual> ACTIVE = new HashMap<>();
    private static long lastSequence;

    private static final class CastVisual {
        private final boolean mainHand;
        private final long startGameTime;
        private final int totalTicks;
        private long slashGameTime = -1L;
        private double slashOriginX;
        private double slashOriginY;
        private double slashOriginZ;
        private float slashYawDeg;

        private CastVisual(boolean mainHand, long startGameTime, int totalTicks) {
            this.mainHand = mainHand;
            this.startGameTime = startGameTime;
            this.totalTicks = Math.max(1, totalTicks);
        }
    }

    private PhainonSkillEffectRenderer() {
    }

    /**
     * 清理全部客户端缓存视觉状态。
     */
    public static void clear() {
        ACTIVE.clear();
        lastSequence = 0L;
        PhainonUltimateEffectRenderer.clear();
        PhainonEnhancedBasicEffects.clear();
    }

    /**
     * 应用服务端同步的战技表现数据包。
     */
    public static void apply(PhainonSkillEffectPayload payload) {
        if (payload.sequence() < lastSequence) {
            return;
        }
        lastSequence = payload.sequence();

        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;

        switch (payload.action()) {
            case START -> {
                CastVisual visual = new CastVisual(payload.mainHand(), now, payload.totalTicks());
                visual.slashOriginX = payload.originX();
                visual.slashOriginY = payload.originY();
                visual.slashOriginZ = payload.originZ();
                visual.slashYawDeg = payload.yawDeg();
                ACTIVE.put(payload.casterId(), visual);
            }
            case SLASH -> {
                CastVisual visual = ACTIVE.get(payload.casterId());
                if (visual == null) {
                    visual = new CastVisual(payload.mainHand(), Math.max(0L, now - 28L), payload.totalTicks());
                    ACTIVE.put(payload.casterId(), visual);
                }
                visual.slashGameTime = now;
                visual.slashOriginX = payload.originX();
                visual.slashOriginY = payload.originY();
                visual.slashOriginZ = payload.originZ();
                visual.slashYawDeg = payload.yawDeg();
            }
            case CANCEL -> ACTIVE.remove(payload.casterId());
            case ULTIMATE_START, ULTIMATE_CANCEL -> PhainonUltimateEffectRenderer.apply(payload);
            case GUARD, RIPOSTE, GUARD_END -> PhainonCounterEffects.apply(payload);
            case ENHANCED_BASIC -> PhainonEnhancedBasicEffects.apply(payload);
        }
    }

    /**
     * 获取实体当前正在播放普通战技的已持续时间（tick）；未施放则返回 {@code -1.0F}。
     */
    public static float getActiveCastAge(int entityId, float partialTick) {
        CastVisual visual = ACTIVE.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (visual == null || mc.level == null) {
            return -1.0F;
        }
        float age = (mc.level.getGameTime() - visual.startGameTime) + partialTick;
        if (age < 0.0F || age > visual.totalTicks) {
            return -1.0F;
        }
        return age;
    }

    /**
     * 判断指定实体当前是否正在通过指定手部施放普通战技。
     */
    public static boolean isCastingWithHand(int entityId, InteractionHand hand) {
        CastVisual visual = ACTIVE.get(entityId);
        if (visual == null) {
            return false;
        }
        return visual.mainHand == (hand == InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        long now = mc.level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            CastVisual visual = entry.getValue();
            Entity entity = mc.level.getEntity(entry.getKey());
            return !(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || (now - visual.startGameTime) > visual.totalTicks + 2;
        });

        for (Map.Entry<Integer, CastVisual> entry : ACTIVE.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity owner)) {
                continue;
            }
            CastVisual visual = entry.getValue();
            long age = now - visual.startGameTime;
            if (age >= 3 && age <= 26 && age % 2 == 0) {
                double angle = now * 0.58D;
                double radius = 1.25D;
                mc.level.addParticle(
                        ParticleTypes.END_ROD,
                        owner.getX() + Math.cos(angle) * radius,
                        owner.getY() + 0.45D + (age * 0.055D),
                        owner.getZ() + Math.sin(angle) * radius,
                        0.0D, 0.05D, 0.0D
                );
            }
            if (visual.slashGameTime >= 0) {
                long slashAge = now - visual.slashGameTime;
                if (slashAge >= 0 && slashAge <= 12 && slashAge % 2 == 0) {
                    float yawRad = (float) Math.toRadians(visual.slashYawDeg + ((slashAge - 6) * 14.0F));
                    double fx = -Mth.sin(yawRad);
                    double fz = Mth.cos(yawRad);
                    double dist = 3.5D + slashAge * 0.6D;
                    mc.level.addParticle(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            visual.slashOriginX + fx * dist,
                            visual.slashOriginY + 0.95D,
                            visual.slashOriginZ + fz * dist,
                            fx * 0.14D, 0.025D, fz * 0.14D
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float age = getActiveCastAge(mc.player.getId(), (float) event.getPartialTick());
        if (age < 0.0F) {
            return;
        }

        double deltaFov = 0.0D;
        if (age >= 10.0F && age < 25.0F) {
            // 蓄力巅峰平滑收紧 FOV（-5°）聚集视觉张力
            float t = (age - 10.0F) / 15.0F;
            deltaFov = -5.0D * easeOutCubic(t);
        } else if (age >= 25.0F && age < 34.0F) {
            // 前冲挥击瞬间爆发拉宽至 +9° 再顺滑回落
            float t = (age - 25.0F) / 9.0F;
            if (t < 0.35F) {
                deltaFov = Mth.lerp(t / 0.35F, -5.0F, 9.0F);
            } else {
                deltaFov = Mth.lerp(easeOutCubic((t - 0.35F) / 0.65F), 9.0F, 0.0F);
            }
        }

        if (Math.abs(deltaFov) > 0.01D) {
            event.setFOV(Math.max(30.0D, event.getFOV() + deltaFov));
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float age = getActiveCastAge(mc.player.getId(), (float) event.getPartialTick());
        if (age < 25.0F || age > 35.0F) {
            return;
        }

        float local = age - 25.0F;
        // 挥击瞬间克制的斜斩镜头微倾（最大 Roll 3.0°）
        float rollEnv = Mth.sin(Mth.clamp(local / 8.0F, 0.0F, 1.0F) * Mth.PI);
        event.setRoll(event.getRoll() + 3.0F * rollEnv);

        // 短促 4 tick 冲击微震（Pitch/Yaw ±0.6° 阻尼衰减）
        if (local <= 4.5F) {
            float damp = 1.0F - (local / 4.5F);
            event.setPitch(event.getPitch() + Mth.sin(local * 11.0F) * 0.6F * damp);
            event.setYaw(event.getYaw() + Mth.cos(local * 13.0F) * 0.6F * damp);
        }
    }

    static boolean hasWorldEffects() {
        return !ACTIVE.isEmpty();
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        PhainonVfxModelData models = PhainonVfxModelData.getOrLoad();
        if (models == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = mc.level.getGameTime();
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        record RenderEntry(LivingEntity owner, CastVisual visual, float age, float slashAge,
                           boolean isFirstPerson, double px, double py, double pz) {}

        List<RenderEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, CastVisual> e : ACTIVE.entrySet()) {
            Entity entity = mc.level.getEntity(e.getKey());
            if (!(entity instanceof LivingEntity owner) || !owner.isAlive()) {
                continue;
            }
            CastVisual visual = e.getValue();
            float age = Math.max(0.0F, (gameTime - visual.startGameTime) + partialTick);
            float slashAge = visual.slashGameTime >= 0
                    ? Math.max(0.0F, (gameTime - visual.slashGameTime) + partialTick)
                    : -1.0F;
            boolean isFirstPerson = owner == mc.player && mc.options.getCameraType().isFirstPerson();
            double px = Mth.lerp(partialTick, owner.xOld, owner.getX()) - camX;
            double py = Mth.lerp(partialTick, owner.yOld, owner.getY()) - camY;
            double pz = Mth.lerp(partialTick, owner.zOld, owner.getZ()) - camZ;
            entries.add(new RenderEntry(owner, visual, age, slashAge, isFirstPerson, px, py, pz));
        }

        if (entries.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        var bufferSource = mc.renderBuffers().bufferSource();
        pose.pushPose();

        // Pass 1：日轮圣印阵、3D 圣日轮与裂空光柱（Additive 叠加发光 SUNBURST_TEXTURE）
        RenderType sunburstLayer = PhainonVfxRenderTypes.additiveGlow(SUNBURST_TEXTURE);
        VertexConsumer sunburstBuffer = bufferSource.getBuffer(sunburstLayer);
        for (RenderEntry r : entries) {
            if (r.age <= 32.0F) {
                renderGroundSunSigil(pose, sunburstBuffer, r.px, r.py, r.pz, r.age, r.isFirstPerson);
            }
            if (r.age <= 27.0F) {
                float ownerYaw = Mth.lerp(partialTick, r.owner.yRotO, r.owner.getYRot());
                renderSwordChargeFlameCone(pose, sunburstBuffer, models, r.px, r.py, r.pz,
                        ownerYaw, r.age, r.isFirstPerson);
            }
            if (r.age >= 8.0F && r.age <= 29.0F) {
                float ownerYaw = Mth.lerp(partialTick, r.owner.yRotO, r.owner.getYRot());
                renderSunburstWheel3D(pose, sunburstBuffer, models, r.px, r.py, r.pz,
                        ownerYaw, r.age, r.isFirstPerson);
            }
            if (r.slashAge >= -2.0F && r.slashAge <= 7.0F) {
                double sx = r.visual.slashOriginX - camX;
                double sy = r.visual.slashOriginY - camY;
                double sz = r.visual.slashOriginZ - camZ;
                renderSlashBeam3D(pose, sunburstBuffer, models, sx, sy, sz,
                        r.visual.slashYawDeg, Math.max(0.0F, r.slashAge + 2.0F));
            }
        }
        bufferSource.endBatch(sunburstLayer);

        // Pass 2：巨型 3D 蓝金弯月能量波壳体 + 流动炽金外锋（Additive 叠加发光 SLASH_ARC_TEXTURE）
        RenderType slashArcLayer = PhainonVfxRenderTypes.additiveGlow(SLASH_ARC_TEXTURE);
        VertexConsumer slashBuffer = bufferSource.getBuffer(slashArcLayer);
        for (RenderEntry r : entries) {
            if (r.slashAge >= 0.0F && r.slashAge <= SLASH_DURATION_TICKS) {
                double sx = r.visual.slashOriginX - camX;
                double sy = r.visual.slashOriginY - camY;
                double sz = r.visual.slashOriginZ - camZ;
                renderCrescentWave3D(pose, slashBuffer, models, sx, sy, sz,
                        r.visual.slashYawDeg, r.slashAge);
            }
        }
        bufferSource.endBatch(slashArcLayer);

        // Pass 3：贴地白色冲击气浪与炽金地面划痕（Additive 叠加发光 GROUND_DUST_TEXTURE）
        RenderType groundDustLayer = PhainonVfxRenderTypes.additiveGlow(GROUND_DUST_TEXTURE);
        VertexConsumer dustBuffer = bufferSource.getBuffer(groundDustLayer);
        for (RenderEntry r : entries) {
            if (r.slashAge >= 0.0F && r.slashAge <= SLASH_DURATION_TICKS) {
                double sx = r.visual.slashOriginX - camX;
                double sy = r.visual.slashOriginY - camY;
                double sz = r.visual.slashOriginZ - camZ;
                renderGroundShockDustAndScars(pose, dustBuffer, models, sx, sy, sz,
                        r.visual.slashYawDeg, r.slashAge);
            }
        }
        bufferSource.endBatch(groundDustLayer);

        // Pass 4：14 块 3D 立体翻飞玄武岩碎石（不透明实体渲染 DEBRIS_ROCK_TEXTURE）
        RenderType debrisLayer = PhainonVfxRenderTypes.debrisSolid(DEBRIS_ROCK_TEXTURE);
        VertexConsumer debrisBuffer = bufferSource.getBuffer(debrisLayer);
        for (RenderEntry r : entries) {
            if (r.slashAge >= 0.0F && r.slashAge <= SLASH_DURATION_TICKS) {
                renderFlyingDebrisShards(pose, debrisBuffer, models, mc,
                        r.visual.slashOriginX, r.visual.slashOriginY, r.visual.slashOriginZ,
                        camX, camY, camZ, r.visual.slashYawDeg, r.slashAge);
            }
        }
        bufferSource.endBatch(debrisLayer);

        pose.popPose();
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        CastVisual visual = ACTIVE.get(mc.player.getId());
        if (visual == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = mc.level.getGameTime();
        float age = (now - visual.startGameTime) + partialTick;
        float slashAge = visual.slashGameTime >= 0
                ? (now - visual.slashGameTime) + partialTick
                : -1.0F;

        float intensity = 0.0F;
        if (age >= 12.0F && age <= 26.0F) {
            float t = (age - 12.0F) / 14.0F;
            intensity = Math.max(intensity, Mth.sin(t * Mth.PI) * 0.14F);
        }
        if (slashAge >= 0.0F && slashAge <= 5.0F) {
            float t = 1.0F - (slashAge / 5.0F);
            intensity = Math.max(intensity, t * 0.18F);
        }
        if (intensity <= 0.005F) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        int edge = Math.max(12, (int) (Math.min(width, height) * 0.06F));
        int outerAlpha = Math.min(46, (int) (intensity * 255.0F));
        int outerColor = (outerAlpha << 24) | 0x00FFD25A;
        int innerColor = 0x00FFD25A;

        gui.fillGradient(0, 0, width, edge, outerColor, innerColor);
        gui.fillGradient(0, height - edge, width, height, innerColor, outerColor);
        gui.fill(0, edge, edge / 2, height - edge, ((outerAlpha / 2) << 24) | 0x00FFE38E);
        gui.fill(width - edge / 2, edge, width, height - edge, ((outerAlpha / 2) << 24) | 0x00FFE38E);
    }

    private static void renderGroundSunSigil(PoseStack pose, VertexConsumer buffer,
                                             double px, double py, double pz,
                                             float age, boolean isFirstPerson) {
        float expand = easeOutCubic(Mth.clamp(age / 8.0F, 0.0F, 1.0F));
        float fade = age <= 22.0F ? 1.0F : Mth.clamp(1.0F - (age - 22.0F) / 10.0F, 0.0F, 1.0F);
        float baseAlpha = (isFirstPerson ? 0.65F : 0.90F) * fade;
        if (baseAlpha <= 0.01F) {
            return;
        }

        // 外环黎明金圣印（直径最大 5.4 格）
        pose.pushPose();
        pose.translate(px, py + 0.04D, pz);
        pose.mulPose(Axis.YP.rotationDegrees(age * 3.8F));
        drawTexturedTriangleQuadXZ(pose.last().pose(), buffer, 2.7F * expand, 1.0F, 0.84F, 0.36F, baseAlpha);
        pose.popPose();

        // 浮空反向内环炽白金圣印
        pose.pushPose();
        pose.translate(px, py + 0.18D, pz);
        pose.mulPose(Axis.YP.rotationDegrees(-age * 6.4F));
        drawTexturedTriangleQuadXZ(pose.last().pose(), buffer, 1.65F * expand, 1.0F, 0.96F, 0.72F, baseAlpha * 0.95F);
        pose.popPose();
    }

    private static void renderSwordChargeFlameCone(PoseStack pose, VertexConsumer buffer,
                                                   PhainonVfxModelData models,
                                                   double px, double py, double pz,
                                                   float yawDeg, float age, boolean isFirstPerson) {
        float ramp = easeOutCubic(Mth.clamp(age / 8.0F, 0.0F, 1.0F));
        float fade = age <= 22.0F ? 1.0F : Mth.clamp(1.0F - (age - 22.0F) / 5.0F, 0.0F, 1.0F);
        float alpha = (isFirstPerson ? 0.38F : 0.72F) * ramp * fade;
        if (alpha <= 0.01F) {
            return;
        }

        float yawRad = (float) Math.toRadians(yawDeg);
        double forwardX = -Mth.sin(yawRad);
        double forwardZ = Mth.cos(yawRad);
        double rightX = Mth.cos(yawRad);
        double rightZ = Mth.sin(yawRad);

        // 在第三人称下贴近举剑右侧上方；第一人称置于视线上前方以免遮挡准星
        double offsetFwd = isFirstPerson ? 0.95D : 0.35D;
        double offsetRight = isFirstPerson ? 0.28D : 0.32D;
        double baseY = isFirstPerson ? py + 1.55D : py + 1.45D;

        pose.pushPose();
        pose.translate(px + forwardX * offsetFwd + rightX * offsetRight,
                baseY,
                pz + forwardZ * offsetFwd + rightZ * offsetRight);
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        // 旋转 -90° 使光锥 +Z 尖端朝天竖起，包裹高举的长剑并高速自旋
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(age * 14.0F));
        pose.scale(0.45F * ramp, 0.45F * ramp, 2.8F * ramp);
        models.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.86F, 0.38F, alpha, -age * 0.06F, 0.0F);
        pose.popPose();
    }

    private static void renderSunburstWheel3D(PoseStack pose, VertexConsumer buffer,
                                              PhainonVfxModelData models,
                                              double px, double py, double pz,
                                              float yawDeg, float age, boolean isFirstPerson) {
        float localAge = age - 8.0F;
        float progress = Mth.clamp(localAge / 21.0F, 0.0F, 1.0F);
        float expand = easeOutBack(Mth.clamp(localAge / 6.5F, 0.0F, 1.0F));
        float fade = progress < 0.72F ? 1.0F : Mth.clamp(1.0F - (progress - 0.72F) / 0.28F, 0.0F, 1.0F);
        float alpha = (isFirstPerson ? 0.55F : 0.95F) * fade;
        if (alpha <= 0.01F) {
            return;
        }

        float yawRad = (float) Math.toRadians(yawDeg);
        double forwardX = -Mth.sin(yawRad);
        double forwardZ = Mth.cos(yawRad);
        double offsetFwd = isFirstPerson ? 1.75D : -0.40D;
        double centerY = isFirstPerson ? py + 2.75D : py + 2.45D;
        float radius = Mth.lerp(expand, 0.40F, 2.90F);

        pose.pushPose();
        pose.translate(px + forwardX * offsetFwd, centerY, pz + forwardZ * offsetFwd);
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));

        // 1. Blockbench 3D 立体圣日轮与放射光锥
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(localAge * 3.2F));
        pose.scale(radius, radius, radius * 0.65F);
        models.sunburstWheel().renderAdditive(pose, buffer, 1.0F, 0.86F, 0.40F, alpha, 0.0F, 0.0F);
        pose.popPose();

        // 2. 同轴反向旋转的炽白高光圣印叠加层
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.06F);
        pose.mulPose(Axis.ZP.rotationDegrees(-localAge * 4.8F));
        float innerScale = radius * 0.78F;
        pose.scale(innerScale, innerScale, innerScale * 0.5F);
        models.sunburstWheel().renderAdditive(pose, buffer, 1.0F, 0.96F, 0.76F, alpha * 0.78F, 0.0F, 0.0F);
        pose.popPose();

        pose.popPose();
    }

    private static void renderSlashBeam3D(PoseStack pose, VertexConsumer buffer,
                                          PhainonVfxModelData models,
                                          double sx, double sy, double sz,
                                          float yawDeg, float beamAge) {
        float progress = Mth.clamp(beamAge / 9.0F, 0.0F, 1.0F);
        float alpha = (1.0F - progress) * 0.95F;
        if (alpha <= 0.01F) {
            return;
        }
        float stretch = easeOutCubic(Mth.clamp(beamAge / 2.2F, 0.0F, 1.0F));
        float widthScale = Mth.lerp(progress, 0.85F, 0.16F);
        float lengthScale = 11.0F * (0.25F + 0.75F * stretch);

        pose.pushPose();
        pose.translate(sx, sy + 1.15D, sz);
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        pose.mulPose(Axis.ZP.rotationDegrees(13.0F));

        // 外层青蓝裂空光柱
        pose.pushPose();
        pose.scale(widthScale, widthScale, lengthScale);
        models.slashBeam().renderAdditive(pose, buffer, 0.45F, 0.88F, 1.0F, alpha, -beamAge * 0.12F, 0.0F);
        pose.popPose();

        // 内核炽白耀眼光剑
        pose.pushPose();
        pose.scale(widthScale * 0.45F, widthScale * 0.45F, lengthScale * 1.04F);
        models.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.98F, 0.92F, alpha, -beamAge * 0.16F, 0.0F);
        pose.popPose();

        pose.popPose();
    }

    private static void renderCrescentWave3D(PoseStack pose, VertexConsumer buffer,
                                             PhainonVfxModelData models,
                                             double sx, double sy, double sz,
                                             float yawDeg, float slashAge) {
        float t = Mth.clamp(slashAge / SLASH_DURATION_TICKS, 0.0F, 1.0F);
        float sweep = easeOutCubic(t);
        float fade = t < 0.58F ? 1.0F : Mth.clamp(1.0F - (t - 0.58F) / 0.42F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }

        float radiusScale = Mth.lerp(sweep, 2.4F, 10.5F);
        float heightScale = Mth.lerp(sweep, 1.35F, 3.05F);
        // 高速 UV 流动让星河轨迹与金焰锋芒随斩击掠过弧面
        float uvScrollU = -t * 0.35F;

        pose.pushPose();
        pose.translate(sx, sy + 1.10D, sz);
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        pose.mulPose(Axis.ZP.rotationDegrees(13.0F));

        // 1. 巨型 3D 蓝金弯月主体能量波壳体
        pose.pushPose();
        pose.scale(radiusScale, heightScale, radiusScale);
        models.crescentWave().renderAdditive(pose, buffer, 1.0F, 1.0F, 1.0F, 0.96F * fade, uvScrollU, 0.0F);
        pose.popPose();

        // 2. 略微超前且加高的炽金高光叠亮层，令锋刃耀眼夺目
        pose.pushPose();
        pose.translate(0.0F, 0.06F, 0.14F);
        pose.scale(radiusScale * 1.04F, heightScale * 1.12F, radiusScale * 1.04F);
        models.crescentWave().renderAdditive(pose, buffer, 1.0F, 0.90F, 0.58F, 0.72F * fade, uvScrollU * 1.3F, 0.0F);
        pose.popPose();

        pose.popPose();
    }

    private static void renderGroundShockDustAndScars(PoseStack pose, VertexConsumer buffer,
                                                       PhainonVfxModelData models,
                                                       double sx, double sy, double sz,
                                                       float yawDeg, float slashAge) {
        float t = Mth.clamp(slashAge / SLASH_DURATION_TICKS, 0.0F, 1.0F);
        float sweep = easeOutCubic(t);
        float fade = t < 0.45F ? 1.0F : Mth.clamp(1.0F - (t - 0.45F) / 0.55F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }

        float outerR = Mth.lerp(sweep, 2.5F, 10.6F);
        float innerR = Math.max(0.8F, outerR - 3.6F);

        pose.pushPose();
        pose.translate(sx, sy + 0.08D, sz);
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));

        // 贴地白色冲击气浪与金星划痕弧带
        Matrix4f mat = pose.last().pose();
        int color = packColor(1.0F, 1.0F, 1.0F, 0.85F * fade);
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        float halfArcRad = (float) Math.toRadians(ARC_TOTAL_ANGLE_DEG * 0.5F);

        for (int i = 0; i < GROUND_ARC_SEGMENTS; i++) {
            float u0 = (float) i / GROUND_ARC_SEGMENTS;
            float u1 = (float) (i + 1) / GROUND_ARC_SEGMENTS;
            float a0 = Mth.lerp(u0, -halfArcRad, halfArcRad);
            float a1 = Mth.lerp(u1, -halfArcRad, halfArcRad);

            float s0 = Mth.sin(a0), c0 = Mth.cos(a0);
            float s1 = Mth.sin(a1), c1 = Mth.cos(a1);

            float ox0 = s0 * outerR, oz0 = c0 * outerR;
            float ix0 = s0 * innerR, iz0 = c0 * innerR;
            float ox1 = s1 * outerR, oz1 = c1 * outerR;
            float ix1 = s1 * innerR, iz1 = c1 * innerR;

            buffer.addVertex(mat, ox0, 0.0F, oz0).setColor(color).setUv(u0, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
            buffer.addVertex(mat, ix0, 0.0F, iz0).setColor(color).setUv(u0, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
            buffer.addVertex(mat, ix1, 0.0F, iz1).setColor(color).setUv(u1, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);

            buffer.addVertex(mat, ox0, 0.0F, oz0).setColor(color).setUv(u0, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
            buffer.addVertex(mat, ix1, 0.0F, iz1).setColor(color).setUv(u1, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
            buffer.addVertex(mat, ox1, 0.0F, oz1).setColor(color).setUv(u1, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        }

        // 3 道沿地面扇面撕裂的炽金光痕
        float scarLength = Mth.lerp(sweep, 3.0F, 9.8F);
        for (float angleDeg : new float[]{-34.0F, 2.0F, 38.0F}) {
            pose.pushPose();
            pose.translate(0.0F, 0.02F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(angleDeg));
            pose.scale(0.32F, 0.14F, scarLength);
            models.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.85F, 0.32F, 0.82F * fade, 0.0F, 0.0F);
            pose.popPose();
        }

        pose.popPose();
    }

    private static void renderFlyingDebrisShards(PoseStack pose, VertexConsumer buffer,
                                                 PhainonVfxModelData models, Minecraft mc,
                                                 double originX, double originY, double originZ,
                                                 double camX, double camY, double camZ,
                                                 float yawDeg, float slashAge) {
        if (mc.level == null) {
            return;
        }
        float t = Mth.clamp(slashAge / SLASH_DURATION_TICKS, 0.0F, 1.0F);
        float baseYawRad = (float) Math.toRadians(yawDeg);

        for (int i = 0; i < DEBRIS_COUNT; i++) {
            float seed = i * 1.618034F;
            float angleOffset = Mth.lerp((float) i / (DEBRIS_COUNT - 1), -1.15F, 1.15F) + 0.18F * Mth.sin(seed * 5.0F);
            float worldAngle = baseYawRad + angleOffset;
            double dirX = -Mth.sin(worldAngle);
            double dirZ = Mth.cos(worldAngle);

            double startDist = 2.1D + (i % 4) * 1.15D;
            double launchSpeed = 3.2D + (i % 3) * 1.3D;
            double horizontalDist = startDist + launchSpeed * t;

            // 垂直抛物线腾空与重力回落
            double peakHeight = 1.4D + (i % 5) * 0.45D;
            double verticalOffset = peakHeight * (4.0D * t * (1.0D - t)) + 0.18D;

            double worldX = originX + dirX * horizontalDist;
            double worldY = originY + verticalOffset;
            double worldZ = originZ + dirZ * horizontalDist;

            // 临近落地结束时自然缩尺消失
            float scaleFade = t < 0.75F ? 1.0F : Mth.clamp(1.0F - (t - 0.75F) / 0.25F, 0.0F, 1.0F);
            float rockScale = (0.55F + (i % 4) * 0.18F) * scaleFade;
            if (rockScale <= 0.02F) {
                continue;
            }

            int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(worldX, worldY, worldZ));

            pose.pushPose();
            pose.translate(worldX - camX, worldY - camY, worldZ - camZ);
            pose.mulPose(Axis.XP.rotationDegrees(slashAge * (18.0F + (i % 3) * 9.0F) + i * 37.0F));
            pose.mulPose(Axis.YP.rotationDegrees(slashAge * (22.0F - (i % 4) * 7.0F) + i * 53.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(slashAge * 15.0F + i * 29.0F));
            pose.scale(rockScale, rockScale * (0.85F + (i % 2) * 0.25F), rockScale);

            int shade = 195 + (i % 3) * 20;
            models.debrisShard().renderSolid(pose, buffer, packedLight, shade, shade, Math.min(255, shade + 10));
            pose.popPose();
        }
    }

    private static void drawTexturedTriangleQuadXZ(Matrix4f mat, VertexConsumer buffer, float halfSize,
                                                   float r, float g, float b, float alpha) {
        if (alpha <= 0.005F) {
            return;
        }
        int color = packColor(r, g, b, alpha);
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        // Triangle 1
        buffer.addVertex(mat, -halfSize, 0.0F, -halfSize).setColor(color).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(mat, -halfSize, 0.0F, halfSize).setColor(color).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(mat, halfSize, 0.0F, halfSize).setColor(color).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        // Triangle 2
        buffer.addVertex(mat, -halfSize, 0.0F, -halfSize).setColor(color).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(mat, halfSize, 0.0F, halfSize).setColor(color).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(mat, halfSize, 0.0F, -halfSize).setColor(color).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }

    private static int packColor(float r, float g, float b, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int ri = (int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = (int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float xm1 = Mth.clamp(x, 0.0F, 1.0F) - 1.0F;
        return 1.0F + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
    }
}
