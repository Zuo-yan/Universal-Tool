package org.gwfx.universaltool.mixin;

import com.november.mcphone.core.client.PhoneKeys;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PhoneKeys.Key.class, remap = false)
abstract class PhoneKeyDefaultsMixin {
    @Shadow @Final private String id;

    @Inject(method = "defaultCode", at = @At("HEAD"), cancellable = true)
    private void universalTool$setCompatibleDefaults(CallbackInfoReturnable<Integer> cir) {
        switch (id) {
            case "key.mcphone.camera_exit" -> cir.setReturnValue(GLFW.GLFW_KEY_Z);
            case "key.mcphone.reader_prev" -> cir.setReturnValue(GLFW.GLFW_KEY_LEFT_BRACKET);
            case "key.mcphone.reader_next" -> cir.setReturnValue(GLFW.GLFW_KEY_RIGHT_BRACKET);
            default -> { }
        }
    }
}
