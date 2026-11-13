package org.gwfx.universaltool.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModMenuTypes;
import org.gwfx.universaltool.item.TeleportCodexItem;

/**
 * Client-only event subscriber for registering screens, block/entity renderers, keybinds, payload handlers, and client hooks.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class UniversalToolClientMod {

    private UniversalToolClientMod() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientPayloadHandlers.registerAll();
        TeleportCodexItem.registerClientHooks(
                ClientScreens::openCodex,
                () -> ClientWaypointCache.getWaypoints().size(),
                ClientWaypointCache::getMaxCapacity
        );
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWaypointCache.clear();
        PhotoManager.clearTextureCache();
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.UNIVERSAL_POLYMERIZER_MENU.get(), PolymerizerScreen::new);
        event.register(ModMenuTypes.PHOENIX_POD_MENU.get(), PhoenixPodScreen::new);
        event.register(ModMenuTypes.KLEIN_MENU.get(), KleinScreen::new);
        event.register(ModMenuTypes.UNIVERSAL_LOTTERY_MACHINE_MENU.get(), UniversalLotteryMachineScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.UNIVERSAL_POLYMERIZER_BE.get(),
                UniversalPolymerizerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PHOENIX_POD_BE.get(), PhoenixPodRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.UNIVERSAL_LOTTERY_MACHINE_BE.get(),
                UniversalLotteryMachineRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING_SPEAR_PROJECTILE.get(),
                LightningSpearRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        SkillClientController.registerKeys(event);
        KleinKeybind.registerKeys(event);
    }
}
