package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.UniversalToolMod;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonMeteor.*;
import org.gwfx.universaltool.phainon.MeteorShower;

/** World-space molten cores, flared fire sheaths and shock fronts. Units are blocks and ticks. */
public final class PhainonMeteorVisuals {
    private static final RenderType CRUST = PhainonVfxRenderTypes.solidTriangles(texture("meteor_crust"));
    private static final RenderType FIRE = PhainonVfxRenderTypes.additiveGlow(texture("meteor_flame"));
    private static final RenderType SIGIL = PhainonVfxRenderTypes.additiveGlow(texture("sunburst_sigil"));
    private static final float TAU = 2 * Mth.PI;
    private static volatile PhainonVfxModelData.BakedMesh core;

    private PhainonMeteorVisuals() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/misc/phainon_" + name + ".png");
    }

    public static void reload(ResourceManager manager) {
        core = PhainonVfxModelData.loadObjMesh(manager, ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "models/vfx/phainon_meteor_core.obj"));
    }

    static void corona(PoseStack p, BufferSource buffers, Vec3 center, float yaw, float age, boolean firstPerson) {
        var models = PhainonVfxModelData.getOrLoad();
        if (models == null) return;
        float release = smooth((age - RELEASE) / 8);
        float charge = smooth(age / 32);
        p.pushPose();
        p.translate(center.x, center.y + release * 3.1, center.z);
        p.mulPose(Axis.YP.rotationDegrees(-yaw));
        p.translate(0, firstPerson ? .4 : 0, -.45);
        p.mulPose(Axis.ZP.rotationDegrees(age * 2.4F));
        float scale = .25F + charge * .86F;
        p.scale(scale, scale, scale * .48F);
        models.sunburstWheel().renderAdditive(p, buffers.getBuffer(SIGIL), 1, .93F, .63F,
                charge * (1 - release) * (firstPerson ? .48F : 1), 0, 0);
        if (age > RELEASE - 4) {
            float lift = smooth((age - RELEASE + 4) / 12);
            p.mulPose(Axis.XP.rotationDegrees(-90));
            p.scale(.45F + .25F * lift, .5F, 1 + 8 * lift);
            models.slashBeam().renderAdditive(p, buffers.getBuffer(SIGIL), 1, .96F, .72F,
                    (1 - release) * smooth((age - RELEASE + 4) / 3), 0, 0);
        }
        buffers.endBatch(SIGIL);
        p.popPose();
    }

    static void draw(PoseStack p, BufferSource buffers, MeteorShower shower, float age) {
        if (age >= DURATION) return;
        PhainonMeteorSky.render(p, buffers, age);
        if (core == null || age < RELEASE - 15) return;
        if (age < IMPACT) {
            float portal = smooth((age - RELEASE + 15) / 22) * (1 - smooth((age - IMPACT + 10) / 10));
            var fire = buffers.getBuffer(FIRE);
            for (int i = 0; i < 3; i++) {
                float r = (3 + 1.75F * i) * (.35F + .65F * portal);
                band(p, fire, r, r + 1.8F, 12.4F + i * .65F, 1.2F, age * .042F + i, portal * .9F);
            }
            buffers.endBatch(FIRE);
        }
        if (age < IMPACT + 6) {
            meteors(p, buffers, shower, age, false);
            meteors(p, buffers, shower, age, true);
        }
        if (age >= IMPACT) blast(p, buffers, age - IMPACT);
    }

    private static void meteors(PoseStack p, BufferSource buffers, MeteorShower shower, float age, boolean flames) {
        RenderType layer = flames ? FIRE : CRUST;
        var buf = buffers.getBuffer(layer);
        for (int i = 0; i < MeteorShower.COUNT; i++) {
            float t = MeteorShower.flightProgress(age, i);
            if (t <= 0 || t >= 1.28F) continue;
            Vec3 offset = shower.landings().get(i).subtract(shower.center());
            if (t >= 1) {
                if (flames) {
                    float splash = (t - 1) / .28F;
                    p.pushPose();
                    p.translate(offset.x, offset.y + .1, offset.z);
                    band(p, buf, .15F + splash * .7F, .4F + splash, 0, .65F, i, 1 - splash);
                    p.popPose();
                }
                continue;
            }
            float spread = 1 + .75F * (1 - t);
            float size = (.18F + .045F * (i % 4)) * smooth(t * 7) * (1 - smooth((t - .90F) * 10));
            body(p, buf, (float) offset.x * spread, Mth.lerp(t * t, 14, (float) offset.y),
                    (float) offset.z * spread, size, age, i, flames);
        }
        if (age > GIANT && age < IMPACT) {
            float t = Mth.clamp((age - GIANT) / (IMPACT - GIANT), 0, 1);
            float size = (.35F + 1.8F * smooth(t * 2.8F)) * smooth(t * 9);
            body(p, buf, (1 - t) * 1.8F, 14 * (1 - t * t) + 1.3F, 0, size, age, 21, flames);
        }
        buffers.endBatch(layer);
    }

    private static void body(PoseStack p, VertexConsumer buf, float x, float y, float z, float size,
                             float age, int seed, boolean flames) {
        if (size < .01F) return;
        p.pushPose();
        p.translate(x, y, z);
        p.mulPose(Axis.ZP.rotationDegrees(seed == 21 ? -7 : 13 * Mth.sin(seed)));
        p.scale(size, size, size);
        p.mulPose(Axis.YP.rotationDegrees(seed * 47 + age * 3));
        if (flames) {
            band(p, buf, .16F, 1.16F, -1.28F, 1.2F, seed + age * .05F, 1);
            band(p, buf, 1.16F, .11F, -.12F, 4.9F, seed - age * .08F, .94F);
            band(p, buf, .86F, .025F, .50F, 6.2F, age * .10F, .85F);
        } else {
            core.renderSolid(p, buf, LightTexture.FULL_BRIGHT, 255, 244, 210);
        }
        p.popPose();
    }

    private static void blast(PoseStack p, BufferSource buffers, float age) {
        float t = Mth.clamp(age / (DURATION - IMPACT), 0, 1);
        float expand = 1 - (1 - t) * (1 - t) * (1 - t);
        float fade = 1 - smooth((t - .34F) / .66F);
        var rock = buffers.getBuffer(CRUST);
        for (int i = 0; i < 20; i++) {
            float a = i * 2.39996F;
            float dist = expand * (2.2F + i % 7);
            float y = .4F + (3.0F + i % 5) * 4 * t * (1 - t);
            float size = (.12F + (i % 4) * .085F) * fade;
            if (size < .01F) continue;
            p.pushPose();
            p.translate(dist * Mth.cos(a), y, dist * Mth.sin(a));
            p.mulPose(Axis.ZP.rotationDegrees(i * 57 + age * 7));
            p.scale(size, size * 1.44F, size);
            core.renderSolid(p, rock, LightTexture.pack(10, 12), 147, 108, 105);
            p.popPose();
        }
        buffers.endBatch(CRUST);
        var fire = buffers.getBuffer(FIRE);
        float radius = 1 + 7.7F * expand;
        band(p, fire, radius * .60F, radius, .1F, .45F, t, fade);
        band(p, fire, radius * .24F, radius * .92F, .15F, .6F + 4.9F * expand, 2 + t, fade);
        band(p, fire, radius * .92F, radius * .52F, .75F + 4.9F * expand, 1.3F, 2 + t, fade * .76F);
        if (age < 9) {
            float flash = 1 - age / 9;
            band(p, fire, .2F, radius * 1.75F, .2F, 2.6F, age * .12F, flash);
            band(p, fire, .32F, .06F, .12F, 9 * flash, .7F, flash);
        }
        buffers.endBatch(FIRE);
    }

    /** Uneven annular strip; continuous circumference UV, with the bright seam between its edges. */
    private static void band(PoseStack p, VertexConsumer buf, float inner, float outer, float y,
                             float height, float phase, float alpha) {
        if (alpha < .01F) return;
        final int segments = 32;
        for (int i = 0; i < segments; i++) {
            float u = (float)i / segments;
            float next = (float)(i + 1) / segments;
            vertex(p, buf, u, inner, y, phase, 0, alpha);
            vertex(p, buf, next, inner, y, phase, 0, alpha);
            vertex(p, buf, next, outer, y + height, phase, 1, alpha);
            vertex(p, buf, u, inner, y, phase, 0, alpha);
            vertex(p, buf, next, outer, y + height, phase, 1, alpha);
            vertex(p, buf, u, outer, y + height, phase, 1, alpha);
        }
    }

    private static void vertex(PoseStack p, VertexConsumer buf, float u, float radius,
                               float y, float phase, float v, float alpha) {
        float a = u * TAU;
        float ripple = Mth.sin(5 * a + phase) + .45F * Mth.cos(9 * a - phase);
        float r = radius * (1 + .065F * ripple);
        buf.addVertex(p.last().pose(), r * Mth.cos(a), y + radius * .055F * ripple, r * Mth.sin(a))
                .setColor(1F, .97F, .89F, alpha).setUv(u + phase * .08F, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
