package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.saveddata.SavedData;

public final class KleinStorageSavedData extends SavedData {
    private static final String NAME = "universal_tool_klein_storage";
    private static final int FORMAT = 2;
    private final Map<UUID, KleinWarehouse> warehouses = new HashMap<>();
    private final Map<UUID, Boolean> autoDeposit = new HashMap<>();
    private final Map<UUID, KleinSortOrder> sortOrders = new HashMap<>();
    private final Map<UUID, Set<KleinMenu>> viewers = new HashMap<>();

    public static KleinStorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(KleinStorageSavedData::new, KleinStorageSavedData::load), NAME);
    }

    public KleinWarehouse warehouse(UUID owner) {
        return warehouses.computeIfAbsent(owner, ignored -> new KleinWarehouse());
    }

    public boolean autoDeposit(UUID owner) {
        return autoDeposit.getOrDefault(owner, false);
    }

    public KleinSortOrder sortOrder(UUID owner) {
        return sortOrders.getOrDefault(owner, KleinSortOrder.DEFAULT);
    }

    public void setSortOrder(UUID owner, KleinSortOrder order) {
        if (sortOrder(owner).equals(order)) return;
        if (order.equals(KleinSortOrder.DEFAULT)) sortOrders.remove(owner);
        else sortOrders.put(owner, order);
        changed();
    }

    public void setAutoDeposit(UUID owner, boolean enabled) {
        if (enabled) autoDeposit.put(owner, true);
        else autoDeposit.remove(owner);
        changed();
        syncAutoDeposit(owner, enabled);
    }

    public void syncAutoDeposit(UUID owner, boolean enabled) {
        Set<KleinMenu> menus = viewers.get(owner);
        if (menus != null) {
            for (KleinMenu menu : List.copyOf(menus)) {
                menu.applyAutoDeposit(enabled);
                if (menu.inventoryPlayer() instanceof ServerPlayer player) {
                    PacketDistributor.sendToPlayer(player, new KleinAutoDepositPayload(enabled));
                }
            }
        }
    }

    public void watch(UUID owner, KleinMenu menu) {
        viewers.computeIfAbsent(owner, ignored -> java.util.Collections.newSetFromMap(new WeakHashMap<>())).add(menu);
    }

    public void unwatch(UUID owner, KleinMenu menu) {
        Set<KleinMenu> menus = viewers.get(owner);
        if (menus != null) menus.remove(menu);
    }

    public void syncViewers(UUID owner) {
        Set<KleinMenu> menus = viewers.get(owner);
        if (menus != null) List.copyOf(menus).forEach(KleinMenu::syncFromWarehouse);
    }

    public void changed() { setDirty(); }

    static KleinStorageSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        if (root.getInt("Format") > FORMAT) throw new IllegalStateException("Unsupported Klein storage format");
        KleinStorageSavedData data = new KleinStorageSavedData();
        ListTag players = root.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag player = players.getCompound(i);
            UUID owner = player.getUUID("Owner");
            KleinWarehouse warehouse = data.warehouse(owner);
            if (player.getBoolean("AutoDeposit")) data.autoDeposit.put(owner, true);
            if (player.contains("SortMode", Tag.TAG_INT)) {
                KleinSortOrder sort = new KleinSortOrder(player.getInt("SortMode"), player.getBoolean("SortDescending"));
                if (!sort.equals(KleinSortOrder.DEFAULT)) data.sortOrders.put(owner, sort);
            }
            ListTag entries = player.getList("Entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag entry = entries.getCompound(j);
                String amountText = entry.getString("Amount");
                if (amountText.isEmpty() || amountText.length() > KleinWarehouse.MAX_AMOUNT_DIGITS
                        || !amountText.chars().allMatch(Character::isDigit)) continue;
                BigInteger amount = new BigInteger(amountText);
                RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
                ItemStack template = ItemStack.SINGLE_ITEM_CODEC.parse(ops, entry.get("Template")).result().orElse(ItemStack.EMPTY);
                if (amount.signum() > 0 && amount.compareTo(KleinWarehouse.MAX_AMOUNT) <= 0 && !template.isEmpty()
                        && !(template.getItem() instanceof KleinBottleItem)) {
                    warehouse.addLoaded(entry.getLong("Id"), template, amount);
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("Format", FORMAT);
        ListTag players = new ListTag();
        java.util.Set<UUID> owners = new java.util.HashSet<>(warehouses.keySet());
        owners.addAll(autoDeposit.keySet());
        owners.addAll(sortOrders.keySet());
        owners.forEach(owner -> {
            KleinWarehouse warehouse = warehouses.computeIfAbsent(owner, ignored -> new KleinWarehouse());
            KleinSortOrder sort = sortOrder(owner);
            if (warehouse.size() == 0 && !autoDeposit(owner) && sort.equals(KleinSortOrder.DEFAULT)) return;
            CompoundTag player = new CompoundTag();
            player.putUUID("Owner", owner);
            player.putBoolean("AutoDeposit", autoDeposit(owner));
            if (!sort.equals(KleinSortOrder.DEFAULT)) {
                player.putInt("SortMode", sort.mode());
                player.putBoolean("SortDescending", sort.descending());
            }
            ListTag entries = new ListTag();
            for (KleinWarehouse.Entry entry : warehouse.entries()) {
                CompoundTag tag = new CompoundTag();
                tag.putLong("Id", entry.id());
                tag.putString("Amount", entry.amount().toString());
                ItemStack.SINGLE_ITEM_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), entry.template())
                        .result().ifPresent(template -> tag.put("Template", template));
                entries.add(tag);
            }
            player.put("Entries", entries);
            players.add(player);
        });
        root.put("Players", players);
        return root;
    }
}
