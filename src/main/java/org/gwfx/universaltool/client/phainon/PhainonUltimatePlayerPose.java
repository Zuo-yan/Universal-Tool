package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;

/**
 * 白厄终结技三幕式角色肢体动作姿态控制：
 * <ul>
 *   <li>第一幕（0–38 ticks，对应帧 02）：持剑臂高举指天，副手优雅斜下舒展，单膝微抬离地升空；</li>
 *   <li>第二幕（38–82 ticks，对应帧 03/05）：双臂护于胸前、微微低首蜷身聚火；</li>
 *   <li>第三幕（82–112 ticks，对应帧 08）：破茧瞬间双臂向两侧舒展微张，挺身仰首悬浮降临。</li>
 * </ul>
 */
public final class PhainonUltimatePlayerPose {
    private PhainonUltimatePlayerPose() {
    }

    public static void apply(PlayerModel<?> model, Player player) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float age = PhainonUltimateEffectRenderer.openingAge(player.getId(), InteractionHand.MAIN_HAND, partial);
        HumanoidArm swordSide = player.getMainArm();
        if (age < 0.0F) {
            age = PhainonUltimateEffectRenderer.openingAge(player.getId(), InteractionHand.OFF_HAND, partial);
            swordSide = swordSide.getOpposite();
        }
        if (age < 0.0F) {
            return;
        }

        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float openingEnd = PhainonUltimateHandler.OPENING_TICKS;
        float enter = ease(age / 10.0F);
        float exit = 1.0F - ease((age - (openingEnd - 10.0F)) / 10.0F);
        float weight = enter * exit;

        boolean right = swordSide == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        var swordArm = right ? model.rightArm : model.leftArm;
        var freeArm = right ? model.leftArm : model.rightArm;
        var frontLeg = right ? model.leftLeg : model.rightLeg;
        var backLeg = right ? model.rightLeg : model.leftLeg;

        float swordX, swordY, swordZ;
        float freeX, freeY, freeZ;
        float frontLegX, backLegX, headX, bodyY;

        if (age < 38.0F) {
            // 第一幕：高举侵晨指天，副手斜下张开（帧 02）
            float t = ease(age / 18.0F);
            swordX = Mth.lerp(t, -1.40F, -2.85F);
            swordY = side * (-0.18F);
            swordZ = side * 0.22F;
            freeX = -0.25F;
            freeY = side * 0.15F;
            freeZ = -side * 0.58F;
            frontLegX = -0.32F;
            backLegX = 0.22F;
            headX = -0.14F;
            bodyY = side * 0.12F;
        } else if (age < awaken) {
            // 第二幕：收剑护于胸口、低头蜷身蓄能（帧 03/05）
            float t = ease((age - 38.0F) / 14.0F);
            swordX = Mth.lerp(t, -2.85F, -1.35F);
            swordY = side * Mth.lerp(t, -0.18F, -0.78F);
            swordZ = side * Mth.lerp(t, 0.22F, -0.26F);
            freeX = Mth.lerp(t, -0.25F, -1.25F);
            freeY = side * Mth.lerp(t, 0.15F, 0.72F);
            freeZ = -side * Mth.lerp(t, 0.58F, -0.22F);
            frontLegX = Mth.lerp(t, -0.32F, -0.62F);
            backLegX = Mth.lerp(t, 0.22F, -0.36F);
            headX = Mth.lerp(t, -0.14F, 0.42F);
            bodyY = side * Mth.lerp(t, 0.12F, -0.06F);
        } else {
            // 第三幕：破茧瞬间双臂向两侧舒展微张、微仰首神明悬浮（帧 08）
            float t = ease((age - awaken) / 9.0F);
            swordX = Mth.lerp(t, -1.35F, -0.58F);
            swordY = side * Mth.lerp(t, -0.78F, 0.28F);
            swordZ = side * Mth.lerp(t, -0.26F, 0.88F);
            freeX = Mth.lerp(t, -1.25F, -0.72F);
            freeY = side * Mth.lerp(t, 0.72F, -0.24F);
            freeZ = -side * Mth.lerp(t, -0.22F, 0.92F);
            frontLegX = Mth.lerp(t, -0.62F, -0.26F);
            backLegX = Mth.lerp(t, -0.36F, 0.18F);
            headX = Mth.lerp(t, 0.42F, -0.18F);
            bodyY = 0.0F;
        }

        swordArm.xRot = Mth.lerp(weight, swordArm.xRot, swordX);
        swordArm.yRot = Mth.lerp(weight, swordArm.yRot, swordY);
        swordArm.zRot = Mth.lerp(weight, swordArm.zRot, swordZ);
        freeArm.xRot = Mth.lerp(weight, freeArm.xRot, freeX);
        freeArm.yRot = Mth.lerp(weight, freeArm.yRot, freeY);
        freeArm.zRot = Mth.lerp(weight, freeArm.zRot, freeZ);
        frontLeg.xRot = Mth.lerp(weight, frontLeg.xRot, frontLegX);
        backLeg.xRot = Mth.lerp(weight, backLeg.xRot, backLegX);
        model.body.yRot = Mth.lerp(weight, model.body.yRot, bodyY);
        model.head.xRot = Mth.lerp(weight * 0.80F, model.head.xRot, headX);

        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.jacket.copyFrom(model.body);
    }

    private static float ease(float x) {
        return PhainonUltimateEffectRenderer.ease(x);
    }
}
