package org.gwfx.universaltool.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PhainonSuspensionMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    // The second serverLevel() call is reached after the thread and packet-value checks.
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;serverLevel()Lnet/minecraft/server/level/ServerLevel;",
            ordinal = 1), cancellable = true)
    private void universalTool$holdAwakeningPosition(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (!PhainonUltimateHandler.isMovementLocked(player)) return;
        player.setYRot(Mth.wrapDegrees(packet.getYRot(player.getYRot())));
        player.setXRot(Mth.wrapDegrees(packet.getXRot(player.getXRot())));
        this.clientIsFloating = false;
        this.aboveGroundTickCount = 0;
        callback.cancel();
    }
}
