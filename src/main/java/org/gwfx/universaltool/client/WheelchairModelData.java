package org.gwfx.universaltool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.gwfx.universaltool.UniversalToolMod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lazily loads {@code assets/universal_tool/models/entity/all_terrain_wheelchair.json}
 * and pre-bakes the cubes (with per-cube Blockbench pivot rotations and UV scaling)
 * into part-local quad batches for fast entity and 3D item rendering.
 */
public final class WheelchairModelData {

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/entity/all_terrain_wheelchair.png");
    private static final ResourceLocation MODEL_LOCATION = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/entity/all_terrain_wheelchair.json");

    // Model horizontal center is X=8 in Blockbench 1/16 units; Y=0 sits at wheel contact; Z=0 is chassis origin.
    private static final float MODEL_CENTER_X = 8.0F;
    private static final float MODEL_CENTER_Y = 0.0F;
    private static final float MODEL_CENTER_Z = 0.0F;
    private static final float UNITS_PER_BLOCK = 16.0F;

    private static volatile WheelchairModelData instance;

    private final BakedPart chassis;
    private final BakedPart wheelLeft;
    private final BakedPart wheelRight;
    private final BakedCaster casterLeft;
    private final BakedCaster casterRight;
    private final BakedPart joystick;
    private final List<Nozzle> nozzles;

    private WheelchairModelData(BakedPart chassis,
                                BakedPart wheelLeft,
                                BakedPart wheelRight,
                                BakedCaster casterLeft,
                                BakedCaster casterRight,
                                BakedPart joystick,
                                List<Nozzle> nozzles) {
        this.chassis = chassis;
        this.wheelLeft = wheelLeft;
        this.wheelRight = wheelRight;
        this.casterLeft = casterLeft;
        this.casterRight = casterRight;
        this.joystick = joystick;
        this.nozzles = nozzles;
    }

    /**
     * Returns the lazily loaded and baked wheelchair model, or {@code null} if loading fails.
     */
    public static WheelchairModelData getOrLoad() {
        WheelchairModelData cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (WheelchairModelData.class) {
            if (instance == null) {
                instance = loadFromResources();
            }
            return instance;
        }
    }

    /**
     * Clears the cached model so resource reloads re-bake geometry and UVs cleanly.
     */
    public static void invalidate() {
        instance = null;
    }

    /**
     * Renders the complete wheelchair in centered Blockbench block space
     * (front = -Z, rear = +Z, up = +Y, right = +X, ground contact at Y ~ 0).
     */
    public void render(PoseStack poseStack,
                       VertexConsumer consumer,
                       int packedLight,
                       int packedOverlay,
                       float leftWheelSpin,
                       float rightWheelSpin,
                       float casterYaw,
                       float casterSpin,
                       float joystickPitch,
                       float joystickRoll,
                       Quaternionf nozzleRotation) {
        Vector3f tmpPos = new Vector3f();
        Vector3f tmpNorm = new Vector3f();

        renderPart(poseStack, consumer, chassis, 0.0F, 0.0F, 0.0F, packedLight, packedOverlay, tmpPos, tmpNorm);
        renderPart(poseStack, consumer, wheelLeft, leftWheelSpin, 0.0F, 0.0F, packedLight, packedOverlay, tmpPos, tmpNorm);
        renderPart(poseStack, consumer, wheelRight, rightWheelSpin, 0.0F, 0.0F, packedLight, packedOverlay, tmpPos, tmpNorm);
        renderCaster(poseStack, consumer, casterLeft, casterYaw, casterSpin, packedLight, packedOverlay, tmpPos, tmpNorm);
        renderCaster(poseStack, consumer, casterRight, casterYaw, casterSpin, packedLight, packedOverlay, tmpPos, tmpNorm);
        renderPart(poseStack, consumer, joystick, joystickPitch, 0.0F, joystickRoll, packedLight, packedOverlay, tmpPos, tmpNorm);
        for (Nozzle nozzle : nozzles) {
            poseStack.pushPose();
            nozzle.applyPose(poseStack, nozzleRotation);
            emitQuads(poseStack.last(), consumer, nozzle.geometry.quads, packedLight, packedOverlay, tmpPos, tmpNorm);
            poseStack.popPose();
        }
    }

    List<Nozzle> nozzles() {
        return nozzles;
    }

    record Nozzle(BakedPart geometry, float outletZ) {
        void applyPose(PoseStack stack, Quaternionf rotation) {
            stack.translate(geometry.originX, geometry.originY, geometry.originZ);
            stack.mulPose(rotation);
        }

        Vector3f outlet(Quaternionf rotation) {
            return rotation.transform(new Vector3f(0.0F, 0.0F, outletZ))
                    .add(geometry.originX, geometry.originY, geometry.originZ);
        }
    }

