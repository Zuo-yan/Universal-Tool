package org.gwfx.universaltool.client;

import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Default collisions between Universal Tool, its bundled phone, Minecraft and JEI. */
public final class ClientKeyDefaults {
    private record FormerDefault(int keyCode, String conflictingAction) {}

    private static final Map<String, FormerDefault> FORMER_DEFAULTS = Map.of(
            "key.universal_tool.klein.open", new FormerDefault(GLFW.GLFW_KEY_G, "key.mcphone.hud_toggle"),
            "key.universal_tool.skills.select", new FormerDefault(GLFW.GLFW_KEY_V, "key.mcphone.camera_shutter"),
            "key.mcphone.camera_exit", new FormerDefault(GLFW.GLFW_KEY_X, "key.loadToolbarActivator"),
            "key.mcphone.reader_prev", new FormerDefault(GLFW.GLFW_KEY_PAGE_UP, "key.jei.previousRecipePage"),
            "key.mcphone.reader_next", new FormerDefault(GLFW.GLFW_KEY_PAGE_DOWN, "key.jei.nextRecipePage")
    );

    public static boolean migrate(KeyMapping[] mappings) {
        boolean changed = false;
        for (KeyMapping mapping : mappings) {
            FormerDefault former = FORMER_DEFAULTS.get(mapping.getName());
            if (former == null || mapping.getKeyModifier() != KeyModifier.NONE
                    || !mapping.matches(former.keyCode(), 0) || mapping.isDefault()) continue;

            boolean oldKeyConflicts = Arrays.stream(mappings).anyMatch(other ->
                    other.getName().equals(former.conflictingAction()) && mapping.same(other));
            boolean newKeyOccupied = Arrays.stream(mappings).anyMatch(other ->
                    other != mapping && other.getKey().equals(mapping.getDefaultKey()));
            if (oldKeyConflicts && !newKeyOccupied) {
                mapping.setKeyModifierAndCode(KeyModifier.NONE, mapping.getDefaultKey());
                changed = true;
            }
        }
        if (changed) KeyMapping.resetMapping();
        return changed;
    }

    private ClientKeyDefaults() {}
}
