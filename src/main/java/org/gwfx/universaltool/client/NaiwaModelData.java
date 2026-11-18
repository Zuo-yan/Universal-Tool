package org.gwfx.universaltool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lazily loads {@code assets/universal_tool/models/entity/naiwa.json}, bakes the
 * hierarchical bone mesh quads in bone-local block units, and evaluates Blockbench
 * keyframe animations ({@code 捧腹大笑} and {@code 呼吸待机}) plus procedural head/limb motion.
 */
public final class NaiwaModelData {

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/entity/naiwa.png");
    private static final ResourceLocation MODEL_LOCATION = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/entity/naiwa.json");

    private static volatile NaiwaModelData instance;

    private final float unitsPerBlock;
    private final BoneNode rootBone;
    @Nullable
    private final AnimationClip laughAnim;
    @Nullable
    private final AnimationClip idleAnim;

    private NaiwaModelData(float unitsPerBlock,
                           BoneNode rootBone,
                           @Nullable AnimationClip laughAnim,
                           @Nullable AnimationClip idleAnim) {
        this.unitsPerBlock = unitsPerBlock;
        this.rootBone = rootBone;
        this.laughAnim = laughAnim;
        this.idleAnim = idleAnim;
    }

    /**
     * Returns the lazily loaded Naiwa model, or {@code null} if loading fails.
     */
    @Nullable
    public static NaiwaModelData getOrLoad() {
        NaiwaModelData cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (NaiwaModelData.class) {
            if (instance == null) {
                instance = loadFromResources();
            }
            return instance;
        }
    }

    /**
     * Clears the cached model so resource reloads re-read geometry and keyframes.
     */
    public static void invalidate() {
        instance = null;
    }

    /**
     * Renders Naiwa's full bone hierarchy at the current PoseStack origin.
     */
    public void render(PoseStack poseStack,
                       VertexConsumer consumer,
                       int packedLight,
                       int packedOverlay,
                       float animTimeSeconds,
                       boolean laughing,
                       float netHeadYawDeg,
                       float headPitchDeg,
                       float limbSwing,
                       float limbSwingAmount) {
        AnimationClip clip = laughing ? laughAnim : idleAnim;
        float evalTime = 0.0F;
        if (clip != null) {
            if (clip.loop && clip.length > 1.0E-5F) {
                evalTime = animTimeSeconds % clip.length;
                if (evalTime < 0.0F) {
                    evalTime += clip.length;
                }
            } else {
                evalTime = Mth.clamp(animTimeSeconds, 0.0F, clip.length);
            }
        }

        float clampedSwing = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float legSwingDeg = Mth.cos(limbSwing * 0.6662F) * 42.0F * clampedSwing;
        if (laughing) {
            legSwingDeg *= 0.35F;
        }
        float clampedHeadYaw = Mth.clamp(netHeadYawDeg, -55.0F, 55.0F);
        float clampedHeadPitch = Mth.clamp(headPitchDeg, -35.0F, 35.0F);

        RenderScratch scratch = new RenderScratch();
        renderBone(poseStack, consumer, rootBone, clip, evalTime, laughing,
                clampedHeadYaw, clampedHeadPitch, legSwingDeg,
                packedLight, packedOverlay, scratch);
    }

