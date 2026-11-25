package org.gwfx.universaltool.mixin;

import net.minecraft.world.entity.player.Player;
import org.gwfx.universaltool.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Scale the finished melee hit including enchantments, cooldown and sweep, once per victim. */
@Mixin(Player.class)
abstract class PhainonMeleeDamageMixin {
    @ModifyArg(method = "attack", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private float universalTool$directStrike(float amount) {
        return universalTool$swordDamage(amount);
    }

    @ModifyArg(method = "attack", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private float universalTool$sweepStrike(float amount) {
        return universalTool$swordDamage(amount);
    }

    @org.spongepowered.asm.mixin.Unique
    private float universalTool$swordDamage(float amount) {
        Player player = (Player)(Object)this;
        return player.getMainHandItem().is(ModItems.QINCHEN_SWORD.get()) ? amount * 4.5F : amount;
    }
}
