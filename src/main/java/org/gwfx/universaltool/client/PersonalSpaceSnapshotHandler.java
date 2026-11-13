package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModBlocks;

/**
 * Silently records a panoramic snapshot when the player visits a Pocket Realm so the Star Gate
 * preview shows their actual base on subsequent visits or password updates.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PersonalSpaceSnapshotHandler {
    private static final int CAPTURE_DELAY_TICKS = 60;
    private static final int SNAPSHOT_WIDTH = 272;
    private static final int SNAPSHOT_HEIGHT = 68;

    private static ResourceLocation lastDimension;
    private static int ticksInSpace;
    private static boolean capturedThisVisit;

    private PersonalSpaceSnapshotHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Boolean.getBoolean("universal_tool.islandVisual")) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.isPaused()) {
            reset();
            return;
        }

        ResourceLocation dimension = client.level.dimension().location();
        if (!isPersonalSpace(dimension)) {
            reset();
            return;
        }

        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            ticksInSpace = 0;
            capturedThisVisit = false;
        }

        if (client.screen != null || !client.player.isAlive()) return;
        if (capturedThisVisit) return;

        if (++ticksInSpace >= CAPTURE_DELAY_TICKS && RenderSystem.isOnRenderThread()) {
            capturedThisVisit = PhotoManager.captureSpaceSnapshot(
                    dimension.getPath(), SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT);
        }
    }

    @SubscribeEvent
    public static void onReturnGateUsed(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (Boolean.getBoolean("universal_tool.islandVisual")) return;
        var state = event.getLevel().getBlockState(event.getPos());
        boolean isRealmReturn = state.is(ModBlocks.PERSONAL_SPACE_RETURN.get());
        boolean isStarryReturn = state.is(ModBlocks.STARRY_VOID_BLOCK.get()) && event.getEntity().isShiftKeyDown();
        if (!isRealmReturn && !isStarryReturn) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.screen != null || !RenderSystem.isOnRenderThread()) return;
        ResourceLocation dimension = client.level.dimension().location();
        if (!isPersonalSpace(dimension)) return;

        PhotoManager.captureSpaceSnapshot(dimension.getPath(), SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT);
    }

    private static boolean isPersonalSpace(ResourceLocation dimension) {
        return UniversalToolMod.MODID.equals(dimension.getNamespace())
                && (dimension.getPath().startsWith("personal_space")
                        || dimension.getPath().startsWith("realm/")
                        || dimension.getPath().startsWith("private_pocket/"));
    }

    private static void reset() {
        lastDimension = null;
        ticksInSpace = 0;
        capturedThisVisit = false;
    }
}
