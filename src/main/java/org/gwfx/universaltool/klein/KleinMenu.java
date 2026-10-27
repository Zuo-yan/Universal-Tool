package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModMenuTypes;

public final class KleinMenu extends AbstractContainerMenu {
    public static final int GRID_X = 8, GRID_Y = 46, PLAYER_X = 9, PLAYER_Y = 154, HOTBAR_Y = 213;
    public static final int VISIBLE_ROWS = 5;
    public static final int DRAWER_WIDTH = 118, DRAWER_HEIGHT = 88;
    public static final int DRAWER_X = -122, DRAWER_Y = 74;
    public static final int DRAWER_GRID_X = 11, DRAWER_GRID_Y = 26, DRAWER_SLOT_STEP = 18;
    public static final int DRAWER_RESULT_X = 89, DRAWER_RESULT_Y = 44;
    public static final int ANVIL_LEFT_X = 11, ANVIL_RIGHT_X = 50;

    public static final int CRAFT_RESULT_SLOT = 36;
    public static final int CRAFT_GRID_START = 37;
    public static final int SMELT_INPUT_SLOT = 46, SMELT_OUTPUT_SLOT = 47;
    public static final int ANVIL_LEFT_SLOT = 48, ANVIL_RIGHT_SLOT = 49, ANVIL_RESULT_SLOT = 50;

    public static final int SORT_QUANTITY = 0, SORT_NAME = 1, SORT_MOD = 2;
    public static final int DRAWER_NONE = 0, DRAWER_CRAFTING = 1, DRAWER_SMELTING = 2, DRAWER_ANVIL = 3;

    private final Inventory inventory;
    private final int lockedInventorySlot;
    private final ItemStack openingBottle;
    private KleinWarehouse warehouse;
    private KleinStorageSavedData savedData;
    private int lastSequence = -1;
    private int clientSequence = -1;
    private final Map<Long, KleinEntryData> clientEntries = new LinkedHashMap<>();
    private final List<KleinEntryData> filtered = new ArrayList<>();
    private String query = "";
    private int sortMode = SORT_QUANTITY;
    private boolean descending = true;
    private int scroll;
    private int drawerMode = DRAWER_NONE;

    private final TransientCraftingContainer craftSlots;
    private final ResultContainer craftResult;
    private final DrawerContainer smeltInput;
    private final DrawerContainer smeltOutput;
    private final DrawerContainer anvilLeft;
    private final DrawerContainer anvilRight;
    private final ResultContainer anvilResult;
    private boolean recomputeGuard;
    private String anvilItemName = "";

