package org.gwfx.universaltool.client;

import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.api.client.ui.IPhonePage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.snake.SnakeBoard;

/**
 * Preinstalled MCphone arcade mini-game app: Snake ("贪吃蛇").
 */
public final class SnakeApp implements IPhoneApp {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "snake");
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/app/snake.png");

    public SnakeApp() {
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("app.universal_tool.snake");
    }

    @Override
    public ResourceLocation getIconTexture() {
        return ICON;
    }

    @Override
    public boolean isPreinstalled() {
        return true;
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
        return Component.translatable("app.universal_tool.snake.desc", SnakeBoard.REWARD_TARGET_SCORE)
                .getString();
    }

    @Override
    public IPhonePage openPage() {
        return new SnakePage();
    }

    @Override
    public void onPress() {
        // Handled inside MCphone via openPage().
    }
}
