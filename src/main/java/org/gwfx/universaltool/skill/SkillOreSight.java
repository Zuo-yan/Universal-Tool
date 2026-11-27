package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.OreSightPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Loaded-section scanning and marker updates for the Ore Sight active. */
final class SkillOreSight {
    private static final int SECTIONS_PER_TICK = 4;
    private static final int ORE_MARKER_LIMIT = 512;
    private static final int SPECIAL_MARKER_LIMIT = 128;
    private static final Map<UUID, Scan> SCANS = new LinkedHashMap<>();
    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private SkillOreSight() {}

    static void begin(ServerPlayer player, int level, int radius, int vertical) {
        SCANS.put(player.getUUID(), new Scan(player, radius, vertical, level >= 3));
    }

    static void scanTick() {
        // Each section costs at most 4096 reads, shared fairly between all active casts.
        for (int i = 0; i < SECTIONS_PER_TICK && !SCANS.isEmpty(); i++) {
            var first = SCANS.entrySet().iterator().next();
            UUID id = first.getKey();
            Scan scan = first.getValue();
            SCANS.remove(id);
            ServerPlayer player = scan.player;
            if (!player.isAlive() || player.isRemoved() || player.serverLevel() != scan.world) continue;
            if (scan.scanNextSection()) {
                long expiresAt = scan.world.getGameTime() + SkillManager.ORE_SIGHT_TICKS;
                View view = new View(scan, expiresAt);
                VIEWS.put(id, view);
                view.refresh(player, scan.world.getGameTime(), true);
                player.displayClientMessage(Component.translatable("message.universal_tool.skills.ore_sight",
                        scan.ores.size() + scan.specials.size()), true);
            } else {
                SCANS.put(id, scan);
            }
        }
    }

    static void tickMarkers(ServerPlayer player, long now) {
        View view = VIEWS.get(player.getUUID());
        if (view == null) return;
        if (now >= view.expiresAt || player.serverLevel() != view.scan.world) {
            VIEWS.remove(player.getUUID());
            send(player, Markers.EMPTY, 0);
        } else if (now >= view.nextRefresh) {
            view.refresh(player, now, false);
        }
    }

    static void clear(ServerPlayer player) {
        SCANS.remove(player.getUUID());
        if (VIEWS.remove(player.getUUID()) != null) send(player, Markers.EMPTY, 0);
    }

    static void clearAll() {
        SCANS.clear();
        VIEWS.clear();
    }

    private record Slice(int x, int y, int z) {}
    private record Marker(BlockPos pos, int color) {}
    private record Markers(List<Marker> ores, List<BlockPos> specials) {
        private static final Markers EMPTY = new Markers(List.of(), List.of());
    }

