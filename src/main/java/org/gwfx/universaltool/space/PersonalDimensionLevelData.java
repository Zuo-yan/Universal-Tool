package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.*;

/** Keeps the private spawn independent while sharing the server's clock and game rules. */
final class PersonalDimensionLevelData extends DerivedLevelData {
    private SpawnData spawn;
    PersonalDimensionLevelData(WorldData world, ServerLevelData wrapped) {
        super(world, wrapped);
        spawn = new SpawnData(wrapped.getSpawnPos(), wrapped.getSpawnAngle());
    }
    void attach(ServerLevel level) {
        SpawnData fallback = spawn;
        spawn = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> fallback, SpawnData::load),
                "universal_tool_private_spawn");
    }
    @Override public BlockPos getSpawnPos() { return spawn.position; }
    @Override public float getSpawnAngle() { return spawn.angle; }
    @Override public void setSpawn(BlockPos position, float angle) {
        spawn.position = position.immutable(); spawn.angle = angle; spawn.setDirty();
    }
    private static final class SpawnData extends SavedData {
        private BlockPos position;
        private float angle;
        SpawnData(BlockPos position, float angle) { this.position = position; this.angle = angle; }
        static SpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
            return new SpawnData(new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")), tag.getFloat("Angle"));
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("X", position.getX());tag.putInt("Y", position.getY());tag.putInt("Z", position.getZ());
            tag.putFloat("Angle", angle);return tag;
        }
    }
}
