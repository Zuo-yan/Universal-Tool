package org.gwfx.universaltool.phainon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;

import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.EXTEND_TICK;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.RETREAT_TICK;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.WINDUP_TICK;

/** Horizontal advance and recoil along the same collision-checked path. */
public final class PhainonEnhancedBasicMotion {
    public static final int END_TICK = PhainonEnhancedBasic.POSE_TICKS;
    public static final float MAX_DISTANCE = 1.55F;
    private static final double STEP = 0.10;
    private static final double SUPPORT_DEPTH = 0.12;

    private final ServerPlayer player;
    private final Vec3 origin;
    private final float yaw;
    private final long startTick;
    private final float distance;
    private Vec3 heldPosition;
    private int lastHurtTime;

    private PhainonEnhancedBasicMotion(ServerPlayer player, Vec3 origin, float yaw, long startTick, float distance) {
        this.player = player;
        this.origin = origin;
        this.yaw = yaw;
        this.startTick = startTick;
        this.distance = distance;
        this.heldPosition = origin;
        this.lastHurtTime = player.hurtTime;
    }

    @Nullable
    static PhainonEnhancedBasicMotion plan(ServerPlayer player, @Nullable Entity target, long now) {
        if (!player.onGround() || !canMove(player)) return null;
        Vec3 origin = player.position();
        float yaw = player.getYRot();
        AABB targetBox = target == null ? null : target.getBoundingBox().inflate(0.12);
        Vec3 lastSafe = furthestSafe(player, origin.add(forward(yaw).scale(MAX_DISTANCE)), targetBox);
        float distance = (float) origin.distanceTo(lastSafe);
        return distance >= 0.18F ? new PhainonEnhancedBasicMotion(player, origin, yaw, now, distance) : null;
    }

    float distance() {
        return distance;
    }

    boolean ownsPosition() {
        return canMove(player) && player.hurtTime <= lastHurtTime
                && player.position().distanceToSqr(heldPosition) < 0.0225;
    }

    boolean tick(long now) {
        if (!ownsPosition()) return false;
        long age = now - startTick;
        lastHurtTime = player.hurtTime;
        heldPosition = moveTo(player, positionAt(origin, yaw, distance, age));
        player.connection.resetPosition();
        if (age == PhainonEnhancedBasic.SLASH_TICK) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.55F, 1.30F);
        }
        if (age < END_TICK) return true;
        syncPosition();
        return false;
    }

    void syncPosition() {
        if (ownsPosition()) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                    Set.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT));
        }
    }

    public static boolean canMove(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isInWater() && !player.isInLava() && !player.onClimbable();
    }

    public static Vec3 positionAt(Vec3 origin, float yaw, float distance, float age) {
        float advance = smooth((age - WINDUP_TICK) / (EXTEND_TICK - (float) WINDUP_TICK));
        float retreat = smooth((age - RETREAT_TICK) / (END_TICK - (float) RETREAT_TICK));
        return origin.add(forward(yaw).scale(distance * advance * (1 - retreat)));
    }

    public static Vec3 moveTo(Player player, Vec3 wanted) {
        Vec3 safe = furthestSafe(player, wanted, null);
        player.setPos(safe.x, safe.y, safe.z);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(0, velocity.y, 0);
        return safe;
    }

    private static Vec3 furthestSafe(Player player, Vec3 wanted, @Nullable AABB target) {
        Vec3 start = player.position();
        Vec3 delta = wanted.subtract(start);
        int steps = Mth.ceil(delta.length() / STEP);
        AABB box = player.getBoundingBox().deflate(1.0E-5);
        Vec3 safe = start;
        for (int i = 1; i <= steps; i++) {
            Vec3 offset = delta.scale((double) i / steps);
            AABB next = box.move(offset);
            if ((target != null && next.intersects(target)) || !player.level().noCollision(player, next)
                    || !player.level().getBlockCollisions(player, next.move(0, -SUPPORT_DEPTH, 0)).iterator().hasNext()) {
                break;
            }
            safe = start.add(offset);
        }
        return safe;
    }

    static Vec3 forward(float yaw) {
        return Vec3.directionFromRotation(0, yaw);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3 - 2 * t);
    }
}
