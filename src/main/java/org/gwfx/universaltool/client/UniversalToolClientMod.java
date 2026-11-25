package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.phainon.PhainonBloodthornVisuals;
import org.gwfx.universaltool.client.phainon.PhainonMeteorVisuals;
import org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects;
import org.gwfx.universaltool.client.phainon.PhainonSkillEffectRenderer;
import org.gwfx.universaltool.client.phainon.PhainonSwordClientExtensions;
import org.gwfx.universaltool.client.phainon.PhainonUltimateMeshes;
import org.gwfx.universaltool.client.phainon.PhainonVfxModelData;
import org.gwfx.universaltool.client.sportscar.SportsCarItemRenderer;
import org.gwfx.universaltool.client.sportscar.SportsCarModelData;
import org.gwfx.universaltool.client.sportscar.SportsCarRenderer;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModMenuTypes;
import org.gwfx.universaltool.init.ModParticles;
import org.gwfx.universaltool.item.NetheriteToolForm;
import org.gwfx.universaltool.item.TeleportCodexItem;
import org.gwfx.universaltool.space.PrivateSpaceManager;

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
        event.enqueueWork(() -> {
            ResourceLocation formProp = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "tool_form");
            net.minecraft.client.renderer.item.ClampedItemPropertyFunction formFn = (stack, level, entity, seed) -> {
                Level resolved = level != null ? level
                        : (entity != null ? entity.level() : Minecraft.getInstance().level);
                return NetheriteToolForm.resolveActiveForm(stack, resolved).propertyValue();
            };
            for (var itemHolder : java.util.List.of(
                    ModItems.WOODEN_UNIVERSAL_TOOL,
                    ModItems.STONE_UNIVERSAL_TOOL,
                    ModItems.IRON_UNIVERSAL_TOOL,
                    ModItems.GOLDEN_UNIVERSAL_TOOL,
                    ModItems.DIAMOND_UNIVERSAL_TOOL,
                    ModItems.NETHERITE_UNIVERSAL_TOOL
            )) {
                ItemProperties.register(itemHolder.get(), formProp, formFn);
            }
            ResourceLocation awakenedSword = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "qinchen_awakened");
            ItemProperties.register(ModItems.QINCHEN_SWORD.get(), awakenedSword,
                    (stack, level, entity, seed) -> ModDataComponents.getPhainonWeaponState(stack).transformed() ? 1.0F : 0.0F);
            ResourceLocation litBlade = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "qinchen_bloodthorn_lit");
            ItemProperties.register(ModItems.QINCHEN_SWORD.get(), litBlade, (stack, level, entity, seed) -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
                return entity instanceof Player player && PhainonEnhancedBasicEffects.isBladeLit(player, stack, partial)
                        ? 1.0F : 0.0F;
            });
        });
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWaypointCache.clear();
        PhotoManager.clearTextureCache();
        PhainonSkillEffectRenderer.clear();
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
        event.registerBlockEntityRenderer(ModBlockEntities.STARRY_VOID_BE.get(),
                StarryVoidBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PERSONAL_SPACE_GATE_BE.get(),
                PersonalSpaceGateRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING_SPEAR_PROJECTILE.get(),
                LightningSpearRenderer::new);
        event.registerEntityRenderer(ModEntities.WORMHOLE.get(),
                WormholeRenderer::new);
        event.registerEntityRenderer(ModEntities.ALL_TERRAIN_WHEELCHAIR.get(),
                AllTerrainWheelchairRenderer::new);
        event.registerEntityRenderer(ModEntities.NAIWA.get(),
                NaiwaRenderer::new);
        event.registerEntityRenderer(ModEntities.GULF_GT_CAR.get(),
                SportsCarRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(WheelchairItemRenderer.WheelchairItemExtensions.INSTANCE,
                ModItems.ALL_TERRAIN_WHEELCHAIR.get());
        event.registerItem(SportsCarItemRenderer.SportsCarItemExtensions.INSTANCE,
                ModItems.GULF_GT_CAR.get());
        event.registerItem(PhainonSwordClientExtensions.INSTANCE,
                ModItems.QINCHEN_SWORD.get());
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        // 资源包重载时清理预烘焙模型缓存，确保模型与贴图变更即时生效
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            SportsCarModelData.invalidate();
            PhainonVfxModelData.invalidate();
            PhainonUltimateMeshes.reload(resourceManager);
            PhainonBloodthornVisuals.reload(resourceManager);
            PhainonMeteorVisuals.reload(resourceManager);
        });
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        SkillClientController.registerKeys(event);
        KleinKeybind.registerKeys(event);
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(PrivateSpaceManager.STARRY_POCKET_EFFECTS, new StarryPocketDimensionEffects());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WORMHOLE_SPARK.get(), WormholeSparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WORMHOLE_EXIT_SPARK.get(), WormholeSparkParticle.ExitProvider::new);
    }
}
