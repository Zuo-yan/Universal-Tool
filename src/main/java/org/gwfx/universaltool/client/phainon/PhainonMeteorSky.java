package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonMeteor.*;

/**
 * 「支柱·死星天裁」高空立体破云漩涡与炽金天幕裂隙渲染器。
 * <p>严格遵循两层分离 Pass：
 * <ol>
 *   <li><b>Pass 0A（Translucent 暗紫雷暴漩涡穹环）</b>：使用 Blockbench 3D 立体漏斗模型
 *       {@code phainon_meteor_sky_rift.obj} 叠加 {@code phainon_meteor_sky_vortex.png}，
 *       双层视差旋卷，即使在晴朗白昼也能瞬间把头顶天幕压暗成深紫焚世云海；</li>
 *   <li><b>Pass 0B（Additive 破云金红裂隙光喉）</b>：在云洞漩涡核心叠加炽金灼烧旋涡与天穹熔炉光焰，
 *       对应关键帧 03-06 陨石雨与死星巨陨破云而出的震撼天象。</li>
 * </ol></p>
 */
final class PhainonMeteorSky {
    private static final RenderType SKY_VORTEX = PhainonVfxRenderTypes.translucentTriangles(PhainonMeteorMeshes.SKY_VORTEX_TEX);
    private static final RenderType RIFT_GLOW = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.BLAST_DOME_TEX);
    private static final RenderType EYE_FLARE = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.SUNBURST_TEX);

    private PhainonMeteorSky() {}

    /**
     * 渲染落点正上方的立体破云漩涡与天幕裂隙。
     */
    static void render(PoseStack pose, BufferSource buffers, PhainonMeteorMeshes meshes, float age) {
        float open = smooth((age - 4.0F) / 34.0F);
        float close = 1.0F - smooth((age - IMPACT - 6.0F) / (DURATION - IMPACT - 6.0F));
        float cover = open * close;
        if (cover < 0.01F) {
            return;
        }

        float releaseProg = smooth((age - RELEASE + 12.0F) / 22.0F);
        float descent = smooth((age - GIANT) / (IMPACT - GIANT));
        float blast = Math.max(0.0F, age - IMPACT);
        float shockLift = smooth(blast / 14.0F);

        // 旋涡中心高度：蓄力至巨陨石出现时向下低垂压迫地面，命中后被冲击波向上掀开
        float baseY = 27.5F - descent * 4.2F + shockLift * 7.5F;
        float mainRadius = 38.0F + 28.0F * open + 12.0F * shockLift;
        float yScale = 23.0F + 5.0F * descent;

        // Pass 0A：暗紫雷暴漩涡主体（双层视差漏斗，白昼也能强力压暗苍穹）
        var vortexBuf = buffers.getBuffer(SKY_VORTEX);

        // 主低空漏斗漩涡云墙
        pose.pushPose();
        pose.translate(0.0F, baseY, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(age * 0.52F + descent * 28.0F));
        pose.scale(mainRadius, yScale, mainRadius);
        float rTint = Mth.lerp(descent, 0.84F, 1.0F);
        float gTint = Mth.lerp(descent, 0.72F, 0.88F) + 0.12F * smooth((4.0F - blast) / 4.0F) * (blast > 0 ? 1.0F : 0.0F);
        float bTint = Mth.lerp(descent, 0.94F, 0.72F);
        meshes.skyRift().renderAdditive(pose, vortexBuf, rTint, Mth.clamp(gTint, 0.0F, 1.0F), bTint, cover * 0.96F, 0.0F, 0.0F);
        pose.popPose();

        // 外层高空反旋暗云冠（增加立体云壁纵深与外围压迫感）
        pose.pushPose();
        pose.translate(0.0F, baseY + 4.8F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(-age * 0.28F - descent * 14.0F + 38.0F));
        float outerRadius = mainRadius * 1.28F;
        pose.scale(outerRadius, yScale * 1.18F, outerRadius);
        meshes.skyRift().renderAdditive(pose, vortexBuf, 0.62F + 0.25F * descent, 0.48F + 0.18F * descent, 0.78F - 0.16F * descent, cover * 0.66F, 0.0F, 0.0F);
        pose.popPose();

        buffers.endBatch(SKY_VORTEX);

        // Pass 0B：破洞漩涡内部的炽金烈焰天幕裂隙（关键帧 03-06 中心如熔炉般灼亮）
        float riftEnergy = releaseProg * (1.0F - smooth((age - IMPACT - 4.0F) / 18.0F));
        if (riftEnergy > 0.015F) {
            float pulse = 0.88F + 0.12F * Mth.sin(age * 0.36F);
            var riftBuf = buffers.getBuffer(RIFT_GLOW);

            // 倒扣在破云漩涡口的炽金火焰漏斗喉部（利用 blastDome 倒置贴合破洞内壁）
            pose.pushPose();
            pose.translate(0.0F, baseY - 1.5F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(-age * 1.65F));
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            float throatR = (10.5F + 10.5F * releaseProg + 5.5F * descent) * (0.85F + 0.15F * pulse);
            pose.scale(throatR, 10.5F + 4.5F * descent, throatR);
            meshes.blastDome().renderAdditive(
                    pose, riftBuf,
                    1.0F, 0.72F + 0.16F * descent, 0.26F + 0.12F * descent,
                    riftEnergy * 0.68F * pulse,
                    age * 0.008F, -age * 0.015F
            );
            pose.popPose();

            // 外环炽橙翻涌破云火唇（稍宽略扁，照亮旋涡内圈云底）
            pose.pushPose();
            pose.translate(0.0F, baseY - 3.0F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(age * 1.15F + 45.0F));
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            float rimR = throatR * 1.52F;
            pose.scale(rimR, 4.8F, rimR);
            meshes.blastDome().renderAdditive(
                    pose, riftBuf,
                    1.0F, 0.50F + 0.18F * descent, 0.14F,
                    riftEnergy * 0.46F,
                    -age * 0.006F, -age * 0.010F
            );
            pose.popPose();

            buffers.endBatch(RIFT_GLOW);

            // 破云中心高空日轮光核（关键帧 03-04 抬头仰望漩涡正中的耀眼炽金太阳）
            var models = PhainonVfxModelData.getOrLoad();
            if (models != null) {
                var eyeBuf = buffers.getBuffer(EYE_FLARE);
                pose.pushPose();
                pose.translate(0.0F, baseY + 0.6F, 0.0F);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(age * 1.8F));
                float eyeScale = (6.8F + 5.2F * releaseProg + 3.0F * descent) * pulse;
                pose.scale(eyeScale, eyeScale, eyeScale * 0.35F);
                models.sunburstWheel().renderAdditive(
                        pose, eyeBuf,
                        1.0F, 0.90F, 0.54F,
                        riftEnergy * (0.55F + 0.30F * descent),
                        0.0F, 0.0F
                );
                pose.popPose();
                buffers.endBatch(EYE_FLARE);
            }
        }
    }
}
