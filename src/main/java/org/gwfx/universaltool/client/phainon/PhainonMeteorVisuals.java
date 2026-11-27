package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.phainon.MeteorShower;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonMeteor.*;

/**
 * 觉醒战技「支柱·死星天裁」电影级 3D 多 Pass 世界空间特效渲染器。
 * <p>完整还原关键帧 01-08 全流程，并严格遵守 {@code minecraft-skill-vfx v2.0} 分层 Pass 与实时 {@code endBatch} 规范：
 * <ul>
 *   <li><b>起手日冕与升空构造（01-03）</b>：脑后双层反旋金色日冕 + 暗紫微光晕 + 冲天金色能量刃柱；</li>
 *   <li><b>Pass 0（Translucent + Additive）</b>：高空立体漏斗破云漩涡穹环与熔金天幕裂隙；</li>
 *   <li><b>Pass 1（Solid 实体焦岩）</b>：18 枚小陨石 + 巨型炽熔单体死星陨石焦岩壳体 + 命中后 36 块高速飞溅玄武岩碎石；</li>
 *   <li><b>Pass 2（Additive 岩浆裂纹）</b>：同 UV 放大 1.022x 的炽熔岩浆裂纹层，在黑焦岩体上雕刻出刺目金红火脉；</li>
 *   <li><b>Pass 3（Additive 螺旋彗尾）</b>：双层螺旋烈焰彗尾巨锥壳体（{@code phainon_meteor_flame_tail.obj}），高速 UV 上滚拉出贯天火柱；</li>
 *   <li><b>Pass 4（Additive 爆炸火云穹顶与冲击波）</b>：地面预警火环、小陨石撞击波、命中瞬间白金强光爆发、双层蘑菇火云爆炸穹顶与贴地激波裙环；</li>
 *   <li><b>Pass 5（Additive 圣印放射光轮）</b>：命中瞬间贴地扩散的巨大黄金日轮冲击印记。</li>
 * </ul></p>
 */
