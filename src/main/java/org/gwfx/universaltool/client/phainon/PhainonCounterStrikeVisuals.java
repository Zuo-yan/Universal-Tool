package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonCounter.IMPACT;

/** Sword tip is the common pivot for the steel, edge light and trailing exposures. */
final class PhainonCounterStrikeVisuals {
    private PhainonCounterStrikeVisuals() {}

    record Strike(Vec3 shoulder, Vec3 hit, float yaw, int side) {
        float mount(PoseStack p, Vec3 camera, float age) {
            float gather = smooth(age / 28);
            float swing = Mth.clamp((age - 30) / (IMPACT - 30), 0, 1);
            float cut = swing * swing * swing;
            float trail = 1 - smooth((age - IMPACT - 3) / 12);
            Vec3 start = shoulder.lerp(hit, .43 * gather);
            Vec3 tip = start.lerp(hit, cut).subtract(camera);
            p.translate(tip.x, tip.y + (2.2 + 6.8 * gather) * (1 - cut) + .09, tip.z);
            p.mulPose(Axis.YP.rotationDegrees(-yaw));
            p.translate(side * (1 - cut) * (1.4 + 3.2 * gather), 0, 0);
            p.mulPose(Axis.ZP.rotationDegrees(side * Mth.lerp(cut, 105 - 26 * gather, 27)));
            p.mulPose(Axis.XP.rotationDegrees(-18 - 12 * cut));
            float size = (.32F + 1.15F * gather) * trail;
            p.scale(size, size, size);
            return trail;
        }
    }

    static void steel(PoseStack p, VertexConsumer buffer, PhainonVfxModelData.BakedMesh sword,
                      Strike strike, Vec3 camera, float age) {
        if (age >= IMPACT + 15) return;
        p.pushPose();
        strike.mount(p, camera, age);
        p.scale(6.3F, -5.9F, -6.3F);
        p.translate(-.6, -1.45, -.48);
        sword.renderSolid(p, buffer, LightTexture.FULL_BRIGHT, 255, 237, 197);
        p.popPose();
    }

    static void edge(PoseStack p, VertexConsumer buffer, PhainonVfxModelData models,
                     Strike strike, Vec3 camera, float age) {
        if (age >= IMPACT + 15) return;
        for (int i = 0; i <= 3; i++) {
            if (i > 0 && (age < 34 || age > IMPACT + 1)) continue;
            p.pushPose();
            float fade = strike.mount(p, camera, age - i * .88F);
            float alpha = fade * (i == 0 ? .88F : .32F / i);
            p.mulPose(Axis.XP.rotationDegrees(-90));
            p.scale(1.6F, .7F, 9.5F);
            models.slashBeam().renderAdditive(p, buffer, 1, i == 0 ? .89F : .52F, .27F, alpha, 0, 0);
            if (i == 0) {
                p.translate(0, 0, .92F);
                p.scale(.8F, 2, .04F);
                models.sunburstWheel().renderAdditive(p, buffer, 1, .89F, .40F, alpha, 0, 0);
            }
            p.popPose();
        }
    }

    /** Dark slabs break away on both sides of the slash. Hot seams follow the same fractures. */
    static void fracture(PoseStack p, VertexConsumer buffer, PhainonVfxModelData models,
                         Strike strike, Vec3 camera, float age, boolean glow) {
        float elapsed = age - IMPACT;
        if (elapsed < 0 || elapsed >= 32) return;
        Vec3 offset = strike.hit.subtract(camera);
        p.pushPose();
        p.translate(offset.x, offset.y + .08, offset.z);
        p.mulPose(Axis.YP.rotationDegrees(-strike.yaw));
        for (int i = 0; i < 22; i++) {
            int bank = i % 2 == 0 ? -1 : 1;
            int row = i / 2;
            float life = elapsed - row * .62F;
            if (life < 0) continue;
            float burst = smooth(life / 5);
            float drift = smooth(life / 20);
            float fade = 1 - smooth((life - 11) / 14);
            if (fade < .015F) continue;
            float z = (row - 4) * .74F;
            float x = bank * (.25F + (row % 4) * .11F + drift * (1 + row % 3));
            p.pushPose();
            p.translate(x, burst * (1.65F + (i % 3) * .52F) * (1 - drift), z + drift * .9F);
            p.mulPose(Axis.ZP.rotationDegrees(bank * (19 + drift * 71)));
            p.mulPose(Axis.YP.rotationDegrees(i * 47));
            float size = (.58F + .14F * (i % 4)) * burst * fade;
            if (glow) {
                p.mulPose(Axis.XP.rotationDegrees(-90));
                p.scale(size * .33F, size * .55F, size * (2.3F + row % 3));
                models.slashBeam().renderAdditive(p, buffer, 1, .37F + .34F * fade, .13F, fade, 0, 0);
            } else {
                p.scale(size * .90F, size * 1.56F, size);
                models.debrisShard().renderSolid(p, buffer, LightTexture.pack(11, 10), 164, 92, 79);
            }
            p.popPose();
        }
        if (glow) {
            p.translate(0, .13, -3);
            p.scale(2.7F, .65F, 8 * smooth((elapsed + 1) / 6));
            models.slashBeam().renderAdditive(p, buffer, 1, .59F, .18F, 1 - smooth(elapsed / 29), 0, 0);
        }
        p.popPose();
    }
}
