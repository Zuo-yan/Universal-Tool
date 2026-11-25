package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonMeteor.*;

/** A low, open funnel over the strike point. Its underside lights from the eye out on impact. */
final class PhainonMeteorSky {
    private static final RenderType CLOUDS = PhainonVfxRenderTypes.translucentTriangles(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/misc/phainon_meteor_clouds.png"));
    private static final int SIDES = 48, RINGS = 10;
    private static final float[] X = new float[SIDES + 1], Z = new float[SIDES + 1];
    static {
        for (int i = 0; i <= SIDES; i++) {
            float angle = Mth.TWO_PI * i / SIDES;
            X[i] = Mth.cos(angle);
            Z[i] = Mth.sin(angle);
        }
    }

    private PhainonMeteorSky() {}

    static void render(PoseStack pose, BufferSource buffers, float age) {
        float cover = smooth((age - 3) / 36) * (1 - smooth((age - IMPACT - 8) / (DURATION - IMPACT - 8)));
        if (cover < .01F) return;
        float descent = smooth((age - GIANT) / (IMPACT - GIANT));
        float blast = Math.max(0, age - IMPACT);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * .28F + descent * 13));
        float radius = 29 + 11 * cover + 7 * smooth(blast / 18);
        pose.scale(radius, 1, radius);
        var buffer = buffers.getBuffer(CLOUDS);
        for (int ring = 0; ring < RINGS; ring++) {
            var near = shell(ring, cover, descent, age);
            var far = shell(ring + 1, cover, descent, age);
            for (int side = 0; side < SIDES; side++) {
                emit(pose, buffer, near, side);
                emit(pose, buffer, far, side);
                emit(pose, buffer, far, side + 1);
                emit(pose, buffer, near, side);
                emit(pose, buffer, far, side + 1);
                emit(pose, buffer, near, side + 1);
            }
        }
        buffers.endBatch(CLOUDS);
        pose.popPose();
    }

    private record Ring(float r, float height, float red, float green, float blue, float alpha) {}

    private static Ring shell(int ring, float cover, float descent, float age) {
        float r = .043F + .957F * ring / RINGS;
        float waveAge = age - IMPACT - r * 13;
        float wave = smooth((waveAge + 1) / 3) * (1 - smooth((waveAge - 2) / 9));
        float height = 12.6F + 10 * r + 6 * r * r - descent * 2.4F + wave * 2;
        return new Ring(r, height, .74F + .26F * descent, .64F + .28F * descent + .08F * wave,
                .88F - .20F * descent + .32F * wave, cover * .94F);
    }

    private static void emit(PoseStack pose, VertexConsumer buffer, Ring ring, int side) {
        float x = X[side] * ring.r, z = Z[side] * ring.r;
        buffer.addVertex(pose.last().pose(), x, ring.height, z).setColor(ring.red, ring.green, ring.blue, ring.alpha)
                .setUv(.5F + x * .5F, .5F + z * .5F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, -1, 0);
    }
}
