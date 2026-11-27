package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonCounter.IMPACT;

/**
 * 白厄觉醒弹反「灾厄·弑魂焚诏」反击斩落（RIPOSTE）阶段的多 Pass 电影级 3D 视觉渲染器。
 * <p>完整串联五大层次：</p>
 * <ol>
 *   <li>Pass 1（Solid）：32 块不透明黑曜石/玄武岩碎石自裂谷两侧与爆心炸裂抛飞；</li>
 *   <li>Pass 2（Translucent）：黑曜石锻铁巨型裁决巨剑实体剪影凌空显形并雷霆竖劈；</li>
 *   <li>Pass 3A（Additive）：同轮廓炽金/熔红巨剑高光叠加层 + 2 道残影剑锋；</li>
 *   <li>Pass 3B（Additive）：3D 立体竖劈裂空刀浪壳体（含高速 UV 流动）+ 地面炽金熔岩裂痕；</li>
 *   <li>Pass 3C &amp; 3D（Additive）：裂地火山熔岩喷涌光锥（双层反卷流光）+ 剑柄烈日法环 + 地面辐射火焰冲击环。</li>
 * </ol>
 */
final class PhainonCounterStrikeVisuals {
    /** 巨剑落地插地后的残影淡出窗口（tick）。 */
    private static final float SWORD_END_AGE = IMPACT + 17.0F;
    /** 地面裂谷与熔岩喷涌的持续时长（tick）。 */
    private static final float CRATER_DURATION = 32.0F;
    /** 飞溅黑曜石碎石块数量（沿裂谷两侧 + 放射环）。 */
    private static final int DEBRIS_COUNT = 32;

    private PhainonCounterStrikeVisuals() {
    }

    /**
     * 单次反击打击的起止空间轨迹与左右惯用手标记。
     */
    record Strike(Vec3 shoulder, Vec3 hit, float yaw, int side) {

        /**
         * 计算巨剑在给定时间 {@code age} 下的世界空间变换（原点落在剑尖，+Y 指向剑柄方向，方便精准劈落至命中点）。
         *
         * @return 巨剑当前不透明度权重（0 表示已完全消散）
         */
        float mountSword(PoseStack pose, Vec3 camera, float age) {
            if (age < 2.0F || age >= SWORD_END_AGE) {
                return 0.0F;
            }
            float appear = smooth((age - 2.0F) / 10.0F);
            float gather = smooth((age - 4.0F) / 26.0F);
            float swingRaw = Mth.clamp((age - 30.0F) / (IMPACT - 30.0F), 0.0F, 1.0F);
            // 三次方爆发加速下劈，末端极具斩落重击感
            float cut = swingRaw * swingRaw * swingRaw;
            float fade = 1.0F - smooth((age - IMPACT - 4.0F) / 13.0F);
            float alpha = appear * fade;
            if (alpha <= 0.01F) {
                return 0.0F;
            }

            // 蓄势悬浮点位于施法者与命中点连线上方，下劈后剑尖精准贯入 hit 点
            Vec3 hoverBase = shoulder.lerp(hit, 0.36D + 0.14D * gather);
            Vec3 tipWorld = hoverBase.lerp(hit, cut);
            double liftY = (2.8D + 5.8D * gather) * (1.0D - cut) + 0.06D;
            // 落地后微微下沉形成巨剑深嵌地面的重压质感
            double embedY = cut >= 0.99F ? -0.35D * smooth((age - IMPACT) / 4.0F) : 0.0D;

            Vec3 rel = tipWorld.subtract(camera);
            pose.translate(rel.x, rel.y + liftY + embedY, rel.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            // 蓄势阶段向持剑侧扬起拉满张力，下劈瞬间凌厉收归正中略微斜劈
            pose.translate(side * (1.0F - cut) * (1.15F + 2.45F * gather), 0.0F, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(side * Mth.lerp(cut, 92.0F - 24.0F * gather, 14.0F)));
            // 蓄势时巨剑高扬后仰蓄满劈势，下劈时自施法者高空斜贯斩入地面（契合 Preview_2_t2.5s 斩落角度）
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, -62.0F + 16.0F * gather, -18.0F)));