    private void renderBone(PoseStack poseStack,
                            VertexConsumer consumer,
                            BoneNode bone,
                            @Nullable AnimationClip clip,
                            float evalTime,
                            boolean laughing,
                            float netHeadYawDeg,
                            float headPitchDeg,
                            float legSwingDeg,
                            int packedLight,
                            int packedOverlay,
                            RenderScratch scratch) {
        float posX = 0.0F;
        float posY = 0.0F;
        float posZ = 0.0F;
        float rotX = 0.0F;
        float rotY = 0.0F;
        float rotZ = 0.0F;
        float scaleX = 1.0F;
        float scaleY = 1.0F;
        float scaleZ = 1.0F;

        if (clip != null) {
            BoneChannels channels = clip.bones.get(bone.name);
            if (channels != null) {
                if (channels.position != null) {
                    channels.position.sample(evalTime, clip.loop, scratch.vec);
                    posX = scratch.vec.x;
                    posY = scratch.vec.y;
                    posZ = scratch.vec.z;
                }
                if (channels.rotation != null) {
                    channels.rotation.sample(evalTime, clip.loop, scratch.vec);
                    rotX = scratch.vec.x;
                    rotY = scratch.vec.y;
                    rotZ = scratch.vec.z;
                }
                if (channels.scale != null) {
                    channels.scale.sample(evalTime, clip.loop, scratch.vec);
                    scaleX = scratch.vec.x;
                    scaleY = scratch.vec.y;
                    scaleZ = scratch.vec.z;
                }
            }
        }

        // Procedural look & walk-cycle additions
        switch (bone.name) {
            case "头部" -> {
                rotX -= headPitchDeg;
                rotY -= netHeadYawDeg;
            }
            case "左腿" -> rotX += legSwingDeg;
            case "右腿" -> rotX -= legSwingDeg;
            case "左上臂" -> {
                if (!laughing) {
                    rotX -= legSwingDeg * 0.4F;
                }
            }
            case "右上臂" -> {
                if (!laughing) {
                    rotX += legSwingDeg * 0.4F;
                }
            }
            default -> {
            }
        }

        poseStack.pushPose();

        float tx = (bone.offsetX + posX) / unitsPerBlock;
        float ty = (bone.offsetY + posY) / unitsPerBlock;
        float tz = (bone.offsetZ + posZ) / unitsPerBlock;
        if (tx != 0.0F || ty != 0.0F || tz != 0.0F) {
            poseStack.translate(tx, ty, tz);
        }

        float finalRx = bone.rotX + rotX;
        float finalRy = bone.rotY + rotY;
        float finalRz = bone.rotZ + rotZ;
        if (Math.abs(finalRx) > 1.0E-5F || Math.abs(finalRy) > 1.0E-5F || Math.abs(finalRz) > 1.0E-5F) {
            scratch.quat.identity().rotateZYX(
                    (float) Math.toRadians(finalRz),
                    (float) Math.toRadians(finalRy),
                    (float) Math.toRadians(finalRx));
            poseStack.mulPose(scratch.quat);
        }

        boolean scaled = Math.abs(scaleX - 1.0F) > 1.0E-5F
                || Math.abs(scaleY - 1.0F) > 1.0E-5F
                || Math.abs(scaleZ - 1.0F) > 1.0E-5F;
        if (scaled) {
            poseStack.scale(scaleX, scaleY, scaleZ);
        }

        if (!bone.quads.isEmpty()) {
            emitQuads(poseStack.last(), consumer, bone.quads, packedLight, packedOverlay,
                    scaled, scratch.pos, scratch.norm);
        }

        for (int i = 0, n = bone.children.size(); i < n; i++) {
            renderBone(poseStack, consumer, bone.children.get(i), clip, evalTime, laughing,
                    netHeadYawDeg, headPitchDeg, legSwingDeg, packedLight, packedOverlay, scratch);
        }

        poseStack.popPose();
    }

    private static void emitQuads(PoseStack.Pose pose,
                                  VertexConsumer consumer,
                                  List<BakedQuad> quads,
                                  int packedLight,
                                  int packedOverlay,
                                  boolean normalizeNormals,
                                  Vector3f tmpPos,
                                  Vector3f tmpNorm) {
        Matrix4f mat = pose.pose();
        for (int i = 0, n = quads.size(); i < n; i++) {
            BakedQuad q = quads.get(i);
            pose.transformNormal(q.nx, q.ny, q.nz, tmpNorm);
            if (normalizeNormals) {
                float lenSq = tmpNorm.lengthSquared();
                if (lenSq > 1.0E-8F) {
                    tmpNorm.div((float) Math.sqrt(lenSq));
                }
            }
            float nx = tmpNorm.x;
            float ny = tmpNorm.y;
            float nz = tmpNorm.z;

            mat.transformPosition(q.x0, q.y0, q.z0, tmpPos);
            consumer.addVertex(tmpPos.x, tmpPos.y, tmpPos.z)
                    .setColor(255, 255, 255, 255)
                    .setUv(q.u0, q.v0)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(nx, ny, nz);

            mat.transformPosition(q.x1, q.y1, q.z1, tmpPos);
            consumer.addVertex(tmpPos.x, tmpPos.y, tmpPos.z)
                    .setColor(255, 255, 255, 255)
                    .setUv(q.u1, q.v1)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(nx, ny, nz);

            mat.transformPosition(q.x2, q.y2, q.z2, tmpPos);
            consumer.addVertex(tmpPos.x, tmpPos.y, tmpPos.z)
                    .setColor(255, 255, 255, 255)
                    .setUv(q.u2, q.v2)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(nx, ny, nz);

            mat.transformPosition(q.x3, q.y3, q.z3, tmpPos);
            consumer.addVertex(tmpPos.x, tmpPos.y, tmpPos.z)
                    .setColor(255, 255, 255, 255)
                    .setUv(q.u3, q.v3)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(nx, ny, nz);
        }
    }

