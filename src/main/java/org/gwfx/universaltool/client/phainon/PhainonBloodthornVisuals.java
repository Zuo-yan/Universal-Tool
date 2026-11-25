package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
            } else if (frame.age() >= SHARDS_TICK) {
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
            blade(pose, buffer, mesh, -side * 0.67F, 1.09F, 0.39F + 0.08F * age,
                    side * 56F, 0.48F, 0.40F, charge * 0.68F, 0.82F, 0.63F);
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

        if (elapsed < 0 || elapsed >= 12) return;
        float unfold = smooth((elapsed + 0.52F) / 2.5F);
        float fade = (1 - smooth((elapsed - 1.9F) / 10.1F)) * firstPersonFade;
        float center = side * Mth.lerp(unfold, -0.64F, 0.21F);
        float length = Mth.lerp(unfold, 0.72F, 2.54F);
        float reach = frame.reach();
        float height = strikeHeight(frame);
        blade(pose, buffer, mesh, center, height, reach, side * -8F, length, 1.17F, fade * 0.76F, 0.82F, 0.75F);
        blade(pose, buffer, mesh, center + 0.13F * side, height - 0.085F, reach + 0.12F,
                side * -11F, length * 0.92F, 0.36F, fade * 0.93F, 1.0F, 0.89F);

        float splinter = fade * (1 - smooth((elapsed - 0.8F) / 5.4F));
        blade(pose, buffer, mesh, side * 0.42F, height + 0.12F, reach + 0.17F, side * 62F,
                0.45F + unfold * 0.86F, 0.39F, splinter * 0.70F, 0.62F, 0.61F);
        blade(pose, buffer, mesh, side * -0.96F, height + 0.26F, reach + 0.38F, side * -22F,
                0.72F * unfold, 0.29F, splinter * 0.46F, 0.77F, 0.66F);
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
            int column = i % 6;
            int row = i / 6;
            float horizontal = column - 2.5F;
            float delay = Math.abs(horizontal) * 0.62F + row * 1.2F + (i % 2) * 0.38F;
            float life = frame.age() - SHARDS_TICK - delay;
            if (life <= 0) continue;
            float rise = smooth(life / 3.5F);
            float disperse = smooth((life - 4) / 14);
            float vanish = 1 - smooth((frame.age() - 24) / (EFFECT_TICKS - 24.0F));
            float x = horizontal * (0.43F + 0.37F * disperse) + (row - 1) * 0.13F;
            float y = Math.max(0.12F, height + (row - 1) * 0.61F + 0.30F * ((i * 7) % 4 - 1.5F)
                    + rise * 0.28F + disperse * ((i % 3) - 0.65F) * 0.53F);
            float z = frame.reach() + (row - 1) * 0.28F + ((i * 3) % 5) * 0.075F + disperse * 0.61F;
            float size = (0.24F + ((i * 7) % 5) * 0.078F) * rise * vanish;
            float rimScale = rim ? 1.055F : frame.firstPerson() ? 0.80F : 1.0F;
            if (size < 0.015F) continue;

            pose.pushPose();
            pose.translate(x * frame.side(), y, z);
            pose.mulPose(Axis.YP.rotationDegrees((i * 13 % 47 - 23) + disperse * (column - 2) * 32));
            pose.mulPose(Axis.ZP.rotationDegrees((i * 3 % 11 - 5) + disperse * horizontal * 9));
            pose.scale(size * rimScale, size * (1.04F + (i % 4) * 0.66F) * rimScale, size * 0.21F * rimScale);
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
