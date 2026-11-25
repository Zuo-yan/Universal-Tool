package org.gwfx.universaltool.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.gwfx.universaltool.client.phainon.PhainonMeteorAtmosphere;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
abstract class MeteorLightTextureMixin {
    @Unique private PhainonMeteorAtmosphere.Scene universalTool$meteorLight;

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void universalTool$sampleLight(float partial, CallbackInfo ci) {
        universalTool$meteorLight = PhainonMeteorAtmosphere.current(partial);
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"), index = 2)
    private int universalTool$tintLight(int abgr) {
        return universalTool$meteorLight.lighting(abgr);
    }
}
