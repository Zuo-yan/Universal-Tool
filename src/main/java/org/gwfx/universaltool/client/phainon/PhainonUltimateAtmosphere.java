package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;
import org.joml.Matrix4f;

/**
 * 白厄终结技环境氛围与标志性 3D 场景元素渲染器：
 * <ul>
 *   <li>世界空间暗化苍穹穹顶（白天开大也能瞬间天地失色，压暗背景衬托焚世金光）；</li>
 *   <li>32 块不透明受光黑褐碎石风暴（第二幕绕金球旋舞、第三幕破茧向外炸散）；</li>
 *   <li>12 枚黄金泰坦神徽纹章（4×3 UV 图集面向相机高低错落悬浮公转，还原帧 08）。</li>
 * </ul>
 */
final class PhainonUltimateAtmosphere {
    static final ResourceLocation ROCK = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/misc/phainon_debris_rock.png");
    private static final float GOLDEN_ANGLE = 2.399963F;
    private static final int ROCK_COUNT = 32;
    private static final int RUNE_COUNT = 12;

    private PhainonUltimateAtmosphere() {
    }

    /**
     * Pass 0：绘制包裹玩家周围 26 格的暗紫金红焚世苍穹球穹，营造领域暗化压迫感。
     */
    static void renderSkyVeilDome(PoseStack pose, VertexConsumer buffer,
                                  PhainonUltimateMeshes meshes, Vec3 burstOrigin, float age) {
        if (age < 26.0F || age > PhainonUltimateHandler.OPENING_TICKS + 16.0F) {
            return;
        }
        float enter = ease((age - 26.0F) / 16.0F);
        float exit = 1.0F - ease((age - PhainonUltimateHandler.OPENING_TICKS + 4.0F) / 20.0F);
        float alpha = enter * exit * 0.78F;
        if (alpha <= 0.01F) {
            return;
        }

        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float postAwaken = age >= awaken ? ease((age - awaken) / 10.0F) : 0.0F;
        // 第二幕偏向暗紫黑曜石，破茧后染上炙热金红霞光
        float r = Mth.lerp(postAwaken, 0.85F, 1.0F);
        float g = Mth.lerp(postAwaken, 0.75F, 0.88F);
        float b = Mth.lerp(postAwaken, 0.95F, 0.72F);

        pose.pushPose();
        pose.translate(burstOrigin.x, burstOrigin.y + 1.2D, burstOrigin.z);
        pose.mulPose(Axis.YP.rotationDegrees(age * 0.45F));
        pose.scale(26.0F, 26.0F, 26.0F);
        meshes.skyDome().renderAdditive(pose, buffer, r, g, b, alpha, age * 0.002F, 0.0F);
        pose.popPose();
    }

    /**
     * Pass 1：绘制 32 块不透明受光黑褐玄武岩碎石（第二幕绕金球旋舞 + 第三幕破茧爆射）。
     */
    static void renderDebrisStorm(PoseStack pose, VertexConsumer buffer,
                                  PhainonVfxModelData.BakedMesh rockMesh,
                                  Vec3 burstOrigin, float age, boolean firstPerson) {
        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float released = age - awaken;
        if (age < 28.0F || released > 38.0F) {
            return;
        }
        float gather = ease((age - 28.0F) / 14.0F);
        float crush = ease((age - (awaken - 12.0F)) / 12.0F);
        float lifetime = Mth.clamp(released / 38.0F, 0.0F, 1.0F);
        float blast = 1.0F - (1.0F - lifetime) * (1.0F - lifetime);

        for (int i = 0; i < ROCK_COUNT; i++) {
            float speed = 0.65F + (i % 6) * 0.16F;
            float baseAngle = GOLDEN_ANGLE * i;
            float orbitAngle = baseAngle + Math.min(age, awaken) * (0.048F + 0.015F * (i % 3)) + crush * 0.55F;
            float orbitRadius = Mth.lerp(crush, 3.6F + (i % 5) * 0.85F, 2.3F + (i % 4) * 0.42F)
                    + blast * (6.8F * speed);

            float verticalWave = ((i % 7) - 2.6F) * 0.68F + Mth.sin(age * 0.09F + i) * 0.25F;
            float height = 1.9F + verticalWave + blast * (1.8F + (i % 4) * 0.45F) - 3.2F * lifetime * lifetime;
            if (firstPerson) {
                height = Math.min(height, 0.95F);
            }

            float fadeOut = 1.0F - ease((lifetime - 0.58F) / 0.42F);
            float scale = (0.28F + (i % 5) * 0.12F) * gather * fadeOut;
            if (scale < 0.02F || height < 0.08F) {
                continue;
            }

            pose.pushPose();
            pose.translate(
                    burstOrigin.x + Mth.cos(orbitAngle) * orbitRadius,
                    burstOrigin.y + height,
                    burstOrigin.z + Mth.sin(orbitAngle) * orbitRadius
            );
            pose.mulPose(Axis.YP.rotationDegrees(i * 29.0F + age * (4.2F + speed * 2.0F)));
            pose.mulPose(Axis.XP.rotationDegrees(i * 19.0F + age * (i % 2 == 0 ? 5.8F : -4.4F)));
            pose.mulPose(Axis.ZP.rotationDegrees(age * 3.1F + i * 13.0F));
            pose.scale(scale, scale * (0.85F + (i % 3) * 0.22F), scale);

            int shade = 52 + (i % 4) * 14;
            rockMesh.renderSolid(pose, buffer, LightTexture.FULL_BRIGHT, shade + 12, shade, shade + 6);
            pose.popPose();
        }
    }

