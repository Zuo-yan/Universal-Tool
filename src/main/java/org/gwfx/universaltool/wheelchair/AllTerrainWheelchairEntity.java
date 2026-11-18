package org.gwfx.universaltool.wheelchair;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.fluids.FluidType;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All-Terrain Wheelchair (Tesla Roadster omni-magnetic drive).
 *
 * <p>Coordinate frame convention:
 * <ul>
 *   <li>{@code attachedFace}: Direction from entity toward the solid surface it clings to
 *       ({@code DOWN}=floor, {@code NORTH/SOUTH/EAST/WEST}=wall, {@code UP}=ceiling).</li>
 *   <li>{@code N}: Surface normal pointing away from solid block into open air
 *       ({@code attachedFace.getOpposite()}).</li>
 *   <li>{@code F}: Local tangent forward vector rotated around {@code N} by {@code wheelchairYaw}.</li>
 *   <li>{@code R = F x N}: Local tangent right vector.</li>
 *   <li>Render orientation matrix columns are {@code (R, N, -F)} tilted by {@code slopePitch}
 *       around local {@code +X} ({@code R}). Consequently, model-local {@code -Z} aligns with
 *       world {@code +F}, model-local {@code +X} aligns with {@code +R}, and model-local {@code +Y}
 *       aligns with {@code +N}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public class AllTerrainWheelchairEntity extends Entity {

    private static final EntityDataAccessor<Direction> DATA_ATTACHED_FACE =
            SynchedEntityData.defineId(AllTerrainWheelchairEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Float> DATA_SLOPE_PITCH =
            SynchedEntityData.defineId(AllTerrainWheelchairEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BOOSTING =
            SynchedEntityData.defineId(AllTerrainWheelchairEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_STEER =
            SynchedEntityData.defineId(AllTerrainWheelchairEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(AllTerrainWheelchairEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WHEELCHAIR_YAW =
            SynchedEntityData.defineId(AllTerrainWheelchairEntity.class, EntityDataSerializers.FLOAT);

    public static final double BASE_MAX_SPEED = 0.65D;
    public static final double BOOST_MAX_SPEED = 1.25D;
    public static final float BASE_TURN_RATE = 5.5F;
    public static final float BOOST_TURN_RATE = 4.0F;
    public static final double DOWNFORCE = 0.18D;
    public static final double AIR_GRAVITY = 0.06D;
    public static final double SUPPORT_CHECK_DISTANCE = 0.40D;
    public static final double JUMP_NORMAL_IMPULSE = 0.62D;
    public static final double JUMP_FORWARD_IMPULSE = 0.35D;
    public static final float RAM_DAMAGE = 12.0F;
    public static final double RAM_INFLATE = 0.65D;
    public static final double RAM_KNOCKBACK_FORWARD = 1.4D;
    public static final double RAM_KNOCKBACK_NORMAL = 0.45D;
    public static final long RAM_COOLDOWN_TICKS = 10L;

    public static final double SEAT_NORMAL_OFFSET = 0.19D;
    public static final double SEAT_FORWARD_OFFSET = -0.08D;
    private static final float WHEEL_RADIUS = 0.44F;
    private static final float CASTER_RADIUS = 0.14F;

    // Server input state
    private float serverThrottle;
    private float serverSteer;
    private boolean serverJumping;
    private boolean pendingServerJump;
    private boolean serverBoosting;
    private boolean prevServerBoostingActive;

    // Timers & cooldowns
    private int detachCooldownTicks;
    private int jumpCooldownTicks;
    private int slopePitchHoldTicks;
    private final Map<UUID, Long> ramCooldowns = new HashMap<>();

    // Client interpolation & networking lerp state
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // Local client input prediction / animation state
    private float clientThrottle;
    private float clientSteer;
    private boolean clientJumping;
    private boolean clientBoosting;
    private int clientInputTtl;

    private float prevWheelchairYaw;
    private float prevSlopePitch;
    private Direction prevAttachedFace = Direction.DOWN;
    private final Quaternionf prevRenderOrientation = new Quaternionf();
    private final Quaternionf renderOrientation = new Quaternionf();
    private boolean orientationInitialized;

    private float prevLeftWheelSpin;
    private float leftWheelSpin;
    private float prevRightWheelSpin;
    private float rightWheelSpin;
    private float prevCasterYaw;
    private float casterYaw;
    private float prevCasterSpin;
    private float casterSpin;
    private float prevJoystickPitch;
    private float joystickPitch;
    private float prevJoystickRoll;
    private float joystickRoll;

    public AllTerrainWheelchairEntity(EntityType<? extends AllTerrainWheelchairEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @SubscribeEvent
    public static void onRiderIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof AllTerrainWheelchairEntity) {
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
        builder.define(DATA_ATTACHED_FACE, Direction.DOWN);
        builder.define(DATA_SLOPE_PITCH, 0.0F);
        builder.define(DATA_BOOSTING, false);
        builder.define(DATA_STEER, 0.0F);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_WHEELCHAIR_YAW, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AttachedFace")) {
            Direction face = Direction.byName(tag.getString("AttachedFace"));
            setAttachedFace(face != null ? face : Direction.DOWN);
        }
        if (tag.contains("WheelchairYaw")) {
            setWheelchairYaw(tag.getFloat("WheelchairYaw"));
        }
        if (tag.contains("SlopePitch")) {
            this.entityData.set(DATA_SLOPE_PITCH, tag.getFloat("SlopePitch"));
        }
        if (tag.contains("Boosting")) {
            this.entityData.set(DATA_BOOSTING, tag.getBoolean("Boosting"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("AttachedFace", getAttachedFace().getName());
        tag.putFloat("WheelchairYaw", getWheelchairYaw());
        tag.putFloat("SlopePitch", getSlopePitch());
        tag.putBoolean("Boosting", isBoosting());
    }

    public Direction getAttachedFace() {
        return this.entityData.get(DATA_ATTACHED_FACE);
    }

    public void setAttachedFace(Direction face) {
        this.entityData.set(DATA_ATTACHED_FACE, face != null ? face : Direction.DOWN);
    }

    public float getSlopePitch() {
        return this.entityData.get(DATA_SLOPE_PITCH);
    }

    public float getSlopePitch(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevSlopePitch, getSlopePitch());
    }

    public float getWheelchairYaw() {
        return this.entityData.get(DATA_WHEELCHAIR_YAW);
    }

    public float getWheelchairYaw(float partialTick) {
        if (this.prevAttachedFace != getAttachedFace()) {
            return getWheelchairYaw();
        }
        return Mth.rotLerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevWheelchairYaw, getWheelchairYaw());
    }

    public void setWheelchairYaw(float yaw) {
        this.entityData.set(DATA_WHEELCHAIR_YAW, Mth.wrapDegrees(yaw));
    }

    public float getThrottle() {
        return this.entityData.get(DATA_THROTTLE);
    }

    public float getSteer() {
        return this.entityData.get(DATA_STEER);
    }

    public boolean isBoosting() {
        if (this.entityData.get(DATA_BOOSTING)) {
            return true;
        }
        return level().isClientSide
                && this.clientInputTtl > 0
                && this.clientBoosting
                && Math.abs(this.clientThrottle) > 0.05F;
    }

    public void applyClientInput(float throttle, float steer, boolean jumping, boolean boosting) {
        this.clientThrottle = sanitizeUnitAxis(throttle);
        this.clientSteer = sanitizeUnitAxis(steer);
        this.clientJumping = jumping;
        this.clientBoosting = boosting;
        this.clientInputTtl = 4;
    }

    public void setServerInput(float throttle, float steer, boolean jumping, boolean boosting) {
        this.serverThrottle = sanitizeUnitAxis(throttle);
        this.serverSteer = sanitizeUnitAxis(steer);
        this.serverJumping = jumping;
        if (jumping) {
            this.pendingServerJump = true;
        }
        this.serverBoosting = boosting;
    }

    private static float sanitizeUnitAxis(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, -1.0F, 1.0F) : 0.0F;
    }

    public Vec3 getWorldNormal() {
        return computeNormal(getAttachedFace());
    }

    public Vec3 getWorldForward() {
        return computeForward(getAttachedFace(), getWheelchairYaw());
    }

    public Vec3 getWorldRight() {
        return computeRight(getAttachedFace(), getWheelchairYaw());
    }

    public Vec3 getWheelchairCenter(float partialTick) {
        double t = Mth.clamp(partialTick, 0.0F, 1.0F);
        double cx = Mth.lerp(t, this.xo, this.getX());
        double cy = Mth.lerp(t, this.yo, this.getY()) + this.getBbHeight() * 0.5D;
        double cz = Mth.lerp(t, this.zo, this.getZ());
        return new Vec3(cx, cy, cz);
    }

    public Vec3 getSeatPosition(float partialTick) {
        Vec3 center = getWheelchairCenter(partialTick);
        Quaternionf orient = getRenderOrientation(partialTick);
        Vector3f localOffset = new Vector3f(0.0F, (float) SEAT_NORMAL_OFFSET, (float) (-SEAT_FORWARD_OFFSET));
        orient.transform(localOffset);
        return center.add(localOffset.x, localOffset.y, localOffset.z);
    }

    public Quaternionf getRenderOrientation(float partialTick) {
        if (!this.orientationInitialized) {
            Quaternionf target = computeTargetOrientation(getAttachedFace(), getWheelchairYaw(), getSlopePitch());
            this.prevRenderOrientation.set(target);
            this.renderOrientation.set(target);
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

    public float getLeftWheelSpin(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevLeftWheelSpin, this.leftWheelSpin);
    }

    public float getRightWheelSpin(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevRightWheelSpin, this.rightWheelSpin);
    }

    public float getCasterYaw(float partialTick) {
        float t = Mth.clamp(partialTick, 0.0F, 1.0F);
        float delta = wrapAngleRadians(this.casterYaw - this.prevCasterYaw);
        return this.prevCasterYaw + delta * t;
    }

    public float getCasterSpin(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevCasterSpin, this.casterSpin);
    }

    public float getJoystickPitch(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevJoystickPitch, this.joystickPitch);
    }

    public float getJoystickRoll(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevJoystickRoll, this.joystickRoll);
    }

    public float yawForForward(Direction face, Vec3 forwardVec) {
        Vec3 n = computeNormal(face);
        Vec3 proj = forwardVec.subtract(n.scale(forwardVec.dot(n)));
        if (proj.lengthSqr() < 1.0E-6D) {
            return 0.0F;
        }
        proj = proj.normalize();
        double fDot = proj.dot(refForward(face));
        double rDot = proj.dot(refRight(face));
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(rDot, fDot)));
    }

    public static Vec3 computeNormal(Direction face) {
        return Vec3.atLowerCornerOf(face.getOpposite().getNormal());
    }

    public static Vec3 computeForward(Direction face, float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return refForward(face).scale(cos).add(refRight(face).scale(sin)).normalize();
    }

    public static Vec3 computeRight(Direction face, float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return refRight(face).scale(cos).subtract(refForward(face).scale(sin)).normalize();
    }

    private static Vec3 refForward(Direction face) {
        return switch (face) {
            case DOWN, UP -> new Vec3(0.0D, 0.0D, 1.0D);
            case NORTH, SOUTH, EAST, WEST -> new Vec3(0.0D, 1.0D, 0.0D);
        };
    }

    private static Vec3 refRight(Direction face) {
        return switch (face) {
            case DOWN -> new Vec3(-1.0D, 0.0D, 0.0D);
            case UP -> new Vec3(1.0D, 0.0D, 0.0D);
            case NORTH -> new Vec3(1.0D, 0.0D, 0.0D);
            case SOUTH -> new Vec3(-1.0D, 0.0D, 0.0D);
            case EAST -> new Vec3(0.0D, 0.0D, 1.0D);
            case WEST -> new Vec3(0.0D, 0.0D, -1.0D);
        };
    }

    private static Quaternionf computeTargetOrientation(Direction face, float yawDegrees, float slopePitch) {
        Vec3 f = computeForward(face, yawDegrees);
        Vec3 n = computeNormal(face);
        Vec3 r = computeRight(face, yawDegrees);
        Matrix3f mat = new Matrix3f();
        mat.setColumn(0, (float) r.x, (float) r.y, (float) r.z);
        mat.setColumn(1, (float) n.x, (float) n.y, (float) n.z);
        mat.setColumn(2, (float) -f.x, (float) -f.y, (float) -f.z);
        Quaternionf q = new Quaternionf().setFromNormalized(mat);
        float pitchRad = (float) Math.toRadians(-slopePitch);
        if (Math.abs(pitchRad) > 1.0E-4F) {
            q.rotateX(pitchRad);
        }
        return q.normalize();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.detachCooldownTicks > 0) {
            this.detachCooldownTicks--;
        }
        if (this.jumpCooldownTicks > 0) {
            this.jumpCooldownTicks--;
        }

        if (level().isClientSide) {
            tickClient();
        } else {
            tickServer();
        }

        updateOrientationTracking();
        updateClientAnimations();
        resetPassengersFallDistance();
    }

    private void tickClient() {
        if (this.clientInputTtl > 0) {
            this.clientInputTtl--;
            if (this.clientInputTtl == 0) {
                this.clientThrottle = 0.0F;
                this.clientSteer = 0.0F;
                this.clientJumping = false;
                this.clientBoosting = false;
            }
        }

        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ,
                    this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        } else {
            this.reapplyPosition();
        }

        if (isBoosting() && Math.abs(getEffectiveThrottle()) > 0.05F) {
            spawnThrusterParticlesClient();
        }
    }

    private void tickServer() {
        Direction face = getAttachedFace();
        float yaw = getWheelchairYaw();
        float throttle = this.serverThrottle;
        float steer = this.serverSteer;
        boolean boostingInput = this.serverBoosting;
        boolean boostingActive = boostingInput && Math.abs(throttle) > 0.05F;
        boolean wantJump = this.serverJumping || this.pendingServerJump;
        this.pendingServerJump = false;

        if (boostingActive && !this.prevServerBoostingActive) {
            if (getFirstPassenger() instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("message.universal_tool.wheelchair.boost")
                                .withStyle(ChatFormatting.AQUA),
                        true);
            }
            level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.55F, 1.35F);
        }
        this.prevServerBoostingActive = boostingActive;

        // Steering rotation around N
        if (Math.abs(steer) > 0.01F) {
            float turnRate = boostingActive ? BOOST_TURN_RATE : BASE_TURN_RATE;
            yaw = Mth.wrapDegrees(yaw - steer * turnRate);
            setWheelchairYaw(yaw);
        }

        Vec3 normal = computeNormal(face);
        Vec3 forward = computeForward(face, yaw);
        boolean supported = hasSurfaceSupport(face);
        boolean inWater = isInWater();

        // Jump action
        if (wantJump && this.jumpCooldownTicks == 0 && (supported || onGround() || inWater)) {
            Vec3 launchVel = normal.scale(JUMP_NORMAL_IMPULSE).add(forward.scale(JUMP_FORWARD_IMPULSE));
            setDeltaMovement(launchVel);
            if (face != Direction.DOWN) {
                float horizYaw = horizontalYawFromVectors(forward, normal);
                setAttachedFace(Direction.DOWN);
                setWheelchairYaw(horizYaw);
                face = Direction.DOWN;
                normal = computeNormal(face);
                forward = computeForward(face, horizYaw);
            }
            this.entityData.set(DATA_SLOPE_PITCH, 0.0F);
            this.slopePitchHoldTicks = 0;
            this.detachCooldownTicks = 5;
            this.jumpCooldownTicks = 8;
            level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.75F, 1.15F);
            supported = false;
        }

        // Compute velocity in current tangent frame
        Vec3 vel = getDeltaMovement();
        double normalSpeed = vel.dot(normal);
        Vec3 tangentVel = vel.subtract(normal.scale(normalSpeed));
        double maxSpeed = boostingActive ? BOOST_MAX_SPEED : BASE_MAX_SPEED;

        if (Math.abs(throttle) > 0.05F) {
            double accelFactor = boostingActive ? 0.45D : 0.40D;
            // High lateral grip + snappy Roadster longitudinal accel
            double curFwd = tangentVel.dot(forward);
            Vec3 lateral = tangentVel.subtract(forward.scale(curFwd));
            double newFwd = Mth.lerp(accelFactor, curFwd, throttle * maxSpeed);
            tangentVel = forward.scale(newFwd).add(lateral.scale(0.25D));
        } else {
            double damping = (supported || onGround() || inWater) ? 0.65D : 0.96D;
            tangentVel = tangentVel.scale(damping);
            if (tangentVel.lengthSqr() < 2.5E-5D) {
                tangentVel = Vec3.ZERO;
            }
        }

        // Normal force (magnetic downforce vs buoyancy vs gravity)
        if (this.detachCooldownTicks > 0) {
            normalSpeed -= AIR_GRAVITY;
        } else if (supported) {
            normalSpeed = -DOWNFORCE;
        } else if (inWater && face == Direction.DOWN) {
            double waterHeight = getFluidHeight(FluidTags.WATER);
            double buoy = waterHeight > 0.35D ? 0.075D : (waterHeight > 0.12D ? 0.015D : -0.02D);
            normalSpeed = Mth.clamp(normalSpeed + buoy, -0.35D, 0.25D);
        } else if (face == Direction.DOWN) {
            normalSpeed = Math.max(-1.5D, normalSpeed - AIR_GRAVITY);
        } else {
            normalSpeed = 0.0D;
        }

        Vec3 moveVec = tangentVel.add(normal.scale(normalSpeed));
        Vec3 travelDir = null;
        if (Math.abs(throttle) > 0.05F) {
            travelDir = throttle > 0.0F ? forward : forward.scale(-1.0D);
        } else if (tangentVel.lengthSqr() > 4.0E-4D) {
            travelDir = tangentVel.normalize();
        }

        double preMoveSpeed = tangentVel.length();
        Vec3 beforePos = position();
        setDeltaMovement(moveVec);
        this.move(MoverType.SELF, moveVec);

        Vec3 actualDelta = position().subtract(beforePos);

        // Track 1-block step-up slope pitch on floor
        boolean steppedUp = false;
        if (face == Direction.DOWN && this.detachCooldownTicks == 0
                && actualDelta.y > 0.20D && normalSpeed <= 0.05D) {
            steppedUp = true;
            float pitch = throttle < -0.05F ? 35.0F : -35.0F;
            this.entityData.set(DATA_SLOPE_PITCH, pitch);
            this.slopePitchHoldTicks = 3;
        } else if (this.slopePitchHoldTicks > 0) {
            this.slopePitchHoldTicks--;
        } else {
            float curPitch = getSlopePitch();
            if (Math.abs(curPitch) > 0.5F) {
                this.entityData.set(DATA_SLOPE_PITCH, curPitch * 0.72F);
            } else if (curPitch != 0.0F) {
                this.entityData.set(DATA_SLOPE_PITCH, 0.0F);
            }
        }

        // Concave surface climb check (Floor -> Wall -> Ceiling, Wall -> Wall, Ceiling -> Wall)
        boolean transitioned = false;
        if (!steppedUp && this.detachCooldownTicks == 0 && travelDir != null && Math.abs(throttle) > 0.05F) {
            double desiredFwdDist = tangentVel.dot(travelDir);
            double actualFwdDist = actualDelta.dot(travelDir);
            if (desiredFwdDist > 0.03D && actualFwdDist < desiredFwdDist * 0.85D) {
                Direction hitWallFace = findBlockedSurfaceDirection(face, travelDir);
                if (hitWallFace != null) {
                    transitionConcave(face, hitWallFace, forward, Math.max(preMoveSpeed, Math.abs(throttle) * maxSpeed * 0.65D), throttle);
                    transitioned = true;
                }
            }
        }

        // Convex edge wrapping or detachment when support disappears on wall/ceiling
        if (!transitioned && this.detachCooldownTicks == 0 && face != Direction.DOWN && !hasSurfaceSupport(face)) {
            Vec3 wrapTravel = travelDir != null ? travelDir : forward;
            if (!tryWrapConvexEdge(face, forward, wrapTravel, Math.max(preMoveSpeed, Math.abs(throttle) * maxSpeed * 0.55D), throttle)) {
                detachToFloor(forward, normal);
            }
        }

        // Sync driving inputs & boost flag
        this.entityData.set(DATA_THROTTLE, throttle);
        this.entityData.set(DATA_STEER, steer);
        this.entityData.set(DATA_BOOSTING, boostingActive);
        syncVanillaYaw();

        if (boostingActive) {
            performBoostRamming(forward, normal);
        }
    }

    private boolean hasSurfaceSupport(Direction face) {
        Vec3 checkOffset = Vec3.atLowerCornerOf(face.getNormal()).scale(SUPPORT_CHECK_DISTANCE);
        AABB probe = getBoundingBox().expandTowards(checkOffset);
        return !level().noCollision(this, probe);
    }

    @Nullable
    private Direction findBlockedSurfaceDirection(Direction currentFace, Vec3 travelDir) {
        List<Direction> candidates = new ArrayList<>(4);
        for (Direction dir : Direction.values()) {
            if (dir == currentFace || dir == currentFace.getOpposite()) {
                continue;
            }
            Vec3 dirVec = Vec3.atLowerCornerOf(dir.getNormal());
            if (dirVec.dot(travelDir) > 0.35D) {
                candidates.add(dir);
            }
        }
        candidates.sort(Comparator.comparingDouble((Direction d) ->
                Vec3.atLowerCornerOf(d.getNormal()).dot(travelDir)).reversed());

        for (Direction cand : candidates) {
            Vec3 step = Vec3.atLowerCornerOf(cand.getNormal()).scale(0.14D);
            AABB probe = getBoundingBox().move(step);
            if (!level().noCollision(this, probe)) {
                return cand;
            }
        }
        return null;
    }

    private void transitionConcave(Direction oldFace, Direction newFace, Vec3 oldForward, double speed, float throttle) {
        Vec3 oldN = computeNormal(oldFace);
        Vec3 newN = computeNormal(newFace);
        Vec3 newF = rotateVectorBetweenNormals(oldN, newN, oldForward);
        float newYaw = yawForForward(newFace, newF);

        setAttachedFace(newFace);
        setWheelchairYaw(newYaw);
        this.entityData.set(DATA_SLOPE_PITCH, 0.0F);
        this.slopePitchHoldTicks = 0;

        Vec3 recomputedF = computeForward(newFace, newYaw);
        Vec3 travelF = throttle >= 0.0F ? recomputedF : recomputedF.scale(-1.0D);
        // Nudge slightly into open space along newN + forward along travelF so AABB doesn't snag the corner
        Vec3 nudge = newN.scale(0.03D).add(travelF.scale(0.06D));
        this.move(MoverType.SELF, nudge);

        setDeltaMovement(travelF.scale(speed).add(newN.scale(-DOWNFORCE)));
    }

    private boolean tryWrapConvexEdge(Direction oldFace, Vec3 oldForward, Vec3 travelDir, double speed, float throttle) {
        Vec3 oldN = computeNormal(oldFace);
        Direction bestDir = null;
        double bestDot = 0.35D;
        for (Direction dir : Direction.values()) {
            if (dir == oldFace || dir == oldFace.getOpposite()) {
                continue;
            }
            double dot = Vec3.atLowerCornerOf(dir.getNormal()).dot(travelDir);
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }
        if (bestDir == null) {
            return false;
        }

        Direction newFace = bestDir.getOpposite();
        Vec3 newN = computeNormal(newFace);
        Vec3 supportStep = Vec3.atLowerCornerOf(newFace.getNormal()).scale(SUPPORT_CHECK_DISTANCE + 0.08D);

        for (double dist : new double[]{0.60D, 0.45D}) {
            Vec3 wrapOffset = oldN.scale(-dist);
            AABB wrappedBox = getBoundingBox().move(wrapOffset);
            if (level().noCollision(this, wrappedBox)
                    && !level().noCollision(this, wrappedBox.expandTowards(supportStep))) {
                Vec3 newF = rotateVectorBetweenNormals(oldN, newN, oldForward);
                float newYaw = yawForForward(newFace, newF);

                setAttachedFace(newFace);
                setWheelchairYaw(newYaw);
                this.entityData.set(DATA_SLOPE_PITCH, 0.0F);
                this.slopePitchHoldTicks = 0;

                this.move(MoverType.SELF, wrapOffset.add(newN.scale(-0.08D)));
                Vec3 recomputedF = computeForward(newFace, newYaw);
                Vec3 travelF = throttle >= 0.0F ? recomputedF : recomputedF.scale(-1.0D);
                setDeltaMovement(travelF.scale(speed).add(newN.scale(-DOWNFORCE)));
                return true;
            }
        }
        return false;
    }

    private void detachToFloor(Vec3 oldForward, Vec3 oldNormal) {
        float horizYaw = horizontalYawFromVectors(oldForward, oldNormal);
        setAttachedFace(Direction.DOWN);
        setWheelchairYaw(horizYaw);
        this.entityData.set(DATA_SLOPE_PITCH, 0.0F);
        this.slopePitchHoldTicks = 0;
        Vec3 vel = getDeltaMovement();
        setDeltaMovement(vel.x, Math.min(vel.y, 0.0D) - AIR_GRAVITY, vel.z);
    }

    private float horizontalYawFromVectors(Vec3 forward, Vec3 fallbackNormal) {
        Vec3 horiz = new Vec3(forward.x, 0.0D, forward.z);
        if (horiz.lengthSqr() < 1.0E-4D) {
            horiz = new Vec3(fallbackNormal.x, 0.0D, fallbackNormal.z);
        }
        if (horiz.lengthSqr() < 1.0E-4D) {
            return getWheelchairYaw();
        }
        return yawForForward(Direction.DOWN, horiz.normalize());
    }

    private static Vec3 rotateVectorBetweenNormals(Vec3 oldN, Vec3 newN, Vec3 vec) {
        if (oldN.distanceToSqr(newN) < 1.0E-6D) {
            return vec.normalize();
        }
        if (oldN.dot(newN) < -0.99D) {
            return vec.scale(-1.0D).normalize();
        }
        Vec3 axis = oldN.cross(newN);
        double axisLenSq = axis.lengthSqr();
        if (axisLenSq < 1.0E-6D) {
            return vec.normalize();
        }
        axis = axis.scale(1.0D / Math.sqrt(axisLenSq));
        return axis.scale(axis.dot(vec)).add(axis.cross(vec)).normalize();
    }

    private void syncVanillaYaw() {
        Vec3 f = getWorldForward();
        if (f.x * f.x + f.z * f.z > 1.0E-4D) {
            float yRot = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
            setYRot(Mth.wrapDegrees(yRot));
        } else {
            setYRot(getWheelchairYaw());
        }
    }

    private void performBoostRamming(Vec3 forward, Vec3 normal) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level().getGameTime();
        if (now % 40L == 0L && !this.ramCooldowns.isEmpty()) {
            this.ramCooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        }

        AABB ramBox = getBoundingBox().inflate(RAM_INFLATE);
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class,
                ramBox,
                target -> target.isAlive()
                        && !hasPassenger(target)
                        && (target instanceof Enemy || !(target instanceof Player))
        );
        if (targets.isEmpty()) {
            return;
        }

        Entity rider = getFirstPassenger();
        DamageSource source = rider instanceof LivingEntity living
                ? damageSources().mobAttack(living)
                : damageSources().generic();

        Vec3 knockback = forward.scale(RAM_KNOCKBACK_FORWARD).add(normal.scale(RAM_KNOCKBACK_NORMAL));

        for (LivingEntity target : targets) {
            Long nextAllowed = this.ramCooldowns.get(target.getUUID());
            if (nextAllowed != null && now < nextAllowed) {
                continue;
            }
            if (target.hurt(source, RAM_DAMAGE)) {
                this.ramCooldowns.put(target.getUUID(), now + RAM_COOLDOWN_TICKS);
                Vec3 prevVel = target.getDeltaMovement();
                target.setDeltaMovement(
                        prevVel.x * 0.2D + knockback.x,
                        Math.max(prevVel.y * 0.2D + knockback.y, 0.35D),
                        prevVel.z * 0.2D + knockback.z
                );
                target.hasImpulse = true;
                target.hurtMarked = true;

                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                        12, 0.3D, 0.3D, 0.3D, 0.15D);
                serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.85F);
            }
        }
    }

    private void spawnThrusterParticlesClient() {
        Vec3 center = getWheelchairCenter(1.0F);
        Quaternionf orient = getRenderOrientation(1.0F);
        Vector3f exhaustDir = new Vector3f(0.0F, 0.0F, 1.0F);
        orient.transform(exhaustDir);

        for (float side : new float[]{-0.24F, 0.24F}) {
            Vector3f nozzleLocal = new Vector3f(side, -0.34F, 0.58F);
            orient.transform(nozzleLocal);
            double px = center.x + nozzleLocal.x;
            double py = center.y + nozzleLocal.y;
            double pz = center.z + nozzleLocal.z;

            double vx = exhaustDir.x * 0.28D + (this.random.nextDouble() - 0.5D) * 0.04D;
            double vy = exhaustDir.y * 0.28D + (this.random.nextDouble() - 0.5D) * 0.04D;
            double vz = exhaustDir.z * 0.28D + (this.random.nextDouble() - 0.5D) * 0.04D;

            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx, vy, vz);
            if ((this.tickCount & 1) == 0) {
                level().addParticle(ParticleTypes.END_ROD, px, py, pz, vx * 0.7D, vy * 0.7D, vz * 0.7D);
            }
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, vx * 1.1D, vy * 1.1D, vz * 1.1D);
        }
    }

    private void updateOrientationTracking() {
        Direction face = getAttachedFace();
        float yaw = getWheelchairYaw();
        float pitch = getSlopePitch();

        if (!this.orientationInitialized) {
            Quaternionf init = computeTargetOrientation(face, yaw, pitch);
            this.prevRenderOrientation.set(init);
            this.renderOrientation.set(init);
            this.prevWheelchairYaw = yaw;
            this.prevSlopePitch = pitch;
            this.prevAttachedFace = face;
            this.orientationInitialized = true;
            return;
        }

        if (this.prevAttachedFace != face) {
            this.prevWheelchairYaw = yaw;
        }
        this.prevRenderOrientation.set(this.renderOrientation);

        Quaternionf target = computeTargetOrientation(face, yaw, pitch);
        if (this.renderOrientation.dot(target) < 0.0F) {
            target.mul(-1.0F);
        }
        float dot = Mth.clamp(this.renderOrientation.dot(target), -1.0F, 1.0F);
        boolean largeTurn = this.prevAttachedFace != face || dot < 0.96F;
        float slerpAlpha = largeTurn ? 0.38F : 0.72F;
        this.renderOrientation.slerp(target, slerpAlpha).normalize();

        this.prevWheelchairYaw = yaw;
        this.prevSlopePitch = pitch;
        this.prevAttachedFace = face;
    }

    private void updateClientAnimations() {
        this.prevLeftWheelSpin = this.leftWheelSpin;
        this.prevRightWheelSpin = this.rightWheelSpin;
        this.prevCasterYaw = this.casterYaw;
        this.prevCasterSpin = this.casterSpin;
        this.prevJoystickPitch = this.joystickPitch;
        this.prevJoystickRoll = this.joystickRoll;

        float effThrottle = getEffectiveThrottle();
        float effSteer = getEffectiveSteer();

        Vec3 forward = getWorldForward();
        Vec3 actualDelta = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
        float forwardDist = (float) actualDelta.dot(forward);
        if (Math.abs(forwardDist) < 1.0E-4F && Math.abs(effThrottle) > 0.05F) {
            forwardDist = effThrottle * (isBoosting() ? (float) BOOST_MAX_SPEED : (float) BASE_MAX_SPEED) * 0.25F;
        }

        float wheelBaseAdvance = forwardDist / WHEEL_RADIUS;
        float steerDiff = effSteer * 0.22F;
        // Steering left (+1): left wheel turns slower/backward, right wheel turns faster/forward
        this.leftWheelSpin += wheelBaseAdvance - steerDiff;
        this.rightWheelSpin += wheelBaseAdvance + steerDiff;

        float targetCasterYaw;
        if (forwardDist < -0.02F || (Math.abs(forwardDist) <= 0.02F && effThrottle < -0.05F)) {
            targetCasterYaw = (float) Math.PI + effSteer * 0.35F;
        } else {
            targetCasterYaw = effSteer * 0.45F;
        }
        float yawDiff = wrapAngleRadians(targetCasterYaw - this.casterYaw);
        this.casterYaw = wrapAngleRadians(this.casterYaw + yawDiff * 0.35F);

        this.casterSpin += (forwardDist * Mth.cos(this.casterYaw)) / CASTER_RADIUS;

        float targetJoyPitch = effThrottle * 0.35F;
        float targetJoyRoll = effSteer * 0.35F;
        this.joystickPitch = Mth.lerp(0.45F, this.joystickPitch, targetJoyPitch);
        this.joystickRoll = Mth.lerp(0.45F, this.joystickRoll, targetJoyRoll);
    }

    private float getEffectiveThrottle() {
        return (level().isClientSide && this.clientInputTtl > 0) ? this.clientThrottle : getThrottle();
    }

    private float getEffectiveSteer() {
        return (level().isClientSide && this.clientInputTtl > 0) ? this.clientSteer : getSteer();
    }

    private static float wrapAngleRadians(float angle) {
        return Mth.wrapDegrees(angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }

    private void resetPassengersFallDistance() {
        this.fallDistance = 0.0F;
        for (Entity passenger : getPassengers()) {
            passenger.resetFallDistance();
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!hasPassenger(passenger)) {
            return;
        }
        Vec3 seatPos = getSeatPosition(1.0F);
        Vec3 attachment = passenger.getVehicleAttachmentPoint(this);
        callback.accept(passenger,
                seatPos.x - attachment.x,
                seatPos.y - attachment.y,
                seatPos.z - attachment.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 center = getWheelchairCenter(1.0F);
        Vec3 normal = getWorldNormal();
        Vec3 right = getWorldRight();
        Vec3 forward = getWorldForward();

        List<Vec3> candidates = new ArrayList<>();
        Vec3 base = center.add(normal.scale(0.85D));
        candidates.add(base.add(right.scale(1.05D)));
        candidates.add(base.subtract(right.scale(1.05D)));
        candidates.add(base.subtract(forward.scale(1.05D)));
        candidates.add(base.add(forward.scale(1.05D)));
        candidates.add(center.add(normal.scale(1.15D)));
        candidates.add(center.add(normal.scale(1.35D)).subtract(0.0D, 0.9D, 0.0D));

        // Prefer a collision-free location with solid footing below
        for (Vec3 cand : candidates) {
            for (double dy : new double[]{0.0D, -0.5D, -1.0D, 0.5D}) {
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

        // Otherwise pick any collision-free open-air spot away from walls/ceilings
        for (Vec3 cand : candidates) {
            for (double dy : new double[]{0.0D, -0.5D, -1.0D, -1.5D, 0.5D}) {
                Vec3 test = cand.add(0.0D, dy, 0.0D);
                if (DismountHelper.canDismountTo(level(), test, passenger, Pose.STANDING)) {
                    return test;
                }
            }
        }

        if (getAttachedFace() == Direction.UP) {
            return new Vec3(center.x, center.y - 1.5D, center.z);
        }
        return position().add(normal.scale(1.1D));
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getPassengers().isEmpty()) {
            this.serverThrottle = 0.0F;
            this.serverSteer = 0.0F;
            this.serverJumping = false;
            this.pendingServerJump = false;
            this.serverBoosting = false;
            this.prevServerBoostingActive = false;
            if (!level().isClientSide) {
                this.entityData.set(DATA_THROTTLE, 0.0F);
                this.entityData.set(DATA_STEER, 0.0F);
                this.entityData.set(DATA_BOOSTING, false);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide && !isRemoved()) {
                pickupToPlayer(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
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
        if (level().isClientSide) {
            return source.getEntity() instanceof Player;
        }
        if (source.getEntity() instanceof Player player) {
            pickupToPlayer(player);
            return true;
        }
        return false;
    }

    private void pickupToPlayer(Player player) {
        ejectPassengers();
        ItemStack stack = new ItemStack(ModItems.ALL_TERRAIN_WHEELCHAIR.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.1F);
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
        return getPassengers().isEmpty();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // Server-authoritative via WheelchairInputPayload (avoids vanilla fly-kick / speed-cap on walls & ceilings).
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
        return getAttachedFace() == Direction.DOWN ? 1.05F : 0.0F;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ALL_TERRAIN_WHEELCHAIR.get());
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.EVENTS;
    }
}