    @Nullable
    private static NaiwaModelData loadFromResources() {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        try (Reader reader = rm.openAsReader(MODEL_LOCATION)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            float texWidth = root.has("texture_width") ? root.get("texture_width").getAsFloat() : 512.0F;
            float texHeight = root.has("texture_height") ? root.get("texture_height").getAsFloat() : 512.0F;
            float unitsPerBlock = root.has("units_per_block") ? root.get("units_per_block").getAsFloat() : 16.0F;
            if (texWidth <= 0.0F) texWidth = 512.0F;
            if (texHeight <= 0.0F) texHeight = 512.0F;
            if (unitsPerBlock <= 0.0F) unitsPerBlock = 16.0F;

            BoneNode rootBone = parseBone(root.getAsJsonObject("bones"), texWidth, texHeight, unitsPerBlock);

            AnimationClip laughAnim = null;
            AnimationClip idleAnim = null;
            if (root.has("animations") && root.get("animations").isJsonObject()) {
                JsonObject anims = root.getAsJsonObject("animations");
                if (anims.has("laugh") && anims.get("laugh").isJsonObject()) {
                    laughAnim = parseAnimation(anims.getAsJsonObject("laugh"));
                }
                if (anims.has("idle") && anims.get("idle").isJsonObject()) {
                    idleAnim = parseAnimation(anims.getAsJsonObject("idle"));
                }
            }

            return new NaiwaModelData(unitsPerBlock, rootBone, laughAnim, idleAnim);
        } catch (Exception e) {
            UniversalToolMod.LOGGER.error("Failed to load Naiwa model from {}", MODEL_LOCATION, e);
            return null;
        }
    }

    private static BoneNode parseBone(JsonObject obj, float texWidth, float texHeight, float unitsPerBlock) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "";
        float[] offset = readVec3(obj.getAsJsonArray("offset"), 0.0F, 0.0F, 0.0F);
        float[] rot = readVec3(obj.getAsJsonArray("rotation"), 0.0F, 0.0F, 0.0F);

