package org.gwfx.universaltool.naiwa;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One authoritative order per player, including members whose chunks have unloaded. */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class DidiOrderBook extends SavedData {
    static final int DURATION = 2 * 60 * 20;
    private static final SavedData.Factory<DidiOrderBook> FACTORY =
            new SavedData.Factory<>(DidiOrderBook::new, DidiOrderBook::load);
    private final Map<UUID, Order> active = new HashMap<>();

    static DidiOrderBook get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "universal_tool_didi_orders");
    }

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    @Nullable
    Order current(ServerLevel level, UUID owner) {
        Order order = active.get(owner);
        if (order != null && remaining(level, order) == 0) {
            finish(level, owner);
            return null;
        }
        return order;
    }

    int remaining(ServerLevel level, Order order) {
        return (int) Math.clamp(order.expiresAt - now(level), 0, DURATION);
    }

    void start(ServerLevel level, UUID owner, UUID id, List<NaiwaEntity> squad) {
        Set<UUID> members = new LinkedHashSet<>();
        for (NaiwaEntity naiwa : squad) {
            members.add(naiwa.getUUID());
        }
        active.put(owner, new Order(id, level.dimension(), now(level) + DURATION, members));
        setDirty();
    }

    boolean isServing(ServerLevel level, NaiwaEntity naiwa) {
        Order order = current(level, naiwa.getOwnerUuid());
        return order != null && order.dimension.equals(level.dimension()) && order.contains(naiwa);
    }

    void useUltimate(Order order) {
        order.ultimateUsed = true;
        setDirty();
    }

    void finish(ServerLevel level, UUID owner) {
        Order order = active.remove(owner);
        if (order == null) {
            return;
        }
        setDirty();
        ServerLevel origin = level.getServer().getLevel(order.dimension);
        if (origin != null) {
            for (NaiwaEntity naiwa : order.loadedMembers(origin)) {
                naiwa.withdrawFromOrder();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            get(level).finish(level, player.getUUID());
        } else if (event.getEntity() instanceof NaiwaEntity naiwa && naiwa.getDidiOrderId() != null) {
            DidiOrderBook book = get(level);
            Order order = book.active.get(naiwa.getOwnerUuid());
            if (order != null && order.contains(naiwa)) {
                order.members.remove(naiwa.getUUID());
                if (order.members.isEmpty()) {
                    book.finish(level, naiwa.getOwnerUuid());
                }
                book.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void expireOrders(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level == null || level.getGameTime() % 20 != 0) {
            return;
        }
        DidiOrderBook book = get(level);
        List<UUID> expired = new ArrayList<>();
        book.active.forEach((owner, order) -> {
            if (book.remaining(level, order) == 0) {
                expired.add(owner);
            }
        });
        expired.forEach(owner -> book.finish(level, owner));
    }

    private static DidiOrderBook load(CompoundTag tag, HolderLookup.Provider registries) {
        DidiOrderBook book = new DidiOrderBook();
        for (Tag entry : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            CompoundTag data = (CompoundTag) entry;
            ResourceLocation dimension = ResourceLocation.tryParse(data.getString("Dimension"));
            if (dimension == null || !data.hasUUID("Owner") || !data.hasUUID("Id")) {
                continue;
            }
            Set<UUID> members = new LinkedHashSet<>();
            for (Tag member : data.getList("Members", Tag.TAG_INT_ARRAY)) {
                members.add(NbtUtils.loadUUID(member));
            }
            if (!members.isEmpty()) {
                Order order = new Order(data.getUUID("Id"), ResourceKey.create(Registries.DIMENSION, dimension),
                        data.getLong("ExpiresAt"), members);
                order.ultimateUsed = data.getBoolean("UltimateUsed");
                book.active.put(data.getUUID("Owner"), order);
            }
        }
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag orders = new ListTag();
        active.forEach((owner, order) -> {
            CompoundTag data = new CompoundTag();
            data.putUUID("Owner", owner);
            data.putUUID("Id", order.id);
            data.putString("Dimension", order.dimension.location().toString());
            data.putLong("ExpiresAt", order.expiresAt);
            data.putBoolean("UltimateUsed", order.ultimateUsed);
            ListTag members = new ListTag();
            order.members.forEach(id -> members.add(NbtUtils.createUUID(id)));
            data.put("Members", members);
            orders.add(data);
        });
        tag.put("Orders", orders);
        return tag;
    }

    static final class Order {
        private final UUID id;
        private final ResourceKey<Level> dimension;
        private final long expiresAt;
        private final Set<UUID> members;
        private boolean ultimateUsed;

        private Order(UUID id, ResourceKey<Level> dimension, long expiresAt, Set<UUID> members) {
            this.id = id;
            this.dimension = dimension;
            this.expiresAt = expiresAt;
            this.members = members;
        }

        private boolean contains(NaiwaEntity naiwa) {
            return id.equals(naiwa.getDidiOrderId()) && members.contains(naiwa.getUUID());
        }

        int size() {
            return members.size();
        }

        boolean ultimateUsed() {
            return ultimateUsed;
        }

        List<NaiwaEntity> loadedMembers(ServerLevel level) {
            List<NaiwaEntity> squad = new ArrayList<>();
            if (dimension.equals(level.dimension())) {
                for (UUID member : members) {
                    if (level.getEntity(member) instanceof NaiwaEntity naiwa && naiwa.isAlive() && contains(naiwa)) {
                        squad.add(naiwa);
                    }
                }
            }
            return squad;
        }
    }
}
