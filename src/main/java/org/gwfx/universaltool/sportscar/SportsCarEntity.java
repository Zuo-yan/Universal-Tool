package org.gwfx.universaltool.sportscar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.fluids.FluidType;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModSounds;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 海湾 GT 跑车实体（双座、鼠标视角转向+A/D辅助、手刹漂移、自动回充氮气、四轮地形拟合与自动迎宾门）。
 *
 * <p>局部坐标系约定（与 {@code gulf_gt_car.bbmodel} 及实体原点对齐）：
 * <ul>
 *   <li>实体原点 {@code (getX(), getY(), getZ())} 位于车底正中心（模型 {@code Y=0}）。</li>
 *   <li>模型局部 {@code -Z} 为车头正前方向（对应世界 {@code +F}）。</li>
 *   <li>模型局部 {@code +X} 为车身右侧（对应世界 {@code +R = F x U}）。</li>
 *   <li>模型局部 {@code +Y} 为车顶上方（对应世界 {@code +U}）。</li>
 *   <li>航向角 {@code carYaw} 采用 Minecraft 标准角度：0°=+Z(南)，+90°=-X(西)，180°=-Z(北)，-90°=+X(东)。</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public class SportsCarEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_CAR_YAW =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TERRAIN_PITCH =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TERRAIN_ROLL =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER_ANGLE =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DOOR_OPEN_PROGRESS =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NITRO_ENERGY =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BRAKING =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRIFTING =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NITRO_ACTIVE =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_FORWARD_SPEED =
            SynchedEntityData.defineId(SportsCarEntity.class, EntityDataSerializers.FLOAT);

    // 速度与加速度物理参数（单位：格/tick）
    public static final double NORMAL_MAX_FORWARD_SPEED = 0.95D;
    public static final double NITRO_MAX_FORWARD_SPEED = 1.45D;
    public static final double MAX_REVERSE_SPEED = 0.42D;
    private static final double FORWARD_ACCEL = 0.062D;
    private static final double NITRO_ACCEL = 0.115D;
    private static final double REVERSE_ACCEL = 0.045D;
    private static final double BRAKE_DECEL = 0.095D;
    private static final double COAST_DAMPING = 0.955D;
    private static final double AIR_DAMPING = 0.985D;
    private static final double GRAVITY = 0.075D;

    // 抓地与漂移参数
    private static final double NORMAL_LATERAL_GRIP = 0.26D;
    private static final double DRIFT_LATERAL_GRIP = 0.935D;
    private static final double DRIFT_SPEED_THRESHOLD = 0.22D;
    private static final double HANDBRAKE_DRIFT_DAMPING = 0.975D;
    private static final double HANDBRAKE_STOP_DAMPING = 0.72D;
    private static final float BASE_MAX_TURN_RATE_DEG = 6.2F;
    private static final float DRIFT_TURN_MULTIPLIER = 1.45F;
    public static final float MAX_STEER_ANGLE_RAD = 0.55F;

    // 氮气能量槽（0..100），每秒 20 tick
    public static final float MAX_NITRO_ENERGY = 100.0F;
    private static final float NITRO_DRAIN_PER_TICK = 35.0F / 20.0F;
    private static final float NITRO_REGEN_PER_TICK = 16.0F / 20.0F;
    private static final float NITRO_DRIFT_REGEN_PER_TICK = 32.0F / 20.0F;

    // 高速撞击生物参数
    public static final double RAM_SPEED_THRESHOLD = 0.38D;
    public static final float RAM_DAMAGE = 6.0F;
    private static final long RAM_COOLDOWN_TICKS = 12L;

    // 车型几何尺寸（按 16 BB 单位 = 1 方块换算）
    public static final int MAX_PASSENGERS = 2;
    private static final Vec3 DRIVER_SEAT_LOCAL = new Vec3(-0.406D, 0.36D, 0.116D);
    private static final Vec3 PASSENGER_SEAT_LOCAL = new Vec3(0.406D, 0.36D, 0.116D);
    private static final float WHEEL_RADIUS = 0.536F;
    private static final double WHEEL_HALF_TRACK = 1.103D;
    private static final double FRONT_WHEEL_Z = -1.875D;
    private static final double REAR_WHEEL_Z = 2.000D;
    private static final double WHEELBASE = REAR_WHEEL_Z - FRONT_WHEEL_Z;
    private static final double TRACK_WIDTH = WHEEL_HALF_TRACK * 2.0D;

    // 自动迎宾门参数
    private static final double DOOR_PROXIMITY_RANGE = 3.5D;
    private static final int DOOR_STAY_TICKS = 40;
    private static final float DOOR_STEP_PER_TICK = 0.12F;
    private static final float DOOR_SOUND_THRESHOLD = 0.15F;

    // 服务端驾驶输入状态
    private float serverThrottle;
    private float serverSteerOverride;
    private float serverTargetYaw;
    private boolean serverHandbrake;
    private boolean serverNitro;

    // 服务端状态缓存
    private float steerAngleRad;
    private int doorStayTicks;
    private final Map<UUID, Long> ramCooldowns = new HashMap<>();

    // 客户端网络插值状态
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // 客户端本地平滑预测（4-tick TTL）与帧间插值记录
    private float clientThrottle;
    private float clientSteerOverride;
    private boolean clientHandbrake;
    private boolean clientNitro;
    private int clientPredictionTtl;

    private float prevCarYaw;
    private float prevTerrainPitch;
    private float prevTerrainRoll;
    private float prevSteerAngle;
    private float prevDoorOpenProgress;
    private float prevWheelSpin;
    private float wheelSpin;

    private final Quaternionf prevRenderOrientation = new Quaternionf();
    private final Quaternionf renderOrientation = new Quaternionf();
    private boolean orientationInitialized;

    public SportsCarEntity(EntityType<? extends SportsCarEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    /**
     * 保护车内乘员免受贴墙擦碰或微小台阶产生的窒息/摔落误伤。
     */
    @SubscribeEvent
    public static void onRiderIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof SportsCarEntity) {
            DamageSource source = event.getSource();
            if (source.is(DamageTypes.IN_WALL)
                    || source.is(DamageTypes.FALL)
                    || source.is(DamageTypes.FLY_INTO_WALL)) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CAR_YAW, 0.0F);
        builder.define(DATA_TERRAIN_PITCH, 0.0F);
        builder.define(DATA_TERRAIN_ROLL, 0.0F);
        builder.define(DATA_STEER_ANGLE, 0.0F);
        builder.define(DATA_DOOR_OPEN_PROGRESS, 0.0F);
        builder.define(DATA_NITRO_ENERGY, MAX_NITRO_ENERGY);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_BRAKING, false);
        builder.define(DATA_DRIFTING, false);
        builder.define(DATA_NITRO_ACTIVE, false);
        builder.define(DATA_FORWARD_SPEED, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("CarYaw")) {
            setCarYaw(tag.getFloat("CarYaw"));
            this.serverTargetYaw = getCarYaw();
        }
        if (tag.contains("TerrainPitch")) {
            this.entityData.set(DATA_TERRAIN_PITCH, Mth.clamp(tag.getFloat("TerrainPitch"), -35.0F, 35.0F));
        }
        if (tag.contains("TerrainRoll")) {
            this.entityData.set(DATA_TERRAIN_ROLL, Mth.clamp(tag.getFloat("TerrainRoll"), -30.0F, 30.0F));
        }
        if (tag.contains("NitroEnergy")) {
            this.entityData.set(DATA_NITRO_ENERGY, Mth.clamp(tag.getFloat("NitroEnergy"), 0.0F, MAX_NITRO_ENERGY));
        }
        if (tag.contains("DoorOpenProgress")) {
            this.entityData.set(DATA_DOOR_OPEN_PROGRESS, Mth.clamp(tag.getFloat("DoorOpenProgress"), 0.0F, 1.0F));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("CarYaw", getCarYaw());
        tag.putFloat("TerrainPitch", getTerrainPitch());
        tag.putFloat("TerrainRoll", getTerrainRoll());
        tag.putFloat("NitroEnergy", getNitroEnergy());
        tag.putFloat("DoorOpenProgress", getDoorOpenProgress());
    }

    // ==================== 公开状态与动画 Getter（供客户端渲染与测试调用） ====================

    /**
     * 获取当前车身航向角（单位：度，Minecraft 朝向约定：0=+Z，90=-X，180=-Z）。
     */
    public float getCarYaw() {
        return this.entityData.get(DATA_CAR_YAW);
    }

    /**
     * 获取帧间插值后的车身航向角（单位：度）。
     */
    public float getCarYaw(float partialTick) {
        return Mth.rotLerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevCarYaw, getCarYaw());
    }

    public void setCarYaw(float yaw) {
        float wrapped = Mth.wrapDegrees(yaw);
        this.entityData.set(DATA_CAR_YAW, wrapped);
        setYRot(wrapped);
    }

    /**
     * 获取地形拟合俯仰角（单位：度，正值表示车头抬起）。
     */
    public float getTerrainPitch() {
        return this.entityData.get(DATA_TERRAIN_PITCH);
    }

    /**
     * 获取帧间插值后的地形拟合俯仰角（单位：度，正值表示车头抬起）。
     */
    public float getTerrainPitch(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevTerrainPitch, getTerrainPitch());
    }

    /**
     * 获取地形拟合侧倾角（单位：度，正值表示左侧高于右侧，渲染绕 +Z 旋转取反）。
     */
    public float getTerrainRoll() {
        return this.entityData.get(DATA_TERRAIN_ROLL);
    }

    /**
     * 获取帧间插值后的地形拟合侧倾角（单位：度）。
     */
    public float getTerrainRoll(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevTerrainRoll, getTerrainRoll());
    }

    /**
     * 获取前轮与方向盘转向角（单位：弧度，正值表示向左转向，范围 [-0.55, 0.55]）。
     */
    public float getSteerAngle() {
        return this.entityData.get(DATA_STEER_ANGLE);
    }

    /**
     * 获取帧间插值后的转向角（单位：弧度，正值表示向左转向）。
     */
    public float getSteerAngle(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevSteerAngle, getSteerAngle());
    }

    /**
     * 获取车轮累计滚动角（单位：弧度，前进时正向递增，渲染绕 +X 旋转需取负）。
     */
    public float getWheelSpin() {
        return this.wheelSpin;
    }

    /**
     * 获取帧间插值后的车轮滚动角（单位：弧度，前进时正向递增）。
     */
    public float getWheelSpin(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevWheelSpin, this.wheelSpin);
    }

    /**
     * 获取自动迎宾门开启进度（0.0=完全关闭，1.0=完全开启 72°）。
     */
    public float getDoorOpenProgress() {
        return this.entityData.get(DATA_DOOR_OPEN_PROGRESS);
    }

    /**
     * 获取帧间插值后的车门开启进度（0.0..1.0）。
     */
    public float getDoorOpenProgress(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevDoorOpenProgress, getDoorOpenProgress());
    }

    /**
     * 获取当前氮气能量槽（0.0..100.0）。
     */
    public float getNitroEnergy() {
        return this.entityData.get(DATA_NITRO_ENERGY);
    }

    /**
     * 获取当前油门输入（-1.0..1.0）。
     */
    public float getThrottle() {
        if (level().isClientSide && this.clientPredictionTtl > 0) {
            return this.clientThrottle;
        }
        return this.entityData.get(DATA_THROTTLE);
    }

    /**
     * 获取沿车头方向的带符号行驶速度（格/tick，正值前进，负值倒车）。
     */
    public float getForwardSpeed() {
        return this.entityData.get(DATA_FORWARD_SPEED);
    }

    /**
     * 是否处于刹车或倒车状态（用于尾灯高亮自发光）。
     */
    public boolean isBraking() {
        if (level().isClientSide && this.clientPredictionTtl > 0) {
            return this.clientThrottle < -0.05F || this.clientHandbrake;
        }
        return this.entityData.get(DATA_BRAKING);
    }

    /**
     * 是否处于手刹高速漂移状态。
     */
    public boolean isDrifting() {
        return this.entityData.get(DATA_DRIFTING);
    }

    /**
     * 是否正在激活氮气加速。
     */
    public boolean isNitroActive() {
        if (level().isClientSide && this.clientPredictionTtl > 0 && this.clientNitro
                && this.clientThrottle >= 0.0F && getNitroEnergy() > 0.5F) {
            return true;
        }
        return this.entityData.get(DATA_NITRO_ACTIVE);
    }

    /**
     * 获取指定座位的模型局部坐标（单位：方块，原点在车底正中心，-Z 为车头）。
     * 0=主驾左舵 {@code [-0.406, 0.36, 0.116]}，1=副驾右座 {@code [+0.406, 0.36, 0.116]}。
     */
    public Vec3 getSeatLocalPosition(int seatIndex) {
        return seatIndex == 1 ? PASSENGER_SEAT_LOCAL : DRIVER_SEAT_LOCAL;
    }

    /**
     * 查询某实体在车内的座位索引（0=主驾，1=副驾，-1=不在车上）。
     */
    public int getSeatIndex(Entity passenger) {
        return getPassengers().indexOf(passenger);
    }

    /**
     * 判断该实体是否为主驾驶位（0 号位）驾驶员。
     */
    public boolean isDriver(Entity entity) {
        List<Entity> passengers = getPassengers();
        return !passengers.isEmpty() && passengers.get(0) == entity;
    }

    /**
     * 获取车身前向单位向量（水平面）。
     */
    public Vec3 getWorldForward() {
        return computeForward(getCarYaw());
    }

    /**
     * 获取车身右侧单位向量（水平面）。
     */
    public Vec3 getWorldRight() {
        return computeRight(getCarYaw());
    }

    /**
     * 获取插值后的实体底部中心世界坐标（对应模型局部 {@code (0, 0, 0)}）。
     */
    public Vec3 getCarBasePosition(float partialTick) {
        double t = Mth.clamp(partialTick, 0.0F, 1.0F);
        return new Vec3(
                Mth.lerp(t, this.xo, getX()),
                Mth.lerp(t, this.yo, getY()),
                Mth.lerp(t, this.zo, getZ())
        );
    }

    /**
     * 获取平滑插值后的车体旋转四元数（包含航向、地形俯仰与侧倾，局部列为 {@code (R, U, -F)}）。
     */
    public Quaternionf getRenderOrientation(float partialTick) {
        if (!this.orientationInitialized) {
            Quaternionf init = computeTargetOrientation(getCarYaw(), getTerrainPitch(), getTerrainRoll());
            this.prevRenderOrientation.set(init);
            this.renderOrientation.set(init);
            this.orientationInitialized = true;
        }
        Quaternionf out = new Quaternionf(this.prevRenderOrientation);
        Quaternionf dst = new Quaternionf(this.renderOrientation);
        if (out.dot(dst) < 0.0F) {
            dst.mul(-1.0F);
        }
        out.slerp(dst, Mth.clamp(partialTick, 0.0F, 1.0F));
        return out.normalize();
    }

    /**
     * 计算指定座位在世界坐标系中的精确位置（按车体旋转矩阵变换）。
     */
    public Vec3 getSeatWorldPosition(int seatIndex, float partialTick) {
        Vec3 base = getCarBasePosition(partialTick);
        Vec3 local = getSeatLocalPosition(seatIndex);
        Quaternionf orient = getRenderOrientation(partialTick);
        Vector3f v = new Vector3f((float) local.x, (float) local.y, (float) local.z);
        orient.transform(v);
        return base.add(v.x, v.y, v.z);
    }

    /**
     * 计算指定乘客对应的世界座位坐标。
     */
    public Vec3 getSeatWorldPosition(Entity passenger, float partialTick) {
        int idx = getSeatIndex(passenger);
        return getSeatWorldPosition(Math.max(0, idx), partialTick);
    }

    // ==================== 输入接收（服务端权威 + 客户端预测） ====================

    /**
     * 服务端接收主驾输入包后更新控制目标。
     */
    public void setServerInput(float throttle, float steerOverride, float targetYaw, boolean handbrake, boolean nitro) {
        this.serverThrottle = sanitizeUnit(throttle);
        this.serverSteerOverride = sanitizeUnit(steerOverride);
        this.serverTargetYaw = Float.isFinite(targetYaw) ? Mth.wrapDegrees(targetYaw) : getCarYaw();
        this.serverHandbrake = handbrake;
        this.serverNitro = nitro;
    }

    /**
     * 客户端本地输入预测，用于减少高延迟下的灯光/氮气显示滞后。
     */
    public void applyClientPrediction(float throttle, float steerOverride, boolean handbrake, boolean nitro) {
        this.clientThrottle = sanitizeUnit(throttle);
        this.clientSteerOverride = sanitizeUnit(steerOverride);
        this.clientHandbrake = handbrake;
        this.clientNitro = nitro;
        this.clientPredictionTtl = 4;
    }

    private void resetServerInputs() {
        this.serverThrottle = 0.0F;
        this.serverSteerOverride = 0.0F;
        this.serverHandbrake = false;
        this.serverNitro = false;
    }

    private static float sanitizeUnit(float val) {
        return Float.isFinite(val) ? Mth.clamp(val, -1.0F, 1.0F) : 0.0F;
    }

    // ==================== 帧循环逻辑 ====================

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            tickClient();
        } else {
            tickServer();
        }

        updateOrientationTracking();
        resetPassengersFallDistance();
    }

    private void tickClient() {
        this.prevCarYaw = getCarYaw();
        this.prevTerrainPitch = getTerrainPitch();
        this.prevTerrainRoll = getTerrainRoll();
        this.prevSteerAngle = getSteerAngle();
        this.prevDoorOpenProgress = getDoorOpenProgress();
        this.prevWheelSpin = this.wheelSpin;

        if (this.clientPredictionTtl > 0) {
            this.clientPredictionTtl--;
            if (this.clientPredictionTtl == 0) {
                this.clientThrottle = 0.0F;
                this.clientSteerOverride = 0.0F;
                this.clientHandbrake = false;
                this.clientNitro = false;
            }
        }

        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ,
                    this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        } else {
            this.reapplyPosition();
        }

        // 根据实际位移（不足时回退到同步的前向速度）累积车轮滚动角
        Vec3 forward = getWorldForward();
        Vec3 delta = new Vec3(getX() - this.xo, 0.0D, getZ() - this.zo);
        double forwardDist = delta.dot(forward);
        if (Math.abs(forwardDist) < 1.0E-4D) {
            forwardDist = getForwardSpeed();
        }
        this.wheelSpin += (float) (forwardDist / WHEEL_RADIUS);
    }

    private void tickServer() {
        float throttle = this.serverThrottle;
        float steerOverride = this.serverSteerOverride;
        boolean handbrake = this.serverHandbrake;
        boolean nitroInput = this.serverNitro;

        float yaw = getCarYaw();
        Vec3 vel = getDeltaMovement();
        Vec3 horizVel = new Vec3(vel.x, 0.0D, vel.z);
        double horizSpeed = horizVel.length();
        Vec3 forward = computeForward(yaw);
        double forwardSpeed = horizVel.dot(forward);

        // 判断当前是否处于倒车工况
        boolean reversing = (throttle < -0.05F && forwardSpeed < 0.08D)
                || (forwardSpeed < -0.05D && throttle <= 0.05F);
        boolean braking = (throttle < -0.05F) || handbrake;

        // 速度较大时按下手刹进入漂移状态
        boolean drifting = handbrake && horizSpeed > DRIFT_SPEED_THRESHOLD && onGround();

        // 氮气能量槽管理（行驶中按住 Ctrl 消耗，松开回充，漂移加快回充）
        float nitroEnergy = getNitroEnergy();
        boolean nitroActive = nitroInput && !reversing && !handbrake
                && (throttle > 0.05F || horizSpeed > 0.15D)
                && nitroEnergy > 0.5F;

        if (nitroActive) {
            nitroEnergy = Math.max(0.0F, nitroEnergy - NITRO_DRAIN_PER_TICK);
            if (nitroEnergy <= 0.0F) {
                nitroActive = false;
            }
        } else {
            float regen = drifting ? NITRO_DRIFT_REGEN_PER_TICK : NITRO_REGEN_PER_TICK;
            nitroEnergy = Math.min(MAX_NITRO_ENERGY, nitroEnergy + regen);
        }

        // 转向计算：静止不原地打转，前进/滑行追随鼠标 targetYaw + A/D 辅助，倒车仅用 A/D 反向转向
        SteeringResult steerRes = computeSteering(yaw, horizSpeed, reversing, drifting, throttle, steerOverride);
        yaw = steerRes.newYaw();
        setCarYaw(yaw);
        this.steerAngleRad = Mth.lerp(0.38F, this.steerAngleRad, steerRes.targetSteerAngle());

        // 按更新后的朝向分解纵向/横向速度并施加加减速与抓地阻尼
        forward = computeForward(yaw);
        Vec3 right = computeRight(yaw);
        double fwdSpeed = horizVel.dot(forward);
        double latSpeed = horizVel.dot(right);

        if (drifting) {
            if (throttle > 0.05F) {
                double driftTarget = throttle * NORMAL_MAX_FORWARD_SPEED * 0.88D;
                fwdSpeed = Mth.lerp(0.05D, fwdSpeed, driftTarget);
            } else {
                fwdSpeed *= HANDBRAKE_DRIFT_DAMPING;
            }
        } else if (handbrake) {
            fwdSpeed *= HANDBRAKE_STOP_DAMPING;
        } else if (throttle > 0.05F) {
            double maxFwd = nitroActive ? NITRO_MAX_FORWARD_SPEED : NORMAL_MAX_FORWARD_SPEED;
            double accel = nitroActive ? NITRO_ACCEL : FORWARD_ACCEL;
            double targetFwd = throttle * maxFwd;
            if (fwdSpeed < targetFwd) {
                fwdSpeed = Math.min(targetFwd, fwdSpeed + accel * throttle);
            } else {
                fwdSpeed = Mth.lerp(0.12D, fwdSpeed, targetFwd);
            }
        } else if (throttle < -0.05F) {
            if (fwdSpeed > 0.05D) {
                fwdSpeed = Math.max(0.0D, fwdSpeed - BRAKE_DECEL * (-throttle));
            } else {
                double targetRev = throttle * MAX_REVERSE_SPEED;
                fwdSpeed = Math.max(targetRev, fwdSpeed + throttle * REVERSE_ACCEL);
            }
        } else {
            double coast = onGround() ? COAST_DAMPING : AIR_DAMPING;
            fwdSpeed *= coast;
        }

        // 普通行驶高抓地抑制侧滑，手刹漂移放宽阻尼保留侧滑惯性
        double lateralDamping = onGround()
                ? (drifting ? DRIFT_LATERAL_GRIP : NORMAL_LATERAL_GRIP)
                : 0.96D;
        latSpeed *= lateralDamping;

        Vec3 newHoriz = forward.scale(fwdSpeed).add(right.scale(latSpeed));
        if (newHoriz.lengthSqr() < 1.0E-5D && Math.abs(throttle) <= 0.05F) {
            newHoriz = Vec3.ZERO;
        }

        // 垂直运动与水中浮力
        double vy = vel.y;
        if (isInWater()) {
            vy = Math.min(0.14D, vy * 0.88D + 0.055D);
            newHoriz = newHoriz.scale(0.90D);
        } else {
            vy = Math.max(-1.5D, vy - GRAVITY);
        }

        Vec3 intendedMove = new Vec3(newHoriz.x, vy, newHoriz.z);
        Vec3 beforePos = position();
        setDeltaMovement(intendedMove);
        this.move(MoverType.SELF, intendedMove);
        Vec3 actualDelta = position().subtract(beforePos);

        // 撞墙时保留切向滑擦速度，清除指向墙面的法向分量，不破坏方块
        double postVy = onGround() ? 0.0D : actualDelta.y;
        if (this.horizontalCollision) {
            setDeltaMovement(actualDelta.x, postVy, actualDelta.z);
        } else {
            setDeltaMovement(newHoriz.x, postVy, newHoriz.z);
        }

        // 四轮地形高度拟合（忽略单轮深坑）
        updateTerrainAlignment(yaw);

        // 高速撞击非乘客生物
        double actualHorizSpeed = Math.hypot(actualDelta.x, actualDelta.z);
        double ramCheckSpeed = Math.max(horizSpeed, actualHorizSpeed);
        if (ramCheckSpeed > RAM_SPEED_THRESHOLD) {
            performEntityRamming(forward, ramCheckSpeed);
        }

        // 自动迎宾门状态机与开关音效
        updateWelcomeDoors(actualHorizSpeed, throttle);

        // 同步数据到客户端
        double syncedFwdSpeed = new Vec3(actualDelta.x, 0.0D, actualDelta.z).dot(forward);
        this.entityData.set(DATA_STEER_ANGLE, this.steerAngleRad);
        this.entityData.set(DATA_NITRO_ENERGY, nitroEnergy);
        this.entityData.set(DATA_THROTTLE, throttle);
        this.entityData.set(DATA_BRAKING, braking);
        this.entityData.set(DATA_DRIFTING, drifting);
        this.entityData.set(DATA_NITRO_ACTIVE, nitroActive);
        this.entityData.set(DATA_FORWARD_SPEED, (float) syncedFwdSpeed);
    }

    private record SteeringResult(float newYaw, float targetSteerAngle) {
    }

    /**
     * 计算速度耦合转向与方向盘/前轮目标转角。
     */
    private SteeringResult computeSteering(float currentYaw, double horizSpeed, boolean reversing,
                                           boolean drifting, float throttle, float steerOverride) {
        if (horizSpeed < 0.03D) {
            // 静止状态禁止原地旋转车身，仅允许原地微转方向盘预览
            float idleWheelAngle = steerOverride * MAX_STEER_ANGLE_RAD * 0.6F;
            return new SteeringResult(currentYaw, idleWheelAngle);
        }

        float speedFactor = Mth.clamp((float) ((horizSpeed - 0.03D) / 0.25D), 0.0F, 1.0F);
        float highSpeedDamp = drifting ? 1.0F : (1.0F - 0.18F * Mth.clamp((float) ((horizSpeed - 0.65D) / 0.80D), 0.0F, 1.0F));
        float maxTurn = BASE_MAX_TURN_RATE_DEG * speedFactor * highSpeedDamp * (drifting ? DRIFT_TURN_MULTIPLIER : 1.0F);

        if (reversing) {
            // 倒车工况：忽略鼠标前方视角，仅响应 A/D 并反转车身偏航方向
            float reverseTurn = BASE_MAX_TURN_RATE_DEG * 0.75F * speedFactor;
            float newYaw = Mth.wrapDegrees(currentYaw + steerOverride * reverseTurn);
            float targetSteer = steerOverride * MAX_STEER_ANGLE_RAD;
            return new SteeringResult(newYaw, targetSteer);
        }

        // 前进或滑行：平滑追随鼠标 targetYaw，A/D 提供辅助转向
        float yawDiff = Mth.wrapDegrees(this.serverTargetYaw - currentYaw);
        float mouseSteer = Math.abs(yawDiff) < 0.35F ? 0.0F : Mth.clamp(-yawDiff / 28.0F, -1.0F, 1.0F);
        float combinedSteer = Math.abs(steerOverride) > 0.05F
                ? Mth.clamp(mouseSteer * 0.5F + steerOverride, -1.0F, 1.0F)
                : mouseSteer;

        float yawStep = combinedSteer * maxTurn;
        float newYaw = Mth.wrapDegrees(currentYaw - yawStep);
        float targetSteer = combinedSteer * MAX_STEER_ANGLE_RAD;
        return new SteeringResult(newYaw, targetSteer);
    }

    /**
     * 在 4 个轮位探测方块顶面高度，忽略单轮深坑，计算平滑的车身俯仰与侧倾角。
     */
    private void updateTerrainAlignment(float yaw) {
        Vec3 forward = computeForward(yaw);
        Vec3 right = computeRight(yaw);

        // 轮位：0=左前，1=右前，2=左后，3=右后
        double[] wheelX = {-WHEEL_HALF_TRACK, WHEEL_HALF_TRACK, -WHEEL_HALF_TRACK, WHEEL_HALF_TRACK};
        double[] wheelZ = {FRONT_WHEEL_Z, FRONT_WHEEL_Z, REAR_WHEEL_Z, REAR_WHEEL_Z};
        double[] heights = new double[4];
        int validCount = 0;

        for (int i = 0; i < 4; i++) {
            Vec3 worldPos = position()
                    .add(right.scale(wheelX[i]))
                    .add(forward.scale(-wheelZ[i]));
            heights[i] = probeWheelSupportHeight(worldPos.x, getY(), worldPos.z);
            if (!Double.isNaN(heights[i])) {
                validCount++;
            }
        }

        float targetPitch = 0.0F;
        float targetRoll = 0.0F;

        if (validCount >= 3) {
            // 识别并剔除单轮深坑，使用其余三轮的共面关系插值补全
            int pitIdx = findSingleWheelPitIndex(heights);
            if (pitIdx >= 0) {
                heights[pitIdx] = switch (pitIdx) {
                    case 0 -> heights[1] + heights[2] - heights[3];
                    case 1 -> heights[0] + heights[3] - heights[2];
                    case 2 -> heights[0] + heights[3] - heights[1];
                    default -> heights[1] + heights[2] - heights[0];
                };
            }

            if (Double.isFinite(heights[0]) && Double.isFinite(heights[1])
                    && Double.isFinite(heights[2]) && Double.isFinite(heights[3])) {
                double frontAvg = (heights[0] + heights[1]) * 0.5D;
                double rearAvg = (heights[2] + heights[3]) * 0.5D;
                double leftAvg = (heights[0] + heights[2]) * 0.5D;
                double rightAvg = (heights[1] + heights[3]) * 0.5D;

                targetPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(frontAvg - rearAvg, WHEELBASE)), -35.0F, 35.0F);
                targetRoll = Mth.clamp((float) Math.toDegrees(Math.atan2(leftAvg - rightAvg, TRACK_WIDTH)), -30.0F, 30.0F);
            }
        }

        float nextPitch = Mth.lerp(0.28F, getTerrainPitch(), targetPitch);
        float nextRoll = Mth.lerp(0.28F, getTerrainRoll(), targetRoll);
        if (Math.abs(nextPitch) < 0.08F && Math.abs(targetPitch) < 0.08F) {
            nextPitch = 0.0F;
        }
        if (Math.abs(nextRoll) < 0.08F && Math.abs(targetRoll) < 0.08F) {
            nextRoll = 0.0F;
        }
        this.entityData.set(DATA_TERRAIN_PITCH, nextPitch);
        this.entityData.set(DATA_TERRAIN_ROLL, nextRoll);
    }

    private double probeWheelSupportHeight(double wx, double baseY, double wz) {
        int bx = Mth.floor(wx);
        int bz = Mth.floor(wz);
        int maxScanY = Mth.floor(baseY + 1.15D);
        int minScanY = Mth.floor(baseY - 1.50D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = maxScanY; y >= minScanY; y--) {
            cursor.set(bx, y, bz);
            BlockState state = level().getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level(), cursor);
            if (shape.isEmpty()) {
                continue;
            }
            double topY = y + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (topY <= baseY + 1.15D) {
                return topY;
            }
        }
        return Double.NaN;
    }

    private static int findSingleWheelPitIndex(double[] h) {
        int nanIdx = -1;
        int nanCount = 0;
        for (int i = 0; i < 4; i++) {
            if (Double.isNaN(h[i])) {
                nanIdx = i;
                nanCount++;
            }
        }
        if (nanCount == 1) {
            return nanIdx;
        }
        if (nanCount > 1) {
            return -1;
        }

        for (int i = 0; i < 4; i++) {
            double otherMin = Double.POSITIVE_INFINITY;
            for (int j = 0; j < 4; j++) {
                if (i != j) {
                    otherMin = Math.min(otherMin, h[j]);
                }
            }
            if (h[i] < otherMin - 0.65D) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 高速撞击非乘客生物，造成轻微击退与带冷却的撞击伤害。
     */
    private void performEntityRamming(Vec3 forward, double speed) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level().getGameTime();
        if (now % 40L == 0L && !this.ramCooldowns.isEmpty()) {
            this.ramCooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        }

        boolean pvpAllowed = serverLevel.getServer().isPvpAllowed();
        AABB ramBox = getBoundingBox().inflate(0.55D);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                ramBox,
                target -> target.isAlive()
                        && !hasPassenger(target)
                        && !(target instanceof Player p && (p.isCreative() || p.isSpectator() || !pvpAllowed))
        );
        if (targets.isEmpty()) {
            return;
        }

        Entity driver = getFirstPassenger();
        DamageSource source = driver instanceof LivingEntity living
                ? damageSources().mobAttack(living)
                : damageSources().generic();

        double knockScale = Mth.clamp(speed * 0.95D, 0.45D, 1.35D);
        Vec3 knockback = forward.scale(knockScale);

        for (LivingEntity target : targets) {
            Long nextAllowed = this.ramCooldowns.get(target.getUUID());
            if (nextAllowed != null && now < nextAllowed) {
                continue;
            }
            if (target.hurt(source, RAM_DAMAGE)) {
                this.ramCooldowns.put(target.getUUID(), now + RAM_COOLDOWN_TICKS);
                Vec3 prevVel = target.getDeltaMovement();
                target.setDeltaMovement(
                        prevVel.x * 0.25D + knockback.x,
                        Math.max(prevVel.y * 0.25D + 0.32D, 0.32D),
                        prevVel.z * 0.25D + knockback.z
                );
                target.hasImpulse = true;
                target.hurtMarked = true;

                serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.75F, 0.95F);
            }
        }
    }

    /**
     * 更新自动迎宾门状态并在跨阈值时播放开关音效。
     */
    private void updateWelcomeDoors(double horizSpeed, float throttle) {
        boolean isDriving = horizSpeed > 0.08D || Math.abs(throttle) > 0.05F;
        if (isDriving) {
            this.doorStayTicks = 0;
        } else if (this.doorStayTicks > 0) {
            this.doorStayTicks--;
        }

        boolean playerNearby = false;
        if (!isDriving) {
            AABB searchBox = getBoundingBox().inflate(DOOR_PROXIMITY_RANGE);
            List<Player> nearby = level().getEntitiesOfClass(
                    Player.class,
                    searchBox,
                    p -> !p.isSpectator() && p.getVehicle() != this && distanceTo(p) <= DOOR_PROXIMITY_RANGE
            );
            playerNearby = !nearby.isEmpty();
        }

        boolean shouldOpen = !isDriving && (this.doorStayTicks > 0 || playerNearby);
        float prev = getDoorOpenProgress();
        float target = shouldOpen ? 1.0F : 0.0F;
        float next = prev;

        if (prev < target) {
            next = Math.min(target, prev + DOOR_STEP_PER_TICK);
        } else if (prev > target) {
            next = Math.max(target, prev - DOOR_STEP_PER_TICK);
        }

        if (prev < DOOR_SOUND_THRESHOLD && next >= DOOR_SOUND_THRESHOLD) {
            level().playSound(null, getX(), getY() + 0.7D, getZ(),
                    ModSounds.SPORTS_CAR_DOOR.get(), SoundSource.NEUTRAL, 0.75F, 1.08F);
        } else if (prev > DOOR_SOUND_THRESHOLD && next <= DOOR_SOUND_THRESHOLD) {
            level().playSound(null, getX(), getY() + 0.7D, getZ(),
                    ModSounds.SPORTS_CAR_DOOR.get(), SoundSource.NEUTRAL, 0.70F, 0.85F);
        }

        if (next != prev) {
            this.entityData.set(DATA_DOOR_OPEN_PROGRESS, next);
        }
    }

    // ==================== 坐标变换与插值追踪 ====================

    public static Vec3 computeForward(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad)).normalize();
    }

    public static Vec3 computeRight(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new Vec3(-Math.cos(rad), 0.0D, -Math.sin(rad)).normalize();
    }

    private static Quaternionf computeTargetOrientation(float yawDegrees, float pitchDegrees, float rollDegrees) {
        Vec3 f = computeForward(yawDegrees);
        Vec3 r = computeRight(yawDegrees);
        Matrix3f mat = new Matrix3f();
        mat.setColumn(0, (float) r.x, (float) r.y, (float) r.z);
        mat.setColumn(1, 0.0F, 1.0F, 0.0F);
        mat.setColumn(2, (float) -f.x, (float) -f.y, (float) -f.z);
        Quaternionf q = new Quaternionf().setFromNormalized(mat);
        if (Math.abs(pitchDegrees) > 1.0E-3F) {
            q.rotateX((float) Math.toRadians(pitchDegrees));
        }
        if (Math.abs(rollDegrees) > 1.0E-3F) {
            q.rotateZ((float) Math.toRadians(-rollDegrees));
        }
        return q.normalize();
    }

    private void updateOrientationTracking() {
        float yaw = getCarYaw();
        float pitch = getTerrainPitch();
        float roll = getTerrainRoll();

        if (!this.orientationInitialized) {
            Quaternionf init = computeTargetOrientation(yaw, pitch, roll);
            this.prevRenderOrientation.set(init);
            this.renderOrientation.set(init);
            this.prevCarYaw = yaw;
            this.prevTerrainPitch = pitch;
            this.prevTerrainRoll = roll;
            this.orientationInitialized = true;
            return;
        }

        this.prevRenderOrientation.set(this.renderOrientation);
        Quaternionf target = computeTargetOrientation(yaw, pitch, roll);
        if (this.renderOrientation.dot(target) < 0.0F) {
            target.mul(-1.0F);
        }
        this.renderOrientation.slerp(target, 0.72F).normalize();
    }

    private void resetPassengersFallDistance() {
        this.fallDistance = 0.0F;
        for (Entity passenger : getPassengers()) {
            passenger.resetFallDistance();
        }
    }

    // ==================== 双座乘员摆放与安全下车 ====================

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide) {
            this.doorStayTicks = DOOR_STAY_TICKS;
            if (getPassengers().size() == 1) {
                this.serverTargetYaw = getCarYaw();
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        boolean wasDriver = isDriver(passenger);
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            this.doorStayTicks = DOOR_STAY_TICKS;
            if (wasDriver || getPassengers().isEmpty()) {
                resetServerInputs();
                this.entityData.set(DATA_THROTTLE, 0.0F);
                this.entityData.set(DATA_BRAKING, false);
                this.entityData.set(DATA_DRIFTING, false);
                this.entityData.set(DATA_NITRO_ACTIVE, false);
            }
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        int seatIdx = getSeatIndex(passenger);
        if (seatIdx < 0) {
            return;
        }
        Vec3 seatPos = getSeatWorldPosition(seatIdx, 1.0F);
        Vec3 attachment = passenger.getVehicleAttachmentPoint(this);
        callback.accept(passenger,
                seatPos.x - attachment.x,
                seatPos.y - attachment.y,
                seatPos.z - attachment.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int seatIndex = getSeatIndex(passenger);
        Vec3 center = position().add(0.0D, 0.25D, 0.0D);
        Vec3 right = getWorldRight();
        Vec3 forward = getWorldForward();

        // 主驾在左侧（-R），副驾在右侧（+R），优先在各自车门外侧 ±2.0 格寻找安全落脚点
        Vec3 primarySide = seatIndex == 1 ? right : right.scale(-1.0D);
        Vec3 secondarySide = primarySide.scale(-1.0D);

        List<Vec3> candidates = new ArrayList<>(8);
        candidates.add(center.add(primarySide.scale(2.0D)));
        candidates.add(center.add(secondarySide.scale(2.0D)));
        candidates.add(center.add(primarySide.scale(2.5D)));
        candidates.add(center.add(secondarySide.scale(2.5D)));
        candidates.add(center.add(primarySide.scale(1.65D)));
        candidates.add(center.subtract(forward.scale(3.6D)));
        candidates.add(center.add(forward.scale(3.6D)));
        candidates.add(center.add(0.0D, 1.75D, 0.0D));

        double[] dyOffsets = {0.0D, -0.5D, -1.0D, 0.5D, 1.0D};
        for (Vec3 cand : candidates) {
            for (double dy : dyOffsets) {
                Vec3 test = cand.add(0.0D, dy, 0.0D);
                if (DismountHelper.canDismountTo(level(), test, passenger, Pose.STANDING)) {
                    AABB groundProbe = passenger.getLocalBoundsForPose(Pose.STANDING)
                            .move(test.x, test.y - 0.15D, test.z);
                    if (!level().noCollision(passenger, groundProbe)) {
                        return test;
                    }
                }
            }
        }

        for (Vec3 cand : candidates) {
            for (double dy : dyOffsets) {
                Vec3 test = cand.add(0.0D, dy, 0.0D);
                if (DismountHelper.canDismountTo(level(), test, passenger, Pose.STANDING)) {
                    return test;
                }
            }
        }

        return center.add(primarySide.scale(2.0D)).add(0.0D, 0.5D, 0.0D);
    }

    // ==================== 交互、无损回收与原版实体属性 ====================

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (getPassengers().isEmpty()) {
                if (!level().isClientSide && !isRemoved()) {
                    pickupToPlayer(player);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (canAddPassenger(player)) {
            if (!level().isClientSide) {
                return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved()) {
            return true;
        }
        if (source.getEntity() instanceof Player player
                && player.getMainHandItem().isEmpty()
                && getPassengers().isEmpty()) {
            if (!level().isClientSide) {
                pickupToPlayer(player);
            }
            return true;
        }
        return false;
    }

    private void pickupToPlayer(Player player) {
        ejectPassengers();
        ItemStack stack = new ItemStack(ModItems.GULF_GT_CAR.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.75F, 1.05F);
        discard();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = Math.max(1, Math.min(steps, 2));
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float) this.lerpXRot : getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : getYRot();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < MAX_PASSENGERS;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // 服务端权威输入由 SportsCarInputPayload 处理，避免原版骑乘速度上限与飞踢判定
        return null;
    }

    @Override
    public boolean dismountsUnderwater() {
        return false;
    }

    @Override
    public boolean canBeRiddenUnderFluidType(FluidType type, Entity rider) {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public float maxUpStep() {
        return 1.05F;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.GULF_GT_CAR.get());
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.EVENTS;
    }
}