        List<BakedQuad> quads = new ArrayList<>();
        if (obj.has("quads") && obj.get("quads").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("quads")) {
                BakedQuad quad = parseQuad(el.getAsJsonObject(), texWidth, texHeight, unitsPerBlock);
                if (quad != null) {
                    quads.add(quad);
                }
            }
        }

        List<BoneNode> children = new ArrayList<>();
        if (obj.has("children") && obj.get("children").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("children")) {
                children.add(parseBone(el.getAsJsonObject(), texWidth, texHeight, unitsPerBlock));
            }
        }

        return new BoneNode(
                name,
                offset[0], offset[1], offset[2],
                rot[0], rot[1], rot[2],
                List.copyOf(quads),
                List.copyOf(children)
        );
    }

    @Nullable
    private static BakedQuad parseQuad(JsonObject obj, float texWidth, float texHeight, float unitsPerBlock) {
        JsonArray verts = obj.getAsJsonArray("vertices");
        if (verts == null || verts.size() < 4) {
            return null;
        }
        float[] norm = readVec3(obj.getAsJsonArray("normal"), 0.0F, 1.0F, 0.0F);

        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        float[] us = new float[4];
        float[] vs = new float[4];

        for (int i = 0; i < 4; i++) {
            JsonObject v = verts.get(i).getAsJsonObject();
            JsonArray posArr = v.getAsJsonArray("pos");
            JsonArray uvArr = v.getAsJsonArray("uv");
            xs[i] = posArr.get(0).getAsFloat() / unitsPerBlock;
            ys[i] = posArr.get(1).getAsFloat() / unitsPerBlock;
            zs[i] = posArr.get(2).getAsFloat() / unitsPerBlock;
            us[i] = uvArr.get(0).getAsFloat() / texWidth;
            vs[i] = uvArr.get(1).getAsFloat() / texHeight;
        }

        return new BakedQuad(
                xs[0], ys[0], zs[0], us[0], vs[0],
                xs[1], ys[1], zs[1], us[1], vs[1],
                xs[2], ys[2], zs[2], us[2], vs[2],
                xs[3], ys[3], zs[3], us[3], vs[3],
                norm[0], norm[1], norm[2]
        );
    }

    private static AnimationClip parseAnimation(JsonObject obj) {
        float length = obj.has("length") ? obj.get("length").getAsFloat() : 1.0F;
        boolean loop = !obj.has("loop") || obj.get("loop").getAsBoolean();
        Map<String, BoneChannels> boneMap = new HashMap<>();

        if (obj.has("bones") && obj.get("bones").isJsonObject()) {
            JsonObject bonesObj = obj.getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> entry : bonesObj.entrySet()) {
                JsonObject chObj = entry.getValue().getAsJsonObject();
                ChannelTrack rotTrack = parseChannel(chObj.getAsJsonArray("rotation"));
                ChannelTrack posTrack = parseChannel(chObj.getAsJsonArray("position"));
                ChannelTrack scaleTrack = parseChannel(chObj.getAsJsonArray("scale"));
                if (rotTrack != null || posTrack != null || scaleTrack != null) {
                    boneMap.put(entry.getKey(), new BoneChannels(rotTrack, posTrack, scaleTrack));
                }
            }
        }
        return new AnimationClip(length, loop, Map.copyOf(boneMap));
    }

    @Nullable
    private static ChannelTrack parseChannel(@Nullable JsonArray arr) {
        if (arr == null || arr.isEmpty()) {
            return null;
        }
        List<Keyframe> frames = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject kf = el.getAsJsonObject();
            float time = kf.has("time") ? kf.get("time").getAsFloat() : 0.0F;
            float[] val = readVec3(kf.getAsJsonArray("val"), 0.0F, 0.0F, 0.0F);
            String interp = kf.has("interp") ? kf.get("interp").getAsString() : "linear";
            frames.add(new Keyframe(time, val[0], val[1], val[2], "catmullrom".equals(interp), "step".equals(interp)));
        }
        frames.sort((a, b) -> Float.compare(a.time, b.time));
        return new ChannelTrack(List.copyOf(frames));
    }

    private static float[] readVec3(@Nullable JsonArray arr, float defX, float defY, float defZ) {
        if (arr == null || arr.size() < 3) {
            return new float[]{defX, defY, defZ};
        }
        return new float[]{
                arr.get(0).getAsFloat(),
                arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat()
        };
    }

    private static final class RenderScratch {
        final Vector3f vec = new Vector3f();
        final Vector3f pos = new Vector3f();
        final Vector3f norm = new Vector3f();
        final Quaternionf quat = new Quaternionf();
    }

    private record BoneNode(
            String name,
            float offsetX, float offsetY, float offsetZ,
            float rotX, float rotY, float rotZ,
            List<BakedQuad> quads,
            List<BoneNode> children) {
    }

    private record BakedQuad(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float nx, float ny, float nz) {
    }

    private record AnimationClip(float length, boolean loop, Map<String, BoneChannels> bones) {
    }

    private record BoneChannels(
            @Nullable ChannelTrack rotation,
            @Nullable ChannelTrack position,
            @Nullable ChannelTrack scale) {
    }

    private record Keyframe(float time, float x, float y, float z, boolean catmullRom, boolean step) {
    }

    private record ChannelTrack(List<Keyframe> frames) {
        void sample(float t, boolean loop, Vector3f out) {
            int n = frames.size();
            if (n == 0) {
                out.set(0.0F, 0.0F, 0.0F);
                return;
            }
            if (n == 1 || t <= frames.get(0).time) {
                Keyframe k = frames.get(0);
                out.set(k.x, k.y, k.z);
                return;
            }
            Keyframe last = frames.get(n - 1);
            if (t >= last.time) {
                out.set(last.x, last.y, last.z);
                return;
            }

            int idx = 0;
            for (int i = 0; i < n - 1; i++) {
                if (t >= frames.get(i).time && t <= frames.get(i + 1).time) {
                    idx = i;
                    break;
                }
            }

            Keyframe k1 = frames.get(idx);
            Keyframe k2 = frames.get(idx + 1);
            float dt = k2.time - k1.time;
            if (dt <= 1.0E-6F || k1.step) {
                out.set(k1.x, k1.y, k1.z);
                return;
            }
            float u = Mth.clamp((t - k1.time) / dt, 0.0F, 1.0F);

            if (k1.catmullRom || k2.catmullRom) {
                Keyframe k0 = idx > 0 ? frames.get(idx - 1) : (loop && n > 2 ? frames.get(n - 2) : k1);
                Keyframe k3 = (idx + 2 < n) ? frames.get(idx + 2) : (loop && n > 2 ? frames.get(1) : k2);
                out.set(
                        catmull(k0.x, k1.x, k2.x, k3.x, u),
                        catmull(k0.y, k1.y, k2.y, k3.y, u),
                        catmull(k0.z, k1.z, k2.z, k3.z, u)
                );
            } else {
                out.set(
                        Mth.lerp(u, k1.x, k2.x),
                        Mth.lerp(u, k1.y, k2.y),
                        Mth.lerp(u, k1.z, k2.z)
                );
            }
        }

        private static float catmull(float p0, float p1, float p2, float p3, float u) {
            float u2 = u * u;
            float u3 = u2 * u;
            return 0.5F * ((2.0F * p1)
                    + (-p0 + p2) * u
                    + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * u2
                    + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * u3);
        }
    }
}
