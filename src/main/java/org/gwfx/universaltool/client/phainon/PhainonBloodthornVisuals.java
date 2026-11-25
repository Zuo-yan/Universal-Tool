package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.List;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.EFFECT_TICKS;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.SHARDS_TICK;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.SLASH_TICK;

public final class PhainonBloodthornVisuals {
    private static final ResourceLocation CUT = asset("textures/misc/phainon_bloodthorn_cut.png");
    private static final ResourceLocation SHARD_BODY = asset("textures/misc/phainon_bloodthorn_shard_body.png");
    private static final ResourceLocation SHARD_RIM = asset("textures/misc/phainon_bloodthorn_shard_rim.png");
    private static volatile Meshes current;

    record Frame(Vec3 origin, float yaw, float pitch, float reach, float travel, float age,
                 int side, boolean firstPerson) {}

    private record Meshes(PhainonVfxModelData.BakedMesh cut, PhainonVfxModelData.BakedMesh fracture) {}

    private enum Pass { CUT_LIGHT, SHARD_BODY, SHARD_RIM }

    private PhainonBloodthornVisuals() {}

    public static void reload(ResourceManager manager) {
        var cut = PhainonVfxModelData.loadObjMesh(manager, asset("models/vfx/phainon_bloodthorn_cut.obj"));
        var fracture = PhainonVfxModelData.loadObjMesh(manager, asset("models/vfx/phainon_bloodthorn_fracture.obj"));
        current = cut == null || fracture == null ? null : new Meshes(cut, fracture);
    }

    static void render(List<Frame> frames, PoseStack pose, Vec3 camera, MultiBufferSource.BufferSource buffers) {
        Meshes meshes = current;
        if (meshes == null || frames.isEmpty()) return;
        drawPass(frames, pose, camera, buffers, PhainonVfxRenderTypes.additiveGlow(CUT), meshes, Pass.CUT_LIGHT);
        drawPass(frames, pose, camera, buffers, PhainonVfxRenderTypes.solidTriangles(SHARD_BODY), meshes, Pass.SHARD_BODY);
        drawPass(frames, pose, camera, buffers, PhainonVfxRenderTypes.additiveGlow(SHARD_RIM), meshes, Pass.SHARD_RIM);
    }

    private static void drawPass(List<Frame> frames, PoseStack pose, Vec3 camera, MultiBufferSource.BufferSource buffers,
                                 RenderType layer, Meshes meshes, Pass pass) {
        VertexConsumer buffer = buffers.getBuffer(layer);
        for (Frame frame : frames) {
            pose.pushPose();
            Vec3 offset = frame.origin().subtract(camera);
            pose.translate(offset.x, offset.y, offset.z);
            pose.mulPose(Axis.YP.rotationDegrees(-frame.yaw()));
            if (pass == Pass.CUT_LIGHT) {
                drawCut(pose, buffer, meshes.cut(), frame);
            } else if (frame.age() >= SHARDS_TICK - 3) {
                drawFracture(pose, buffer, meshes.fracture(), frame, pass == Pass.SHARD_RIM);
            }
            pose.popPose();
        }
        buffers.endBatch(layer);
    }