    /**
     * 第三幕（82 ~ 142 ticks）：围绕全身悬浮的 12 枚黄金泰坦神徽纹章（4×3 图集，面向相机，还原帧 08）。
     */
    static void renderTitanRunes(PoseStack pose, VertexConsumer buffer, Camera camera,
                                 Vec3 centerPos, float burstAge, boolean firstPerson) {
        if (burstAge < 0.0F || burstAge > 62.0F) {
            return;
        }
        float fadeIn = ease(burstAge / 7.0F);
        float fadeOut = 1.0F - ease((burstAge - 34.0F) / 28.0F);
        float alpha = fadeIn * fadeOut * (firstPerson ? 0.45F : 0.96F);
        if (alpha <= 0.01F) {
            return;
        }

        for (int i = 0; i < RUNE_COUNT; i++) {
            float appear = ease((burstAge - i * 0.45F) / 6.0F);
            if (appear <= 0.01F) {
                continue;
            }
            float angle = (Mth.TWO_PI * i / RUNE_COUNT) + burstAge * 0.018F;
            float ringRadius = (3.4F + (i % 3) * 0.95F) * (0.65F + 0.35F * appear);
            float elevation = 1.65F + Mth.sin(i * 1.35F) * 2.15F + Mth.sin(burstAge * 0.10F + i) * 0.16F;
            if (firstPerson && Math.abs(elevation - 1.6F) < 0.8F) {
                elevation += 1.4F;
            }

            float rx = (float) centerPos.x + Mth.cos(angle) * ringRadius;
            float ry = (float) centerPos.y + elevation;
            float rz = (float) centerPos.z + Mth.sin(angle) * ringRadius;

            float pulse = 0.85F + 0.15F * Mth.sin(burstAge * 0.25F + i * 1.1F);
            float halfSize = (0.48F + (i % 3) * 0.10F) * appear;

            int col = i % 4;
            int row = i / 4;
            float u0 = col / 4.0F;
            float u1 = (col + 1) / 4.0F;
            float v0 = row / 3.0F;
            float v1 = (row + 1) / 3.0F;

            pose.pushPose();
            pose.translate(rx, ry, rz);
            pose.mulPose(camera.rotation());
            drawBillboardQuad(pose.last().pose(), buffer, halfSize, u0, v0, u1, v1,
                    1.0F, 0.90F, 0.52F, alpha * appear * pulse);
            pose.popPose();
        }
    }

    /**
     * 绘制面向相机的炽金/金红烈阳背光云晕，强化帧 08 头后爆裂光海。
     */
    static void renderBacklightFlare(PoseStack pose, VertexConsumer buffer, Camera camera,
                                     Vec3 playerPos, float age, boolean firstPerson) {
        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float burst = age - awaken;
        if (burst < -4.0F || burst > 44.0F) {
            return;
        }
        float intensity = burst < 0.0F
                ? ease((burst + 4.0F) / 4.0F) * 0.45F
                : ease(burst / 3.0F) * (1.0F - ease((burst - 14.0F) / 30.0F));
        float alpha = intensity * (firstPerson ? 0.24F : 0.88F);
        if (alpha <= 0.01F) {
            return;
        }

        Vec3 backShift = new Vec3(camera.getLookVector()).scale(firstPerson ? 2.4D : 1.15D);
        float radius = (firstPerson ? 2.4F : 4.6F) * (0.70F + 0.45F * ease(Math.max(0.0F, burst) / 8.0F));

        pose.pushPose();
        pose.translate(playerPos.x + backShift.x, playerPos.y + 1.85D + backShift.y, playerPos.z + backShift.z);
        pose.mulPose(camera.rotation());
        pose.mulPose(Axis.ZP.rotationDegrees(age * 1.4F));
        drawBillboardQuad(pose.last().pose(), buffer, radius, 0.0F, 0.0F, 1.0F, 1.0F,
                1.0F, 0.72F, 0.25F, alpha * 0.75F);
        pose.mulPose(Axis.ZP.rotationDegrees(-age * 2.8F + 24.0F));
        drawBillboardQuad(pose.last().pose(), buffer, radius * 0.62F, 0.0F, 0.0F, 1.0F, 1.0F,
                1.0F, 0.96F, 0.78F, alpha * 0.90F);
        pose.popPose();
    }

    private static void drawBillboardQuad(Matrix4f mat, VertexConsumer buffer, float half,
                                          float u0, float v0, float u1, float v1,
                                          float r, float g, float b, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int ri = (int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = (int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        int color = (a << 24) | (ri << 16) | (gi << 8) | bi;
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        buffer.addVertex(mat, -half, -half, 0.0F).setColor(color).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(mat, half, -half, 0.0F).setColor(color).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(mat, half, half, 0.0F).setColor(color).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);

        buffer.addVertex(mat, -half, -half, 0.0F).setColor(color).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(mat, half, half, 0.0F).setColor(color).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        buffer.addVertex(mat, -half, half, 0.0F).setColor(color).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
    }

    private static float ease(float x) {
        return PhainonUltimateEffectRenderer.ease(x);
    }
}
