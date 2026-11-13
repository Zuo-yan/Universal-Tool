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

/**
 * Persistent registry for shared exploration realms, UUID-keyed 25x25x25 private starry pockets,
 * and player return/resume coordinates.
 */
public class PersonalSpaceSavedData extends SavedData {
    private static final String DATA_NAME = "universal_tool_personal_spaces";
    public static final UUID SHARED_REALM_OWNER = new UUID(0L, 0L);

    public record SpaceRecord(UUID owner, ResourceLocation dimension, PrivateSpaceEnvironment environment,
                              String passwordSalt, String passwordHash, String generationVersion, int terrainSize, long terrainSeed) {
        public static final String LEGACY_VERSION = "legacy_v1";
        public boolean isFiniteIsland() { return FiniteIslandChunkGenerator.VERSION.equals(generationVersion); }
        public boolean isStarryPocket() { return StarryPocketChunkGenerator.VERSION.equals(generationVersion); }
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

    private final Map<UUID, SpaceRecord> legacySpaces = new HashMap<>();
    private final Map<PrivateSpaceEnvironment, SpaceRecord> realms = new EnumMap<>(PrivateSpaceEnvironment.class);
    private final Map<UUID, SpaceRecord> privatePockets = new HashMap<>();
    private final Map<UUID, Position> returnPositions = new HashMap<>();
    private final Map<UUID, Position> privateReturnPositions = new HashMap<>();
    private final Map<UUID, Position> resumePositions = new HashMap<>();

    public static PersonalSpaceSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(PersonalSpaceSavedData::new, PersonalSpaceSavedData::load), DATA_NAME);
    }

    public Optional<SpaceRecord> getRealm(PrivateSpaceEnvironment environment) {
        return Optional.ofNullable(realms.get(environment));
    }

    public void putRealm(SpaceRecord record) {
        realms.put(record.environment(), record);
        setDirty();
    }

    public Optional<SpaceRecord> getPrivatePocket(UUID playerUuid) {
        return Optional.ofNullable(privatePockets.get(playerUuid));
    }

    public void putPrivatePocket(SpaceRecord record) {
        privatePockets.put(record.owner(), record);
        setDirty();
    }

    /** Legacy lookup kept for compatibility with older save records and visual tests. */
    public Optional<SpaceRecord> getSpace(UUID owner) {
        SpaceRecord pocket = privatePockets.get(owner);
        return Optional.ofNullable(pocket != null ? pocket : legacySpaces.get(owner));
    }

    public void addSpace(SpaceRecord record) {
        if (record.isStarryPocket()) {
            privatePockets.put(record.owner(), record);
        } else {
            legacySpaces.put(record.owner(), record);
        }
        setDirty();
    }

    public Collection<SpaceRecord> allSpaces() {
        List<SpaceRecord> combined = new ArrayList<>(realms.size() + privatePockets.size() + legacySpaces.size());
        combined.addAll(realms.values());
        combined.addAll(privatePockets.values());
        combined.addAll(legacySpaces.values());
        return Collections.unmodifiableList(combined);
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

    public void setPrivateReturnPosition(UUID player, Position position) {
        privateReturnPositions.put(player, position);
        setDirty();
    }

    public Optional<Position> getPrivateReturnPosition(UUID player) {
        return Optional.ofNullable(privateReturnPositions.get(player));
    }

    public void clearPrivateReturnPosition(UUID player) {
        if (privateReturnPositions.remove(player) != null) {
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
        readRecordList(tag.getList("Spaces", Tag.TAG_COMPOUND), record -> data.legacySpaces.put(record.owner(), record));
        readRecordList(tag.getList("Realms", Tag.TAG_COMPOUND), record -> data.realms.put(record.environment(), record));
        readRecordList(tag.getList("PrivatePockets", Tag.TAG_COMPOUND), record -> data.privatePockets.put(record.owner(), record));
        readPositions(tag.getList("ReturnPositions", Tag.TAG_COMPOUND), data.returnPositions);
        readPositions(tag.getList("PrivateReturnPositions", Tag.TAG_COMPOUND), data.privateReturnPositions);
        readPositions(tag.getList("ResumePositions", Tag.TAG_COMPOUND), data.resumePositions);
        return data;
    }

    private static void readRecordList(ListTag list, java.util.function.Consumer<SpaceRecord> sink) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID owner = entry.getUUID("Owner");
            PrivateSpaceEnvironment environment = PrivateSpaceEnvironment.byId(entry.getString("Environment"));
            if (environment == null) continue;
            sink.accept(new SpaceRecord(
                    owner,
                    ResourceLocation.parse(entry.getString("Dimension")),
                    environment,
                    entry.getString("PasswordSalt"),
                    entry.getString("PasswordHash"),
                    entry.contains("GenerationVersion") ? entry.getString("GenerationVersion") : SpaceRecord.LEGACY_VERSION,
                    entry.contains("TerrainSize") ? entry.getInt("TerrainSize") : 128,
                    entry.getLong("TerrainSeed")
            ));
        }
    }

    private static void readPositions(ListTag list, Map<UUID, Position> destination) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            destination.put(entry.getUUID("Player"), Position.fromTag(entry));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Spaces", writeRecords(legacySpaces.values()));
        tag.put("Realms", writeRecords(realms.values()));
        tag.put("PrivatePockets", writeRecords(privatePockets.values()));
        tag.put("ReturnPositions", writePositions(returnPositions));
        tag.put("PrivateReturnPositions", writePositions(privateReturnPositions));
        tag.put("ResumePositions", writePositions(resumePositions));
        return tag;
    }

    private static ListTag writeRecords(Collection<SpaceRecord> records) {
        ListTag list = new ListTag();
        for (SpaceRecord record : records) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", record.owner());
            entry.putString("Dimension", record.dimension().toString());
            entry.putString("Environment", record.environment().id());
            entry.putString("PasswordSalt", record.passwordSalt());
            entry.putString("PasswordHash", record.passwordHash());
            entry.putString("GenerationVersion", record.generationVersion());
            entry.putInt("TerrainSize", record.terrainSize());
            entry.putLong("TerrainSeed", record.terrainSeed());
            list.add(entry);
        }
        return list;
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
