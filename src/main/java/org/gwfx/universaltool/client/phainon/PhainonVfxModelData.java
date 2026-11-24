package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 延迟加载并预烘焙 Blockbench 导出的 4 套侵晨 VFX Wavefront OBJ 网格模型。
 * <p>模型位置：{@code assets/universal_tool/models/vfx/*.obj}，支持资源包重载时自动清理缓存。</p>
 */
public final class PhainonVfxModelData {

    private static final ResourceLocation SUNBURST_WHEEL_OBJ = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/vfx/phainon_sunburst_wheel.obj");
    private static final ResourceLocation CRESCENT_WAVE_OBJ = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/vfx/phainon_crescent_wave.obj");
    private static final ResourceLocation SLASH_BEAM_OBJ = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/vfx/phainon_slash_beam.obj");
    private static final ResourceLocation DEBRIS_SHARD_OBJ = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/vfx/phainon_debris_shard.obj");

    /** 单个烘焙三角面占用的 float 数：3 个顶点 × (x, y, z, u, v, nx, ny, nz) = 24 */
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTICES_PER_TRIANGLE = 3;
    private static final int FLOATS_PER_TRIANGLE = FLOATS_PER_VERTEX * VERTICES_PER_TRIANGLE;

    private static volatile PhainonVfxModelData instance;

    private final BakedMesh sunburstWheel;
    private final BakedMesh crescentWave;
    private final BakedMesh slashBeam;
    private final BakedMesh debrisShard;

    private PhainonVfxModelData(BakedMesh sunburstWheel,
                                BakedMesh crescentWave,
                                BakedMesh slashBeam,
                                BakedMesh debrisShard) {
        this.sunburstWheel = sunburstWheel;
        this.crescentWave = crescentWave;
        this.slashBeam = slashBeam;
        this.debrisShard = debrisShard;
    }

    /**
     * 资源包重载时清理预烘焙网格缓存。
     */
    public static void invalidate() {
        instance = null;
    }

    /**
     * 获取或延迟烘焙 4 套 VFX 网格实例。
     */
    @Nullable
    public static PhainonVfxModelData getOrLoad() {
        PhainonVfxModelData cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (PhainonVfxModelData.class) {
            if (instance != null) {
                return instance;
            }
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            BakedMesh wheel = loadObjMesh(manager, SUNBURST_WHEEL_OBJ);
            BakedMesh wave = loadObjMesh(manager, CRESCENT_WAVE_OBJ);
            BakedMesh beam = loadObjMesh(manager, SLASH_BEAM_OBJ);
            BakedMesh debris = loadObjMesh(manager, DEBRIS_SHARD_OBJ);
            if (wheel == null || wave == null || beam == null || debris == null) {
                return null;
            }
            instance = new PhainonVfxModelData(wheel, wave, beam, debris);
            return instance;
        }
    }

    public BakedMesh sunburstWheel() {
        return sunburstWheel;
    }

    public BakedMesh crescentWave() {
        return crescentWave;
    }

    public BakedMesh slashBeam() {
        return slashBeam;
    }

    public BakedMesh debrisShard() {
        return debrisShard;
    }

    /**
     * 预烘焙的纯三角面网格，支持叠加发光着色/UV滚动以及实心光照绘制。
     */
    public static final class BakedMesh {
        private final float[] triangles;
        private final int triangleCount;

        private BakedMesh(float[] triangles) {
            this.triangles = triangles;
            this.triangleCount = triangles.length / FLOATS_PER_TRIANGLE;
        }

        /**
         * 以全亮 Additive 模式渲染网格，支持统一染色、透明度渐隐与 UV 流动偏移。
         */
        public void renderAdditive(PoseStack poseStack,
                                   VertexConsumer buffer,
                                   float r, float g, float b, float alpha,
                                   float uOffset, float vOffset) {
            if (triangleCount == 0 || alpha <= 0.005F) {
                return;
            }
            int color = packColor(r, g, b, alpha);
            int light = LightTexture.FULL_BRIGHT;
            int overlay = OverlayTexture.NO_OVERLAY;
            PoseStack.Pose lastPose = poseStack.last();
            Matrix4f mat = lastPose.pose();
            Vector3f normalScratch = new Vector3f();

            for (int t = 0; t < triangleCount; t++) {
                int base = t * FLOATS_PER_TRIANGLE;
                for (int v = 0; v < VERTICES_PER_TRIANGLE; v++) {
                    int off = base + v * FLOATS_PER_VERTEX;
                    float x = triangles[off];
                    float y = triangles[off + 1];
                    float z = triangles[off + 2];
                    float u = triangles[off + 3] + uOffset;
                    float vv = triangles[off + 4] + vOffset;
                    float nx = triangles[off + 5];
                    float ny = triangles[off + 6];
                    float nz = triangles[off + 7];

                    normalScratch.set(nx, ny, nz);
                    lastPose.transformNormal(normalScratch, normalScratch);

                    buffer.addVertex(mat, x, y, z)
                            .setColor(color)
                            .setUv(u, vv)
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(normalScratch.x, normalScratch.y, normalScratch.z);
                }
            }
        }

        /**
         * 以实心受光模式渲染网格（用于飞溅碎石块等实体化构件）。
         */
        public void renderSolid(PoseStack poseStack,
                                VertexConsumer buffer,
                                int packedLight,
                                int r, int g, int b) {
            if (triangleCount == 0) {
                return;
            }
            int color = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            int overlay = OverlayTexture.NO_OVERLAY;
            PoseStack.Pose lastPose = poseStack.last();
            Matrix4f mat = lastPose.pose();
            Vector3f normalScratch = new Vector3f();

            for (int t = 0; t < triangleCount; t++) {
                int base = t * FLOATS_PER_TRIANGLE;
                for (int v = 0; v < VERTICES_PER_TRIANGLE; v++) {
                    int off = base + v * FLOATS_PER_VERTEX;
                    float x = triangles[off];
                    float y = triangles[off + 1];
                    float z = triangles[off + 2];
                    float u = triangles[off + 3];
                    float vv = triangles[off + 4];
                    float nx = triangles[off + 5];
                    float ny = triangles[off + 6];
                    float nz = triangles[off + 7];

                    normalScratch.set(nx, ny, nz);
                    lastPose.transformNormal(normalScratch, normalScratch);

                    buffer.addVertex(mat, x, y, z)
                            .setColor(color)
                            .setUv(u, vv)
                            .setOverlay(overlay)
                            .setLight(packedLight)
                            .setNormal(normalScratch.x, normalScratch.y, normalScratch.z);
                }
            }
        }
    }

    @Nullable
    static BakedMesh loadObjMesh(ResourceManager manager, ResourceLocation location) {
        Optional<Resource> res = manager.getResource(location);
        if (res.isEmpty()) {
            UniversalToolMod.LOGGER.warn("Missing Phainon VFX OBJ model: {}", location);
            return null;
        }

        List<float[]> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<Float> outTriangles = new ArrayList<>();

        try (BufferedReader reader = res.get().openAsReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> {
                        if (parts.length >= 4) {
                            positions.add(new float[]{
                                    Float.parseFloat(parts[1]),
                                    Float.parseFloat(parts[2]),
                                    Float.parseFloat(parts[3])
                            });
                        }
                    }
                    case "vt" -> {
                        if (parts.length >= 3) {
                            // Blockbench 导出 OBJ 时写出了 (u, 1 - v)，这里转回 Minecraft 顶部为 0 的 V 轴
                            float u = Float.parseFloat(parts[1]);
                            float v = 1.0F - Float.parseFloat(parts[2]);
                            texCoords.add(new float[]{u, v});
                        }
                    }
                    case "vn" -> {
                        if (parts.length >= 4) {
                            normals.add(new float[]{
                                    Float.parseFloat(parts[1]),
                                    Float.parseFloat(parts[2]),
                                    Float.parseFloat(parts[3])
                            });
                        }
                    }
                    case "f" -> {
                        if (parts.length >= 4) {
                            int vertCount = parts.length - 1;
                            int[][] faceIndices = new int[vertCount][3];
                            for (int i = 0; i < vertCount; i++) {
                                faceIndices[i] = parseFaceVertex(parts[i + 1], positions.size(), texCoords.size(), normals.size());
                            }
                            // 多边形（三角形/四边形等）使用三角扇拆分，兼容 TRIANGLES 渲染模式
                            for (int i = 1; i + 1 < vertCount; i++) {
                                appendVertex(outTriangles, positions, texCoords, normals, faceIndices[0]);
                                appendVertex(outTriangles, positions, texCoords, normals, faceIndices[i]);
                                appendVertex(outTriangles, positions, texCoords, normals, faceIndices[i + 1]);
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            UniversalToolMod.LOGGER.error("Failed to load Phainon VFX OBJ model: {}", location, e);
            return null;
        }

        float[] array = new float[outTriangles.size()];
        for (int i = 0; i < outTriangles.size(); i++) {
            array[i] = outTriangles.get(i);
        }
        return new BakedMesh(array);
    }

    private static int[] parseFaceVertex(String token, int posCount, int uvCount, int normCount) {
        String[] segs = token.split("/");
        int posIdx = resolveObjIndex(segs[0], posCount);
        int uvIdx = segs.length > 1 && !segs[1].isEmpty() ? resolveObjIndex(segs[1], uvCount) : -1;
        int normIdx = segs.length > 2 && !segs[2].isEmpty() ? resolveObjIndex(segs[2], normCount) : -1;
        return new int[]{posIdx, uvIdx, normIdx};
    }

    private static int resolveObjIndex(String raw, int size) {
        int idx = Integer.parseInt(raw);
        return idx < 0 ? size + idx : idx - 1;
    }

    private static void appendVertex(List<Float> out,
                                     List<float[]> positions,
                                     List<float[]> texCoords,
                                     List<float[]> normals,
                                     int[] indices) {
        float[] p = (indices[0] >= 0 && indices[0] < positions.size())
                ? positions.get(indices[0]) : new float[]{0.0F, 0.0F, 0.0F};
        float[] uv = (indices[1] >= 0 && indices[1] < texCoords.size())
                ? texCoords.get(indices[1]) : new float[]{0.0F, 0.0F};
        float[] n = (indices[2] >= 0 && indices[2] < normals.size())
                ? normals.get(indices[2]) : new float[]{0.0F, 1.0F, 0.0F};

        out.add(p[0]);
        out.add(p[1]);
        out.add(p[2]);
        out.add(uv[0]);
        out.add(uv[1]);
        out.add(n[0]);
        out.add(n[1]);
        out.add(n[2]);
    }

    private static int packColor(float r, float g, float b, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int ri = (int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = (int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
