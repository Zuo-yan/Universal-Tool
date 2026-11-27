package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.phainon.PhainonVfxRenderTypes;
import org.gwfx.universaltool.network.Skill3dEffectPayload;
import org.gwfx.universaltool.network.Skill3dEffectPayload.EffectKind;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能系统统一 3D 立体视觉特效渲染器（Additive 光效渲染）。
 *
 * <p>涵盖新月剑气、折线闪电链、旋转天罚星阵、深空黑洞吸积盘、冰晶牢笼、
 * 威压星穹壁、虚空星锚、浴火凤凰展翼、翠金炼成阵与破军/玄武共鸣 3D 特效。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class Skill3dEffectRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int MAX_ACTIVE = 64;
    private static final int FADE_TICKS = 8;
    private static final double DRAW_DIST_SQR = 96.0 * 96.0;

    private static final Map<Long, Scene> SCENES = new LinkedHashMap<>();
    private static ClientLevel currentLevel;

    private static final class Scene {
        final EffectKind kind;
        final Vec3 origin;
        final Vec3 target;
        final float radius;
        final int color;
        final long born;
        long fadeAt;

        Scene(EffectKind kind, Vec3 origin, Vec3 target, float radius, int color, long born, int duration) {
            this.kind = kind;
            this.origin = origin;
            this.target = target;
            this.radius = Math.max(0.4F, Math.min(48.0F, radius));
            this.color = color & 0x00FFFFFF;
            this.born = born;
            this.fadeAt = born + Math.max(4, Math.min(600, duration));
        }
    }

    private Skill3dEffectRenderer() {}

    public static void apply(Skill3dEffectPayload p) {
        ClientLevel level = syncLevel();
        if (level == null) return;
        long now = level.getGameTime();
        if (p.durationTicks() <= 0) {
            Scene existing = SCENES.get(p.id());
            if (existing != null) {
                existing.fadeAt = Math.min(existing.fadeAt, now);
            }
            return;
        }
        if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z())
                || !Float.isFinite(p.radius())) {
            return;
        }
        if (SCENES.size() >= MAX_ACTIVE) {
            SCENES.remove(SCENES.keySet().iterator().next());
        }
        SCENES.put(p.id(), new Scene(
                EffectKind.byOrdinal(p.kind()),
                new Vec3(p.x(), p.y(), p.z()),
                new Vec3(p.tx(), p.ty(), p.tz()),
                p.radius(),
                p.color(),
                now,
                p.durationTicks()
        ));
    }

    private static ClientLevel syncLevel() {
        ClientLevel lvl = Minecraft.getInstance().level;
        if (lvl != currentLevel) {
            SCENES.clear();
            currentLevel = lvl;
        }
        return lvl;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel lvl = syncLevel();
        if (lvl != null && !SCENES.isEmpty()) {
            long now = lvl.getGameTime();
            SCENES.values().removeIf(s -> now >= s.fadeAt + FADE_TICKS);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SCENES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != currentLevel) return;

        var pose = event.getPoseStack();
        Vec3 eye = event.getCamera().getPosition();
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var type = PhainonVfxRenderTypes.additiveGlow(WHITE);
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buf = null;

        for (Scene s : SCENES.values()) {
            if (s.origin.distanceToSqr(eye) > DRAW_DIST_SQR) continue;
            float age = (now - s.born) + partial;
            float fadeAge = (now - s.fadeAt) + partial;
            if (age < 0 || fadeAge >= FADE_TICKS) continue;
            if (buf == null) buf = buffers.getBuffer(type);

            float fadeAlpha = fadeAge <= 0 ? 1.0F : Math.max(0.0F, 1.0F - ease(fadeAge / FADE_TICKS));
            float totalDur = Math.max(4.0F, (float) (s.fadeAt - s.born));
            float lifeT = Mth.clamp(age / totalDur, 0.0F, 1.0F);

            Vec3 rel = s.origin.subtract(eye);
            pose.pushPose();
            pose.translate(rel.x, rel.y, rel.z);
            Matrix4f m = pose.last().pose();
            renderScene(m, buf, s, age, lifeT, fadeAlpha);
            pose.popPose();
        }
        if (buf != null) {
            buffers.endBatch(type);
        }
    }

    private static void renderScene(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        switch (s.kind) {
            case ASTRAL_CLEAVE -> drawAstralCleave(m, buf, s, lifeT, alpha);
            case THUNDER_ZONE -> drawThunderZone(m, buf, s, age, alpha);
            case LIGHTNING_CHAIN -> drawLightningChain(m, buf, s, age, lifeT, alpha);
            case GRAVITY_BLACK_HOLE -> drawBlackHole(m, buf, s, age, lifeT, alpha);
            case ICE_PRISON -> drawIcePrison(m, buf, s, age, lifeT, alpha);
            case OPPRESSIVE_WAVE -> drawDomeWave(m, buf, s.radius, lifeT, alpha, 0xFF4C3A, 0xFFE38C);
            case VOID_ANCHOR -> drawVoidAnchor(m, buf, s, age, alpha);
            case VOID_SLASH -> drawVoidSlash(m, buf, s, lifeT, alpha);
            case PHOENIX_WINGS -> drawPhoenixWings(m, buf, s, age, lifeT, alpha);
            case SANCTUARY_BLAST -> drawDomeWave(m, buf, s.radius, lifeT, alpha, 0x4FEAC0, 0xFFF29C);
            case AIR_SHOCKWAVE -> drawAirShockwave(m, buf, s.radius, lifeT, alpha);
            case ORE_SONAR -> drawDomeWave(m, buf, s.radius, lifeT, alpha, 0x5CE6FF, 0xE7B8FF);
            case STONE_TO_ORE_WAVE -> drawStoneToOre(m, buf, s, age, lifeT, alpha);
            case ALCHEMY_CIRCLE -> drawAlchemyCircle(m, buf, s, age, lifeT, alpha);
            case RESONANCE_BLADE -> drawResonanceBlade(m, buf, s, lifeT, alpha);
            case RESONANCE_AEGIS -> drawDomeWave(m, buf, s.radius, lifeT, alpha, 0x48E8B5, 0xFFF099);
        }
    }

    // ===== 1. 新月星刃（裂空星斩 / 兵刃交叉斩） =====
    private static void drawAstralCleave(Matrix4f m, VertexConsumer buf, Scene s, float lifeT, float alpha) {
        Vec3 fwd = safeNormalize(s.target);
        Vec3 right = safeNormalize(fwd.cross(new Vec3(0, 1, 0)));
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(fwd).normalize();

        float progress = ease(Math.min(1.0F, lifeT * 1.15F));
        float dist = s.radius * progress;
        float arcHalfW = (s.color == 0 ? 1.8F : 2.3F) * (0.75F + 0.35F * Mth.sin(progress * Mth.PI));
        float a = alpha * (1.0F - 0.55F * lifeT);
        int cOuter = s.color != 0 ? s.color : 0xB892FF;
        int cInner = 0xFFF2BE;

        int segs = 12;
        for (int i = 0; i < segs; i++) {
            float u0 = -1.0F + 2.0F * i / segs;
            float u1 = -1.0F + 2.0F * (i + 1) / segs;
            float b0 = (1.0F - u0 * u0) * 1.15F;
            float b1 = (1.0F - u1 * u1) * 1.15F;
            float w0 = (1.0F - Math.abs(u0) * 0.72F) * 0.42F;
            float w1 = (1.0F - Math.abs(u1) * 0.72F) * 0.42F;

            Vec3 p0 = fwd.scale(dist + b0).add(right.scale(u0 * arcHalfW)).add(up.scale(u0 * 0.25));
            Vec3 p1 = fwd.scale(dist + b1).add(right.scale(u1 * arcHalfW)).add(up.scale(u1 * 0.25));
            Vec3 p0Back = p0.subtract(fwd.scale(w0));
            Vec3 p1Back = p1.subtract(fwd.scale(w1));

            quad(m, buf,
                    (float) p0Back.x, (float) p0Back.y, (float) p0Back.z,
                    (float) p0.x, (float) p0.y, (float) p0.z,
                    (float) p1.x, (float) p1.y, (float) p1.z,
                    (float) p1Back.x, (float) p1Back.y, (float) p1Back.z,
                    cOuter, a * 0.65F);

            Vec3 p0Core = p0.subtract(fwd.scale(w0 * 0.42F));
            Vec3 p1Core = p1.subtract(fwd.scale(w1 * 0.42F));
            quad(m, buf,
                    (float) p0Core.x, (float) p0Core.y + 0.04F, (float) p0Core.z,
                    (float) p0.x, (float) p0.y + 0.04F, (float) p0.z,
                    (float) p1.x, (float) p1.y + 0.04F, (float) p1.z,
                    (float) p1Core.x, (float) p1Core.y + 0.04F, (float) p1Core.z,
                    cInner, a * 0.85F);
        }
    }

    // ===== 2. 天罚雷域（旋转紫金星阵 + 巨型主雷柱 + 残留电网） =====
    private static void drawThunderZone(Matrix4f m, VertexConsumer buf, Scene s, float age, float alpha) {
        float strikeT = 16.0F;
        float r = s.radius;
        if (age < strikeT) {
            float prep = Mth.clamp(age / strikeT, 0.0F, 1.0F);
            ringRotated(m, buf, r, 0.08F, 0.14F, 48, 0xFFAE42, 0.65F * alpha, age * 0.15F);
            ringRotated(m, buf, r * (1.0F - 0.65F * prep), 0.14F + prep * 0.5F, 0.12F, 36, 0xC678FF, 0.8F * alpha, -age * 0.25F);
            starPolygon(m, buf, r * 0.88F, 0.10F, 6, age * 0.12F, 0xFFF2A0, 0.55F * alpha);
        } else {
            float post = age - strikeT;
            ringRotated(m, buf, r, 0.08F, 0.18F, 48, 0xFFAE42, 0.55F * alpha, age * 0.07F);
            ringRotated(m, buf, r * 0.62F, 0.12F, 0.10F, 32, 0xC678FF, 0.45F * alpha, -age * 0.10F);

            // 主雷柱瞬时爆发（前 14 tick）
            if (post < 14.0F) {
                float pFade = 1.0F - post / 14.0F;
                pillar(m, buf, 0, 0, 0, 0.55F * (0.4F + pFade), 18.0F, 0xFFF6C2, 0.85F * pFade * alpha);
                pillar(m, buf, 0, 0, 0, 1.15F * (0.4F + pFade), 16.0F, 0xB56BFF, 0.40F * pFade * alpha);
                for (int k = 0; k < 4; k++) {
                    double ang = k * Mth.HALF_PI + 0.5F;
                    float ox = Mth.cos((float) ang) * r * 0.55F;
                    float oz = Mth.sin((float) ang) * r * 0.55F;
                    pillar(m, buf, ox, 0, oz, 0.28F, 12.0F, 0xFFD97D, 0.55F * pFade * alpha);
                }
            }
            // Lv.3 导电残留电网弧线（半径 > 5.5 或 duration > 40）
            if (s.fadeAt - s.born > 40) {
                for (int k = 0; k < 6; k++) {
                    double ang = k * Mth.TWO_PI / 6.0 + age * 0.04;
                    float x1 = Mth.cos((float) ang) * r * 0.92F;
                    float z1 = Mth.sin((float) ang) * r * 0.92F;
                    float yWave = 0.35F + 0.35F * Mth.sin(age * 0.25F + k);
                    jaggedBolt(m, buf, 0, 1.2F, 0, x1, yWave, z1, 0.07F, 0x9F6BFF, 0.48F * alpha, age + k * 7);
                }
            }
        }
    }

    // ===== 3. 折线闪电链 =====
    private static void drawLightningChain(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        Vec3 relEnd = s.target.subtract(s.origin);
        float a = alpha * (1.0F - ease(lifeT));
        if (relEnd.lengthSqr() > 0.05) {
            jaggedBolt(m, buf, 0, 0, 0,
                    (float) relEnd.x, (float) relEnd.y, (float) relEnd.z,
                    0.10F, s.color != 0 ? s.color : 0xA88BFF, 0.85F * a, age);
            jaggedBolt(m, buf, 0, 0, 0,
                    (float) relEnd.x, (float) relEnd.y, (float) relEnd.z,
                    0.045F, 0xFFF8D0, 0.95F * a, age + 3.0F);
        }
        float burstR = s.radius * (0.35F + 0.75F * ease(lifeT));
        ring(m, buf, burstR, 0.15F, 0.14F, 28, 0xB894FF, 0.62F * a);
        ring(m, buf, burstR * 0.65F, 0.45F, 0.09F, 24, 0xFFF4B8, 0.55F * a);
    }

    // ===== 4. 深空黑洞（奇点 + 双层倾斜吸积盘 + 坍缩超新星） =====
    private static void drawBlackHole(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        float rVis = Math.min(4.2F, s.radius * 0.12F);
        boolean collapseBurst = lifeT > 0.88F;
        if (collapseBurst) {
            float bt = (lifeT - 0.88F) / 0.12F;
            float wave = rVis * (0.4F + 2.2F * ease(bt));
            float ba = (1.0F - bt) * alpha;
            ring(m, buf, wave, 0.0F, 0.25F, 48, 0xD296FF, 0.85F * ba);
            ring(m, buf, wave * 0.72F, 0.35F, 0.18F, 36, 0xFFF0A8, 0.75F * ba);
            return;
        }
        float pulse = 1.0F + 0.08F * Mth.sin(age * 0.22F);
        float coreR = rVis * 0.38F * pulse;
        // 旋转吸积盘双环
        tiltedRing(m, buf, rVis * 1.25F, 0.16F, 48, 0.34F, age * 0.16F, 0x9C55FF, 0.68F * alpha);
        tiltedRing(m, buf, rVis * 0.92F, 0.11F, 40, -0.28F, -age * 0.22F, 0xFFD980, 0.58F * alpha);
        // 奇点核心十字光环
        tiltedRing(m, buf, coreR, 0.14F, 24, 1.15F, age * 0.3F, 0x4A1B96, 0.85F * alpha);
        tiltedRing(m, buf, coreR, 0.14F, 24, -1.15F, -age * 0.3F, 0xC68BFF, 0.70F * alpha);
        // 内吸引力流环
        float inT = (age * 0.08F) % 1.0F;
        ring(m, buf, rVis * (1.5F - 1.1F * inT), 0.0F, 0.06F, 32, 0xBA82FF, (1.0F - inT) * 0.45F * alpha);
    }

    // ===== 5. 冰晶棱柱牢笼 + 霜链 =====
    private static void drawIcePrison(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        float h = Math.max(1.8F, s.radius * 1.4F);
        float r = Math.max(0.75F, s.radius * 0.65F);
        float open = ease(Math.min(1.0F, age / 8.0F));
        float curR = Mth.lerp(open, r * 1.6F, r);

        ring(m, buf, curR, 0.08F, 0.09F, 12, 0x73ECFF, 0.72F * alpha);
        ring(m, buf, curR * 0.9F, h, 0.08F, 12, 0xC6FCFF, 0.62F * alpha);
        for (int i = 0; i < 6; i++) {
            double ang = i * Mth.TWO_PI / 6.0 + age * 0.02F;
            float px = Mth.cos((float) ang) * curR;
            float pz = Mth.sin((float) ang) * curR;
            pillar(m, buf, px, 0.05F, pz, 0.075F, h * open, i % 2 == 0 ? 0x62DEFF : 0xD2FFFF, 0.62F * alpha);
        }
        // 起手前 12 tick 绘制霜链光束连接牵引出发点
        if (age < 14.0F) {
            Vec3 relFrom = s.target.subtract(s.origin);
            if (relFrom.lengthSqr() > 0.25) {
                float ca = (1.0F - age / 14.0F) * alpha;
                beam(m, buf, 0, h * 0.5F, 0, (float) relFrom.x, (float) relFrom.y, (float) relFrom.z,
                        0.09F, 0x8EF2FF, 0.78F * ca);
            }
        }
    }

    // ===== 6. 虚空星锚（10 秒原点旋转双锥晶体） =====
    private static void drawVoidAnchor(Matrix4f m, VertexConsumer buf, Scene s, float age, float alpha) {
        float rot = age * 0.11F;
        float bob = 0.14F * Mth.sin(age * 0.14F);
        ringRotated(m, buf, 1.1F, 0.06F, 0.09F, 32, 0x9B5CFF, 0.62F * alpha, rot);
        starPolygon(m, buf, 0.95F, 0.08F, 4, -rot * 1.4F, 0xF5B8FF, 0.70F * alpha);
        // 上下双锥柱体
        double[][] pts = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double a = rot + i * Mth.HALF_PI;
            pts[i][0] = Math.cos(a) * 0.36;
            pts[i][1] = Math.sin(a) * 0.36;
        }
        float midY = 1.15F + bob;
        float topY = 1.95F + bob;
        float botY = 0.35F + bob;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            quad(m, buf,
                    (float) pts[i][0], midY, (float) pts[i][1],
                    (float) pts[j][0], midY, (float) pts[j][1],
                    0, topY, 0, 0, topY, 0, 0xD28CFF, 0.72F * alpha);
            quad(m, buf,
                    (float) pts[j][0], midY, (float) pts[j][1],
                    (float) pts[i][0], midY, (float) pts[i][1],
                    0, botY, 0, 0, botY, 0, 0x7A48FF, 0.66F * alpha);
        }
    }

    // ===== 7. 虚空暗影交叉斩 / 残影引爆 =====
    private static void drawVoidSlash(Matrix4f m, VertexConsumer buf, Scene s, float lifeT, float alpha) {
        float a = alpha * (1.0F - ease(lifeT));
        float spread = s.radius * (0.45F + 0.65F * ease(lifeT));
        beam(m, buf, -spread, 0.25F, -spread * 0.4F, spread, 1.95F, spread * 0.4F, 0.14F, 0xD47BFF, 0.88F * a);
        beam(m, buf, -spread * 0.4F, 1.95F, spread, spread * 0.4F, 0.25F, -spread, 0.14F, 0xFFF0B8, 0.82F * a);
        ring(m, buf, spread * 1.1F, 0.12F, 0.15F, 32, 0x944DFF, 0.58F * a);
    }

    // ===== 8. 凤凰涅槃展翼火环与烈焰柱 =====
    private static void drawPhoenixWings(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        float expand = ease(Math.min(1.0F, lifeT * 1.6F));
        float a = alpha * (1.0F - 0.7F * lifeT);
        float pillarA = alpha * Math.max(0.0F, 1.0F - lifeT * 1.4F);
        pillar(m, buf, 0, 0, 0, 0.9F * (1.0F - 0.3F * lifeT), 10.0F, 0xFFB84A, 0.52F * pillarA);
        ring(m, buf, s.radius * (0.35F + 0.75F * expand), 0.10F, 0.20F, 48, 0xFF6B38, 0.65F * a);
        // 左右展翼弧形羽翼光带（沿 look 或 X 轴展开）
        Vec3 fwd = safeNormalize(s.target.lengthSqr() > 0.01 ? s.target : new Vec3(0, 0, 1));
        Vec3 right = safeNormalize(fwd.cross(new Vec3(0, 1, 0)));
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 6; f++) {
                float t = f / 5.0F;
                float span = (0.7F + t * 2.9F) * expand;
                float yBase = 1.0F + t * 1.65F + 0.18F * Mth.sin(age * 0.25F + f);
                float back = -0.35F - t * 0.9F;
                Vec3 root = right.scale(side * span * 0.35F).add(fwd.scale(back * 0.35F)).add(0, yBase * 0.75F, 0);
                Vec3 tip = right.scale(side * span).add(fwd.scale(back)).add(0, yBase + 0.45F, 0);
                beam(m, buf, (float) root.x, (float) root.y, (float) root.z,
                        (float) tip.x, (float) tip.y, (float) tip.z,
                        0.12F * (1.0F - 0.45F * t), f % 2 == 0 ? 0xFF9F40 : 0xFFE588, 0.78F * a);
            }
        }
    }

    // ===== 9. 凌空踏步冲击波 =====
    private static void drawAirShockwave(Matrix4f m, VertexConsumer buf, float radius, float lifeT, float alpha) {
        float t = ease(lifeT);
        float a = alpha * (1.0F - t);
        ring(m, buf, radius * (0.25F + 0.85F * t), 0.14F, 0.18F, 40, 0x76E7FF, 0.76F * a);
        ring(m, buf, radius * (0.15F + 0.65F * t), 0.48F, 0.12F, 32, 0xF5FFDF, 0.68F * a);
    }

    // ===== 10. 点石成矿炼金扫描环 =====
    private static void drawStoneToOre(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        float t = ease(lifeT);
        float a = alpha * (1.0F - 0.8F * lifeT);
        ringRotated(m, buf, s.radius * (0.20F + 0.85F * t), 0.15F, 0.16F, 48, 0x4BE8A6, 0.72F * a, age * 0.10F);
        starPolygon(m, buf, s.radius * 0.45F * (0.5F + 0.6F * t), 0.12F, 6, -age * 0.12F, 0xFFE985, 0.62F * a);
    }

    // ===== 11. 炼金脉冲炽金星阵 =====
    private static void drawAlchemyCircle(Matrix4f m, VertexConsumer buf, Scene s, float age, float lifeT, float alpha) {
        float a = alpha * (1.0F - ease(lifeT));
        float r = s.radius * (0.8F + 0.35F * ease(lifeT));
        ringRotated(m, buf, r, 0.10F, 0.10F, 36, 0xFF9C3D, 0.78F * a, age * 0.15F);
        starPolygon(m, buf, r * 0.88F, 0.12F, 5, -age * 0.18F, 0xFFEB91, 0.75F * a);
        pillar(m, buf, 0, 0, 0, 0.35F, 2.8F, 0xFFBE5C, 0.38F * a);
    }

    // ===== 12. 破军 6 星天降 3D 星刃穿刺 =====
    private static void drawResonanceBlade(Matrix4f m, VertexConsumer buf, Scene s, float lifeT, float alpha) {
        float fall = ease(Math.min(1.0F, lifeT * 2.2F));
        float a = alpha * (1.0F - Math.max(0.0F, (lifeT - 0.4F) / 0.6F));
        float tipY = Mth.lerp(fall, 6.5F, 0.05F);
        float topY = tipY + 4.4F;
        // 四棱立体星剑剑身
        float w = 0.36F;
        quad(m, buf, -w, topY, 0, w, topY, 0, 0, tipY, 0, 0, tipY, 0, 0xFF6B4A, 0.85F * a);
        quad(m, buf, 0, topY, -w, 0, topY, w, 0, tipY, 0, 0, tipY, 0, 0xFFD97D, 0.85F * a);
        if (fall > 0.75F) {
            float wave = s.radius * (fall - 0.75F) * 4.0F;
            ring(m, buf, wave, 0.12F, 0.16F, 32, 0xFF7B54, 0.70F * a);
        }
    }

    // ===== 通用半球扩散穹波 =====
    private static void drawDomeWave(Matrix4f m, VertexConsumer buf, float maxR, float lifeT, float alpha,
                                     int colPrimary, int colSecondary) {
        float t = ease(lifeT);
        float a = alpha * (1.0F - t);
        float curR = maxR * (0.20F + 0.85F * t);
        ring(m, buf, curR, 0.12F, 0.18F, 48, colPrimary, 0.72F * a);
        ring(m, buf, curR * 0.85F, Math.min(2.6F, curR * 0.28F), 0.12F, 40, colSecondary, 0.56F * a);
        ring(m, buf, curR * 0.60F, Math.min(4.2F, curR * 0.52F), 0.09F, 32, colPrimary, 0.42F * a);
    }

    // ===== 基础几何原语 =====
    private static Vec3 safeNormalize(Vec3 v) {
        return v.lengthSqr() > 1.0E-5 ? v.normalize() : new Vec3(0, 0, 1);
    }

    private static void ring(Matrix4f m, VertexConsumer buf, float r, float y, float w, int steps, int color, float alpha) {
        ringRotated(m, buf, r, y, w, steps, color, alpha, 0.0F);
    }

    private static void ringRotated(Matrix4f m, VertexConsumer buf, float r, float y, float w, int steps,
                                    int color, float alpha, float rot) {
        if (alpha <= 0.008F || w <= 0.004F) return;
        for (int i = 0; i < steps; i++) {
            float a = rot + i * Mth.TWO_PI / steps;
            float b = rot + (i + 1) * Mth.TWO_PI / steps;
            float ca = Mth.cos(a), sa = Mth.sin(a), cb = Mth.cos(b), sb = Mth.sin(b);
            float inner = Math.max(0, r - w), outer = r + w;
            quad(m, buf, ca * inner, y, sa * inner, ca * outer, y, sa * outer,
                    cb * outer, y, sb * outer, cb * inner, y, sb * inner, color, alpha);
        }
    }

    private static void tiltedRing(Matrix4f m, VertexConsumer buf, float r, float w, int steps,
                                   float tiltRad, float spinRad, int color, float alpha) {
        if (alpha <= 0.008F) return;
        float cosT = Mth.cos(tiltRad), sinT = Mth.sin(tiltRad);
        float cosS = Mth.cos(spinRad), sinS = Mth.sin(spinRad);
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps;
            float b = (i + 1) * Mth.TWO_PI / steps;
            float inner = Math.max(0, r - w), outer = r + w;
            float[] p1 = tiltPt(Mth.cos(a) * inner, Mth.sin(a) * inner, cosT, sinT, cosS, sinS);
            float[] p2 = tiltPt(Mth.cos(a) * outer, Mth.sin(a) * outer, cosT, sinT, cosS, sinS);
            float[] p3 = tiltPt(Mth.cos(b) * outer, Mth.sin(b) * outer, cosT, sinT, cosS, sinS);
            float[] p4 = tiltPt(Mth.cos(b) * inner, Mth.sin(b) * inner, cosT, sinT, cosS, sinS);
            quad(m, buf, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                    p3[0], p3[1], p3[2], p4[0], p4[1], p4[2], color, alpha);
        }
    }

    private static float[] tiltPt(float lx, float lz, float cosT, float sinT, float cosS, float sinS) {
        float y1 = -lz * sinT;
        float z1 = lz * cosT;
        float rx = lx * cosS - z1 * sinS;
        float rz = lx * sinS + z1 * cosS;
        return new float[]{rx, y1, rz};
    }

    private static void starPolygon(Matrix4f m, VertexConsumer buf, float r, float y, int points,
                                    float rot, int color, float alpha) {
        if (alpha <= 0.008F) return;
        for (int i = 0; i < points; i++) {
            float a = rot + i * Mth.TWO_PI / points;
            float b = rot + (i + 2) * Mth.TWO_PI / points;
            beam(m, buf, Mth.cos(a) * r, y, Mth.sin(a) * r,
                    Mth.cos(b) * r, y, Mth.sin(b) * r, 0.055F, color, alpha);
        }
    }

    private static void pillar(Matrix4f m, VertexConsumer buf, float cx, float baseY, float cz,
                               float halfW, float height, int color, float alpha) {
        if (alpha <= 0.008F || height <= 0.02F) return;
        float topY = baseY + height;
        quad(m, buf, cx - halfW, baseY, cz, cx + halfW, baseY, cz,
                cx + halfW, topY, cz, cx - halfW, topY, cz, color, alpha);
        quad(m, buf, cx, baseY, cz - halfW, cx, baseY, cz + halfW,
                cx, topY, cz + halfW, cx, topY, cz - halfW, color, alpha);
    }

    private static void jaggedBolt(Matrix4f m, VertexConsumer buf, float x1, float y1, float z1,
                                   float x2, float y2, float z2, float width, int color, float alpha, float seed) {
        int segs = 7;
        float px = x1, py = y1, pz = z1;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            float nx = Mth.lerp(t, x1, x2);
            float ny = Mth.lerp(t, y1, y2);
            float nz = Mth.lerp(t, z1, z2);
            if (i < segs) {
                float amp = 0.42F * Mth.sin(t * Mth.PI);
                nx += Mth.sin(seed * 2.7F + i * 5.3F) * amp;
                ny += Mth.cos(seed * 3.1F + i * 4.1F) * amp;
                nz += Mth.sin(seed * 1.9F + i * 6.7F) * amp;
            }
            beam(m, buf, px, py, pz, nx, ny, nz, width, color, alpha);
            px = nx; py = ny; pz = nz;
        }
    }

    private static void beam(Matrix4f m, VertexConsumer buf, float x1, float y1, float z1,
                             float x2, float y2, float z2, float halfW, int color, float alpha) {
        if (alpha <= 0.008F) return;
        Vec3 dir = new Vec3(x2 - x1, y2 - y1, z2 - z1);
        if (dir.lengthSqr() < 1.0E-5) return;
        Vec3 nDir = dir.normalize();
        Vec3 perpA = nDir.cross(new Vec3(0, 1, 0));
        if (perpA.lengthSqr() < 1.0E-4) perpA = nDir.cross(new Vec3(1, 0, 0));
        perpA = perpA.normalize().scale(halfW);
        Vec3 perpB = nDir.cross(perpA).normalize().scale(halfW);

        quad(m, buf,
                (float) (x1 - perpA.x), (float) (y1 - perpA.y), (float) (z1 - perpA.z),
                (float) (x1 + perpA.x), (float) (y1 + perpA.y), (float) (z1 + perpA.z),
                (float) (x2 + perpA.x), (float) (y2 + perpA.y), (float) (z2 + perpA.z),
                (float) (x2 - perpA.x), (float) (y2 - perpA.y), (float) (z2 - perpA.z), color, alpha);
        quad(m, buf,
                (float) (x1 - perpB.x), (float) (y1 - perpB.y), (float) (z1 - perpB.z),
                (float) (x1 + perpB.x), (float) (y1 + perpB.y), (float) (z1 + perpB.z),
                (float) (x2 + perpB.x), (float) (y2 + perpB.y), (float) (z2 + perpB.z),
                (float) (x2 - perpB.x), (float) (y2 - perpB.y), (float) (z2 - perpB.z), color, alpha);
    }

    private static void quad(Matrix4f m, VertexConsumer buf,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int tint, float alpha) {
        int rgba = (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24) | (tint & 0x00FFFFFF);
        vertex(m, buf, ax, ay, az, rgba);
        vertex(m, buf, bx, by, bz, rgba);
        vertex(m, buf, cx, cy, cz, rgba);
        vertex(m, buf, ax, ay, az, rgba);
        vertex(m, buf, cx, cy, cz, rgba);
        vertex(m, buf, dx, dy, dz, rgba);
    }

    private static void vertex(Matrix4f m, VertexConsumer buf, float x, float y, float z, int rgba) {
        buf.addVertex(m, x, y, z).setColor(rgba).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static float ease(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
