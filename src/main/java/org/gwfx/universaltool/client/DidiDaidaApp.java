package org.gwfx.universaltool.client;

import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.api.client.ui.IPhonePage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.naiwa.DidiAppPurchase;

/**
 * MCphone App Store entry for "滴滴代打" (Didi Daida), allowing players to summon
 * and command Naiwa bodyguards directly from MCphone.
 */
public final class DidiDaidaApp implements IPhoneApp {

    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/app/didi_daida.png");

    public DidiDaidaApp() {
    }

    @Override
    public ResourceLocation getId() {
        return DidiAppPurchase.APP_ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("app.universal_tool.didi_daida");
    }

    @Override
    public ResourceLocation getIconTexture() {
        return ICON;
    }

    @Override
    public boolean isPreinstalled() {
        return false;
    }

    @Override
    public String getVersion() {
        return "1.1.0";
    }

    @Override
    public String getAuthor() {
        return "Universal Tool";
    }

    @Override
    public String getDescription() {
        return Component.translatable("app.universal_tool.didi_daida.desc").getString();
    }

    @Override
    public IPhonePage openPage() {
        return new DidiDaidaPage();
    }

    @Override
    public void onPress() {
        // Handled inside MCphone via openPage().
    }
}
