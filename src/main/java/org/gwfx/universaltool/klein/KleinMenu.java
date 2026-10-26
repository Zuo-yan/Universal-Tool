package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModMenuTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

public final class KleinMenu extends AbstractContainerMenu {
    public static final int GRID_X = 8, GRID_Y = 42, PLAYER_X = 9, PLAYER_Y = 134, HOTBAR_Y = 193;
    private final Inventory inventory;
    private final InteractionHand hand;
    private final int lockedInventorySlot;
    private final ItemStack openingBottle;
    private KleinWarehouse warehouse;
    private KleinStorageSavedData savedData;
    private int lastSequence = -1;
    private int syncCursor;
    private final Map<Long, KleinEntryData> clientEntries = new LinkedHashMap<>();
    private final List<KleinEntryData> filtered = new ArrayList<>();
    private String query = "";
    public static final int SORT_QUANTITY = 0, SORT_NAME = 1, SORT_MOD = 2;
    private int sortMode = SORT_QUANTITY;
    private boolean descending = true;
    private int scroll;

    public KleinMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, extra.readEnum(InteractionHand.class));
    }

    public KleinMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenuTypes.KLEIN_MENU.get(), id);
        this.inventory = inventory;
        this.hand = hand;
        this.lockedInventorySlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
        this.openingBottle = inventory.getItem(lockedInventorySlot).copy();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new ProtectedSlot(inventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new ProtectedSlot(inventory, col, PLAYER_X + col * 18, HOTBAR_Y));
        if (inventory.player instanceof ServerPlayer player) {
            savedData = KleinStorageSavedData.get(player.server);
            warehouse = savedData.warehouse(player.getUUID());
        }
    }

    @Override public boolean stillValid(Player player) {
        return player.isAlive() && ItemStack.matches(openingBottle, inventory.getItem(lockedInventorySlot));
    }

    private boolean validAction(Player player) {
        return warehouse != null && player instanceof ServerPlayer && stillValid(player);
    }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot target = slots.get(slotId);
            if (target.container == inventory && target.getContainerSlot() == lockedInventorySlot && type != ClickType.CLONE) {
                return;
            }
        }
        if (type == ClickType.SWAP && (button == lockedInventorySlot || (hand == InteractionHand.OFF_HAND && button == 40))) {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!validAction(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.getContainerSlot() == lockedInventorySlot || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack before = slot.getItem().copy();
        BigInteger accepted = warehouse.insert(before, BigInteger.valueOf(before.getCount()));
        int count = accepted.intValue();
        if (count > 0) {
            slot.remove(count);
            slot.setChanged();
            savedData.changed();
            syncChanges(player);
        }
        return ItemStack.EMPTY;
    }

    void handleAction(KleinActionPayload action) {
        ServerPlayer player = (ServerPlayer) inventory.player;
        if (action.sequence() != lastSequence + 1 || !validAction(player)) return;
        lastSequence = action.sequence();
        if (action.operation() == KleinActionPayload.INSERT_CARRIED || action.operation() == KleinActionPayload.INSERT_ONE) {
            ItemStack carried = getCarried();
            if (carried.isEmpty() || carried.getItem() instanceof KleinBottleItem) return;
            BigInteger amount = warehouse.insert(carried, BigInteger.valueOf(action.operation() == KleinActionPayload.INSERT_ONE ? 1 : carried.getCount()));
            if (amount.signum() > 0) {
                carried.shrink(amount.intValue());
                setCarried(carried);
                savedData.changed();
            }
        } else if (action.operation() == KleinActionPayload.DEPOSIT_MATCHING) {
            boolean anyChanged = false;
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.container == inventory && slot.getContainerSlot() != lockedInventorySlot && slot.hasItem()) {
                    ItemStack item = slot.getItem();
                    if (!(item.getItem() instanceof KleinBottleItem) && warehouse.containsType(item)) {
                        BigInteger accepted = warehouse.insert(item, BigInteger.valueOf(item.getCount()));
                        int count = accepted.intValue();
                        if (count > 0) {
                            slot.remove(count);
                            slot.setChanged();
                            anyChanged = true;
                        }
                    }
                }
            }
            if (anyChanged) {
                savedData.changed();
            }
        } else if (action.operation() == KleinActionPayload.EXTRACT_INVENTORY) {
            extractToInventory(action.entryId(), Integer.MAX_VALUE, player);
        } else if (action.operation() == KleinActionPayload.EXTRACT_ONE) {
            extractToInventory(action.entryId(), 1, player);
        } else if (action.operation() == KleinActionPayload.EXTRACT || action.operation() == KleinActionPayload.EXTRACT_HALF) {
            if (getCarried().isEmpty()) {
                KleinWarehouse.Entry entry = warehouse.entry(action.entryId());
                if (entry != null) {
                    int max = action.operation() == KleinActionPayload.EXTRACT_HALF
                            ? Math.max(1, entry.template().getMaxStackSize() / 2)
                            : entry.template().getMaxStackSize();
                    ItemStack extracted = warehouse.extract(action.entryId(), max);
                    if (!extracted.isEmpty()) {
                        setCarried(extracted);
                        savedData.changed();
                    }
                }
            }
        }
        syncChanges(player);
        broadcastChanges();
    }

    private void extractToInventory(long entryId, int limit, ServerPlayer player) {
        int moved = 0;
        while (moved < limit) {
            KleinWarehouse.Entry entry = warehouse.entry(entryId);
            if (entry == null) break;
            ItemStack extracted = warehouse.extract(entryId, Math.min(entry.template().getMaxStackSize(), limit - moved));
            if (extracted.isEmpty()) break;
            int original = extracted.getCount();
            ItemStack remainder = insertRealStack(extracted.copy());
            int accepted = original - remainder.getCount();
            if (accepted > 0) {
                savedData.changed();
                moved += accepted;
            }
            if (!remainder.isEmpty()) {
                warehouse.insert(remainder, BigInteger.valueOf(remainder.getCount()));
                break;
            }
            if (accepted < original || entry.singleInstance()) break;
        }
    }

    private ItemStack insertRealStack(ItemStack stack) {
        for (Slot slot : slots) {
            if (slot.container != inventory || slot.getContainerSlot() == lockedInventorySlot || !slot.mayPlace(stack)) continue;
            ItemStack existing = slot.getItem();
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
            int add = Math.min(stack.getCount(), Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount());
            if (add > 0) {
                existing.grow(add);
                stack.shrink(add);
                slot.setChanged();
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        for (Slot slot : slots) {
            if (slot.container != inventory || slot.getContainerSlot() == lockedInventorySlot || slot.hasItem() || !slot.mayPlace(stack)) continue;
            int count = Math.min(stack.getCount(), slot.getMaxStackSize(stack));
            slot.setByPlayer(stack.split(count));
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    private void syncChanges(Player player) {
        if (savedData != null) savedData.syncViewers(player.getUUID());
    }

    void syncFromWarehouse() {
        if (inventory.player instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new KleinSyncPayload(warehouse.entries().stream().limit(64)
                    .map(entry -> new KleinEntryData(entry.id(), entry.template(), entry.amount())).toList(), true));
        }
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (savedData != null) savedData.unwatch(player.getUUID(), this);
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (warehouse == null || !(inventory.player instanceof ServerPlayer player)) return;
        if (syncCursor < warehouse.size()) {
            List<KleinEntryData> page = warehouse.entries().stream().skip(syncCursor).limit(64)
                    .map(entry -> new KleinEntryData(entry.id(), entry.template(), entry.amount())).toList();
            syncCursor += page.size();
            PacketDistributor.sendToPlayer(player, new KleinSyncPayload(page, syncCursor == page.size()));
        }
    }

    public void applySync(KleinSyncPayload payload) {
        if (payload.reset()) clientEntries.clear();
        payload.entries().forEach(entry -> {
            if (entry.amount().signum() == 0) clientEntries.remove(entry.id());
            else clientEntries.put(entry.id(), entry);
        });
        refilter();
    }

    public void setView(String query, int sortMode, boolean descending) {
        this.query = query.length() > 64 ? query.substring(0, 64) : query;
        this.sortMode = sortMode;
        this.descending = descending;
        scroll = 0;
        refilter();
    }

    public void setView(String query, boolean quantitySort, boolean descending) {
        setView(query, quantitySort ? SORT_QUANTITY : SORT_NAME, descending);
    }

    private void refilter() {
        String raw = query.trim();
        filtered.clear();

        if (raw.startsWith("@")) {
            String modQuery = raw.substring(1).trim().toLowerCase(Locale.ROOT);
            clientEntries.values().stream().filter(entry -> {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(entry.template().getItem());
                if (key.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery)) return true;
                return ModList.get().getModContainerById(key.getNamespace())
                        .map(c -> c.getModInfo().getDisplayName().toLowerCase(Locale.ROOT).contains(modQuery))
                        .orElse(false);
            }).forEach(filtered::add);
        } else if (raw.startsWith("#")) {
            String tagQuery = raw.substring(1).trim().toLowerCase(Locale.ROOT);
            clientEntries.values().stream().filter(entry ->
                entry.template().getTags().anyMatch(tag ->
                    tag.location().getPath().toLowerCase(Locale.ROOT).contains(tagQuery)
                    || tag.location().toString().toLowerCase(Locale.ROOT).contains(tagQuery)
                )
            ).forEach(filtered::add);
        } else {
            String needle = raw.toLowerCase(Locale.ROOT);
            clientEntries.values().stream().filter(entry ->
                needle.isEmpty()
                || entry.template().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                || BuiltInRegistries.ITEM.getKey(entry.template().getItem()).toString().toLowerCase(Locale.ROOT).contains(needle)
            ).forEach(filtered::add);
        }

        Comparator<KleinEntryData> comparator;
        if (sortMode == SORT_MOD) {
            comparator = Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.template().getItem()).getNamespace(), String.CASE_INSENSITIVE_ORDER);
            comparator = comparator.thenComparing(entry -> entry.template().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        } else if (sortMode == SORT_NAME) {
            comparator = Comparator.comparing(entry -> entry.template().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparing(KleinEntryData::amount);
        }
        comparator = comparator.thenComparing(KleinEntryData::id);
        filtered.sort(descending ? comparator.reversed() : comparator);
    }

    public List<KleinEntryData> filtered() { return filtered; }
    public int scroll() { return scroll; }
    public void scroll(int value) { scroll = Math.max(0, Math.min(value, Math.max(0, (filtered.size() + 8) / 9 - 4))); }
    public int sortMode() { return sortMode; }
    public boolean quantitySort() { return sortMode == SORT_QUANTITY; }
    public boolean descending() { return descending; }
    public int lockedInventorySlot() { return lockedInventorySlot; }

    public void virtualClick(KleinEntryData entry, int button, boolean shift) {
        if (!getCarried().isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new KleinActionPayload(entry == null ? -1 : entry.id(),
                    button == 1 ? KleinActionPayload.INSERT_ONE : KleinActionPayload.INSERT_CARRIED, -1, ++lastSequence));
            return;
        }
        if (entry == null) return;
        int operation = shift ? KleinActionPayload.EXTRACT_INVENTORY : button == 1 ? KleinActionPayload.EXTRACT_HALF : KleinActionPayload.EXTRACT;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new KleinActionPayload(entry.id(), operation, -1, ++lastSequence));
    }

    public void virtualScroll(KleinEntryData entry, boolean up) {
        if (entry == null) return;
        if (up) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new KleinActionPayload(entry.id(), KleinActionPayload.EXTRACT_ONE, -1, ++lastSequence));
        } else {
            // Wheel down: deposit 1 matching item from player inventory if present
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.container == inventory && slot.getContainerSlot() != lockedInventorySlot && slot.hasItem()
                        && ItemStack.isSameItemSameComponents(slot.getItem(), entry.template())) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new KleinActionPayload(entry.id(), KleinActionPayload.INSERT_ONE, i, ++lastSequence));
                    break;
                }
            }
        }
    }

    private final class ProtectedSlot extends Slot {
        private ProtectedSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean mayPickup(Player player) { return getContainerSlot() != lockedInventorySlot; }
        @Override public boolean mayPlace(ItemStack stack) { return getContainerSlot() != lockedInventorySlot && super.mayPlace(stack); }
    }
}
