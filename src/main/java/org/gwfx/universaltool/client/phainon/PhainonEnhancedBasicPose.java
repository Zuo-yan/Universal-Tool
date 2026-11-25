package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.POSE_TICKS;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.RETREAT_TICK;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.SLASH_TICK;
import static org.gwfx.universaltool.phainon.PhainonEnhancedBasic.WINDUP_TICK;

public final class PhainonEnhancedBasicPose {
    private record Motion(float windup, float cut, float recoil) {
        static Motion at(float age) {
            float cross = smooth((age - (SLASH_TICK - 1.4F)) / 3.4F);
            float recover = smooth((age - RETREAT_TICK) / (POSE_TICKS - (float) RETREAT_TICK));
            return new Motion(smooth(age / WINDUP_TICK) * (1 - cross), cross * (1 - recover),
                    4 * recover * (1 - recover));
        }
    }

    private PhainonEnhancedBasicPose() {}

    public static void apply(PlayerModel<?> model, Player player) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float age = PhainonEnhancedBasicEffects.poseAge(player, partial);
        if (age < 0) return;

        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        int side = rightHanded ? 1 : -1;
        var swordArm = rightHanded ? model.rightArm : model.leftArm;
        var freeArm = rightHanded ? model.leftArm : model.rightArm;
        Motion motion = Motion.at(age);
        float windup = motion.windup();
        float cut = motion.cut();
        float weight = windup + cut;

        swordArm.xRot = Mth.lerp(weight, swordArm.xRot, -1.16F) + 0.54F * windup - 0.27F * cut;
        swordArm.yRot = Mth.lerp(weight, swordArm.yRot, 0) + side * (1.20F * windup - 1.44F * cut);
        swordArm.zRot = Mth.lerp(weight, swordArm.zRot, 0) + side * (0.42F * windup - 0.30F * cut);
        freeArm.xRot = Mth.lerp(weight, freeArm.xRot, -0.30F) + 0.56F * cut;
        freeArm.zRot -= side * (0.17F * windup + 0.30F * cut);
        model.body.yRot += side * (0.43F * windup - 0.52F * cut);
        if (player.onGround() && !player.isPassenger()) {
            var lead = rightHanded ? model.leftLeg : model.rightLeg;
            var trail = rightHanded ? model.rightLeg : model.leftLeg;
            lead.xRot -= 0.54F * cut + 0.14F * windup;
            trail.xRot += 0.39F * cut - 0.19F * motion.recoil();
            model.leftPants.copyFrom(model.leftLeg);
            model.rightPants.copyFrom(model.rightLeg);
        }

        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.jacket.copyFrom(model.body);
    }

    static void applyHand(PoseStack pose, HumanoidArm arm, float equipProgress, float age) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Motion motion = Motion.at(age);
        float windup = motion.windup();
        float cut = motion.cut();
        pose.translate(side * (0.56F + 0.17F * windup - 0.36F * cut),
                -0.52F - 0.6F * equipProgress - 0.10F * windup - 0.11F * cut + 0.06F * motion.recoil(),
                -0.72F + 0.17F * windup - 0.22F * cut);
        pose.mulPose(Axis.YP.rotationDegrees(side * (43.0F * windup - 62.0F * cut)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (30.0F * windup - 43.0F * cut)));
        pose.mulPose(Axis.XP.rotationDegrees(-16.0F * windup + 15.0F * cut));
    }
}
