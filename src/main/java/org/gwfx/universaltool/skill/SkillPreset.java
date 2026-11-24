package org.gwfx.universaltool.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 单套星盘构筑预设，保存主被动装配与当前选中的主动槽索引。
 */
public final class SkillPreset {
    private String name;
    private final List<String> activeLoadout = new ArrayList<>();
    private final List<String> passiveLoadout = new ArrayList<>();
    private int selectedActive;

    public SkillPreset(String defaultName) {
        this.name = defaultName;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public List<String> activeLoadout() {
        return activeLoadout;
    }

    public List<String> passiveLoadout() {
        return passiveLoadout;
    }

    public int selectedActive() {
        return selectedActive;
    }

    public void setSelectedActive(int selectedActive) {
        this.selectedActive = Math.max(0, selectedActive);
    }

    public void clear() {
        activeLoadout.clear();
        passiveLoadout.clear();
        selectedActive = 0;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putInt("SelectedActive", selectedActive);
        tag.put("ActiveLoadout", toListTag(activeLoadout));
        tag.put("PassiveLoadout", toListTag(passiveLoadout));
        return tag;
    }

    public void deserialize(CompoundTag tag, String fallbackName) {
        name = tag.contains("Name", Tag.TAG_STRING) && !tag.getString("Name").isBlank()
                ? tag.getString("Name") : fallbackName;
        selectedActive = Math.max(0, tag.getInt("SelectedActive"));
        activeLoadout.clear();
        passiveLoadout.clear();
        readListTag(tag, "ActiveLoadout", activeLoadout);
        readListTag(tag, "PassiveLoadout", passiveLoadout);
    }

    private static ListTag toListTag(List<String> list) {
        ListTag result = new ListTag();
        list.forEach(s -> result.add(StringTag.valueOf(s)));
        return result;
    }

    private static void readListTag(CompoundTag tag, String key, List<String> output) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            output.add(list.getString(i));
        }
    }
}
