package org.gwfx.universaltool.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicPose;
import org.gwfx.universaltool.client.phainon.PhainonMeteorEffects;
import org.gwfx.universaltool.client.phainon.PhainonUltimatePlayerPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PhainonPlayerPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void universalTool$poseAwakening(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (entity instanceof Player player) {
            PhainonUltimatePlayerPose.apply((PlayerModel<?>) (Object) this, player);
            PhainonMeteorEffects.applyPose((PlayerModel<?>) (Object) this, player);
            org.gwfx.universaltool.client.phainon.PhainonCounterEffects.pose((PlayerModel<?>) (Object) this, player);
            PhainonEnhancedBasicPose.apply((PlayerModel<?>) (Object) this, player);
        }
    }
}