    private static void drawCut(PoseStack pose, VertexConsumer buffer, PhainonVfxModelData.BakedMesh mesh, Frame frame) {
        float firstPersonFade = frame.firstPerson() ? 0.66F : 1.0F;
        float age = frame.age();
        float elapsed = age - SLASH_TICK;
        int side = frame.side();
        if (elapsed < 0) {
            float charge = smooth((age - 0.7F) / 3.1F) * firstPersonFade;
            float draw = smooth(age / 5);
            float edgeX = -side * (.56F + draw * .31F);
            float edgeY = 1.10F + .20F * draw;
            float edgeZ = .44F + .39F * draw;
            blade(pose, buffer, mesh, edgeX, edgeY, edgeZ,
                    side * 56F, .18F + .51F * draw, .48F, charge, .89F, .54F);
            float glint = smooth((age - 2) / 1.7F) * (1 - smooth((age - 4) / 2));
            blade(pose, buffer, mesh, edgeX + side * .16F, edgeY + .24F, edgeZ,
                    -side * 32F, .27F, .35F, firstPersonFade * glint, 1, .86F);
        }

        if (frame.travel() > 0 && age > 3 && age < 12) {
            float streak = smooth((age - 3) / 2) * (1 - smooth((age - 7) / 5)) * firstPersonFade;
            for (int i = 0; i < 3; i++) {
                pose.pushPose();
                pose.translate(side * (-0.59F + 0.53F * i), 0.07F + i * 0.018F, 0.12F + frame.travel() * 0.56F);
                pose.mulPose(Axis.YP.rotationDegrees(77 + i * 11));
                pose.mulPose(Axis.XP.rotationDegrees(66));
                blade(pose, buffer, mesh, 0, 0, 0, 0, 0.44F + frame.travel() * 0.51F, 0.22F,
                        streak * (0.37F + 0.09F * i), 0.63F, 0.50F);
                pose.popPose();
            }
        }

        if (elapsed >= -2 && elapsed < 8) sweep(pose, buffer, frame, elapsed);
        if (elapsed < 0 || elapsed >= 13) return;
        float unfold = smooth((elapsed + 0.20F) / 3.2F);
        float fade = (1 - smooth((elapsed - 2.4F) / 10.6F)) * firstPersonFade;
        // Keep the cut's leading tip travelling across the target while the trailing edge lingers.
        float left = Mth.lerp(smooth((elapsed - 3) / 10), -1.85F, .95F);
        float right = Mth.lerp(unfold, -1.65F, 2.65F);
        float center = side * (left + right) * .5F;
        float length = (right - left) * .5F;
        float reach = frame.reach();
        float height = strikeHeight(frame);
        blade(pose, buffer, mesh, center, height, reach, side * -8F, length, 1.65F,
                fade * .83F, .58F, .48F);
        blade(pose, buffer, mesh, center, height + .025F, reach + .02F,
                side * -8F, length, .44F, fade, 1, .86F);
        float glint = fade * (1 - smooth((elapsed - 2) / 3));
        blade(pose, buffer, mesh, side * right, height - right * .13F, reach,
                side * 55F, .45F, .64F, glint, .96F, .73F);

        float splinter = fade * (1 - smooth((elapsed - 0.8F) / 5.4F));
        blade(pose, buffer, mesh, side * 0.42F, height + 0.12F, reach + 0.17F, side * 62F,
                0.45F + unfold * 0.86F, 0.39F, splinter * 0.70F, 0.62F, 0.61F);
        blade(pose, buffer, mesh, side * -0.96F, height + 0.26F, reach + 0.38F, side * -22F,
                0.72F * unfold, 0.29F, splinter * 0.46F, 0.77F, 0.66F);
    }

    /** A tapered ribbon follows the outer edge of the swing and peels away behind the blade. */
    private static void sweep(PoseStack pose, VertexConsumer buffer, Frame frame, float elapsed) {
        float head = smooth((elapsed + 2) / 5);
        float tail = smooth((elapsed + .4F) / 8.4F);
        if (head <= tail) return;
        float fade = (1 - smooth((elapsed - 2) / 6)) * (frame.firstPerson() ? .68F : .91F);
        for (int segment = 0; segment < 20; segment++) {
            float u = segment / 20F, next = (segment + 1) / 20F;
            sweepVertex(pose, buffer, frame, tail, head, u, 0, fade);
            sweepVertex(pose, buffer, frame, tail, head, next, 0, fade);
            sweepVertex(pose, buffer, frame, tail, head, next, 1, fade);
            sweepVertex(pose, buffer, frame, tail, head, u, 0, fade);
            sweepVertex(pose, buffer, frame, tail, head, next, 1, fade);
            sweepVertex(pose, buffer, frame, tail, head, u, 1, fade);
        }
    }