            // 巨剑宏伟尺度（基准模型高 5.1 格 -> 满蓄 2.3x 达 11.7 格裁决巨剑！）
            float scale = Mth.lerp(gather, 0.95F, 2.30F) * (0.85F + 0.15F * appear) * (0.90F + 0.10F * fade);
            pose.scale(scale, scale, scale);
            return alpha;
        }
    }

    /**
     * Pass 2：绘制巨型黑曜石锻铁裁决巨剑的暗色实体剪影（Translucent），在白天晴空下也能压出凌厉暗金轮廓。
     */
    static void renderGreatswordBody(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                     Strike strike, Vec3 camera, float age, boolean firstPerson) {
        if (age < 2.0F || age >= SWORD_END_AGE) {
            return;
        }
        pose.pushPose();
        float alpha = strike.mountSword(pose, camera, age);
        if (alpha > 0.01F) {
            // 将模型剑尖（y = GREATSWORD_TIP_Y）翻折对齐至局部原点，使剑柄朝上、剑尖向下劈入地面
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            pose.translate(0.0F, -PhainonCounterMeshes.GREATSWORD_TIP_Y, 0.0F);
            float bodyAlpha = alpha * (firstPerson ? 0.78F : 0.96F);
            meshes.greatsword().renderAdditive(pose, buffer, 1.0F, 1.0F, 1.0F, bodyAlpha, 0.0F, 0.0F);
        }
        pose.popPose();
    }

    /**
     * Pass 3A：同网格放大 1.018x 叠绘炽金/熔红巨剑发光层，并在高速下劈期间拉出 2 道残影剑锋。
     */
    static void renderGreatswordGlow(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                     Strike strike, Vec3 camera, float age, boolean firstPerson) {
        if (age < 2.0F || age >= SWORD_END_AGE) {
            return;
        }
        float fpScale = firstPerson ? 0.76F : 1.0F;
        // 0 = 主体高光层；1..2 = 雷霆下劈高速残影
        for (int trail = 0; trail <= 2; trail++) {
            if (trail > 0 && (age < 32.0F || age > IMPACT + 5.0F)) {
                continue;
            }
            float sampleAge = age - trail * 1.15F;
            pose.pushPose();
            float alpha = strike.mountSword(pose, camera, sampleAge);
            if (alpha > 0.01F) {
                pose.mulPose(Axis.XP.rotationDegrees(180.0F));
                pose.translate(0.0F, -PhainonCounterMeshes.GREATSWORD_TIP_Y, trail == 0 ? 0.015F : 0.0F);
                float expand = trail == 0 ? 1.018F : 1.04F + trail * 0.04F;
                pose.scale(expand, 1.012F, expand);

                float pulse = 0.88F + 0.12F * Mth.sin(age * 0.75F);
                float layerAlpha = alpha * fpScale * (trail == 0 ? 0.98F * pulse : 0.38F / trail);
                float g = trail == 0 ? 0.95F : 0.56F;
                float b = trail == 0 ? 0.72F : 0.22F;
                meshes.greatsword().renderAdditive(pose, buffer, 1.0F, g, b, layerAlpha, 0.0F, 0.0F);
            }
            pose.popPose();
        }
    }

    /**
     * Pass 3B：绘制 3D 竖直重劈裂空刀浪弧面壳体 + 落地炽金熔岩裂谷光痕与碎石缝隙火光。
     */
    static void renderCleaveAndFissures(PoseStack pose, VertexConsumer buffer,
                                        PhainonCounterMeshes counterMeshes, PhainonVfxModelData sharedModels,
                                        Strike strike, Vec3 camera, float age, boolean firstPerson) {
        float fpAtten = firstPerson ? 0.72F : 1.0F;

        // 1. 雷霆下劈 3D 竖直斩击弧面壳体（age: 31..IMPACT+14）
        if (age >= 31.0F && age <= IMPACT + 14.0F) {
            float t = Mth.clamp((age - 31.0F) / (IMPACT + 14.0F - 31.0F), 0.0F, 1.0F);
            float sweepIn = easeOutCubic(Mth.clamp((age - 31.0F) / 11.0F, 0.0F, 1.0F));
            float fade = t < 0.55F ? 1.0F : 1.0F - smooth((t - 0.55F) / 0.45F);
            if (fade > 0.01F) {
                Vec3 mid = strike.shoulder.lerp(strike.hit, 0.68D).subtract(camera);
                float arcScale = Mth.lerp(sweepIn, 3.6F, 7.8F);
                float widthScale = Mth.lerp(sweepIn, 2.2F, 4.4F);
                float uvScroll = -t * 0.42F;

                pose.pushPose();
                pose.translate(mid.x, mid.y + 3.2D, mid.z);
                pose.mulPose(Axis.YP.rotationDegrees(-strike.yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(strike.side * 12.0F));
                // 随下劈略微前旋，模拟刀浪从苍穹劈落砸入大地的动势
                pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(sweepIn, -28.0F, 14.0F)));

                // 外层熔金炽红主斩弧
                pose.pushPose();
                pose.scale(widthScale, arcScale, arcScale);
                counterMeshes.cleaveArc().renderAdditive(pose, buffer, 1.0F, 0.86F, 0.52F, 0.94F * fade * fpAtten, uvScroll, 0.0F);
                pose.popPose();

                // 内层超前炽白锋刃二次叠亮
                pose.pushPose();
                pose.translate(0.0F, -0.10F, 0.22F);
                pose.scale(widthScale * 0.62F, arcScale * 1.05F, arcScale * 1.05F);
                counterMeshes.cleaveArc().renderAdditive(pose, buffer, 1.0F, 0.98F, 0.86F, 0.74F * fade * fpAtten, uvScroll * 1.35F, 0.0F);
                pose.popPose();

                pose.popPose();
            }
        }

        // 2. 落地裂地灼热岩浆剑痕与碎裂沟壑火纹（age: IMPACT..IMPACT+CRATER_DURATION）
        float elapsed = age - IMPACT;
        if (elapsed >= 0.0F && elapsed < CRATER_DURATION) {
            float burst = easeOutCubic(Mth.clamp((elapsed + 0.8F) / 5.5F, 0.0F, 1.0F));
            float fade = 1.0F - smooth((elapsed - 6.0F) / (CRATER_DURATION - 6.0F));
            if (fade > 0.01F) {
                Vec3 offset = strike.hit.subtract(camera);
                pose.pushPose();
                pose.translate(offset.x, offset.y + 0.09D, offset.z);
                pose.mulPose(Axis.YP.rotationDegrees(-strike.yaw));

                // 主地裂炽金熔岩剑沟（沿斩击方向撕裂 9 格）
                pose.pushPose();
                pose.translate(0.0F, 0.04F, -3.6F);
                pose.scale(2.8F * burst, 0.68F, 9.2F * burst);
                sharedModels.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.74F, 0.24F, 0.95F * fade * fpAtten, -elapsed * 0.04F, 0.0F);
                pose.popPose();

                // 剑沟核心炽白高能裂线
                pose.pushPose();
                pose.translate(0.0F, 0.08F, -3.2F);
                pose.scale(1.15F * burst, 0.42F, 8.6F * burst);
                sharedModels.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.97F, 0.78F, 0.88F * fade * fpAtten, -elapsed * 0.06F, 0.0F);
                pose.popPose();

                // 4 道向外放射的斜向撕裂熔岩支沟（参考 Preview_2_t3.5s 裂地岩浆网）
                for (float branchDeg : new float[]{-46.0F, -21.0F, 22.0F, 48.0F}) {
                    pose.pushPose();
                    pose.translate(0.0F, 0.03F, 0.0F);
                    pose.mulPose(Axis.YP.rotationDegrees(branchDeg));
                    pose.scale(0.95F * burst, 0.36F, 5.8F * burst);
                    sharedModels.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.44F, 0.12F, 0.78F * fade * fpAtten, -elapsed * 0.05F, 0.0F);
                    pose.popPose();
                }

                // 裂谷两侧伴随翻飞碎岩喷出的灼热岩浆火舌
                for (int i = 0; i < 16; i++) {
                    int bank = (i % 2 == 0) ? -1 : 1;
                    int row = i / 2;
                    float life = elapsed - row * 0.45F;
                    if (life < 0.0F) {
                        continue;
                    }
                    float rise = smooth(life / 4.5F);
                    float drift = smooth(life / 18.0F);
                    float seamFade = 1.0F - smooth((life - 8.0F) / 14.0F);
                    if (seamFade <= 0.02F) {
                        continue;
                    }
                    float x = bank * (0.35F + (row % 3) * 0.22F + drift * (1.2F + (row % 3) * 0.45F));
                    float z = (row - 3.5F) * 0.88F;
                    pose.pushPose();
                    pose.translate(x, rise * (1.15F + (i % 3) * 0.42F) * (1.0F - drift * 0.72F), z);
                    pose.mulPose(Axis.ZP.rotationDegrees(bank * (24.0F + drift * 48.0F)));
                    pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                    float sz = (0.48F + (i % 3) * 0.16F) * rise * seamFade;
                    pose.scale(sz * 0.46F, sz * 0.46F, sz * (2.6F + (row % 3) * 0.7F));
                    sharedModels.slashBeam().renderAdditive(pose, buffer, 1.0F, 0.42F + 0.38F * seamFade, 0.12F, seamFade * 0.86F * fpAtten, 0.0F, 0.0F);
                    pose.popPose();
                }

                pose.popPose();
            }
        }
    }

    /**
     * Pass 3C：落地裂地火山熔岩喷涌光锥 + 内核炽白光柱 + 贴地冲击波浪环（高速向上流动无缝 UV）。
     */
    static void renderCraterEruption(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                     Strike strike, Vec3 camera, float age, boolean firstPerson) {
        float elapsed = age - IMPACT;
        if (elapsed < -0.5F || elapsed >= CRATER_DURATION) {
            return;
        }
        float local = Math.max(0.0F, elapsed);
        float erupt = easeOutCubic(Mth.clamp(local / 6.0F, 0.0F, 1.0F));
        float fade = 1.0F - smooth((local - 7.0F) / (CRATER_DURATION - 7.0F));
        if (fade <= 0.01F) {
            return;
        }
        float fpAtten = firstPerson ? 0.68F : 1.0F;
        Vec3 offset = strike.hit.subtract(camera);

        pose.pushPose();
        pose.translate(offset.x, offset.y + 0.04D, offset.z);

        // 1. 外层赤红-熔金喷涌火山光锥与贴地冲击波领环
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(local * 4.2F));
        float xzScale = Mth.lerp(erupt, 0.85F, 2.75F) + local * 0.032F;
        float yScale = Mth.lerp(erupt, 0.90F, 3.15F) * (0.65F + 0.35F * fade);
        pose.scale(xzScale, yScale, xzScale);
        // vOffset 正向滚动驱动熔岩火焰自地心向上狂涌
        meshes.craterEruption().renderAdditive(pose, buffer, 1.0F, 0.78F, 0.46F, 0.92F * fade * fpAtten, local * 0.018F, local * 0.075F);
        pose.popPose();

        // 2. 内层反向旋转炽白金核喷涌光柱（强化纵深叠亮）
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-local * 6.8F));
        float innerXZ = Mth.lerp(erupt, 0.55F, 1.68F);
        float innerY = Mth.lerp(erupt, 1.10F, 3.85F) * (0.55F + 0.45F * fade);
        pose.scale(innerXZ, innerY, innerXZ);
        meshes.craterEruption().renderAdditive(pose, buffer, 1.0F, 0.96F, 0.72F, 0.82F * fade * fpAtten, -local * 0.024F, local * 0.115F);
        pose.popPose();

        pose.popPose();
    }

    /**
     * Pass 3D：绘制反击触发瞬间的格挡爆发环、巨剑剑柄烈日神环，以及落地扩散的金红火焰冲击环。
     */
    static void renderStrikeSigils(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                   Strike strike, Vec3 camera, float age, boolean firstPerson) {
        float fpAtten = firstPerson ? 0.65F : 1.0F;

        // 1. 反击触发瞬间（age: 0..12）：施法者身前的格挡成功爆发闪光环
        if (age < 13.0F) {
            float t = Mth.clamp(age / 13.0F, 0.0F, 1.0F);
            float expand = easeOutCubic(t);
            float alpha = (1.0F - t) * (1.0F - t * 0.4F) * fpAtten;
            if (alpha > 0.01F) {
                Vec3 origin = strike.shoulder.subtract(camera);
                pose.pushPose();
                pose.translate(origin.x, origin.y + 1.25D, origin.z);
                pose.mulPose(Axis.YP.rotationDegrees(-strike.yaw));
                pose.translate(0.0F, 0.0F, firstPerson ? 1.15F : 0.85F);
                pose.mulPose(Axis.ZP.rotationDegrees(age * 11.0F));
                float sz = Mth.lerp(expand, 0.85F, 2.75F);
                pose.scale(sz, sz, sz);
                meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.95F, 0.68F, alpha * 0.95F, 0.0F, 0.0F);
                pose.popPose();
            }
        }

        // 2. 巨剑蓄势显形期间（age: 6..IMPACT+4）：剑柄日核后方旋转的金色裁决神轮
        if (age >= 6.0F && age < IMPACT + 6.0F) {
            pose.pushPose();
            float alpha = strike.mountSword(pose, camera, age);
            if (alpha > 0.01F) {
                pose.mulPose(Axis.XP.rotationDegrees(180.0F));
                pose.translate(0.0F, -(PhainonCounterMeshes.GREATSWORD_TIP_Y - 0.24F), -0.06F);
                float wheelScale = 0.72F + 0.16F * Mth.sin(age * 0.45F);
                pose.mulPose(Axis.ZP.rotationDegrees(age * 7.5F));
                pose.scale(wheelScale, wheelScale, wheelScale * 0.65F);
                meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.92F, 0.56F, alpha * 0.82F * fpAtten, 0.0F, 0.0F);
            }
            pose.popPose();
        }

        // 3. 落地爆裂瞬间（age: IMPACT..IMPACT+26）：地面极速扩张的双层金红火焰冲击环
        float elapsed = age - IMPACT;
        if (elapsed >= 0.0F && elapsed < 26.0F) {
            float t = elapsed / 26.0F;
            float expand = easeOutCubic(t);
            float alpha = (1.0F - t) * (1.0F - 0.35F * t) * fpAtten;
            if (alpha > 0.01F) {
                Vec3 offset = strike.hit.subtract(camera);
                // 外圈绯红-炽金主冲击环
                pose.pushPose();
                pose.translate(offset.x, offset.y + 0.12D, offset.z);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(elapsed * 5.2F));
                float rOuter = Mth.lerp(expand, 1.25F, 5.60F);
                pose.scale(rOuter, rOuter, 0.45F);
                meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.62F, 0.20F, alpha * 0.92F, 0.0F, 0.0F);
                pose.popPose();

                // 内圈反旋炽白金核爆发环
                if (elapsed < 16.0F) {
                    float tIn = elapsed / 16.0F;
                    float rInner = Mth.lerp(easeOutCubic(tIn), 0.95F, 3.65F);
                    float aIn = (1.0F - tIn) * fpAtten;
                    pose.pushPose();
                    pose.translate(offset.x, offset.y + 0.24D, offset.z);
                    pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                    pose.mulPose(Axis.ZP.rotationDegrees(-elapsed * 9.0F));
                    pose.scale(rInner, rInner, 0.35F);
                    meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.96F, 0.72F, aIn * 0.90F, 0.0F, 0.0F);
                    pose.popPose();
                }
            }
        }
    }

    /**
     * Pass 1（Solid）：32 块不透明受光黑曜石/玄武岩碎石自裂谷与爆心剧烈炸裂翻飞，与烈焰形成极致明暗反差。
     */
    static void renderShatteredDebris(PoseStack pose, VertexConsumer buffer, PhainonVfxModelData sharedModels,
                                      Minecraft mc, Strike strike, Vec3 camera, float age) {
        float elapsed = age - IMPACT;
        if (elapsed < 0.0F || elapsed >= CRATER_DURATION || mc.level == null) {
            return;
        }
        float yawRad = (float) Math.toRadians(strike.yaw);
        float sinY = Mth.sin(yawRad);
        float cosY = Mth.cos(yawRad);

        for (int i = 0; i < DEBRIS_COUNT; i++) {
            boolean radialRing = i >= 18;
            float delay = radialRing ? (i - 18) * 0.28F : (i / 2) * 0.48F;
            float life = elapsed - delay;
            if (life < 0.0F) {
                continue;
            }
            float totalLife = radialRing ? 24.0F : 26.0F;
            float t = Mth.clamp(life / totalLife, 0.0F, 1.0F);
            if (t >= 1.0F) {
                continue;
            }

            float launch = easeOutCubic(t);
            float scaleFade = t < 0.72F ? 1.0F : 1.0F - ((t - 0.72F) / 0.28F);
            float popIn = smooth(life / 2.2F);
            float rockSize = (0.48F + (i % 5) * 0.18F) * popIn * scaleFade;
            if (rockSize <= 0.025F) {
                continue;
            }

            double localX;
            double localY;
            double localZ;
            if (!radialRing) {
                // 裂谷两侧向外掀起的大块黑曜石岩片
                int bank = (i % 2 == 0) ? -1 : 1;
                int row = i / 2;
                localX = bank * (0.42D + (row % 3) * 0.26D + launch * (2.6D + (row % 4) * 0.75D));
                localZ = (row - 4.0D) * 0.85D + launch * 0.65D;
                double peakH = 2.1D + (i % 4) * 0.72D;
                localY = peakH * (4.0D * t * (1.0D - t)) + 0.14D;
            } else {
                // 爆心 360° 高速四溅的焦岩碎石
                int k = i - 18;
                float angle = (k / 14.0F) * Mth.TWO_PI + 0.37F * (k % 3);
                double dist = 0.55D + launch * (3.8D + (k % 4) * 1.15D);
                localX = Math.cos(angle) * dist;
                localZ = Math.sin(angle) * dist;
                double peakH = 2.8D + (k % 5) * 0.68D;
                localY = peakH * (4.0D * t * (1.0D - t)) + 0.20D;
            }

            // 将局部坐标按 strike.yaw 旋回世界坐标，用于准确采样环境光照
            double worldX = strike.hit.x + localX * cosY - localZ * sinY;
            double worldY = strike.hit.y + localY;
            double worldZ = strike.hit.z + localX * sinY + localZ * cosY;

            int blockLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(worldX, worldY, worldZ));
            // 叠加微弱爆心火光照亮碎岩底面（确保暗夜也能看出锋利岩石棱角）
            int packedLight = t < 0.45F ? LightTexture.pack(Math.max(10, LightTexture.block(blockLight)), LightTexture.sky(blockLight)) : blockLight;

            pose.pushPose();
            pose.translate(worldX - camera.x, worldY - camera.y, worldZ - camera.z);
            pose.mulPose(Axis.XP.rotationDegrees(life * (17.0F + (i % 4) * 8.0F) + i * 41.0F));
            pose.mulPose(Axis.YP.rotationDegrees(life * (21.0F - (i % 3) * 9.0F) + i * 67.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(life * (14.0F + (i % 5) * 6.0F) + i * 23.0F));
            pose.scale(rockSize * (0.85F + (i % 3) * 0.20F),
                    rockSize * (1.15F + (i % 2) * 0.42F),
                    rockSize * (0.78F + ((i + 1) % 3) * 0.22F));

            // 暗黑曜石底色 + 炽热受火微暖反光
            int rCol = 148 + (i % 4) * 18;
            int gCol = 82 + (i % 3) * 14;
            int bCol = 74 + (i % 3) * 12;
            sharedModels.debrisShard().renderSolid(pose, buffer, packedLight, rCol, gCol, bCol);
            pose.popPose();
        }
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }
}
