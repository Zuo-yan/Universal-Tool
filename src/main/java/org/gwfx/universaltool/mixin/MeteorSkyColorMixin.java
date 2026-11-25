package org.gwfx.universaltool.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.client.phainon.PhainonMeteorAtmosphere;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
abstract class MeteorSkyColorMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void universalTool$meteorSky(Vec3 camera, float partial, CallbackInfoReturnable<Vec3> result) {
        var scene = PhainonMeteorAtmosphere.current(partial);
        if (PhainonMeteorAtmosphere.active(scene)) result.setReturnValue(scene.sky(result.getReturnValue()));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void universalTool$meteorClouds(float partial, CallbackInfoReturnable<Vec3> result) {
        var scene = PhainonMeteorAtmosphere.current(partial);
        if (PhainonMeteorAtmosphere.active(scene)) result.setReturnValue(scene.clouds(result.getReturnValue()));
    }
}