    private static void send(ServerPlayer player, Markers markers, int remainingTicks) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", player.serverLevel().dimension().location().toString());
        tag.putInt("ticks", remainingTicks);
        tag.putLongArray("positions", markers.ores.stream().mapToLong(m -> m.pos.asLong()).toArray());
        tag.putIntArray("colors", markers.ores.stream().mapToInt(Marker::color).toArray());
        tag.putLongArray("specialPositions", markers.specials.stream().mapToLong(BlockPos::asLong).toArray());
        PacketDistributor.sendToPlayer(player, new OreSightPayload(tag));
    }

    private static boolean mightBeSpecial(BlockState state) {
        return state.is(Blocks.SPAWNER) || state.getBlock() instanceof ChestBlock;
    }

    private static boolean isSpecial(LevelChunk chunk, BlockPos pos, BlockState state) {
        return state.is(Blocks.SPAWNER) || (state.getBlock() instanceof ChestBlock
                && chunk.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity);
    }

    static final class Scan {
        private final ServerPlayer player;
        private final ServerLevel world;
        private final boolean includeSpecials;
        private final Predicate<BlockState> sectionFilter;
        private final BlockPos min;
        private final BlockPos max;
        private final List<Slice> slices = new ArrayList<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final Map<BlockPos, Integer> ores = new LinkedHashMap<>();
        final Set<BlockPos> specials = new LinkedHashSet<>();
        private int nextSlice;

        Scan(ServerPlayer player, int radius, int vertical, boolean includeSpecials) {
            this.player = player;
            this.world = player.serverLevel();
            this.includeSpecials = includeSpecials;
            this.sectionFilter = state -> SkillCombatTracker.isOre(state) || (includeSpecials && mightBeSpecial(state));
            BlockPos origin = player.blockPosition();
            min = new BlockPos(origin.getX() - radius, Math.max(world.getMinBuildHeight(), origin.getY() - vertical),
                    origin.getZ() - radius);
            max = new BlockPos(origin.getX() + radius, Math.min(world.getMaxBuildHeight() - 1, origin.getY() + vertical),
                    origin.getZ() + radius);
            if (min.getY() > max.getY()) return;
            for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
                for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                    for (int y = min.getY() >> 4; y <= max.getY() >> 4; y++) {
                        slices.add(new Slice(x, y, z));
                    }
                }
            }
        }

        boolean scanNextSection() {
            if (nextSlice < slices.size()) scan(slices.get(nextSlice++));
            return nextSlice == slices.size();
        }

        private void scan(Slice slice) {
            LevelChunk chunk = world.getChunkSource().getChunkNow(slice.x, slice.z);
            if (chunk == null) return;
            var section = chunk.getSection(world.getSectionIndexFromSectionY(slice.y));
            if (!section.maybeHas(sectionFilter)) return;

            int x0 = Math.max(min.getX(), SectionPos.sectionToBlockCoord(slice.x));
            int y0 = Math.max(min.getY(), SectionPos.sectionToBlockCoord(slice.y));
            int z0 = Math.max(min.getZ(), SectionPos.sectionToBlockCoord(slice.z));
            int x1 = Math.min(max.getX(), SectionPos.sectionToBlockCoord(slice.x, 15));
            int y1 = Math.min(max.getY(), SectionPos.sectionToBlockCoord(slice.y, 15));
            int z1 = Math.min(max.getZ(), SectionPos.sectionToBlockCoord(slice.z, 15));
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (SkillCombatTracker.isOre(state)) {
                            ores.put(new BlockPos(x, y, z), SkillActiveExecutor.oreColor(state));
                        } else if (includeSpecials && isSpecial(chunk, cursor.set(x, y, z), state)) {
                            specials.add(cursor.immutable());
                        }
                    }
                }
            }
        }

        private BlockState current(BlockPos pos) {
            LevelChunk chunk = chunkAt(pos);
            return chunk == null ? null : chunk.getBlockState(pos);
        }

        private LevelChunk chunkAt(BlockPos pos) {
            return world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    private static final class View {
        private final Scan scan;
        private final long expiresAt;
        private long nextRefresh;
        private Markers sent = Markers.EMPTY;
        private BlockPos lastOrigin;

        private View(Scan scan, long expiresAt) {
            this.scan = scan;
            this.expiresAt = expiresAt;
        }

        private void refresh(ServerPlayer player, long now, boolean initial) {
            nextRefresh = now + 20;
            BlockPos origin = player.blockPosition();
            boolean changed = !origin.equals(lastOrigin);
            lastOrigin = origin;
            for (var entries = scan.ores.entrySet().iterator(); entries.hasNext();) {
                var ore = entries.next();
                BlockState state = scan.current(ore.getKey());
                if (state == null || !SkillCombatTracker.isOre(state)) {
                    entries.remove();
                    changed = true;
                } else {
                    int color = SkillActiveExecutor.oreColor(state);
                    if (ore.getValue() != color) {
                        ore.setValue(color);
                        changed = true;
                    }
                }
            }
            changed |= scan.specials.removeIf(pos -> {
                LevelChunk chunk = scan.chunkAt(pos);
                return chunk == null || !isSpecial(chunk, pos, chunk.getBlockState(pos));
            });
            if (!initial && !changed) return;

            Vec3 position = player.position();
            Comparator<BlockPos> byDistance = Comparator.comparingDouble(pos -> position.distanceToSqr(
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            Markers markers = new Markers(scan.ores.keySet().stream().sorted(byDistance).limit(ORE_MARKER_LIMIT)
                    .map(pos -> new Marker(pos, scan.ores.get(pos))).toList(),
                    scan.specials.stream().sorted(byDistance).limit(SPECIAL_MARKER_LIMIT).toList());
            if (initial || !markers.equals(sent)) {
                send(player, markers, (int) (expiresAt - now));
                sent = markers;
            }
        }
    }
}
