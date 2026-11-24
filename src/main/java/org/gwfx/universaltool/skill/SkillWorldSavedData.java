package org.gwfx.universaltool.skill;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界存档级技能模块开关持久化。
 * 默认开启，可由管理员通过 /ut skill on|off 动态切换；关闭时所有效果休眠但保留全部玩家存档数据。
 */
public final class SkillWorldSavedData extends SavedData {
    private static final String DATA_NAME = "universal_tool_skill_settings";

    private boolean enabled = true;

    public static SkillWorldSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(SkillWorldSavedData::new, SkillWorldSavedData::load, null),
                DATA_NAME);
    }

    public static boolean isEnabled(MinecraftServer server) {
        return server == null || get(server).enabled;
    }

    public static boolean isEnabled(ServerLevel level) {
        return level == null || isEnabled(level.getServer());
    }

    public static void setEnabled(MinecraftServer server, boolean enabled) {
        if (server == null) return;
        SkillWorldSavedData data = get(server);
        if (data.enabled != enabled) {
            data.enabled = enabled;
            data.setDirty();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("SkillSystemEnabled", enabled);
        return tag;
    }

    private static SkillWorldSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SkillWorldSavedData data = new SkillWorldSavedData();
        data.enabled = !tag.contains("SkillSystemEnabled") || tag.getBoolean("SkillSystemEnabled");
        return data;
    }
}
