package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.gwfx.universaltool.skill.PlayerSkillData;

/**
 * 玩家技能数据的客户端镜像缓存，包含全局模块开启状态。
 */
public final class ClientSkillCache {
    private static final PlayerSkillData DATA = new PlayerSkillData();
    private static boolean systemEnabled = true;

    public static synchronized void update(CompoundTag tag) {
        if (tag.contains(PlayerSkillData.SYSTEM_ENABLED_KEY)) {
            systemEnabled = tag.getBoolean(PlayerSkillData.SYSTEM_ENABLED_KEY);
        }
        DATA.deserialize(tag, System.currentTimeMillis());
        if (!systemEnabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SkillScreen || mc.screen instanceof SkillWheelScreen) {
                mc.setScreen(null);
            }
            SkillToastOverlay.clear();
        }
    }

    public static synchronized PlayerSkillData data() {
        return DATA;
    }

    public static synchronized boolean isSystemEnabled() {
        return systemEnabled;
    }

    public static void clear() {
        systemEnabled = true;
        DATA.deserialize(new CompoundTag());
        SkillToastOverlay.clear();
    }

    private ClientSkillCache() {
    }
}
