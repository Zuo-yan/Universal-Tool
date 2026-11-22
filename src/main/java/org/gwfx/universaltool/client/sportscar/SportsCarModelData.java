package org.gwfx.universaltool.client.sportscar;

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
import net.minecraft.util.Mth;
import org.gwfx.universaltool.UniversalToolMod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.Reader;

/**
 * 延迟加载 {@code assets/universal_tool/models/entity/gulf_gt_car.json}，
 * 按可动部件预烘焙四边形与三角形（退化为四边形）网格数据，支持资源重载时刷新缓存。
 */
public final class SportsCarModelData {

    public static final ResourceLocation DEFAULT_MAIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/entity/gulf_gt_car.png");
    public static final ResourceLocation DEFAULT_GLASS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/entity/gulf_gt_car_glass.png");
    private static final ResourceLocation MODEL_LOCATION = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "models/entity/gulf_gt_car.json");

    /** 单个烘焙面片占用的 float 数：4 个顶点 × (x, y, z, u, v) + 法线 (nx, ny, nz) */
    private static final int FLOATS_PER_FACE = 23;

    /** 四个车轮在实体局部空间下的默认枢轴坐标（单位：方块） */
    public static final float WHEEL_PIVOT_X = 1.10312F;
    public static final float WHEEL_PIVOT_Y = 0.53625F;
    public static final float FRONT_WHEEL_Z = -1.875F;
    public static final float REAR_WHEEL_Z = 2.000F;

    /** 水上完全变形后车身整体向上抬升高度（单位：方块） */
    public static final float WATER_MORPH_MAX_LIFT = 0.34F;

    private static volatile SportsCarModelData instance;

    private final ResourceLocation mainTexture;
    private final ResourceLocation glassTexture;

    private final BakedPart body;
    private final BakedPart headlights;
    private final BakedPart taillights;
    private final BakedPart glass;

    private final BakedPart wheelFlSteer;
    private final BakedPart wheelFlSpin;
    private final BakedPart wheelFrSteer;
    private final BakedPart wheelFrSpin;
    private final BakedPart wheelRlBrake;
    private final BakedPart wheelRlSpin;
    private final BakedPart wheelRrBrake;
    private final BakedPart wheelRrSpin;

    private final BakedPart steeringWheel;
    private final BakedPart doorLeft;
    private final BakedPart doorLeftGlass;
    private final BakedPart doorRight;
    private final BakedPart doorRightGlass;

    private SportsCarModelData(ResourceLocation mainTexture,
                               ResourceLocation glassTexture,
                               BakedPart body,
                               BakedPart headlights,
                               BakedPart taillights,
                               BakedPart glass,
                               BakedPart wheelFlSteer,
                               BakedPart wheelFlSpin,
                               BakedPart wheelFrSteer,
                               BakedPart wheelFrSpin,
                               BakedPart wheelRlBrake,
                               BakedPart wheelRlSpin,
                               BakedPart wheelRrBrake,
                               BakedPart wheelRrSpin,
                               BakedPart steeringWheel,
                               BakedPart doorLeft,
                               BakedPart doorLeftGlass,
                               BakedPart doorRight,
                               BakedPart doorRightGlass) {
        this.mainTexture = mainTexture;
        this.glassTexture = glassTexture;
        this.body = body;
        this.headlights = headlights;
        this.taillights = taillights;
        this.glass = glass;
        this.wheelFlSteer = wheelFlSteer;
        this.wheelFlSpin = wheelFlSpin;
        this.wheelFrSteer = wheelFrSteer;
        this.wheelFrSpin = wheelFrSpin;
        this.wheelRlBrake = wheelRlBrake;
        this.wheelRlSpin = wheelRlSpin;
        this.wheelRrBrake = wheelRrBrake;
        this.wheelRrSpin = wheelRrSpin;
        this.steeringWheel = steeringWheel;
        this.doorLeft = doorLeft;
        this.doorLeftGlass = doorLeftGlass;
        this.doorRight = doorRight;
        this.doorRightGlass = doorRightGlass;
    }

    /**
     * 获取已缓存的跑车模型；若尚未加载则从资源包读取并预烘焙。
     */
    @Nullable
    public static SportsCarModelData getOrLoad() {
        SportsCarModelData cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (SportsCarModelData.class) {
            if (instance == null) {
                instance = loadFromResources();
            }
            return instance;
        }
    }

    /**
     * 清空已缓存的模型实例，以便在资源包重载时重新读取几何与贴图路径。
     */
    public static void invalidate() {
        instance = null;
    }

    public ResourceLocation mainTexture() {
        return mainTexture;
    }

    public ResourceLocation glassTexture() {
        return glassTexture;
    }

    /**
     * 水上推进舱与车轮折转的统一变换参数（供模型、机械悬臂、涵道护罩与推进光环共享）。
     */
    public record HoverPodTransform(
            float tx,
            float ty,
            float tz,
            float foldPhase,
            float outwardClear,
            float vectorPitchDeg,
            float vectorRollDeg
    ) {
    }

    /**
     * 计算水陆变形下车身整体向上抬升高度（确保折平轮盘与底盘互不穿模）。
     */
    public static float computeWaterMorphLift(float waterMorph) {
        float morph = Mth.clamp(waterMorph, 0.0F, 1.0F);
        return smoothstep(0.08F, 0.90F, morph) * WATER_MORPH_MAX_LIFT;
    }

    /**
     * 计算单个车轮/涵道推进舱在当前变形进度与矢量推进角下的空间变换。
     */
    public static HoverPodTransform computeWheelPodTransform(float pivotX,
                                                             float pivotY,
                                                             float pivotZ,
                                                             boolean isLeft,
                                                             float waterMorph,
                                                             float vectorPitchDeg,
                                                             float vectorRollDeg) {
        float morph = Mth.clamp(waterMorph, 0.0F, 1.0F);
        float outwardClear = smoothstep(0.0F, 0.32F, morph)
                * (1.0F - smoothstep(0.45F, 0.92F, morph)) * 0.16F;
        float foldPhase = smoothstep(0.22F, 1.00F, morph);
        float sideSign = isLeft ? -1.0F : 1.0F;

        // 向轮位外下方微展后折平，使水平轮盘顶面（+0.051）严格处于碳纤维底板（0.074）之下
        float tx = pivotX + sideSign * (outwardClear + foldPhase * 0.08F);
        float ty = pivotY - foldPhase * 0.67625F;
        float vBlend = smoothstep(0.50F, 1.00F, morph);

        return new HoverPodTransform(
                tx, ty, pivotZ,
                foldPhase, outwardClear,
                vectorPitchDeg * vBlend, vectorRollDeg * vBlend
        );
    }

    /**
     * 渲染除半透明玻璃外的所有不透明部件（无矢量推进倾斜的兼容重载，供物品立体渲染使用）。
     */
    public void renderOpaqueParts(PoseStack poseStack,
                                  VertexConsumer consumer,
                                  int baseLight,
                                  int packedOverlay,
                                  float steerAngleRad,
                                  float wheelSpinRad,
                                  float doorOpenProg,
                                  float waterMorph,
                                  float hoverTurbineSpinRad,
                                  int headlightLight,
                                  int headlightTint,
                                  int taillightLight,
                                  int taillightTint) {
        renderOpaqueParts(
                poseStack, consumer, baseLight, packedOverlay,
                steerAngleRad, wheelSpinRad, doorOpenProg, waterMorph, hoverTurbineSpinRad,
                0.0F, 0.0F,
                headlightLight, headlightTint, taillightLight, taillightTint
        );
    }

    /**
     * 渲染除半透明玻璃外的所有不透明部件（车身、四轮、前后制动系统、方向盘、车门、智能车灯及矢量倾转）。
     *
     * @param steerAngleRad       前轮与方向盘转向角（弧度，正值=向左打方向）
     * @param wheelSpinRad        陆地行驶车轮累计滚动角（弧度，前进正向递增）
     * @param doorOpenProg        迎宾门开合进度（0=关，1=全开 72°）
     * @param waterMorph          水陆变形进度（0=陆地四轮，1=水上 90° 折叠气垫涡轮）
     * @param hoverTurbineSpinRad 水上模式气垫涡轮累计旋转角（弧度）
     * @param vectorPitchDeg      水上推进舱矢量俯仰角（度，加速向后喷流为负）
     * @param vectorRollDeg       水上推进舱矢量横倾角（度）
     * @param headlightLight      前大灯光照值（如 {@code LightTexture.FULL_BRIGHT}）
     * @param headlightTint       前大灯 RGBA 染色（如暖白自发光）
     * @param taillightLight      尾灯光照值
     * @param taillightTint       尾灯 RGBA 染色
     */
    public void renderOpaqueParts(PoseStack poseStack,
                                  VertexConsumer consumer,
                                  int baseLight,
                                  int packedOverlay,
                                  float steerAngleRad,
                                  float wheelSpinRad,
                                  float doorOpenProg,
                                  float waterMorph,
                                  float hoverTurbineSpinRad,
                                  float vectorPitchDeg,
                                  float vectorRollDeg,
                                  int headlightLight,
                                  int headlightTint,
                                  int taillightLight,
                                  int taillightTint) {
        RenderScratch scratch = new RenderScratch();

        float morph = Mth.clamp(waterMorph, 0.0F, 1.0F);
        // 变形前段：转向角平滑归零，主段：四轮及对应制动系统同步 90° 折平为水平涡轮定子
        float effectiveSteer = steerAngleRad * (1.0F - smoothstep(0.0F, 0.40F, morph));
        float turbineBlend = smoothstep(0.45F, 0.88F, morph);

        // 静态车身（底板、外蒙皮、空力套件、座舱座椅仪表台等）
        emitAtOrigin(poseStack, consumer, body, baseLight, packedOverlay, 0xFFFFFFFF, scratch);

        // 智能前大灯与尾灯（在各自光效与染色下输出）
        emitAtOrigin(poseStack, consumer, headlights, headlightLight, packedOverlay, headlightTint, scratch);
        emitAtOrigin(poseStack, consumer, taillights, taillightLight, packedOverlay, taillightTint, scratch);

        // 前轮：卡钳转向组与轮胎轮毂共用枢纽，先转向与矢量微倾，再折平并叠加自转/气垫涡轮高速旋转
        renderWheelAssembly(poseStack, consumer, wheelFlSteer, wheelFlSpin, true,
                effectiveSteer, wheelSpinRad, hoverTurbineSpinRad,
                morph, vectorPitchDeg, vectorRollDeg, turbineBlend, baseLight, packedOverlay, scratch);
        renderWheelAssembly(poseStack, consumer, wheelFrSteer, wheelFrSpin, false,
                effectiveSteer, wheelSpinRad, hoverTurbineSpinRad,
                morph, vectorPitchDeg, vectorRollDeg, turbineBlend, baseLight, packedOverlay, scratch);

        // 后轮：后轮制动盘与卡钳同步跟随轮毂折叠并作为水平涡轮定子，彻底消除穿模
        renderWheelAssembly(poseStack, consumer, wheelRlBrake, wheelRlSpin, true,
                0.0F, wheelSpinRad, hoverTurbineSpinRad,
                morph, vectorPitchDeg, vectorRollDeg, turbineBlend, baseLight, packedOverlay, scratch);
        renderWheelAssembly(poseStack, consumer, wheelRrBrake, wheelRrSpin, false,
                0.0F, wheelSpinRad, hoverTurbineSpinRad,
                morph, vectorPitchDeg, vectorRollDeg, turbineBlend, baseLight, packedOverlay, scratch);

        // 方向盘：绕转向柱导出轴向量旋转（向左转向对应正角度逆时针旋转）
        if (!steeringWheel.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(steeringWheel.pivotX, steeringWheel.pivotY, steeringWheel.pivotZ);
            if (Math.abs(effectiveSteer) > 1.0E-4F) {
                scratch.quat.fromAxisAngleRad(
                        steeringWheel.axisX, steeringWheel.axisY, steeringWheel.axisZ, effectiveSteer * 6.5F);
                poseStack.mulPose(scratch.quat);
            }
            emitFaces(poseStack.last(), consumer, steeringWheel, baseLight, packedOverlay, 0xFFFFFFFF, scratch);
            poseStack.popPose();
        }

        // 左右迎宾门本体（左门向外为 -72°，右门向外为 +72°）
        renderDoorPart(poseStack, consumer, doorLeft, -72.0F * doorOpenProg,
                baseLight, packedOverlay, scratch);
        renderDoorPart(poseStack, consumer, doorRight, 72.0F * doorOpenProg,
                baseLight, packedOverlay, scratch);
    }

    /**
     * 在所有不透明部件绘制完毕后，统一使用半透明渲染层绘制前风挡与左右车门侧窗玻璃。
     */
    public void renderTranslucentGlass(PoseStack poseStack,
                                       VertexConsumer glassConsumer,
                                       int packedLight,
                                       int packedOverlay,
                                       float doorOpenProg) {
        RenderScratch scratch = new RenderScratch();
        emitAtOrigin(poseStack, glassConsumer, glass, packedLight, packedOverlay, 0xFFFFFFFF, scratch);
        renderDoorPart(poseStack, glassConsumer, doorLeftGlass, -72.0F * doorOpenProg,
                packedLight, packedOverlay, scratch);
        renderDoorPart(poseStack, glassConsumer, doorRightGlass, 72.0F * doorOpenProg,
                packedLight, packedOverlay, scratch);
    }

    private static void renderWheelAssembly(PoseStack poseStack,
                                            VertexConsumer consumer,
                                            @Nullable BakedPart brakePart,
                                            BakedPart spinPart,
                                            boolean isLeft,
                                            float steerAngleRad,
                                            float wheelSpinRad,
                                            float hoverTurbineSpinRad,
                                            float waterMorph,
                                            float vectorPitchDeg,
                                            float vectorRollDeg,
                                            float turbineBlend,
                                            int packedLight,
                                            int packedOverlay,
                                            RenderScratch scratch) {
        BakedPart pivotPart = !spinPart.isEmpty() ? spinPart : brakePart;
        if (pivotPart == null || pivotPart.isEmpty()) {
            return;
        }
        HoverPodTransform pod = computeWheelPodTransform(
                pivotPart.pivotX, pivotPart.pivotY, pivotPart.pivotZ,
                isLeft, waterMorph, vectorPitchDeg, vectorRollDeg);

        poseStack.pushPose();
        poseStack.translate(pod.tx(), pod.ty(), pod.tz());
        if (Math.abs(steerAngleRad) > 1.0E-4F) {
            poseStack.mulPose(Axis.YP.rotation(steerAngleRad));
        }
        // 在折平前施加矢量推进倾斜，使整个水平推进舱围绕车体参考系前倾/侧倾
        if (Math.abs(pod.vectorPitchDeg()) > 1.0E-3F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pod.vectorPitchDeg()));
        }
        if (Math.abs(pod.vectorRollDeg()) > 1.0E-3F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-pod.vectorRollDeg()));
        }
        // 左轮绕 +Z 向内折转 +90°、右轮折转 -90°，使外侧轮辋面朝下充当气垫涡轮盘
        if (pod.foldPhase() > 1.0E-4F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees((isLeft ? 90.0F : -90.0F) * pod.foldPhase()));
        }
        if (brakePart != null && !brakePart.isEmpty()) {
            emitFaces(poseStack.last(), consumer, brakePart, packedLight, packedOverlay, 0xFFFFFFFF, scratch);
        }
        if (!spinPart.isEmpty()) {
            float landSpin = -wheelSpinRad * (1.0F - turbineBlend);
            float turbineSpin = (isLeft ? -hoverTurbineSpinRad : hoverTurbineSpinRad) * turbineBlend;
            float totalSpin = landSpin + turbineSpin;
            if (Math.abs(totalSpin) > 1.0E-4F) {
                poseStack.mulPose(Axis.XP.rotation(totalSpin));
            }
            emitFaces(poseStack.last(), consumer, spinPart, packedLight, packedOverlay, 0xFFFFFFFF, scratch);
        }
        poseStack.popPose();
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void renderDoorPart(PoseStack poseStack,
                                       VertexConsumer consumer,
                                       BakedPart doorPart,
                                       float angleDeg,
                                       int packedLight,
                                       int packedOverlay,
                                       RenderScratch scratch) {
        if (doorPart.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(doorPart.pivotX, doorPart.pivotY, doorPart.pivotZ);
        if (Math.abs(angleDeg) > 1.0E-3F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(angleDeg));
        }
        emitFaces(poseStack.last(), consumer, doorPart, packedLight, packedOverlay, 0xFFFFFFFF, scratch);
        poseStack.popPose();
    }

    private static void emitAtOrigin(PoseStack poseStack,
                                     VertexConsumer consumer,
                                     BakedPart part,
                                     int packedLight,
                                     int packedOverlay,
                                     int argbTint,
                                     RenderScratch scratch) {
        if (part.isEmpty()) {
            return;
        }
        boolean hasPivot = part.pivotX != 0.0F || part.pivotY != 0.0F || part.pivotZ != 0.0F;
        if (hasPivot) {
            poseStack.pushPose();
            poseStack.translate(part.pivotX, part.pivotY, part.pivotZ);
        }
        emitFaces(poseStack.last(), consumer, part, packedLight, packedOverlay, argbTint, scratch);
        if (hasPivot) {
            poseStack.popPose();
        }
    }

    private static void emitFaces(PoseStack.Pose pose,
                                  VertexConsumer consumer,
                                  BakedPart part,
                                  int packedLight,
                                  int packedOverlay,
                                  int argbTint,
                                  RenderScratch scratch) {
        int a = (argbTint >>> 24) & 0xFF;
        int r = (argbTint >>> 16) & 0xFF;
        int g = (argbTint >>> 8) & 0xFF;
        int b = argbTint & 0xFF;

        Matrix4f mat = pose.pose();
        Vector3f tmpPos = scratch.pos;
        Vector3f tmpNorm = scratch.norm;
        float[] data = part.data;
        int total = part.faceCount * FLOATS_PER_FACE;

        for (int idx = 0; idx < total; idx += FLOATS_PER_FACE) {
            pose.transformNormal(data[idx + 20], data[idx + 21], data[idx + 22], tmpNorm);
            float nx = tmpNorm.x;
            float ny = tmpNorm.y;
            float nz = tmpNorm.z;

            for (int v = 0; v < 4; v++) {
                int vo = idx + v * 5;
                mat.transformPosition(data[vo], data[vo + 1], data[vo + 2], tmpPos);
                consumer.addVertex(tmpPos.x, tmpPos.y, tmpPos.z)
                        .setColor(r, g, b, a)
                        .setUv(data[vo + 3], data[vo + 4])
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(nx, ny, nz);
            }
        }
    }

    @Nullable
    private static SportsCarModelData loadFromResources() {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        try (Reader reader = rm.openAsReader(MODEL_LOCATION)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            ResourceLocation mainTex = DEFAULT_MAIN_TEXTURE;
            ResourceLocation glassTex = DEFAULT_GLASS_TEXTURE;
            if (root.has("textures") && root.get("textures").isJsonObject()) {
                JsonObject texturesObj = root.getAsJsonObject("textures");
                if (texturesObj.has("main")) {
                    mainTex = ResourceLocation.parse(texturesObj.get("main").getAsString());
                }
                if (texturesObj.has("glass")) {
                    glassTex = ResourceLocation.parse(texturesObj.get("glass").getAsString());
                }
            }

            JsonObject parts = root.getAsJsonObject("parts");
            if (parts == null) {
                throw new IllegalStateException("跑车模型缺少 parts 字段");
            }

            return new SportsCarModelData(
                    mainTex,
                    glassTex,
                    bakePart(parts.getAsJsonObject("body")),
                    bakePart(parts.getAsJsonObject("headlights")),
                    bakePart(parts.getAsJsonObject("taillights")),
                    bakePart(parts.getAsJsonObject("glass")),
                    bakePart(parts.getAsJsonObject("wheel_fl_steer")),
                    bakePart(parts.getAsJsonObject("wheel_fl_spin")),
                    bakePart(parts.getAsJsonObject("wheel_fr_steer")),
                    bakePart(parts.getAsJsonObject("wheel_fr_spin")),
                    bakePart(parts.getAsJsonObject("wheel_rl_brake")),
                    bakePart(parts.getAsJsonObject("wheel_rl_spin")),
                    bakePart(parts.getAsJsonObject("wheel_rr_brake")),
                    bakePart(parts.getAsJsonObject("wheel_rr_spin")),
                    bakePart(parts.getAsJsonObject("steering_wheel")),
                    bakePart(parts.getAsJsonObject("door_left")),
                    bakePart(parts.getAsJsonObject("door_left_glass")),
                    bakePart(parts.getAsJsonObject("door_right")),
                    bakePart(parts.getAsJsonObject("door_right_glass"))
            );
        } catch (Exception e) {
            UniversalToolMod.LOGGER.error("加载海湾 GT 跑车模型失败: {}", MODEL_LOCATION, e);
            return null;
        }
    }

    private static BakedPart bakePart(@Nullable JsonObject obj) {
        if (obj == null) {
            return BakedPart.EMPTY;
        }
        float[] pivot = readVec3(obj.getAsJsonArray("pivot"), 0.0F, 0.0F, 0.0F);
        float[] axis = readVec3(obj.getAsJsonArray("axis"), 0.0F, 0.0F, 1.0F);
        float axisLen = (float) Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        if (axisLen > 1.0E-5F) {
            axis[0] /= axisLen;
            axis[1] /= axisLen;
            axis[2] /= axisLen;
        } else {
            axis[0] = 0.0F;
            axis[1] = 0.0F;
            axis[2] = 1.0F;
        }

        JsonArray faces = obj.getAsJsonArray("faces");
        if (faces == null || faces.isEmpty()) {
            return new BakedPart(pivot[0], pivot[1], pivot[2], axis[0], axis[1], axis[2], new float[0], 0);
        }

        float[] raw = new float[faces.size() * FLOATS_PER_FACE];
        int validCount = 0;

        for (JsonElement el : faces) {
            JsonObject face = el.getAsJsonObject();
            JsonArray verts = face.getAsJsonArray("vertices");
            if (verts == null || verts.size() < 3) {
                continue;
            }
            int base = validCount * FLOATS_PER_FACE;
            int vertCount = verts.size();
            for (int i = 0; i < 4; i++) {
                // 三角面片（3 顶点）按 MC QUADS 惯例重复第 3 顶点
                int srcIdx = Math.min(i, vertCount - 1);
                JsonArray vArr = verts.get(srcIdx).getAsJsonArray();
                int vo = base + i * 5;
                raw[vo] = vArr.get(0).getAsFloat();
                raw[vo + 1] = vArr.get(1).getAsFloat();
                raw[vo + 2] = vArr.get(2).getAsFloat();
                raw[vo + 3] = vArr.get(3).getAsFloat();
                raw[vo + 4] = vArr.get(4).getAsFloat();
            }

            float[] norm = readVec3(face.getAsJsonArray("normal"), 0.0F, 1.0F, 0.0F);
            raw[base + 20] = norm[0];
            raw[base + 21] = norm[1];
            raw[base + 22] = norm[2];
            validCount++;
        }

        float[] trimmed = validCount == faces.size()
                ? raw
                : java.util.Arrays.copyOf(raw, validCount * FLOATS_PER_FACE);

        return new BakedPart(pivot[0], pivot[1], pivot[2], axis[0], axis[1], axis[2], trimmed, validCount);
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
        final Vector3f pos = new Vector3f();
        final Vector3f norm = new Vector3f();
        final Quaternionf quat = new Quaternionf();
    }

    private static final class BakedPart {
        static final BakedPart EMPTY = new BakedPart(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, new float[0], 0);

        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float axisX;
        final float axisY;
        final float axisZ;
        final float[] data;
        final int faceCount;

        BakedPart(float pivotX, float pivotY, float pivotZ,
                  float axisX, float axisY, float axisZ,
                  float[] data, int faceCount) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.axisX = axisX;
            this.axisY = axisY;
            this.axisZ = axisZ;
            this.data = data;
            this.faceCount = faceCount;
        }

        boolean isEmpty() {
            return faceCount == 0;
        }
    }
}
