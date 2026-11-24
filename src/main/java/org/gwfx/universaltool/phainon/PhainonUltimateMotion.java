package org.gwfx.universaltool.phainon;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.gwfx.universaltool.UniversalToolMod;

public final class PhainonUltimateMotion {
    private static final double MAX_LIFT = 1.4;
    private static final ResourceLocation GRAVITY_ID = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "phainon_ultimate_suspend");
    private static final AttributeModifier SUSPEND = new AttributeModifier(
            GRAVITY_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    final ServerPlayer player;
    final Vec3 origin;
    final float height;
    private Vec3 heldPosition;
    private boolean locked = true;

    PhainonUltimateMotion(ServerPlayer player) {
        this.player = player;
        this.origin = player.position();
        this.heldPosition = origin;
        var box = player.getBoundingBox().deflate(1.0E-5);
        var blocks = player.level().getBlockCollisions(player, box.expandTowards(0, MAX_LIFT, 0));
        double clearance = Shapes.collide(Direction.Axis.Y, box, blocks, MAX_LIFT);
        this.height = (float) Math.max(0.0, clearance - 0.025);
        var gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(GRAVITY_ID);
            gravity.addTransientModifier(SUSPEND);
        }
        stopMomentum();
    }

    public static float liftAt(float age, float height) {
        float rise = smoothStep((age - 5.0F) / 22.0F);
        float descend = smoothStep((age - PhainonUltimateHandler.AWAKEN_TICK - 9.0F)
                / (PhainonUltimateHandler.OPENING_TICKS - PhainonUltimateHandler.AWAKEN_TICK - 9.0F));
        return height * rise * (1.0F - descend);
    }

    boolean ownsPosition() {
        return locked && player.position().distanceToSqr(heldPosition) < 0.015625;
    }

    boolean locked() {
        return locked;
    }

    boolean positionAt(long age) {
        if (!ownsPosition()) return false;
        Vec3 wanted = origin.add(0, liftAt(age, height), 0);
        var box = player.getBoundingBox().move(wanted.subtract(player.position())).deflate(1.0E-5);
        if (wanted.y != player.getY() && !player.level().noCollision(player, box)) return false;

        heldPosition = wanted;
        player.setPos(wanted.x, wanted.y, wanted.z);
        player.connection.resetPosition();
        if (wanted.y > origin.y + 0.01) player.setOnGround(false);
        stopMomentum();
        return true;
    }

    void stopMomentum() {
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    void release() {
        if (!locked) return;
        locked = false;
        var gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.removeModifier(GRAVITY_ID);
    }

    private static float smoothStep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