    private static void renderPart(PoseStack poseStack,
                                   VertexConsumer consumer,
                                   BakedPart part,
                                   float rotX,
                                   float rotY,
                                   float rotZ,
                                   int packedLight,
                                   int packedOverlay,
                                   Vector3f tmpPos,
                                   Vector3f tmpNorm) {
        if (part.quads.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(part.originX, part.originY, part.originZ);
        if (rotY != 0.0F) {
            poseStack.mulPose(Axis.YP.rotation(rotY));
        }
        if (rotX != 0.0F) {
            poseStack.mulPose(Axis.XP.rotation(rotX));
        }
        if (rotZ != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotation(rotZ));
        }
        emitQuads(poseStack.last(), consumer, part.quads, packedLight, packedOverlay, tmpPos, tmpNorm);
        poseStack.popPose();
    }

    private static void renderCaster(PoseStack poseStack,
                                     VertexConsumer consumer,
                                     BakedCaster caster,
                                     float casterYaw,
                                     float casterSpin,
                                     int packedLight,
                                     int packedOverlay,
                                     Vector3f tmpPos,
                                     Vector3f tmpNorm) {
        if (caster.forkQuads.isEmpty() && caster.wheelQuads.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(caster.originX, caster.originY, caster.originZ);
        if (casterYaw != 0.0F) {
            poseStack.mulPose(Axis.YP.rotation(casterYaw));
        }
        if (!caster.forkQuads.isEmpty()) {
            emitQuads(poseStack.last(), consumer, caster.forkQuads, packedLight, packedOverlay, tmpPos, tmpNorm);
        }
        if (!caster.wheelQuads.isEmpty()) {
            if (casterSpin != 0.0F) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotation(casterSpin));
                emitQuads(poseStack.last(), consumer, caster.wheelQuads, packedLight, packedOverlay, tmpPos, tmpNorm);
                poseStack.popPose();
            } else {
                emitQuads(poseStack.last(), consumer, caster.wheelQuads, packedLight, packedOverlay, tmpPos, tmpNorm);
            }
        }
        poseStack.popPose();
    }

    private static void emitQuads(PoseStack.Pose pose,
                                  VertexConsumer consumer,
                                  List<BakedQuad> quads,
                                  int packedLight,
                                  int packedOverlay,
                                  Vector3f tmpPos,
                                  Vector3f tmpNorm) {
        Matrix4f mat = pose.pose();
        for (int i = 0, n = quads.size(); i < n; i++) {
            BakedQuad q = quads.get(i);
            pose.transformNormal(q.nx, q.ny, q.nz, tmpNorm);
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

    private static WheelchairModelData loadFromResources() {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        try (Reader reader = rm.openAsReader(MODEL_LOCATION)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            float texWidth = root.has("texture_width") ? root.get("texture_width").getAsFloat() : 64.0F;
            float texHeight = root.has("texture_height") ? root.get("texture_height").getAsFloat() : 64.0F;
            float uvRefW = root.has("uv_reference_size") ? root.get("uv_reference_size").getAsFloat() : texWidth;
            float uvRefH = root.has("uv_reference_size") ? root.get("uv_reference_size").getAsFloat() : texHeight;
            if (uvRefW <= 0.0F) uvRefW = 64.0F;
            if (uvRefH <= 0.0F) uvRefH = 64.0F;

            JsonObject parts = root.getAsJsonObject("parts");
            BakedPart chassis = bakePart(parts.getAsJsonObject("chassis"), uvRefW, uvRefH);
            BakedPart wheelLeft = bakePart(parts.getAsJsonObject("wheel_left"), uvRefW, uvRefH);
            BakedPart wheelRight = bakePart(parts.getAsJsonObject("wheel_right"), uvRefW, uvRefH);
            BakedCaster casterLeft = bakeCaster(parts.getAsJsonObject("caster_left"), uvRefW, uvRefH);
            BakedCaster casterRight = bakeCaster(parts.getAsJsonObject("caster_right"), uvRefW, uvRefH);
            BakedPart joystick = bakePart(parts.getAsJsonObject("joystick"), uvRefW, uvRefH);

            List<Nozzle> nozzles = new ArrayList<>(2);
            for (String name : List.of("nozzle_left", "nozzle_right")) {
                JsonObject part = parts.getAsJsonObject(name);
                if (part != null) {
                    nozzles.add(new Nozzle(bakePart(part, uvRefW, uvRefH),
                            part.get("outlet_z").getAsFloat() / UNITS_PER_BLOCK));
                }
            }
            return new WheelchairModelData(chassis, wheelLeft, wheelRight, casterLeft, casterRight,
                    joystick, List.copyOf(nozzles));
        } catch (Exception e) {
            UniversalToolMod.LOGGER.error("Failed to load All-Terrain Wheelchair model from {}", MODEL_LOCATION, e);
            return null;
        }
    }

    private static BakedPart bakePart(JsonObject partObj, float uvRefW, float uvRefH) {
        if (partObj == null) {
            return new BakedPart(0.0F, 0.0F, 0.0F, List.of());
        }
        float[] partOrigin = readVec3(partObj.getAsJsonArray("origin"), 8.0F, 0.0F, 0.0F);
        List<BakedQuad> quads = new ArrayList<>();
        JsonArray cubes = partObj.getAsJsonArray("cubes");
        if (cubes != null) {
            for (JsonElement el : cubes) {
                bakeCube(el.getAsJsonObject(), partOrigin, uvRefW, uvRefH, quads);
            }
        }
        return new BakedPart(
                (partOrigin[0] - MODEL_CENTER_X) / UNITS_PER_BLOCK,
                (partOrigin[1] - MODEL_CENTER_Y) / UNITS_PER_BLOCK,
                (partOrigin[2] - MODEL_CENTER_Z) / UNITS_PER_BLOCK,
                List.copyOf(quads)
        );
    }

    private static BakedCaster bakeCaster(JsonObject partObj, float uvRefW, float uvRefH) {
        if (partObj == null) {
            return new BakedCaster(0.0F, 0.0F, 0.0F, List.of(), List.of());
        }
        float[] partOrigin = readVec3(partObj.getAsJsonArray("origin"), 8.0F, 0.0F, 0.0F);
        List<BakedQuad> forkQuads = new ArrayList<>();
        List<BakedQuad> wheelQuads = new ArrayList<>();
        JsonArray cubes = partObj.getAsJsonArray("cubes");
        if (cubes != null) {
            for (JsonElement el : cubes) {
                JsonObject cube = el.getAsJsonObject();
                String name = cube.has("name") ? cube.get("name").getAsString() : "";
                boolean isWheelSubPart = name.startsWith("ctyre") || name.startsWith("chub");
                bakeCube(cube, partOrigin, uvRefW, uvRefH, isWheelSubPart ? wheelQuads : forkQuads);
            }
        }
        return new BakedCaster(
                (partOrigin[0] - MODEL_CENTER_X) / UNITS_PER_BLOCK,
                (partOrigin[1] - MODEL_CENTER_Y) / UNITS_PER_BLOCK,
                (partOrigin[2] - MODEL_CENTER_Z) / UNITS_PER_BLOCK,
                List.copyOf(forkQuads),
                List.copyOf(wheelQuads)
        );
    }

    private static void bakeCube(JsonObject cube,
                                 float[] partOrigin,
                                 float uvRefW,
                                 float uvRefH,
                                 List<BakedQuad> outQuads) {
        float[] from = readVec3(cube.getAsJsonArray("from"), 0.0F, 0.0F, 0.0F);
        float[] to = readVec3(cube.getAsJsonArray("to"), 0.0F, 0.0F, 0.0F);
        float[] cubeOrigin = readVec3(cube.getAsJsonArray("origin"), 8.0F, 8.0F, 8.0F);
        float[] rotation = readVec3(cube.getAsJsonArray("rotation"), 0.0F, 0.0F, 0.0F);

        float fx = from[0];
        float fy = from[1];
        float fz = from[2];
        float tx = to[0];
        float ty = to[1];
        float tz = to[2];

        boolean hasRot = Math.abs(rotation[0]) > 1.0E-5F
                || Math.abs(rotation[1]) > 1.0E-5F
                || Math.abs(rotation[2]) > 1.0E-5F;
        Quaternionf rotQuat = hasRot
                ? new Quaternionf().rotateZYX(
                (float) Math.toRadians(rotation[2]),
                (float) Math.toRadians(rotation[1]),
                (float) Math.toRadians(rotation[0]))
                : null;

        JsonObject faces = cube.getAsJsonObject("faces");
        if (faces == null) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
            JsonObject faceObj = entry.getValue().getAsJsonObject();
            JsonArray uvArr = faceObj.getAsJsonArray("uv");
            if (uvArr == null || uvArr.size() < 4) {
                continue;
            }
            float u0 = uvArr.get(0).getAsFloat() / uvRefW;
            float v0 = uvArr.get(1).getAsFloat() / uvRefH;
            float u1 = uvArr.get(2).getAsFloat() / uvRefW;
            float v1 = uvArr.get(3).getAsFloat() / uvRefH;
            int uvRot = faceObj.has("rotation") ? faceObj.get("rotation").getAsInt() : 0;

            BakedQuad quad = switch (entry.getKey()) {
                case "north" -> buildFace(
                        tx, fy, fz, fx, fy, fz, fx, ty, fz, tx, ty, fz,
                        0.0F, 0.0F, -1.0F, u0, v0, u1, v1, uvRot,
                        cubeOrigin, partOrigin, rotQuat);
                case "south" -> buildFace(
                        fx, fy, tz, tx, fy, tz, tx, ty, tz, fx, ty, tz,
                        0.0F, 0.0F, 1.0F, u0, v0, u1, v1, uvRot,
                        cubeOrigin, partOrigin, rotQuat);
                case "west" -> buildFace(
                        fx, fy, fz, fx, fy, tz, fx, ty, tz, fx, ty, fz,
                        -1.0F, 0.0F, 0.0F, u0, v0, u1, v1, uvRot,
                        cubeOrigin, partOrigin, rotQuat);
                case "east" -> buildFace(
                        tx, fy, tz, tx, fy, fz, tx, ty, fz, tx, ty, tz,
                        1.0F, 0.0F, 0.0F, u0, v0, u1, v1, uvRot,
                        cubeOrigin, partOrigin, rotQuat);
                case "up" -> buildFace(
                        fx, ty, tz, tx, ty, tz, tx, ty, fz, fx, ty, fz,
                        0.0F, 1.0F, 0.0F, u0, v0, u1, v1, uvRot,
                        cubeOrigin, partOrigin, rotQuat);
                case "down" -> buildFace(
                        fx, fy, fz, tx, fy, fz, tx, fy, tz, fx, fy, tz,
                        0.0F, -1.0F, 0.0F, u0, v0, u1, v1, uvRot,
                        cubeOrigin, partOrigin, rotQuat);
                default -> null;
            };
            if (quad != null) {
                outQuads.add(quad);
            }
        }
    }

    private static BakedQuad buildFace(float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3,
                                       float nx, float ny, float nz,
                                       float u0, float v0, float u1, float v1,
                                       int uvRotDeg,
                                       float[] cubeOrigin,
                                       float[] partOrigin,
                                       Quaternionf rotQuat) {
        Vector3f p0 = toPartLocal(x0, y0, z0, cubeOrigin, partOrigin, rotQuat);
        Vector3f p1 = toPartLocal(x1, y1, z1, cubeOrigin, partOrigin, rotQuat);
        Vector3f p2 = toPartLocal(x2, y2, z2, cubeOrigin, partOrigin, rotQuat);
        Vector3f p3 = toPartLocal(x3, y3, z3, cubeOrigin, partOrigin, rotQuat);

        Vector3f norm = new Vector3f(nx, ny, nz);
        if (rotQuat != null) {
            rotQuat.transform(norm);
        }

        // Standard quad UV corners: bottom-left (u0,v1), bottom-right (u1,v1), top-right (u1,v0), top-left (u0,v0)
        float[] us = {u0, u1, u1, u0};
        float[] vs = {v1, v1, v0, v0};
        int steps = ((uvRotDeg / 90) % 4 + 4) % 4;
        int i0 = (4 - steps) % 4;
        int i1 = (5 - steps) % 4;
        int i2 = (6 - steps) % 4;
        int i3 = (7 - steps) % 4;

        return new BakedQuad(
                p0.x, p0.y, p0.z, us[i0], vs[i0],
                p1.x, p1.y, p1.z, us[i1], vs[i1],
                p2.x, p2.y, p2.z, us[i2], vs[i2],
                p3.x, p3.y, p3.z, us[i3], vs[i3],
                norm.x, norm.y, norm.z
        );
    }

    private static Vector3f toPartLocal(float bx, float by, float bz,
                                        float[] cubeOrigin,
                                        float[] partOrigin,
                                        Quaternionf rotQuat) {
        Vector3f v = new Vector3f(bx - cubeOrigin[0], by - cubeOrigin[1], bz - cubeOrigin[2]);
        if (rotQuat != null) {
            rotQuat.transform(v);
        }
        v.add(cubeOrigin[0] - partOrigin[0], cubeOrigin[1] - partOrigin[1], cubeOrigin[2] - partOrigin[2]);
        v.div(UNITS_PER_BLOCK);
        return v;
    }

    private static float[] readVec3(JsonArray arr, float defX, float defY, float defZ) {
        if (arr == null || arr.size() < 3) {
            return new float[]{defX, defY, defZ};
        }
        return new float[]{
                arr.get(0).getAsFloat(),
                arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat()
        };
    }

    private record BakedPart(float originX, float originY, float originZ, List<BakedQuad> quads) {
    }

    private record BakedCaster(float originX, float originY, float originZ,
                               List<BakedQuad> forkQuads, List<BakedQuad> wheelQuads) {
    }

    private record BakedQuad(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float nx, float ny, float nz) {
    }
}