public final class PhainonMeteorVisuals {
    private static final RenderType CORE_BODY = PhainonVfxRenderTypes.solidTriangles(PhainonMeteorMeshes.CORE_BODY_TEX);
    private static final RenderType CORE_GLOW = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.CORE_GLOW_TEX);
    private static final RenderType FLAME_TAIL = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.TRAIL_TEX);
    private static final RenderType BLAST_DOME = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.BLAST_DOME_TEX);
    private static final RenderType SIGIL = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.SUNBURST_TEX);
    private static final RenderType BLADE_ARC = PhainonVfxRenderTypes.additiveGlow(PhainonMeteorMeshes.SLASH_ARC_TEX);

    /** 巨型死星陨石俯冲起点偏移（相对落点中心），制造由高空漩涡斜向压迫砸落的电影构图。 */
    private static final float GIANT_START_X = 7.5F;
    private static final float GIANT_START_Y = 44.0F;
    private static final float GIANT_START_Z = -4.5F;
    private static final float GIANT_END_Y = 3.6F;

    /** 命中后飞溅碎石块数量（还原关键帧 07-08 漫天碎岩崩裂）。 */
    private static final int DEBRIS_COUNT = 36;

    private PhainonMeteorVisuals() {}

    /**
     * 资源包重载入口（同时委托 {@link PhainonMeteorMeshes#reload(ResourceManager)}，确保热重载即刻生效）。
     */
    public static void reload(ResourceManager manager) {
        PhainonMeteorMeshes.reload(manager);
    }

    /**
     * 关键帧 01-03：渲染施法者脑后双层金色日冕光环与释放瞬间的冲天金色能量刃柱。
     * <p>第一人称视角下自动抬高并克制透明度，严格防止糊脸遮挡准星。</p>
     */
    static void corona(PoseStack pose, BufferSource buffers, Vec3 headCenter, float yaw, float age, boolean firstPerson) {
        var models = PhainonVfxModelData.getOrLoad();
        if (models == null) {
            return;
        }
        float charge = smooth(age / 26.0F);
        float release = smooth((age - RELEASE) / 9.0F);
        float alphaBase = charge * (1.0F - release);
        if (alphaBase < 0.01F) {
            return;
        }
        float fpScale = firstPerson ? 0.36F : 1.0F;
        float pulse = 0.94F + 0.06F * Mth.sin(age * 0.42F);

        // Pass A：脑后双层反旋黄金日冕（外环炽金锯齿 + 内环白金四芒星核）
        var sigilBuf = buffers.getBuffer(SIGIL);
        pose.pushPose();
        pose.translate(headCenter.x, headCenter.y + (firstPerson ? 0.62F : 0.12F) + release * 3.8F, headCenter.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0.0F, 0.0F, firstPerson ? -0.78F : -0.52F);

        // 外层主黄金日冕轮
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(age * 2.1F));
        float mainScale = (0.34F + charge * 1.08F) * pulse;
        pose.scale(mainScale, mainScale, mainScale * 0.42F);
        models.sunburstWheel().renderAdditive(pose, sigilBuf, 1.0F, 0.88F, 0.46F, alphaBase * fpScale, 0.0F, 0.0F);
        pose.popPose();

        // 内层反向高速旋转白炽星核
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.03F);
        pose.mulPose(Axis.ZP.rotationDegrees(-age * 3.8F + 22.5F));
        float innerScale = mainScale * 0.68F;
        pose.scale(innerScale, innerScale, innerScale * 0.36F);
        models.sunburstWheel().renderAdditive(pose, sigilBuf, 1.0F, 0.97F, 0.82F, alphaBase * 0.88F * fpScale, 0.0F, 0.0F);
        pose.popPose();

        // 关键帧 02 蓄力高潮时的蓝紫微光背晕（紧贴日冕后方，呼应原画冷暖撞色）
        float auraWeight = smooth((age - 14.0F) / 18.0F) * (1.0F - release);
        if (auraWeight > 0.01F && !firstPerson) {
            pose.pushPose();
            pose.translate(0.0F, 0.0F, -0.06F);
            pose.mulPose(Axis.ZP.rotationDegrees(-age * 1.2F));
            float auraScale = mainScale * 1.26F;
            pose.scale(auraScale, auraScale, auraScale * 0.25F);
            models.sunburstWheel().renderAdditive(pose, sigilBuf, 0.42F, 0.48F, 1.0F, auraWeight * 0.36F, 0.0F, 0.0F);
            pose.popPose();
        }

        // 关键帧 03 释放瞬间：金色光柱直贯天幕云核
        if (age > RELEASE - 7.0F) {
            float lift = smooth((age - RELEASE + 7.0F) / 13.0F);
            float beamAlpha = smooth((age - RELEASE + 7.0F) / 3.0F) * (1.0F - release) * (firstPerson ? 0.32F : 0.95F);
            if (beamAlpha > 0.01F) {
                pose.pushPose();
                pose.translate(0.0F, 0.4F + lift * 2.2F, 0.0F);
                pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                pose.scale(0.52F + 0.36F * lift, 0.52F + 0.36F * lift, 1.2F + 14.0F * lift);
                models.slashBeam().renderAdditive(pose, sigilBuf, 1.0F, 0.94F, 0.64F, beamAlpha, 0.0F, 0.0F);
                pose.popPose();
            }
        }
        pose.popPose();
        buffers.endBatch(SIGIL);

        // Pass B：关键帧 03 标志性的金色刃状能量构造向高空光团升腾
        if (age > RELEASE - 9.0F && !firstPerson) {
            float rise = smooth((age - RELEASE + 9.0F) / 15.0F);
            float bladeAlpha = smooth((age - RELEASE + 9.0F) / 4.0F) * (1.0F - smooth((age - RELEASE - 2.0F) / 8.0F));
            if (bladeAlpha > 0.01F) {
                var arcBuf = buffers.getBuffer(BLADE_ARC);
                for (int i = 0; i < 3; i++) {
                    pose.pushPose();
                    pose.translate(headCenter.x, headCenter.y + 1.4F + rise * (8.5F + i * 2.2F), headCenter.z);
                    pose.mulPose(Axis.YP.rotationDegrees(-yaw + (i - 1) * 48.0F + rise * 28.0F));
                    pose.mulPose(Axis.XP.rotationDegrees(-62.0F + i * 10.0F));
                    pose.mulPose(Axis.ZP.rotationDegrees((i - 1) * 24.0F));
                    float s = (1.35F + 1.45F * rise) * (1.0F - i * 0.12F);
                    pose.scale(s, s * 1.15F, s);
                    models.crescentWave().renderAdditive(
                            pose, arcBuf,
                            1.0F, 0.88F, 0.44F,
                            bladeAlpha * 0.82F,
                            -age * 0.06F + i * 0.33F, 0.0F
                    );
                    pose.popPose();
                }
                buffers.endBatch(BLADE_ARC);
            }
        }
    }

    /**
     * 渲染落点战场的全部陨石阶段特效（坐标系原点已平移至 {@code shower.center()}）。
     */
    static void draw(PoseStack pose, BufferSource buffers, MeteorShower shower, float age) {
        if (age >= DURATION) {
            return;
        }
        PhainonMeteorMeshes meshes = PhainonMeteorMeshes.getOrLoad();
        if (meshes == null) {
            return;
        }

        // Pass 0：高空立体破云漩涡与炽金天幕裂隙
        PhainonMeteorSky.render(pose, buffers, meshes, age);

        if (age < RELEASE - 15.0F) {
            return;
        }

        // Pass 1：不透明焦岩实体（18 枚小陨石焦岩核 + 巨型死星焦岩主体 + 命中后 36 块飞溅碎石）
        renderSolidPass(pose, buffers, meshes, shower, age);

        // Pass 2：同 UV 对齐的炽熔岩浆裂纹叠亮（在焦岩裂缝中燃烧，保持岩石暗面压迫感）
        if (age < IMPACT + 2.0F) {
            renderCoreGlowPass(pose, buffers, meshes, shower, age);
        }

        // Pass 3：双层螺旋烈焰彗尾巨锥壳体（小陨石群火雨拖尾 + 巨型死星贯天彗尾）
        if (age < IMPACT + 3.0F) {
            renderFlameTailPass(pose, buffers, meshes, shower, age);
        }

        // Pass 4：地面预警火环、小陨石撞击波、命中瞬间白金强光与双层蘑菇火云爆炸穹顶
        renderBlastAndRingsPass(pose, buffers, meshes, shower, age);

        // Pass 5：命中瞬间的地面黄金日轮放射印记（关键帧 07）
        if (age >= IMPACT && age < IMPACT + 18.0F) {
            renderImpactSigilPass(pose, buffers, age - IMPACT);
        }
    }

    // =========================================================================
    // Pass 1：实体暗黑焦岩死星陨石核与飞溅碎石 (SOLID)
    // =========================================================================
    private static void renderSolidPass(PoseStack pose, BufferSource buffers, PhainonMeteorMeshes meshes,
                                        MeteorShower shower, float age) {
        VertexConsumer rockBuf = buffers.getBuffer(CORE_BODY);
        boolean drewAny = false;

        // 1A. 18 枚弹射小陨石的焦岩核心（关键帧 04-05）
        if (age < IMPACT + 4.0F) {
            for (int i = 0; i < MeteorShower.COUNT; i++) {
                MinorState st = sampleMinor(shower, age, i);
                if (st == null || st.size < 0.02F) {
                    continue;
                }
                drewAny = true;
                pose.pushPose();
                pose.translate(st.x, st.y, st.z);
                pose.mulPose(st.orient);
                pose.mulPose(Axis.YP.rotationDegrees(i * 53.0F + age * 7.5F));
                pose.scale(st.size, st.size, st.size);
                meshes.starCore().renderSolid(pose, rockBuf, LightTexture.FULL_BRIGHT, 232, 206, 194);
                pose.popPose();
            }
        }

        // 1B. 巨型炽熔单体死星陨石焦岩壳体（关键帧 05-06）
        GiantState giant = sampleGiant(age);
        if (giant != null) {
            drewAny = true;
            pose.pushPose();
            pose.translate(giant.x, giant.y, giant.z);
            pose.mulPose(giant.orient);
            pose.mulPose(Axis.YP.rotationDegrees(age * 2.4F));
            pose.scale(giant.size, giant.size, giant.size);
            // 保留深黑焦岩高对比度，白昼下也有极强死星压迫轮廓
            meshes.starCore().renderSolid(pose, rockBuf, LightTexture.FULL_BRIGHT, 245, 224, 206);
            pose.popPose();
        }

        // 1C. 命中后 36 块高速抛射焦黑玄武岩碎石（关键帧 07-08）
        if (age >= IMPACT) {
            float impactAge = age - IMPACT;
            float t = Mth.clamp(impactAge / (DURATION - IMPACT), 0.0F, 1.0F);
            float expand = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float fade = 1.0F - smooth((t - 0.56F) / 0.44F);
            var sharedModels = PhainonVfxModelData.getOrLoad();
            for (int i = 0; i < DEBRIS_COUNT; i++) {
                float angle = i * 2.3999632F;
                float dist = (2.5F + (i % 9) * 4.8F) * expand;
                float arcHeight = (4.8F + (i % 7) * 2.4F) * 4.0F * t * (1.0F - 0.86F * t);
                float size = (0.28F + (i % 5) * 0.17F) * fade;
                if (size < 0.025F) {
                    continue;
                }
                drewAny = true;
                pose.pushPose();
                pose.translate(dist * Mth.cos(angle), 0.35F + arcHeight, dist * Mth.sin(angle));
                pose.mulPose(Axis.YP.rotationDegrees(i * 41.0F + impactAge * (6.0F + (i % 5) * 2.4F)));
                pose.mulPose(Axis.XP.rotationDegrees(i * 67.0F + impactAge * (8.0F + (i % 4) * 3.0F)));
                float sx = size * (0.80F + (i % 3) * 0.25F);
                float sy = size * (1.15F + (i % 4) * 0.18F);
                float sz = size * (0.76F + ((i + 1) % 3) * 0.22F);
                pose.scale(sx, sy, sz);
                var shardMesh = (sharedModels != null && (i % 2 == 0)) ? sharedModels.debrisShard() : meshes.starCore();
                // 近中心碎石被爆炸火光映红，外圈保持冷硬暗黑焦岩剪影
                int shadeR = (int) Mth.lerp(expand, 210, 128);
                int shadeG = (int) Mth.lerp(expand, 148, 92);
                int shadeB = (int) Mth.lerp(expand, 118, 86);
                shardMesh.renderSolid(pose, rockBuf, LightTexture.pack(12, 14), shadeR, shadeG, shadeB);
                pose.popPose();
            }
        }

        if (drewAny) {
            buffers.endBatch(CORE_BODY);
        }
    }

    // =========================================================================
    // Pass 2：同 UV 对齐的炽熔岩浆裂纹叠亮 (ADDITIVE)
    // =========================================================================
    private static void renderCoreGlowPass(PoseStack pose, BufferSource buffers, PhainonMeteorMeshes meshes,
                                           MeteorShower shower, float age) {
        VertexConsumer glowBuf = buffers.getBuffer(CORE_GLOW);
        boolean drewAny = false;

        // 2A. 小陨石炽熔裂纹
        for (int i = 0; i < MeteorShower.COUNT; i++) {
            MinorState st = sampleMinor(shower, age, i);
            if (st == null || st.size < 0.02F) {
                continue;
            }
            drewAny = true;
            pose.pushPose();
            pose.translate(st.x, st.y, st.z);
            pose.mulPose(st.orient);
            pose.mulPose(Axis.YP.rotationDegrees(i * 53.0F + age * 7.5F));
            float s = st.size * 1.032F;
            pose.scale(s, s, s);
            meshes.starCore().renderAdditive(pose, glowBuf, 1.0F, 0.86F, 0.45F, 0.95F, 0.0F, 0.0F);
            pose.popPose();
        }

        // 2B. 巨型死星陨石同轮廓岩浆裂纹（放大 1.022x 防 Z-Fighting，临近撞击时烈金加剧）
        GiantState giant = sampleGiant(age);
        if (giant != null) {
            drewAny = true;
            float heatPulse = 0.88F + 0.12F * Mth.sin(age * 0.55F);
            float intensity = (0.85F + 0.15F * giant.progress) * heatPulse;
            pose.pushPose();
            pose.translate(giant.x, giant.y, giant.z);
            pose.mulPose(giant.orient);
            pose.mulPose(Axis.YP.rotationDegrees(age * 2.4F));
            float s = giant.size * 1.022F;
            pose.scale(s, s, s);
            meshes.starCore().renderAdditive(
                    pose, glowBuf,
                    1.0F, 0.86F + 0.12F * giant.progress, 0.42F + 0.24F * giant.progress,
                    intensity,
                    0.0F, 0.0F
            );
            // 二次高温流纹微旋叠亮，模拟死星表层喷涌流动的熔金火海
            pose.mulPose(Axis.YP.rotationDegrees(age * 1.1F + 17.0F));
            pose.scale(1.018F, 1.018F, 1.018F);
            meshes.starCore().renderAdditive(
                    pose, glowBuf,
                    1.0F, 0.62F, 0.16F,
                    0.52F * giant.emerge,
                    age * 0.004F, -age * 0.003F
            );
            pose.popPose();
        }

        if (drewAny) {
            buffers.endBatch(CORE_GLOW);
        }
    }

    // =========================================================================
    // Pass 3：双层螺旋烈焰彗尾巨锥壳体 (ADDITIVE)
    // =========================================================================
    private static void renderFlameTailPass(PoseStack pose, BufferSource buffers, PhainonMeteorMeshes meshes,
                                            MeteorShower shower, float age) {
        VertexConsumer tailBuf = buffers.getBuffer(FLAME_TAIL);
        boolean drewAny = false;

        // 3A. 18 枚小陨石弹射雨的高速长彗尾（关键帧 04-05 漫天火雨倾泻）
        for (int i = 0; i < MeteorShower.COUNT; i++) {
            MinorState st = sampleMinor(shower, age, i);
            if (st == null || st.size < 0.02F) {
                continue;
            }
            drewAny = true;
            pose.pushPose();
            pose.translate(st.x, st.y, st.z);
            pose.mulPose(st.orient);
            pose.mulPose(Axis.YP.rotationDegrees(i * 37.0F - age * 5.2F));
            // 纵向拉长形成极具动势的坠落火流星尾迹
            pose.scale(st.size * 0.96F, st.size * 1.65F, st.size * 0.96F);
            meshes.flameTail().renderAdditive(
                    pose, tailBuf,
                    1.0F, 0.84F, 0.40F,
                    0.94F * st.alpha,
                    i * 0.19F, age * 0.075F
            );
            pose.popPose();
        }

        // 3B. 巨型死星陨石的双层螺旋烈焰巨锥彗尾（关键帧 05-06 核心视觉奇观）
        GiantState giant = sampleGiant(age);
        if (giant != null) {
            drewAny = true;
            float tailStretch = 0.88F + 0.42F * giant.progress;

            // 内层白炽太阳金主彗尾：紧裹死星迎风面，直插 40+ 格高空云层破洞
            pose.pushPose();
            pose.translate(giant.x, giant.y, giant.z);
            pose.mulPose(giant.orient);
            pose.mulPose(Axis.YP.rotationDegrees(-age * 4.8F));
            pose.scale(giant.size * 1.04F, giant.size * tailStretch, giant.size * 1.04F);
            meshes.flameTail().renderAdditive(
                    pose, tailBuf,
                    1.0F, 0.90F + 0.08F * giant.progress, 0.56F + 0.18F * giant.progress,
                    0.96F * giant.emerge,
                    age * 0.012F, age * 0.068F
            );
            pose.popPose();

            // 外层反向螺旋炎橙赤焰披风：更宽、翻卷奔涌，拉满立体火焰层次
            pose.pushPose();
            pose.translate(giant.x, giant.y, giant.z);
            pose.mulPose(giant.orient);
            pose.mulPose(Axis.YP.rotationDegrees(age * 3.4F + 72.0F));
            pose.scale(giant.size * 1.24F, giant.size * (tailStretch * 0.84F), giant.size * 1.24F);
            meshes.flameTail().renderAdditive(
                    pose, tailBuf,
                    1.0F, 0.52F, 0.14F,
                    0.74F * giant.emerge,
                    -age * 0.010F, age * 0.052F
            );
            pose.popPose();
        }

        if (drewAny) {
            buffers.endBatch(FLAME_TAIL);
        }
    }

    // =========================================================================
    // Pass 4：地面预警圈、小陨石撞击波与巨陨石多层蘑菇火云爆炸穹顶 (ADDITIVE)
    // =========================================================================
    private static void renderBlastAndRingsPass(PoseStack pose, BufferSource buffers, PhainonMeteorMeshes meshes,
                                                MeteorShower shower, float age) {
        VertexConsumer blastBuf = buffers.getBuffer(BLAST_DOME);
        boolean drewAny = false;

        // 4A. 落点地面预警灼热光环（蓄力释放至命中前持续搏动）
        if (age < IMPACT) {
            float warn = smooth((age - RELEASE + 10.0F) / 18.0F);
            float urgency = smooth((age - GIANT) / (IMPACT - GIANT));
            if (warn > 0.01F) {
                drewAny = true;
                float pulse = 0.86F + 0.14F * Mth.sin(age * 0.35F);
                pose.pushPose();
                pose.translate(0.0F, 0.12F, 0.0F);
                pose.mulPose(Axis.YP.rotationDegrees(age * 0.9F));
                float r = MeteorShower.GIANT_RADIUS * (0.68F + 0.08F * urgency);
                pose.scale(r, 1.8F + 1.4F * urgency, r);
                meshes.blastDome().renderAdditive(
                        pose, blastBuf,
                        1.0F, 0.56F + 0.24F * urgency, 0.16F + 0.12F * urgency,
                        warn * (0.24F + 0.24F * urgency) * pulse,
                        age * 0.005F, 0.0F
                );
                pose.popPose();
            }
        }

        // 4B. 18 枚小陨石命中地面后的炽金爆燃冲击穹（关键帧 04-05 地面连环火环）
        if (age < IMPACT + 8.0F) {
            for (int i = 0; i < MeteorShower.COUNT; i++) {
                float t = MeteorShower.flightProgress(age, i);
                if (t < 1.0F || t >= 1.42F) {
                    continue;
                }
                float splash = (t - 1.0F) / 0.42F;
                float expand = 1.0F - (1.0F - splash) * (1.0F - splash);
                float alpha = (1.0F - splash) * (1.0F - splash * 0.35F);
                if (alpha < 0.015F) {
                    continue;
                }
                drewAny = true;
                Vec3 offset = shower.landings().get(i).subtract(shower.center());
                pose.pushPose();
                pose.translate(offset.x, offset.y + 0.08, offset.z);
                pose.mulPose(Axis.YP.rotationDegrees(i * 43.0F + splash * 35.0F));
                float r = 1.6F + expand * (MeteorShower.MINOR_RADIUS * 0.58F);
                float h = 1.2F + expand * 4.6F;
                pose.scale(r, h, r);
                meshes.blastDome().renderAdditive(
                        pose, blastBuf,
                        1.0F, 0.78F - 0.28F * splash, 0.34F * (1.0F - splash),
                        alpha * 0.86F,
                        i * 0.17F, -splash * 0.35F
                );
                pose.popPose();
            }
        }

        // 4C. 巨型死星终极命中：白金放射爆闪（关键帧 07）+ 震撼蘑菇火云爆炸穹顶（关键帧 08）
        if (age >= IMPACT) {
            float impactAge = age - IMPACT;
            float t = Mth.clamp(impactAge / (DURATION - IMPACT), 0.0F, 1.0F);
            float expand = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float fade = 1.0F - smooth((t - 0.38F) / 0.62F);
            if (fade > 0.01F) {
                drewAny = true;
                float outerR = 4.5F + (MeteorShower.GIANT_RADIUS - 2.0F) * expand;
                float outerH = 5.5F + 34.0F * (float) Math.pow(expand, 0.82);

                // (1) 外层宏伟绯红-炽金爆炸蘑菇云穹顶 + 地面扩散冲击裙环
                pose.pushPose();
                pose.translate(0.0F, 0.10F, 0.0F);
                pose.mulPose(Axis.YP.rotationDegrees(impactAge * 0.85F));
                pose.scale(outerR, outerH, outerR);
                meshes.blastDome().renderAdditive(
                        pose, blastBuf,
                        1.0F, 0.58F + 0.30F * (1.0F - t), 0.20F + 0.36F * (1.0F - t) * (1.0F - t),
                        fade * 0.94F,
                        impactAge * 0.004F, -impactAge * 0.018F
                );
                pose.popPose();

                // (2) 内层反旋白炽核爆火穹（在蘑菇云内部形成刺目白金中心火海，还原关键帧 08 底部强光）
                pose.pushPose();
                pose.translate(0.0F, 0.14F, 0.0F);
                pose.mulPose(Axis.YP.rotationDegrees(-impactAge * 1.45F + 54.0F));
                float innerR = outerR * 0.74F;
                float innerH = outerH * 0.86F;
                pose.scale(innerR, innerH, innerR);
                meshes.blastDome().renderAdditive(
                        pose, blastBuf,
                        1.0F, 0.92F - 0.22F * t, 0.62F - 0.42F * t,
                        fade * 0.88F,
                        -impactAge * 0.007F, -impactAge * 0.024F
                );
                pose.popPose();

                // (3) 贴地超音速冲击波扩散浪环（超前于穹顶掠过 56 格战场）
                float waveExpand = Mth.clamp(impactAge / 16.0F, 0.0F, 1.0F);
                float waveAlpha = (1.0F - waveExpand) * (1.0F - waveExpand * 0.45F);
                if (waveAlpha > 0.015F) {
                    pose.pushPose();
                    pose.translate(0.0F, 0.16F, 0.0F);
                    pose.mulPose(Axis.YP.rotationDegrees(impactAge * 2.2F));
                    float waveR = 6.0F + 52.0F * (1.0F - (1.0F - waveExpand) * (1.0F - waveExpand));
                    pose.scale(waveR, 4.2F + 6.5F * (1.0F - waveExpand), waveR);
                    meshes.blastDome().renderAdditive(
                            pose, blastBuf,
                            1.0F, 0.94F, 0.72F,
                            waveAlpha * 0.82F,
                            impactAge * 0.012F, 0.0F
                    );
                    pose.popPose();
                }

                // (4) 命中前 10 ticks：关键帧 07 标志性的白金冲天光柱与耀眼放射强光
                if (impactAge < 10.0F) {
                    float flash = 1.0F - impactAge / 10.0F;
                    float flashSq = flash * flash;
                    pose.pushPose();
                    pose.translate(0.0F, 0.20F, 0.0F);
                    pose.mulPose(Axis.YP.rotationDegrees(-impactAge * 5.0F));
                    float pillarR = 4.5F + 14.0F * (1.0F - flash);
                    pose.scale(pillarR, 38.0F * (0.45F + 0.55F * flash), pillarR);
                    meshes.blastDome().renderAdditive(
                            pose, blastBuf,
                            1.0F, 0.98F, 0.86F,
                            flashSq * 0.92F,
                            impactAge * 0.02F, -impactAge * 0.04F
                    );
                    pose.popPose();
                }
            }
        }

        if (drewAny) {
            buffers.endBatch(BLAST_DOME);
        }
    }

    // =========================================================================
    // Pass 5：命中瞬间地面黄金圣印爆发（关键帧 07 白金星芒放射）
    // =========================================================================
    private static void renderImpactSigilPass(PoseStack pose, BufferSource buffers, float impactAge) {
        var models = PhainonVfxModelData.getOrLoad();
        if (models == null) {
            return;
        }
        float t = Mth.clamp(impactAge / 18.0F, 0.0F, 1.0F);
        float alpha = (1.0F - t) * (1.0F - t);
        if (alpha < 0.015F) {
            return;
        }
        var sigilBuf = buffers.getBuffer(SIGIL);
        float radius = 5.0F + 34.0F * (1.0F - (1.0F - t) * (1.0F - t));

        pose.pushPose();
        pose.translate(0.0F, 0.22F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(impactAge * 3.5F));
        pose.scale(radius, radius, 1.8F);
        models.sunburstWheel().renderAdditive(pose, sigilBuf, 1.0F, 0.94F, 0.68F, alpha * 0.92F, 0.0F, 0.0F);
        pose.popPose();

        buffers.endBatch(SIGIL);
    }

    // =========================================================================
    // 陨石弹道采样辅助
    // =========================================================================
    private record MinorState(float x, float y, float z, float size, float alpha, Quaternionf orient) {}

    private record GiantState(float x, float y, float z, float size, float progress, float emerge, Quaternionf orient) {}

    /**
     * 计算第 {@code index} 枚小陨石的当前位置、尺寸与严格对齐飞行方向的朝向四元数。
     * <p>小陨石由高空漩涡向四周放射倾斜砸落，确保彗尾精准指向云层破洞。</p>
     */
    private static MinorState sampleMinor(MeteorShower shower, float age, int index) {
        float t = MeteorShower.flightProgress(age, index);
        if (t <= 0.0F || t >= 1.0F) {
            return null;
        }
        Vec3 target = shower.landings().get(index).subtract(shower.center());
        // 起点聚拢在高空漩涡内圈，落点铺开至四周，形成关键帧 04-05 的放射火雨透视
        float startX = (float) target.x * 0.42F;
        float startZ = (float) target.z * 0.42F;
        float startY = 26.5F + (index % 5) * 2.4F;
        float endX = (float) target.x;
        float endY = (float) target.y;
        float endZ = (float) target.z;

        float accel = t * t;
        float x = Mth.lerp(t, startX, endX);
        float y = Mth.lerp(accel, startY, endY);
        float z = Mth.lerp(t, startZ, endZ);

        // 切线反方向（指向尾部 +Y 轴）
        float vx = endX - startX;
        float vy = 2.0F * Math.max(0.08F, t) * (endY - startY);
        float vz = endZ - startZ;
        Quaternionf orient = alignTailUp(-vx, -vy, -vz);

        float alpha = smooth(t * 7.0F) * (1.0F - smooth((t - 0.90F) * 10.0F));
        float size = (0.34F + 0.095F * (index % 4)) * (0.45F + 0.55F * smooth(t * 5.0F));
        return new MinorState(x, y, z, size, alpha, orient);
    }

    /**
     * 计算巨型炽熔单体死星陨石的当前空间状态（破云出现 → 压迫俯冲 → 轰然坠地）。
     */
    private static GiantState sampleGiant(float age) {
        if (age <= GIANT - 2.0F || age >= IMPACT) {
            return null;
        }
        float t = Mth.clamp((age - GIANT) / (IMPACT - GIANT), 0.0F, 1.0F);
        float emerge = smooth((age - GIANT + 2.0F) / 7.0F);
        // 前慢后快重力加速曲线：破云出现时停留极具压迫感（关键帧 05），末段高速砸落（关键帧 06）
        float drop = t * t * (0.35F + 0.65F * t);
        float x = Mth.lerp(t, GIANT_START_X, 0.0F);
        float y = Mth.lerp(drop, GIANT_START_Y, GIANT_END_Y);
        float z = Mth.lerp(t, GIANT_START_Z, 0.0F);

        // 彗尾朝向精确对齐俯冲轨迹反方向
        float dyDt = Math.max(0.18F, 0.70F * t + 1.95F * t * t) * (GIANT_END_Y - GIANT_START_Y);
        Quaternionf orient = alignTailUp(GIANT_START_X, -dyDt, GIANT_START_Z);

        // 破云膨胀至 5.4 格半径（直径超 10 格的巨型死星压迫体量！）
        float size = (1.35F + 4.15F * smooth(t * 2.2F)) * emerge;
        return new GiantState(x, y, z, size, t, emerge, orient);
    }

    /**
     * 构造将模型局部 {@code +Y} 轴（彗尾延伸方向）对齐至目标世界向量 {@code (tx, ty, tz)} 的旋转四元数。
     */
    private static Quaternionf alignTailUp(float tx, float ty, float tz) {
        Vector3f dir = new Vector3f(tx, ty, tz);
        if (dir.lengthSquared() < 1.0e-5F) {
            return new Quaternionf();
        }
        dir.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir);
    }
}
