package org.gwfx.universaltool.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.gwfx.universaltool.client.ClientKeyDefaults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
abstract class ClientOptionsMixin {
    @Shadow public KeyMapping[] keyMappings;
    @Shadow public abstract void save();

    @Unique private boolean universalTool$keyDefaultsMigrated;

    @Inject(method = "processOptionsForge", at = @At("TAIL"))
    private void universalTool$processKeyDefaultsVersion(Options.FieldAccess access, CallbackInfo ci) {
        universalTool$keyDefaultsMigrated = access.process(
                "universalToolKeyDefaultsMigrated", universalTool$keyDefaultsMigrated);
    }

    // NeoForge reloads key mappings once the mod binding registration has completed.
    @Inject(method = "load(Z)V", at = @At("RETURN"))
    private void universalTool$migrateKeyDefaults(boolean limited, CallbackInfo ci) {
        if (!limited || universalTool$keyDefaultsMigrated) return;
        universalTool$keyDefaultsMigrated = true;
        if (ClientKeyDefaults.migrate(keyMappings)) save();
    }
}