    public KleinMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, extra.readVarInt());
    }

    public KleinMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND);
    }

    public KleinMenu(int id, Inventory inventory, int bottleSlot) {
        super(ModMenuTypes.KLEIN_MENU.get(), id);
        this.inventory = inventory;
        this.lockedInventorySlot = bottleSlot;
        this.openingBottle = inventory.getItem(lockedInventorySlot).copy();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new ProtectedSlot(inventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new ProtectedSlot(inventory, col, PLAYER_X + col * 18, HOTBAR_Y));

        craftSlots = new TransientCraftingContainer(this, 3, 3);
        craftResult = new ResultContainer();
        smeltInput = new DrawerContainer(1);
        smeltOutput = new DrawerContainer(1);
        anvilLeft = new DrawerContainer(1);
        anvilRight = new DrawerContainer(1);
        anvilResult = new ResultContainer();

        addSlot(new ResultSlot(inventory.player, craftSlots, craftResult, 0,
                DRAWER_X + DRAWER_RESULT_X + 1, DRAWER_Y + DRAWER_RESULT_Y + 1) {
            @Override public boolean isActive() { return drawerMode == DRAWER_CRAFTING; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            addSlot(new DrawerSlot(craftSlots, col + row * 3, DRAWER_X + DRAWER_GRID_X + 1 + col * DRAWER_SLOT_STEP,
                    DRAWER_Y + DRAWER_GRID_Y + 1 + row * DRAWER_SLOT_STEP, DRAWER_CRAFTING));
        }
        addSlot(new DrawerSlot(smeltInput, 0, DRAWER_X + 51, DRAWER_Y + 25, DRAWER_SMELTING));
        addSlot(new OutputSlot(smeltOutput, 0, DRAWER_X + 51, DRAWER_Y + 62, DRAWER_SMELTING));
        addSlot(new DrawerSlot(anvilLeft, 0, DRAWER_X + ANVIL_LEFT_X + 1,
                DRAWER_Y + DRAWER_RESULT_Y + 1, DRAWER_ANVIL));
        addSlot(new DrawerSlot(anvilRight, 0, DRAWER_X + ANVIL_RIGHT_X + 1,
                DRAWER_Y + DRAWER_RESULT_Y + 1, DRAWER_ANVIL));
        addSlot(new AnvilResultSlot(anvilResult, 0, DRAWER_X + DRAWER_RESULT_X + 1,
                DRAWER_Y + DRAWER_RESULT_Y + 1, DRAWER_ANVIL));

        if (inventory.player instanceof ServerPlayer player) {
            savedData = KleinStorageSavedData.get(player.server);
            warehouse = savedData.warehouse(player.getUUID());
            clientEntries.clear();
            for (KleinWarehouse.Entry entry : warehouse.entries()) {
                clientEntries.put(entry.id(), new KleinEntryData(entry.id(), entry.template(), entry.amount()));
            }
            refilter();
        }
    }

    @Override public boolean stillValid(Player player) {
        return player.isAlive() && ItemStack.matches(openingBottle, inventory.getItem(lockedInventorySlot));
    }

    private boolean validAction(Player player) {
        return warehouse != null && player instanceof ServerPlayer && stillValid(player);
    }

    public int nextSequence() { return ++clientSequence; }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot target = slots.get(slotId);
            if (!target.isActive()) return;
            if (target.container == inventory && target.getContainerSlot() == lockedInventorySlot && type != ClickType.CLONE) {
                return;
            }
        }
        if (type == ClickType.SWAP && (button == lockedInventorySlot || (lockedInventorySlot == Inventory.SLOT_OFFHAND && button == 40))) {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!validAction(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.container != inventory) return ItemStack.EMPTY;
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
        if (!validAction(player)) return;
        if (action.sequence() != lastSequence + 1) return;
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
            for (Slot slot : slots) {
                if (slot.container != inventory || slot.getContainerSlot() == lockedInventorySlot || !slot.hasItem()) continue;
                ItemStack item = slot.getItem();
                if (item.getItem() instanceof KleinBottleItem || !warehouse.containsType(item)) continue;
                int count = warehouse.insert(item, BigInteger.valueOf(item.getCount())).intValue();
                if (count > 0) {
                    slot.remove(count);
                    slot.setChanged();
                    anyChanged = true;
                }
            }
            if (anyChanged) savedData.changed();
        } else if (action.operation() == KleinActionPayload.EXTRACT_INVENTORY) {
            extractToInventory(action.entryId(), Integer.MAX_VALUE);
        } else if (action.operation() == KleinActionPayload.EXTRACT_ONE) {
            extractToInventory(action.entryId(), 1);
        } else if (action.operation() == KleinActionPayload.EXTRACT
                || action.operation() == KleinActionPayload.EXTRACT_HALF
                || action.operation() == KleinActionPayload.EXTRACT_CURSOR_ONE) {
            // Left: stack, middle: half stack, right: one item, all onto the cursor.
            if (getCarried().isEmpty()) {
                KleinWarehouse.Entry entry = warehouse.entry(action.entryId());
                if (entry != null) {
                    int limit = switch (action.operation()) {
                        case KleinActionPayload.EXTRACT_HALF -> Math.max(1, entry.template().getMaxStackSize() / 2);
                        case KleinActionPayload.EXTRACT_CURSOR_ONE -> 1;
                        default -> entry.template().getMaxStackSize();
                    };
                    ItemStack extracted = warehouse.extract(action.entryId(), limit);
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

    /** Fills the crafting matrix with concrete stacks chosen by the client's JEI transfer. */
    void handleCraftFill(KleinCraftFillPayload payload) {
        if (!(inventory.player instanceof ServerPlayer player) || !validAction(player)) return;
        if (payload.sequence() != lastSequence + 1) return;
        lastSequence = payload.sequence();
        List<ItemStack> grid = payload.grid();
        int sets = payload.maxTransfer() ? maxCraftSets(grid) : oneCraftSet(grid);
        if (sets < 1 || !canTakeCompleteSets(grid, sets)) return;
        boolean changed = false;
        recomputeGuard = true;
        try {
            // Recycle whatever the player had staged before JEI replaces the matrix.
            returnToWarehouse(craftSlots);
            craftResult.clearContent();
            for (int i = 0; i < 9 && i < grid.size(); i++) {
                ItemStack wanted = grid.get(i);
                craftSlots.setItem(i, ItemStack.EMPTY);
                if (wanted.isEmpty()) continue;
                ItemStack taken = ItemStack.EMPTY;
                for (int count = 0; count < sets; count++) taken = appendOne(taken, wanted);
                if (!taken.isEmpty()) { craftSlots.setItem(i, taken); changed = true; }
            }
        } finally {
            recomputeGuard = false;
        }
        if (changed) savedData.changed();
        updateCraftResult();
        syncChanges(player);
        broadcastChanges();
    }

    private ItemStack appendOne(ItemStack collected, ItemStack wanted) {
        ItemStack one = takeOneMatching(wanted);
        if (one.isEmpty()) return collected;
        if (collected.isEmpty()) return one;
        collected.grow(one.getCount());
        return collected;
    }

    private boolean canTakeCompleteSets(List<ItemStack> grid, int sets) {
        for (int i = 0; i < 9 && i < grid.size(); i++) {
            ItemStack wanted = grid.get(i);
            if (wanted.isEmpty()) continue;
            if (availableMatching(wanted).compareTo(BigInteger.valueOf(sets)) < 0) return false;
        }
        return true;
    }

    private BigInteger availableMatching(ItemStack wanted) {
        BigInteger available = BigInteger.ZERO;
        for (KleinWarehouse.Entry entry : warehouse.entries()) {
            if (ItemStack.isSameItemSameComponents(entry.template(), wanted)) {
                available = available.add(entry.amount());
            }
        }
        for (Slot slot : slots) {
            if (slot.container == inventory && slot.getContainerSlot() != lockedInventorySlot && slot.hasItem()
                    && ItemStack.isSameItemSameComponents(slot.getItem(), wanted)) {
                available = available.add(BigInteger.valueOf(slot.getItem().getCount()));
            }
        }
        return available;
    }

    private int maxCraftSets(List<ItemStack> grid) {
        int max = Integer.MAX_VALUE;
        boolean any = false;
        for (ItemStack wanted : grid) {
            if (wanted.isEmpty()) continue;
            any = true;
            int occurrences = 0;
            for (ItemStack other : grid) {
                if (ItemStack.isSameItemSameComponents(wanted, other)) occurrences++;
            }
            int sets = availableMatching(wanted).divide(BigInteger.valueOf(occurrences))
                    .min(BigInteger.valueOf(wanted.getMaxStackSize())).intValue();
            max = Math.min(max, sets);
        }
        return any ? max : 0;
    }

    private static int oneCraftSet(List<ItemStack> grid) {
        return grid.stream().anyMatch(stack -> !stack.isEmpty()) ? 1 : 0;
    }

    private ItemStack takeOneMatching(ItemStack wanted) {
        long entryId = -1;
        for (KleinWarehouse.Entry entry : warehouse.entries()) {
            if (ItemStack.isSameItemSameComponents(entry.template(), wanted)) {
                entryId = entry.id();
                break;
            }
        }
        if (entryId >= 0) {
            ItemStack extracted = warehouse.extract(entryId, 1);
            if (!extracted.isEmpty()) return extracted;
        }
        for (Slot slot : slots) {
            if (slot.container != inventory || slot.getContainerSlot() == lockedInventorySlot || !slot.hasItem()) continue;
            if (ItemStack.isSameItemSameComponents(slot.getItem(), wanted)) {
                return slot.remove(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private void extractToInventory(long entryId, int limit) {
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

    @Override public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (inventory.player.level().isClientSide) return;
        if (recomputeGuard) return;
        if (container == craftSlots) updateCraftResult();
        else if (container == smeltInput) updateSmeltResult();
        else if (container == anvilLeft || container == anvilRight) updateAnvilResult();
    }

    private Level level() { return inventory.player.level(); }

    void updateCraftResult() {
        if (level().isClientSide) return;
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found = level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level());
        if (found.isPresent()) {
            ItemStack assembled = found.get().value().assemble(input, level().registryAccess());
            if (assembled.isItemEnabled(level().enabledFeatures())) result = assembled;
        }
        craftResult.setItem(0, result);
        if (inventory.player instanceof ServerPlayer player) {
            player.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), CRAFT_RESULT_SLOT, result));
        }
    }

    void updateSmeltResult() {
        if (level().isClientSide || recomputeGuard) return;
        ItemStack input = smeltInput.getItem(0);
        if (input.isEmpty()) return;
        ItemStack produced = findSmelting(input);
        if (produced.isEmpty()) return;
        ItemStack output = smeltOutput.getItem(0);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, produced)
                || output.getCount() + produced.getCount() > output.getMaxStackSize())) {
            return;
        }
        recomputeGuard = true;
        try {
            smeltInput.removeItem(0, 1);
            if (output.isEmpty()) smeltOutput.setItem(0, produced.copy());
            else output.grow(produced.getCount());
        } finally {
            recomputeGuard = false;
        }
        updateSmeltResult();
    }

    private ItemStack findSmelting(ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        ItemStack smelting = resultOf(RecipeType.SMELTING, recipeInput);
        if (!smelting.isEmpty()) return smelting;
        ItemStack blasting = resultOf(RecipeType.BLASTING, recipeInput);
        if (!blasting.isEmpty()) return blasting;
        ItemStack smoking = resultOf(RecipeType.SMOKING, recipeInput);
        if (!smoking.isEmpty()) return smoking;
        return ItemStack.EMPTY;
    }

    private <T extends net.minecraft.world.item.crafting.AbstractCookingRecipe> ItemStack resultOf(
            RecipeType<T> type, SingleRecipeInput recipeInput) {
        Optional<RecipeHolder<T>> found = level().getRecipeManager().getRecipeFor(type, recipeInput, level());
        if (found.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = found.get().value().getResultItem(level().registryAccess());
        return result.isEmpty() || !result.isItemEnabled(level().enabledFeatures()) ? ItemStack.EMPTY : result.copy();
    }

    public void setAnvilItemName(String name) {
        this.anvilItemName = name == null ? "" : name;
        updateAnvilResult();
        broadcastChanges();
    }

    public String anvilItemName() {
        return anvilItemName;
    }

    void updateAnvilResult() {
        if (level().isClientSide) {
            anvilResult.setItem(0, anvilPreview(anvilLeft.getItem(0), anvilRight.getItem(0)));
            return;
        }
        ItemStack left = anvilLeft.getItem(0);
        ItemStack right = anvilRight.getItem(0);
        ItemStack result = ItemStack.EMPTY;
        if (!left.isEmpty()) {
            ItemStack work = left.copyWithCount(1);
            boolean changed = false;

            if (anvilItemName != null && !anvilItemName.isBlank()) {
                if (!anvilItemName.equals(left.getHoverName().getString())) {
                    work.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(anvilItemName));
                    changed = true;
                }
            } else if (left.has(DataComponents.CUSTOM_NAME) && anvilItemName != null && anvilItemName.isEmpty()) {
                work.remove(DataComponents.CUSTOM_NAME);
                changed = true;
            }

            if (!right.isEmpty() && EnchantmentHelper.canStoreEnchantments(left)) {
                if (work.isDamageableItem() && work.getItem().isValidRepairItem(left, right)) {
                    int repair = Math.min(work.getDamageValue(), work.getMaxDamage() / 4);
                    if (repair > 0) {
                        work.setDamageValue(work.getDamageValue() - repair);
                        changed = true;
                    }
                } else if (!right.has(DataComponents.STORED_ENCHANTMENTS)
                        && work.is(right.getItem()) && work.isDamageableItem()) {
                    int max = work.getMaxDamage();
                    int combined = (max - work.getDamageValue()) + (right.getMaxDamage() - right.getDamageValue()) + max * 12 / 100;
                    int damage = Math.max(0, max - combined);
                    if (damage < work.getDamageValue()) {
                        work.setDamageValue(damage);
                        changed = true;
                    }
                }
                ItemEnchantments extra = EnchantmentHelper.getEnchantmentsForCrafting(right);
                if (!extra.isEmpty()) {
                    ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(
                            EnchantmentHelper.getEnchantmentsForCrafting(work));
                    boolean mergedAny = false;
                    for (var entry : extra.entrySet()) {
                        Holder<Enchantment> holder = entry.getKey();
                        if (!work.supportsEnchantment(holder)) continue;
                        if (merged.keySet().stream().anyMatch(existing ->
                                !existing.equals(holder) && !Enchantment.areCompatible(existing, holder))) continue;
                        int existing = merged.getLevel(holder);
                        int incoming = entry.getIntValue();
                        int next = Math.min(Math.max(incoming, existing == incoming ? incoming + 1 : incoming),
                                holder.value().getMaxLevel());
                        if (next > existing) {
                            merged.set(holder, next);
                            mergedAny = true;
                        }
                    }
                    if (mergedAny) {
                        EnchantmentHelper.setEnchantments(work, merged.toImmutable());
                        changed = true;
                    }
                }
            }
            if (changed) result = work;
        }
        anvilResult.setItem(0, result);
    }

    private ItemStack anvilPreview(ItemStack left, ItemStack right) {
        if (left.isEmpty()) return ItemStack.EMPTY;
        ItemStack work = left.copyWithCount(1);
        if (!anvilItemName.isBlank() && !anvilItemName.equals(left.getHoverName().getString())) {
            work.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(anvilItemName));
        }
        return work;
    }

    private void syncChanges(Player player) {
        if (savedData != null) savedData.syncViewers(player.getUUID());
    }

    void syncFromWarehouse() {
        if (inventory.player instanceof ServerPlayer player) {
            List<KleinWarehouse.Entry> entries = warehouse.entries().stream().toList();
            if (entries.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new KleinSyncPayload(List.of(), true));
                return;
            }
            for (int start = 0; start < entries.size(); start += 64) {
                int end = Math.min(entries.size(), start + 64);
                PacketDistributor.sendToPlayer(player, new KleinSyncPayload(entries.subList(start, end).stream()
                        .map(entry -> new KleinEntryData(entry.id(), entry.template(), entry.amount())).toList(),
                        start == 0));
            }
        }
    }

    @Override public void removed(Player player) {
        super.removed(player);
        returnToWarehouse(craftSlots);
        returnToWarehouse(smeltInput);
        returnToWarehouse(smeltOutput);
        returnToWarehouse(anvilLeft);
        returnToWarehouse(anvilRight);
        craftResult.clearContent();
        anvilResult.clearContent();
        if (savedData != null) savedData.unwatch(player.getUUID(), this);
    }

    /** Returns the given drawer mode's contents when switching tabs, so nothing is lost. */
    public void flushDrawer(int mode) {
        switch (mode) {
            case DRAWER_CRAFTING -> {
                returnToWarehouse(craftSlots);
                craftResult.clearContent();
            }
            case DRAWER_SMELTING -> {
                returnToWarehouse(smeltInput);
                returnToWarehouse(smeltOutput);
            }
            case DRAWER_ANVIL -> {
                returnToWarehouse(anvilLeft);
                returnToWarehouse(anvilRight);
                anvilResult.clearContent();
            }
            default -> { }
        }
    }

    private void returnToWarehouse(net.minecraft.world.Container container) {
        if (warehouse == null) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && !inventory.player.getInventory().add(stack)) {
                    inventory.player.drop(stack, false);
                }
                container.setItem(i, ItemStack.EMPTY);
            }
            return;
        }
        boolean changed = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            int accepted = warehouse.insert(stack, BigInteger.valueOf(stack.getCount())).intValue();
            if (accepted > 0) {
                stack.shrink(accepted);
                changed = true;
            }
            if (!stack.isEmpty()) {
                ItemStack remainder = insertRealStack(stack.copy());
                if (!remainder.isEmpty()) inventory.player.drop(remainder, false);
            }
            container.setItem(i, ItemStack.EMPTY);
        }
        if (changed && savedData != null) savedData.changed();
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(inventory.player instanceof ServerPlayer) && drawerMode == DRAWER_ANVIL) {
            anvilResult.setItem(0, anvilPreview(anvilLeft.getItem(0), anvilRight.getItem(0)));
        }
        if (warehouse == null || !(inventory.player instanceof ServerPlayer player)) return;
        syncFromWarehouse();
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
        scroll = Math.min(scroll, maxScroll());
    }

    public List<KleinEntryData> filtered() { return filtered; }
    public int scroll() { return scroll; }
    public int maxScroll() { return Math.max(0, (filtered.size() + 8) / 9 - VISIBLE_ROWS); }
    public void scroll(int value) { scroll = Math.clamp(value, 0, maxScroll()); }
    public int sortMode() { return sortMode; }
    public boolean quantitySort() { return sortMode == SORT_QUANTITY; }
    public boolean descending() { return descending; }
    public int lockedInventorySlot() { return lockedInventorySlot; }
    public TransientCraftingContainer craftSlots() { return craftSlots; }
    public int drawerMode() { return drawerMode; }
    public void setDrawerMode(int mode) {
        int next = Math.max(DRAWER_NONE, Math.min(mode, DRAWER_ANVIL));
        if (next != drawerMode) {
            boolean server = !inventory.player.level().isClientSide;
            if (server) flushDrawer(drawerMode);
            drawerMode = next;
            if (server) {
                updateCraftResult();
                updateAnvilResult();
                syncFromWarehouse();
            }
            broadcastChanges();
        }
    }
    public void cycleDrawerMode(int mode) { setDrawerMode(drawerMode == mode ? DRAWER_NONE : mode); }

    /** Client-side helper for JEI: pick a concrete stack that satisfies an ingredient. */
    public ItemStack pickIngredientStack(net.minecraft.world.item.crafting.Ingredient ingredient) {
        for (KleinEntryData entry : clientEntries.values()) {
            ItemStack template = entry.template();
            if (ingredient.test(template)) return template.copyWithCount(1);
        }
        for (Slot slot : slots) {
            if (slot.container != inventory || slot.getContainerSlot() == lockedInventorySlot || !slot.hasItem()) continue;
            if (ingredient.test(slot.getItem())) return slot.getItem().copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    public void virtualClick(KleinEntryData entry, int button, boolean shift) {
        if (!getCarried().isEmpty()) {
            PacketDistributor.sendToServer(new KleinActionPayload(entry == null ? -1 : entry.id(),
                    button == 1 ? KleinActionPayload.INSERT_ONE : KleinActionPayload.INSERT_CARRIED, -1, nextSequence()));
            return;
        }
        if (entry == null) return;
        int operation = shift ? KleinActionPayload.EXTRACT_INVENTORY
                : button == 2 ? KleinActionPayload.EXTRACT_HALF
                : button == 1 ? KleinActionPayload.EXTRACT_CURSOR_ONE
                : KleinActionPayload.EXTRACT;
        PacketDistributor.sendToServer(new KleinActionPayload(entry.id(), operation, -1, nextSequence()));
    }

    public void virtualScroll(KleinEntryData entry, boolean up) {
        if (entry == null) return;
        if (up) {
            PacketDistributor.sendToServer(new KleinActionPayload(entry.id(), KleinActionPayload.EXTRACT_ONE, -1, nextSequence()));
        } else {
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.container == inventory && slot.getContainerSlot() != lockedInventorySlot && slot.hasItem()
                        && ItemStack.isSameItemSameComponents(slot.getItem(), entry.template())) {
                    PacketDistributor.sendToServer(new KleinActionPayload(entry.id(), KleinActionPayload.INSERT_ONE, i, nextSequence()));
                    break;
                }
            }
        }
    }

    public void sendDepositMatching() {
        PacketDistributor.sendToServer(new KleinActionPayload(-1, KleinActionPayload.DEPOSIT_MATCHING, -1, nextSequence()));
    }

    private final class DrawerContainer extends SimpleContainer {
        private DrawerContainer(int size) { super(size); }
        @Override public void setChanged() {
            super.setChanged();
            KleinMenu.this.slotsChanged(this);
        }
    }

    private class DrawerSlot extends Slot {
        private final int requiredMode;
        private DrawerSlot(net.minecraft.world.Container container, int index, int x, int y, int requiredMode) {
            super(container, index, x, y);
            this.requiredMode = requiredMode;
        }
        @Override public boolean isActive() { return drawerMode == requiredMode; }
        @Override public boolean isHighlightable() { return drawerMode == requiredMode; }
    }

    private final class OutputSlot extends DrawerSlot {
        private OutputSlot(net.minecraft.world.Container container, int index, int x, int y, int requiredMode) {
            super(container, index, x, y, requiredMode);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }

    private final class AnvilResultSlot extends DrawerSlot {
        private AnvilResultSlot(net.minecraft.world.Container container, int index, int x, int y, int requiredMode) {
            super(container, index, x, y, requiredMode);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public void onTake(Player player, ItemStack stack) {
            recomputeGuard = true;
            anvilLeft.removeItem(0, 1);
            if (!anvilRight.getItem(0).isEmpty()) {
                anvilRight.removeItem(0, 1);
            }
            recomputeGuard = false;
            updateAnvilResult();
            super.onTake(player, stack);
        }
    }

    private final class ProtectedSlot extends Slot {
        private ProtectedSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean mayPickup(Player player) { return getContainerSlot() != lockedInventorySlot; }
        @Override public boolean mayPlace(ItemStack stack) { return getContainerSlot() != lockedInventorySlot && super.mayPlace(stack); }
    }
}
