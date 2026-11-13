package org.gwfx.universaltool.init;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.waypoint.Waypoint;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed Minecraft 1.21.1 {@link DataComponentType} registrations for Universal Tool items,
 * together with backward-compatible helpers for reading legacy {@link DataComponents#CUSTOM_DATA}.
 */
public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, UniversalToolMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DnaOwnerData>> DNA_OWNER =
            DATA_COMPONENTS.registerComponentType("dna_owner", builder -> builder
                    .persistent(DnaOwnerData.CODEC)
                    .networkSynchronized(DnaOwnerData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NutrientSnapshotData>> NUTRIENT_SNAPSHOT =
            DATA_COMPONENTS.registerComponentType("nutrient_snapshot", builder -> builder
                    .persistent(NutrientSnapshotData.CODEC)
                    .networkSynchronized(NutrientSnapshotData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Waypoint>> TELEPORT_TARGET =
            DATA_COMPONENTS.registerComponentType("teleport_target", builder -> builder
                    .persistent(Waypoint.CODEC)
                    .networkSynchronized(Waypoint.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    // ---- Typed Component Records ----

    public record DnaOwnerData(UUID ownerUuid, String ownerName) {
        public static final Codec<DnaOwnerData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(DnaOwnerData::ownerUuid),
                Codec.STRING.fieldOf("owner_name").forGetter(DnaOwnerData::ownerName)
        ).apply(inst, DnaOwnerData::new));

        public static final StreamCodec<ByteBuf, DnaOwnerData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, DnaOwnerData::ownerUuid,
                ByteBufCodecs.STRING_UTF8, DnaOwnerData::ownerName,
                DnaOwnerData::new
        );

        public DnaOwnerData {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            ownerName = ownerName != null ? ownerName : "Unknown";
        }
    }

    public record NutrientSnapshotData(int itemCount, List<CompoundTag> entries) {
        public static final Codec<NutrientSnapshotData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("item_count").forGetter(NutrientSnapshotData::itemCount),
                CompoundTag.CODEC.listOf().fieldOf("entries").forGetter(NutrientSnapshotData::entries)
        ).apply(inst, NutrientSnapshotData::new));

        public static final StreamCodec<ByteBuf, NutrientSnapshotData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, NutrientSnapshotData::itemCount,
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), NutrientSnapshotData::entries,
                NutrientSnapshotData::new
        );

        public NutrientSnapshotData {
            itemCount = Math.max(0, itemCount);
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public static NutrientSnapshotData fromListTag(int itemCount, ListTag listTag) {
            if (listTag == null || listTag.isEmpty()) {
                return new NutrientSnapshotData(itemCount, List.of());
            }
            List<CompoundTag> list = new ArrayList<>(listTag.size());
            for (int i = 0; i < listTag.size(); i++) {
                list.add(listTag.getCompound(i).copy());
            }
            return new NutrientSnapshotData(itemCount, list);
        }

        public ListTag toListTag() {
            ListTag tag = new ListTag();
            for (CompoundTag entry : entries) {
                tag.add(entry.copy());
            }
            return tag;
        }
    }

    // ---- Backward-Compatible Helpers ----

    @Nullable
    public static DnaOwnerData getDnaOwner(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        DnaOwnerData typed = stack.get(DNA_OWNER.get());
        if (typed != null) {
            return typed;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("OwnerUUID")) {
            CompoundTag tag = customData.copyTag();
            if (tag.hasUUID("OwnerUUID")) {
                UUID uuid = tag.getUUID("OwnerUUID");
                String name = tag.contains("OwnerName", Tag.TAG_STRING) ? tag.getString("OwnerName") : "Unknown";
                return new DnaOwnerData(uuid, name);
            }
        }
        return null;
    }

    public static void setDnaOwner(ItemStack stack, UUID ownerUuid, String ownerName) {
        stack.set(DNA_OWNER.get(), new DnaOwnerData(ownerUuid, ownerName));
    }

    @Nullable
    public static NutrientSnapshotData getNutrientSnapshot(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        NutrientSnapshotData typed = stack.get(NUTRIENT_SNAPSHOT.get());
        if (typed != null) {
            return typed;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.getBoolean("HasSnapshot")) {
                int count = tag.contains("ItemCount", Tag.TAG_INT) ? tag.getInt("ItemCount") : 0;
                ListTag snapshotList = tag.contains("InventorySnapshot", Tag.TAG_LIST)
                        ? tag.getList("InventorySnapshot", Tag.TAG_COMPOUND)
                        : new ListTag();
                return NutrientSnapshotData.fromListTag(count, snapshotList);
            }
        }
        return null;
    }

    public static boolean hasNutrientSnapshot(ItemStack stack) {
        return getNutrientSnapshot(stack) != null;
    }

    public static void setNutrientSnapshot(ItemStack stack, int itemCount, ListTag snapshotList) {
        stack.set(NUTRIENT_SNAPSHOT.get(), NutrientSnapshotData.fromListTag(itemCount, snapshotList));
    }

    @Nullable
    public static Waypoint getTeleportTarget(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Waypoint typed = stack.get(TELEPORT_TARGET.get());
        if (typed != null) {
            return typed;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("WpName", Tag.TAG_STRING)) {
                ResourceLocation dim = ResourceLocation.tryParse(tag.getString("WpDim"));
                if (dim == null) {
                    dim = Level.OVERWORLD.location();
                }
                return new Waypoint(
                        tag.getString("WpId"),
                        tag.getString("WpName"),
                        dim,
                        tag.getDouble("WpX"),
                        tag.getDouble("WpY"),
                        tag.getDouble("WpZ"),
                        tag.getFloat("WpYaw"),
                        tag.getFloat("WpPitch"),
                        System.currentTimeMillis(),
                        tag.getString("WpPhoto")
                );
            }
        }
        return null;
    }

    public static void setTeleportTarget(ItemStack stack, Waypoint waypoint) {
        stack.set(TELEPORT_TARGET.get(), waypoint);
    }
}
