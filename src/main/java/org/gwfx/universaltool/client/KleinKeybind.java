package org.gwfx.universaltool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.klein.KleinOpenPayload;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class KleinKeybind {
    private static final KeyMapping OPEN = new KeyMapping("key.universal_tool.klein.open",
            KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.universal_tool");

    public static net.minecraft.network.chat.Component getOpenKeyMessage() {
        return OPEN.getTranslatedKeyMessage();
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (OPEN.consumeClick()) PacketDistributor.sendToServer(KleinOpenPayload.INSTANCE);
    }

    @SubscribeEvent public static void keyInInventory(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof InventoryScreen && OPEN.matches(event.getKeyCode(), event.getScanCode())) {
            PacketDistributor.sendToServer(KleinOpenPayload.INSTANCE);
            event.setCanceled(true);
        }
    }

    private KleinKeybind() {}
}
