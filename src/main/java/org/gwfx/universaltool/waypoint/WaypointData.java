package org.gwfx.universaltool.waypoint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家的传送点全局档案（AttachmentType，copyOnDeath——死亡不掉点）。
 * 初始容量 12 宫位，最高扩容至 36 终极点位。
 */
public class WaypointData {

    public static final int DEFAULT_CAPACITY = 12;
    public static final int ULTIMATE_CAPACITY = 36;
    public static final int EXPAND_STEP = 4;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, UniversalToolMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WaypointData>> ATTACHMENT =
            ATTACHMENTS.register("waypoint_data", () -> AttachmentType
                    .builder(WaypointData::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, WaypointData>() {
                        @Override
                        public WaypointData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                            WaypointData data = new WaypointData();
                            data.deserialize(tag, provider);
                            return data;
                        }

                        @Override
                        public CompoundTag write(WaypointData attachment, HolderLookup.Provider provider) {
                            CompoundTag tag = new CompoundTag();
                            attachment.serialize(tag, provider);
                            return tag;
                        }
                    })
                    .copyOnDeath()
                    .build());

    private final List<Waypoint> waypoints = new ArrayList<>(ULTIMATE_CAPACITY);
    private int maxCapacity = DEFAULT_CAPACITY;

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int capacity) {
        this.maxCapacity = Math.min(ULTIMATE_CAPACITY, Math.max(DEFAULT_CAPACITY, capacity));
    }

    public boolean canExpand() {
        return this.maxCapacity < ULTIMATE_CAPACITY;
    }

    public boolean expandCapacity() {
        if (canExpand()) {
            this.maxCapacity = Math.min(ULTIMATE_CAPACITY, this.maxCapacity + EXPAND_STEP);
            return true;
        }
        return false;
    }

    public boolean addWaypoint(Waypoint waypoint) {
        if (waypoints.size() < maxCapacity) {
            waypoints.add(waypoint);
            return true;
        }
        return false;
    }

    public boolean removeWaypoint(String id) {
        return waypoints.removeIf(w -> w.id().equals(id));
    }

    public void setWaypoints(List<Waypoint> list) {
        waypoints.clear();
        for (Waypoint w : list) {
            if (waypoints.size() < maxCapacity) {
                waypoints.add(w);
            }
        }
    }

    public void serialize(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag listTag = new ListTag();
        for (Waypoint wp : waypoints) {
            Waypoint.CODEC.encodeStart(NbtOps.INSTANCE, wp).result().ifPresent(listTag::add);
        }
        tag.put("Waypoints", listTag);
        tag.putInt("MaxCapacity", maxCapacity);
    }

    public void deserialize(CompoundTag tag, HolderLookup.Provider provider) {
        waypoints.clear();
        this.maxCapacity = tag.contains("MaxCapacity") ? tag.getInt("MaxCapacity") : DEFAULT_CAPACITY;
        if (tag.contains("Waypoints", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("Waypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag wpTag = listTag.getCompound(i);
                Waypoint.CODEC.parse(NbtOps.INSTANCE, wpTag).result().ifPresent(w -> {
                    if (waypoints.size() < maxCapacity) {
                        waypoints.add(w);
                    }
                });
            }
        }
    }
}
