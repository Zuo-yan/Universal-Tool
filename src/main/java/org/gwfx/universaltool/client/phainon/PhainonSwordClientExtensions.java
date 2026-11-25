package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;

import javax.annotation.Nullable;

/**
 * 侵晨（白厄武器）在客户端的第一人称与第三人称手持动作扩展。
 * 普通战技和终结技起手期间，叠加手部动作与举剑姿态。
 */
public final class PhainonSwordClientExtensions implements IClientItemExtensions {

    public static final PhainonSwordClientExtensions INSTANCE = new PhainonSwordClientExtensions();

    private PhainonSwordClientExtensions() {
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack,
                                           LocalPlayer player,
                                           HumanoidArm arm,
                                           ItemStack itemInHand,
                                           float partialTick,
                                           float equipProcess,
                                           float swingProcess) {
        InteractionHand hand = arm == player.getMainArm()
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        float openingAge = PhainonUltimateEffectRenderer.openingAge(player.getId(), hand, partialTick);
        if (openingAge >= 0.0F) {
            applyUltimateOpening(poseStack, arm, openingAge, equipProcess);
            return true;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            float basicAge = PhainonEnhancedBasicEffects.poseAge(player, partialTick);
            if (basicAge >= 0.0F) {
                PhainonEnhancedBasicPose.applyHand(poseStack, arm, equipProcess, basicAge);
                return true;
            }
        }
        if (PhainonMeteorEffects.applyHand(poseStack, player, hand, arm, partialTick, equipProcess)) return true;
        if (!PhainonSkillEffectRenderer.isCastingWithHand(player.getId(), hand)) {
            return false;
        }
        float age = PhainonSkillEffectRenderer.getActiveCastAge(player.getId(), partialTick);
        if (age < 0.0F) {
            return false;
        }

        int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        // 还原原版第一人称手部标准基座偏移，在此基础上叠加战技动作曲线
        poseStack.translate(sign * 0.56F, -0.52F, -0.72F);

        float tx = 0.0F;
        float ty = 0.0F;
        float tz = 0.0F;
        float rx = 0.0F;
        float ry = 0.0F;
        float rz = 0.0F;

        if (age < 10.0F) {
            // 0..10 ticks：沉稳抬剑高举至视野中央偏右上方
            float t = easeOutCubic(age / 10.0F);
            tx = Mth.lerp(t, 0.0F, -sign * 0.26F);
            ty = Mth.lerp(t, 0.0F, 0.30F);
            tz = Mth.lerp(t, 0.0F, -0.09F);
            rx = Mth.lerp(t, 0.0F, 20.0F);
            ry = Mth.lerp(t, 0.0F, sign * 14.0F);
            rz = Mth.lerp(t, 0.0F, sign * 16.0F);
        } else if (age < 25.0F) {
            // 10..25 ticks：高举日轮聚能蓄力，随时间增强能量微颤张力
            float chargeProgress = (age - 10.0F) / 15.0F;
            float amp = 0.005F + 0.012F * chargeProgress;
            tx = -sign * 0.26F + Mth.sin(age * 3.1F) * amp;
            ty = 0.30F + 0.08F * chargeProgress + Mth.cos(age * 3.7F) * amp * 0.7F;
            tz = -0.09F - 0.03F * chargeProgress;
            rx = 20.0F + 8.0F * chargeProgress;
            ry = sign * 14.0F;
            rz = sign * (16.0F - 5.0F * chargeProgress);
        } else if (age < 31.0F) {
            // 25..31 ticks：凌厉放平剑身由持剑侧大弧度横扫至对侧
            float slashT = easeOutCubic((age - 25.0F) / 6.0F);
            tx = Mth.lerp(slashT, -sign * 0.26F, -sign * 0.94F);
            ty = Mth.lerp(slashT, 0.38F, -0.08F);
            tz = Mth.lerp(slashT, -0.12F, -0.24F);
            rx = Mth.lerp(slashT, 28.0F, -52.0F);
            ry = Mth.lerp(slashT, sign * 14.0F, sign * 64.0F);
            rz = Mth.lerp(slashT, sign * 11.0F, sign * 78.0F);
        } else if (age < 38.0F) {
            // 31..38 ticks：停顿定格收力（体现重剑横扫后的力量余势）
            float holdT = (age - 31.0F) / 7.0F;
            tx = Mth.lerp(holdT, -sign * 0.94F, -sign * 0.88F);
            ty = Mth.lerp(holdT, -0.08F, -0.06F);
            tz = Mth.lerp(holdT, -0.24F, -0.22F);
            rx = Mth.lerp(holdT, -52.0F, -46.0F);
            ry = Mth.lerp(holdT, sign * 64.0F, sign * 58.0F);
            rz = Mth.lerp(holdT, sign * 78.0F, sign * 70.0F);
        } else {
            // 38..50 ticks：三次缓出平滑归位
            float recoverT = Mth.clamp((age - 38.0F) / 12.0F, 0.0F, 1.0F);
            float ease = 1.0F - easeOutCubic(recoverT);
            tx = (-sign * 0.88F) * ease;
            ty = (-0.06F) * ease;
            tz = (-0.22F) * ease;
            rx = (-46.0F) * ease;
            ry = (sign * 58.0F) * ease;
            rz = (sign * 70.0F) * ease;
        }

        poseStack.translate(tx, ty, tz);
        poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        return true;
    }

    @Override
    @Nullable
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        float openingAge = PhainonUltimateEffectRenderer.openingAge(entity.getId(), hand, 0.0F);
        if (openingAge >= 9.0F && openingAge < PhainonUltimateHandler.AWAKEN_TICK) {
            return HumanoidModel.ArmPose.THROW_SPEAR;
        }
        if (PhainonSkillEffectRenderer.isCastingWithHand(entity.getId(), hand)) {
            float age = PhainonSkillEffectRenderer.getActiveCastAge(entity.getId(), 0.0F);
            if (age >= 0.0F && age < 25.0F) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            }
        }
        return null;
    }

    private static void applyUltimateOpening(PoseStack pose, HumanoidArm arm, float age, float equipProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float awake = PhainonUltimateHandler.AWAKEN_TICK;
        float draw = PhainonUltimateEffectRenderer.ease(age / 13.0F);
        float ignition = PhainonUltimateEffectRenderer.ease((age - (awake - 12.0F)) / 12.0F);
        float release = PhainonUltimateEffectRenderer.ease((age - awake) / 7.0F);
        float recover = 1.0F - PhainonUltimateEffectRenderer.ease(
                (age - awake - 11.0F) / (PhainonUltimateHandler.OPENING_TICKS - awake - 11.0F));
        float brace = draw * (1.0F - ignition);
        float lift = ignition * (1.0F - release);
        float exhibit = release * recover;

        pose.translate(side * (0.56F - 0.08F * brace + 0.12F * lift - 0.09F * exhibit),
                -0.52F - 0.6F * equipProcess + 0.06F * brace + 0.35F * lift + 0.05F * exhibit,
                -0.72F + 0.09F * brace - 0.22F * lift - 0.19F * exhibit);
        pose.mulPose(Axis.YP.rotationDegrees(side * (-20.0F * brace + 11.0F * lift - 12.0F * exhibit)));
        pose.mulPose(Axis.XP.rotationDegrees(14.0F * brace - 41.0F * lift + 8.0F * exhibit));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (-19.0F * brace + 3.0F * lift + 9.0F * exhibit)));
    }

    private static float easeOutCubic(float x) {
        float clamped = Mth.clamp(x, 0.0F, 1.0F);
        float inv = 1.0F - clamped;
        return 1.0F - inv * inv * inv;
    }
}
