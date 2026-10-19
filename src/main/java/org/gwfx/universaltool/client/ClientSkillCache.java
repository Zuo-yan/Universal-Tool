package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.gwfx.universaltool.skill.PlayerSkillData;

/** Last server snapshot of this player's skill state. */
public final class ClientSkillCache {
    private static final PlayerSkillData DATA = new PlayerSkillData();

    public static synchronized void update(CompoundTag tag) {
        int before = DATA.level();
        boolean notify = initialized && tag.getInt("Level") > before;
        DATA.deserialize(tag, System.currentTimeMillis());
        initialized = true;
        var minecraft = Minecraft.getInstance();
        if (notify && minecraft.player != null) {
            var key = SkillKeybinds.OPEN;
            var message = key.isUnbound()
                    ? net.minecraft.network.chat.Component.translatable("message.universal_tool.skills.level_up_unbound", DATA.level())
                    : net.minecraft.network.chat.Component.translatable("message.universal_tool.skills.level_up", DATA.level(), key.getTranslatedKeyMessage());
            minecraft.player.displayClientMessage(message, true);
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.65F, 1.0F);
        }
    }

    public static synchronized PlayerSkillData data() {
        return DATA;
    }

    private static boolean initialized;

    public static void clear() { initialized = false; DATA.deserialize(new CompoundTag()); }

    private ClientSkillCache() {
    }
}
