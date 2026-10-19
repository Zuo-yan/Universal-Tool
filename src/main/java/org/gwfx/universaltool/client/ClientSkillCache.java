package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.gwfx.universaltool.skill.PlayerSkillData;

/** Last server snapshot of this player's skill state. */
public final class ClientSkillCache {
    private static final PlayerSkillData DATA = new PlayerSkillData();

    public static synchronized void update(CompoundTag tag) {
        DATA.deserialize(tag, System.currentTimeMillis());
    }

    public static synchronized PlayerSkillData data() {
        return DATA;
    }

    private ClientSkillCache() {
    }
}