    private static void sweepVertex(PoseStack pose, VertexConsumer buffer, Frame frame, float tail, float head,
                                    float u, int edge, float alpha) {
        float t = Mth.lerp(u, tail, head);
        float angle = Mth.lerp(t, -1.32F, 1.36F);
        float width = (.09F + .66F * Mth.sin(Mth.PI * u)) * (edge - .5F);
        float x = frame.side() * Mth.sin(angle) * (1.95F + width);
        float z = frame.reach() - (1 - Mth.cos(angle)) * Math.min(1.30F, frame.reach() * .55F)
                + width * Mth.cos(angle);
        float y = strikeHeight(frame) + .29F - .60F * t + width * .42F;
        buffer.addVertex(pose.last().pose(), x, y, z).setColor(1F, .65F + .30F * u, .56F, alpha)
                .setUv(u, edge).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0, 1, 0);
    }

    private static void blade(PoseStack pose, VertexConsumer buffer, PhainonVfxModelData.BakedMesh mesh,
                              float x, float y, float z, float roll, float length, float width,
                              float alpha, float green, float blue) {
        if (alpha <= 0.005F || length <= 0.01F) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.scale(length, width, 0.87F);
        mesh.renderAdditive(pose, buffer, 1.0F, green, blue, alpha, 0, 0);
        pose.popPose();
    }

    private static void drawFracture(PoseStack pose, VertexConsumer buffer, PhainonVfxModelData.BakedMesh mesh,
                                     Frame frame, boolean rim) {
        float height = strikeHeight(frame);
        for (int i = 0; i < 18; i++) {
            // Interleave large splinters and fine chips, seeded by index for stable multiplayer playback.
            float lane = (i * 7 % 18) / 17F;
            float horizontal = 2 * lane - 1;
            float scatter = Mth.sin(i * 2.39996F);
            float life = frame.age() - SHARDS_TICK + 3 - lane * 4.4F;
            if (life <= 0) continue;
            float rise = smooth(life / 2.3F);
            float disperse = 1 - (float) Math.exp(-life * .105F);
            float fall = smooth((life - 7) / 14);
            float vanish = 1 - smooth((frame.age() - 24) / (EFFECT_TICKS - 24F));
            float x = horizontal * (1.32F + 1.28F * disperse) + .23F * disperse;
            float y = Math.max(.09F, height - .18F * horizontal + scatter * .10F
                    + disperse * (.47F + 1.22F * scatter) - fall * .80F);
            float z = frame.reach() + disperse * (.38F + .89F * Mth.cos(i * 2.39996F));
            float size = (i % 4 == 0 ? .42F : .12F + .045F * (i % 5)) * rise * vanish;
            float rimScale = (frame.firstPerson() ? .8F : 1F) * (rim ? 1.035F : 1F);
            if (size < 0.015F) continue;

            pose.pushPose();
            pose.translate(x * frame.side(), y, z);
            pose.mulPose(Axis.YP.rotationDegrees((i * 13 % 47 - 23) + disperse * scatter * 112));
            pose.mulPose(Axis.ZP.rotationDegrees((i * 3 % 11 - 5) + disperse * horizontal * 57));
            pose.scale(size * rimScale, size * (1.04F + (i % 4) * 0.66F) * rimScale, size * 0.53F * rimScale);
            if (rim) {
                float surge = 0.80F + 0.18F * Mth.sin(life * 0.83F + i * 2.1F);
                mesh.renderAdditive(pose, buffer, 1.0F, 0.83F, 0.84F,
                        rise * vanish * surge * (frame.firstPerson() ? 0.68F : 1.0F), 0, 0);
            } else {
                mesh.renderSolid(pose, buffer, LightTexture.pack(8, 13), 255, 255, 255);
            }
            pose.popPose();
        }
    }

    private static float strikeHeight(Frame frame) {
        return Mth.clamp(1.22F - frame.pitch() * frame.reach() * 0.007F, 0.40F, 2.6F);
    }

    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, path);
    }
}
