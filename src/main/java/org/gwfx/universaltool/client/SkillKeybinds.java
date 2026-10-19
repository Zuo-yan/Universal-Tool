package org.gwfx.universaltool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class SkillKeybinds {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.universal_tool.skills.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.universal_tool");
    public static final KeyMapping SELECT = new KeyMapping(
            "key.universal_tool.skills.select",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.universal_tool");
    public static final KeyMapping CAST = new KeyMapping(
            "key.universal_tool.skills.cast",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.universal_tool");

    private SkillKeybinds() {
    }
}
