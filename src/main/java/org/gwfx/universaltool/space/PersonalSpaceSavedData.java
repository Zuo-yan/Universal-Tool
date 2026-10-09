package org.gwfx.universaltool.space;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PersonalSpaceSavedData extends SavedData {
    private static final String DATA_NAME = "universal_tool_personal_spaces";

    public record SpaceRecord(UUID owner, ResourceLocation dimension, PrivateSpaceEnvironment environment,
                              String passwordSalt, String passwordHash, String generationVersion, int terrainSize, long terrainSeed) {
        public static final String LEGACY_VERSION = "legacy_v1";
        public boolean isFiniteIsland() { return FiniteIslandChunkGenerator.VERSION.equals(generationVersion); }
    }

    public record Position(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension.location().toString());
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            return tag;
        }

        private static Position fromTag(CompoundTag tag) {
            return new Position(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        }
    }

    private final Map<UUID, SpaceRecord> spaces = new HashMap<>();
    private final Map<UUID, Position> returnPositions = new HashMap<>();
    private final Map<UUID, Position> resumePositions = new HashMap<>();

    public static PersonalSpaceSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(PersonalSpaceSavedData::new, PersonalSpaceSavedData::load), DATA_NAME);
    }

    public Optional<SpaceRecord> getSpace(UUID owner) {
        return Optional.ofNullable(spaces.get(owner));
    }

    public Collection<SpaceRecord> allSpaces() {
        return Collections.unmodifiableCollection(spaces.values());
    }

    public void addSpace(SpaceRecord record) {
        spaces.put(record.owner(), record);
        setDirty();
    }

    public void changePassword(UUID owner, String salt, String hash) {
        SpaceRecord old = spaces.get(owner);
        if (old != null) {
            spaces.put(owner, new SpaceRecord(owner, old.dimension(), old.environment(), salt, hash,
                    old.generationVersion(), old.terrainSize(), old.terrainSeed()));
            setDirty();
        }
    }

    public void setReturnPosition(UUID player, Position position) {
        returnPositions.put(player, position);
        resumePositions.put(player, position);
        setDirty();
    }

    public Optional<Position> getReturnPosition(UUID player) {
        return Optional.ofNullable(returnPositions.get(player));
    }

    public void clearReturnPosition(UUID player) {
        boolean changed = returnPositions.remove(player) != null;
        changed |= resumePositions.remove(player) != null;
        if (changed) {
            setDirty();
        }
    }

    public void setResumePosition(UUID player, Position position) {
        resumePositions.put(player, position);
        setDirty();
    }

    public Optional<Position> getResumePosition(UUID player) {
        return Optional.ofNullable(resumePositions.get(player));
    }

    public void clearResumePosition(UUID player) {
        if (resumePositions.remove(player) != null) {
            setDirty();
        }
    }

    static PersonalSpaceSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PersonalSpaceSavedData data = new PersonalSpaceSavedData();
        ListTag spaces = tag.getList("Spaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < spaces.size(); i++) {
            CompoundTag entry = spaces.getCompound(i);
            UUID owner = entry.getUUID("Owner");
            PrivateSpaceEnvironment environment = PrivateSpaceEnvironment.byId(entry.getString("Environment"));
            if (environment == null) continue;
            data.spaces.put(owner, new SpaceRecord(owner, ResourceLocation.parse(entry.getString("Dimension")), environment,
                    entry.getString("PasswordSalt"), entry.getString("PasswordHash"),
                    entry.contains("GenerationVersion") ? entry.getString("GenerationVersion") : SpaceRecord.LEGACY_VERSION,
                    entry.contains("TerrainSize") ? entry.getInt("TerrainSize") : 128,
                    entry.getLong("TerrainSeed")));
        }
        readPositions(tag.getList("ReturnPositions", Tag.TAG_COMPOUND), data.returnPositions);
        readPositions(tag.getList("ResumePositions", Tag.TAG_COMPOUND), data.resumePositions);
        return data;
    }

    private static void readPositions(ListTag list, Map<UUID, Position> destination) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            destination.put(entry.getUUID("Player"), Position.fromTag(entry));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag spaceList = new ListTag();
        for (SpaceRecord record : spaces.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", record.owner());
            entry.putString("Dimension", record.dimension().toString());
            entry.putString("Environment", record.environment().id());
            entry.putString("PasswordSalt", record.passwordSalt());
            entry.putString("PasswordHash", record.passwordHash());
            entry.putString("GenerationVersion", record.generationVersion());
            entry.putInt("TerrainSize", record.terrainSize());
            entry.putLong("TerrainSeed", record.terrainSeed());
            spaceList.add(entry);
        }
        tag.put("Spaces", spaceList);
        tag.put("ReturnPositions", writePositions(returnPositions));
        tag.put("ResumePositions", writePositions(resumePositions));
        return tag;
    }

    private static ListTag writePositions(Map<UUID, Position> positions) {
        ListTag list = new ListTag();
        positions.forEach((player, position) -> {
            CompoundTag entry = position.toTag();
            entry.putUUID("Player", player);
            list.add(entry);
        });
        return list;
    }
}
