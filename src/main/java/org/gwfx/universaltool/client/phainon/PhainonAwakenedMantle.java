package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * 白厄终结技双翼与神环 3D 渲染器：
 * <ul>
 *   <li>第一幕（帧 02）：背后展开圣洁白蓝对称天使光翼（{@link #drawSeraphWings}）。</li>
 *   <li>第三幕与觉醒态（帧 08）：背后展开「左炽烈黄金圣翼 + 右黑曜石金焰残翼」不对称神魔双翼，
 *       右翼通过实体暗紫剪影层与 Additive 金焰叠亮层双 Pass 混绘，还原黑金燃烧质感。</li>
 * </ul>
 */
final class PhainonAwakenedMantle {

    private PhainonAwakenedMantle() {
    }

    /**
     * 第一幕（0 ~ 44 ticks）：在玩家背部对称展开圣洁白蓝色 3D 光翼。
     */
    static void drawSeraphWings(PoseStack pose, VertexConsumer buffer,
                                PhainonUltimateMeshes meshes, float age, float opacity) {
        if (age < 1.0F || age > 44.0F || opacity <= 0.01F) {
            return;
        }
        float open = easeOutBack(Mth.clamp(age / 13.0F, 0.0F, 1.0F));
        float close = age < 32.0F ? 1.0F : 1.0F - smooth(Mth.clamp((age - 32.0F) / 12.0F, 0.0F, 1.0F));
        float alpha = opacity * close * Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            return;
        }
        float flap = Mth.sin(age * 0.20F) * 5.5F;
        float spanScale = 0.72F + 0.42F * open;

        // 镜像绘制左右白蓝圣翼（+X 为玩家左侧，-X 为玩家右侧）
        for (int side : new int[]{1, -1}) {
            pose.pushPose();
            pose.translate(side * 0.16F, 1.32F, -0.22F);
            pose.scale(side * spanScale, spanScale, spanScale);
            pose.mulPose(Axis.YP.rotationDegrees(-16.0F + flap));
            pose.mulPose(Axis.ZP.rotationDegrees(14.0F + flap * 0.45F));
            meshes.seraphWing().renderAdditive(pose, buffer, 0.90F, 0.96F, 1.0F, alpha * 0.96F, 0.0F, 0.0F);

            // 微偏移纯白圣光内翼叠亮
            pose.translate(0.06F, 0.06F, -0.05F);
            pose.scale(0.88F, 0.88F, 0.88F);
            meshes.seraphWing().renderAdditive(pose, buffer, 1.0F, 0.98F, 0.92F, alpha * 0.52F, 0.0F, 0.0F);
            pose.popPose();
        }
    }

    /**
     * 第三幕及觉醒期：绘制右翼「黑曜石暗紫魔翼」实体剪影层（translucentTriangles Pass）。
     * <p>面向正面相机时位于画面右侧（玩家左肩 +X 还是右肩 -X：在正面视角下，角色朝向 +Z，
     * 画面左侧对应角色右肩 -X，画面右侧对应角色左肩 +X）。因此将黄金圣翼放在角色右侧（-X，画面左），
     * 黑曜石残翼放在角色左侧（+X，画面右），与参考图 08_t192 构图严格对齐！</p>
     */
    static void drawObsidianWingBody(PoseStack pose, VertexConsumer buffer,
                                     PhainonUltimateMeshes meshes, float burstAge, float opacity) {
        if (burstAge <= 0.0F || opacity <= 0.01F) {
            return;
        }
        float open = easeOutBack(Mth.clamp(burstAge / 11.0F, 0.0F, 1.0F));
        float settle = 1.0F - smooth(Mth.clamp((burstAge - 26.0F) / 24.0F, 0.0F, 1.0F)) * 0.22F;
        float scale = (0.35F + 0.82F * open) * settle;
        float flap = Mth.sin(burstAge * 0.11F) * 4.2F;

        pose.pushPose();
        pose.translate(0.18F, 1.36F, -0.24F);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(-14.0F + flap));
        pose.mulPose(Axis.ZP.rotationDegrees(10.0F + flap * 0.5F));
        meshes.wingObsidian().renderAdditive(pose, buffer, 1.0F, 1.0F, 1.0F, 0.96F * opacity, 0.0F, 0.0F);
        pose.popPose();
    }

    /**
     * 第三幕及觉醒期：绘制右翼「炽白金焰羽缘」Additive 叠亮层。
     */
    static void drawObsidianWingFlameGlow(PoseStack pose, VertexConsumer buffer,
                                          PhainonUltimateMeshes meshes, float burstAge, float opacity) {
        if (burstAge <= 0.0F || opacity <= 0.01F) {
            return;
        }
        float open = easeOutBack(Mth.clamp(burstAge / 11.0F, 0.0F, 1.0F));
        float settle = 1.0F - smooth(Mth.clamp((burstAge - 26.0F) / 24.0F, 0.0F, 1.0F)) * 0.22F;
        float scale = (0.35F + 0.82F * open) * settle;
        float flap = Mth.sin(burstAge * 0.11F) * 4.2F;
        float pulse = 0.86F + 0.14F * Mth.sin(burstAge * 0.26F);

        pose.pushPose();
        pose.translate(0.18F, 1.36F, -0.22F);
        pose.scale(scale * 1.015F, scale * 1.015F, scale * 1.015F);
        pose.mulPose(Axis.YP.rotationDegrees(-14.0F + flap));
        pose.mulPose(Axis.ZP.rotationDegrees(10.0F + flap * 0.5F));
        meshes.wingObsidian().renderAdditive(pose, buffer, 1.0F, 0.90F, 0.56F, 0.95F * pulse * opacity, 0.0F, 0.0F);
        pose.popPose();
    }

    /**
     * 第三幕及觉醒期：绘制左翼「炽烈黄金神翼」（位于角色右肩 -X，正面画面左侧，与帧 08 构图完全一致）。
     */
    static void drawGoldWingGlow(PoseStack pose, VertexConsumer buffer,
                                 PhainonUltimateMeshes meshes, float burstAge, float opacity) {
        if (burstAge <= 0.0F || opacity <= 0.01F) {
            return;
        }
        float open = easeOutBack(Mth.clamp(burstAge / 10.0F, 0.0F, 1.0F));
        float settle = 1.0F - smooth(Mth.clamp((burstAge - 26.0F) / 24.0F, 0.0F, 1.0F)) * 0.22F;
        float scale = (0.35F + 0.84F * open) * settle;
        float flap = Mth.sin(burstAge * 0.11F + 0.6F) * 4.5F;
        float pulse = 0.88F + 0.12F * Mth.sin(burstAge * 0.22F);

        pose.pushPose();
        pose.translate(-0.18F, 1.34F, -0.24F);
        // 沿 X 轴镜像使黄金神翼向 -X（正面画面左侧）优雅延展
        pose.scale(-scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(-12.0F + flap));
        pose.mulPose(Axis.ZP.rotationDegrees(8.0F + flap * 0.45F));
        meshes.wingGold().renderAdditive(pose, buffer, 1.0F, 0.90F, 0.46F, 0.96F * opacity, 0.0F, 0.0F);

        // 第二层炽白金核叠亮
        pose.translate(0.05F, 0.05F, 0.04F);
        pose.scale(0.95F, 0.95F, 0.95F);
        meshes.wingGold().renderAdditive(pose, buffer, 1.0F, 0.97F, 0.78F, 0.62F * pulse * opacity, 0.0F, 0.0F);
        pose.popPose();
    }

    /**
     * 第三幕与觉醒期间：紧贴头后缓慢旋转的烈阳圣环（爆发期恢宏巨大，常驻期收束为脑后神环）。
     */
    static void drawAwakenedHeadHalo(PoseStack pose, VertexConsumer buffer,
                                     PhainonVfxModelData vfxModels, float burstAge, float opacity) {
        if (burstAge <= 0.0F || opacity <= 0.01F || vfxModels == null) {
            return;
        }
        float burst = easeOutBack(Mth.clamp(burstAge / 7.0F, 0.0F, 1.0F));
        float settle = smooth(Mth.clamp((burstAge - 22.0F) / 24.0F, 0.0F, 1.0F));
        float radius = Mth.lerp(settle, 2.55F * burst, 0.92F);
        float alpha = Mth.lerp(settle, 0.96F, 0.68F) * opacity;

        pose.pushPose();
        pose.translate(0.0F, 1.88F, -0.30F);

        // 主烈金圣日轮
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(burstAge * 2.8F));
        pose.scale(radius, radius, radius * 0.55F);
        vfxModels.sunburstWheel().renderAdditive(pose, buffer, 1.0F, 0.84F, 0.34F, alpha, 0.0F, 0.0F);
        pose.popPose();

        // 同轴反向炙白烈阳内核
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.04F);
        pose.mulPose(Axis.ZP.rotationDegrees(-burstAge * 4.4F));
        float inner = radius * 0.74F;
        pose.scale(inner, inner, inner * 0.45F);
        vfxModels.sunburstWheel().renderAdditive(pose, buffer, 1.0F, 0.97F, 0.80F, alpha * 0.85F, 0.0F, 0.0F);
        pose.popPose();

        pose.popPose();
    }

    private static float smooth(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float xm1 = Mth.clamp(x, 0.0F, 1.0F) - 1.0F;
        return 1.0F + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
    }
}
