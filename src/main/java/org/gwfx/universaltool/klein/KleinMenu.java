package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModMenuTypes;

public final class KleinMenu extends AbstractContainerMenu {
    public static final int MAIN_WIDTH = 190, HEIGHT = 218;
    public static final int SLOT_COLUMN_STEP = 18, SLOT_ROW_STEP = 17;
    public static final int GRID_X = 11, GRID_Y = 45;
    public static final int PLAYER_X = 15, PLAYER_Y = 147, HOTBAR_Y = 200;
    public static final int VISIBLE_ROWS = 5;
    public static final int DRAWER_WIDTH = 114, DRAWER_HEIGHT = 132;
    public static final int DRAWER_X = MAIN_WIDTH + 4, DRAWER_Y = 22;
    public static final int DRAWER_GRID_X = 7, DRAWER_GRID_Y = 30, DRAWER_SLOT_STEP = 18;
    public static final int DRAWER_RESULT_X = 87, DRAWER_RESULT_Y = 48;
    public static final int ANVIL_LEFT_X = 8, ANVIL_RIGHT_X = 45;
    public static final int SMELT_X = 48, SMELT_INPUT_Y = 24, SMELT_OUTPUT_Y = 78;

    public static final int CRAFT_RESULT_SLOT = 36;
    public static final int CRAFT_GRID_START = 37;
    public static final int SMELT_INPUT_SLOT = 46, SMELT_OUTPUT_SLOT = 47;
    public static final int ANVIL_LEFT_SLOT = 48, ANVIL_RIGHT_SLOT = 49, ANVIL_RESULT_SLOT = 50;

    public static final int SORT_QUANTITY = 0, SORT_NAME = 1, SORT_MOD = 2;
    public static final int CATEGORY_ALL = 0, CATEGORY_BLOCKS = 1, CATEGORY_GEAR = 2, CATEGORY_OTHER = 3;
    public static final int DRAWER_NONE = 0, DRAWER_CRAFTING = 1, DRAWER_SMELTING = 2, DRAWER_ANVIL = 3;

    private final Inventory inventory;
    private final int lockedInventorySlot;
    private final ItemStack openingBottle;
    private KleinWarehouse warehouse;
    private KleinStorageSavedData savedData;
    private int lastSequence = -1;
    private int clientSequence = -1;
    private long syncedWarehouseRevision = -1;
    private final Map<Long, KleinEntryData> clientEntries = new LinkedHashMap<>();
    private final Map<Long, KleinEntryData> pendingEntries = new LinkedHashMap<>();
    private final List<KleinEntryData> filtered = new ArrayList<>();
    private String query = "";
    private int sortMode = SORT_QUANTITY;
    private int category = CATEGORY_ALL;
    private boolean descending = true;
    private int scroll;
    private boolean autoDeposit;
    private int drawerMode = DRAWER_NONE;

    private final TransientCraftingContainer craftSlots;
    private final ResultContainer craftResult;
    private final DrawerContainer smeltInput;
    private final DrawerContainer smeltOutput;
    private final DrawerContainer anvilLeft;
    private final DrawerContainer anvilRight;
    private final ResultContainer anvilResult;
    private boolean recomputeGuard;
    private ResourceLocation queuedCookingRecipe;
    private String anvilItemName = "";
    private final AnvilMenu internalAnvil;
    private int anvilCost;
    private int anvilMaterialCost;

    public KleinMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, extra.readVarInt(), extra.isReadable() && extra.readBoolean(),
                extra.isReadable() ? new KleinSortOrder(extra.readVarInt(), extra.readBoolean()) : KleinSortOrder.DEFAULT);
    }

    public KleinMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND, false);
    }

    public KleinMenu(int id, Inventory inventory, int bottleSlot) {
        this(id, inventory, bottleSlot, false);
    }

    public KleinMenu(int id, Inventory inventory, int bottleSlot, boolean initialAutoDeposit) {
        this(id, inventory, bottleSlot, initialAutoDeposit, KleinSortOrder.DEFAULT);
    }

    private KleinMenu(int id, Inventory inventory, int bottleSlot, boolean initialAutoDeposit, KleinSortOrder sort) {
        super(ModMenuTypes.KLEIN_MENU.get(), id);
        this.inventory = inventory;
        this.lockedInventorySlot = bottleSlot;
        this.autoDeposit = initialAutoDeposit;
        this.sortMode = sort.mode();
        this.descending = sort.descending();
        this.openingBottle = inventory.getItem(lockedInventorySlot).copy();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new ProtectedSlot(inventory, col + row * 9 + 9, PLAYER_X + col * SLOT_COLUMN_STEP, PLAYER_Y + row * SLOT_ROW_STEP));
        }
        for (int col = 0; col < 9; col++) addSlot(new ProtectedSlot(inventory, col, PLAYER_X + col * SLOT_COLUMN_STEP, HOTBAR_Y));

        craftSlots = new TransientCraftingContainer(this, 3, 3);
        craftResult = new ResultContainer();
        smeltInput = new DrawerContainer(1);
        smeltOutput = new DrawerContainer(1);
        anvilLeft = new DrawerContainer(1);
        anvilRight = new DrawerContainer(1);
        anvilResult = new ResultContainer();
        this.internalAnvil = new AnvilMenu(0, inventory);

        addSlot(new ResultSlot(inventory.player, craftSlots, craftResult, 0,
                DRAWER_X + DRAWER_RESULT_X + 1, DRAWER_Y + DRAWER_RESULT_Y + 1) {
            @Override public boolean isActive() { return drawerMode == DRAWER_CRAFTING; }

            @Override public void onTake(Player player, ItemStack stack) {
                recomputeGuard = true;
                try {
                    super.onTake(player, stack);
                } finally {
                    recomputeGuard = false;
                }
                updateCraftResult();
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            addSlot(new DrawerSlot(craftSlots, col + row * 3, DRAWER_X + DRAWER_GRID_X + 1 + col * DRAWER_SLOT_STEP,
                    DRAWER_Y + DRAWER_GRID_Y + 1 + row * DRAWER_SLOT_STEP, DRAWER_CRAFTING));
        }
        addSlot(new DrawerSlot(smeltInput, 0, DRAWER_X + SMELT_X + 1, DRAWER_Y + SMELT_INPUT_Y + 1, DRAWER_SMELTING));
        addSlot(new OutputSlot(smeltOutput, 0, DRAWER_X + SMELT_X + 1, DRAWER_Y + SMELT_OUTPUT_Y + 1, DRAWER_SMELTING));
        addSlot(new DrawerSlot(anvilLeft, 0, DRAWER_X + ANVIL_LEFT_X + 1,
                DRAWER_Y + DRAWER_RESULT_Y + 1, DRAWER_ANVIL));
        addSlot(new DrawerSlot(anvilRight, 0, DRAWER_X + ANVIL_RIGHT_X + 1,
                DRAWER_Y + DRAWER_RESULT_Y + 1, DRAWER_ANVIL));
        addSlot(new AnvilResultSlot(anvilResult, 0, DRAWER_X + DRAWER_RESULT_X + 1,
                DRAWER_Y + DRAWER_RESULT_Y + 1, DRAWER_ANVIL));

        if (inventory.player instanceof ServerPlayer player) {
            savedData = KleinStorageSavedData.get(player.server);
            warehouse = savedData.warehouse(player.getUUID());
            if (!initialAutoDeposit) {
                autoDeposit = savedData.autoDeposit(player.getUUID());
            }
            KleinSortOrder savedSort = savedData.sortOrder(player.getUUID());
            sortMode = savedSort.mode();
            descending = savedSort.descending();
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

    private boolean acceptSequence(int sequence) {
        if (sequence != lastSequence + 1) return false;
        lastSequence = sequence;
        return true;
    }

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
        if (!slot.isActive() || !slot.mayPickup(player) || !slot.hasItem()) return ItemStack.EMPTY;
        if (index == CRAFT_RESULT_SLOT || index == ANVIL_RESULT_SLOT) return quickMoveResult(player, slot);
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

    private ItemStack quickMoveResult(Player player, Slot slot) {
        ItemStack result = slot.getItem().copy();
        ItemStack remaining = result.copy();
        if (!moveItemStackTo(remaining, 0, CRAFT_RESULT_SLOT, true)) return ItemStack.EMPTY;

        ItemStack taken = slot.remove(result.getCount());
        slot.onTake(player, taken);
        if (!remaining.isEmpty()) player.drop(remaining, false);
        return result;
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.isActive() && slot.container != craftResult && slot.container != anvilResult
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override public boolean canDragTo(Slot slot) {
        return slot.isActive() && super.canDragTo(slot);
    }

    void handleAction(KleinActionPayload action) {
        ServerPlayer player = (ServerPlayer) inventory.player;
        if (!validAction(player)) return;
        if (!acceptSequence(action.sequence())) return;
        if (action.operation() == KleinActionPayload.RIGHT_CLICK) {
            rightClickWarehouse(action.entryId());
        } else if (action.operation() == KleinActionPayload.INSERT_CARRIED || action.operation() == KleinActionPayload.INSERT_ONE) {
            ItemStack carried = getCarried();
            if (action.operation() == KleinActionPayload.INSERT_ONE && carried.isEmpty()
                    && action.sourceSlot() >= 0 && action.sourceSlot() < slots.size()) {
                Slot source = slots.get(action.sourceSlot());
                if (source.container == inventory && source.getContainerSlot() != lockedInventorySlot && source.hasItem()) {
                    ItemStack stack = source.getItem();
                    KleinWarehouse.Entry targetEntry = warehouse.entry(action.entryId());
                    if (KleinWarehouse.canStore(stack)
                            && (targetEntry == null || ItemStack.isSameItemSameComponents(stack, targetEntry.template()))) {
                        BigInteger amount = warehouse.insert(stack, BigInteger.ONE);
                        if (amount.signum() > 0) {
                            source.remove(amount.intValue());
                            source.setChanged();
                            savedData.changed();
                        }
                    }
                }
            } else if (KleinWarehouse.canStore(carried)) {
                BigInteger amount = warehouse.insert(carried, BigInteger.valueOf(action.operation() == KleinActionPayload.INSERT_ONE ? 1 : carried.getCount()));
                if (amount.signum() > 0) {
                    carried.shrink(amount.intValue());
                    setCarried(carried);
                    savedData.changed();
                }
            }
        } else if (action.operation() == KleinActionPayload.DEPOSIT_MATCHING) {
            boolean anyChanged = false;
            for (Slot slot : slots) {
                if (slot.container != inventory || slot.getContainerSlot() == lockedInventorySlot || !slot.hasItem()) continue;
                ItemStack item = slot.getItem();
                if (!KleinWarehouse.canStore(item) || !warehouse.containsType(item)) continue;
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

    private void rightClickWarehouse(long entryId) {
        ItemStack carried = getCarried();
        KleinWarehouse.Entry entry = warehouse.entry(entryId);
        if (entry != null && (carried.isEmpty() || ItemStack.isSameItemSameComponents(carried, entry.template()))) {
            if (!carried.isEmpty() && carried.getCount() >= carried.getMaxStackSize()) return;
            ItemStack taken = warehouse.extract(entryId, 1);
            if (taken.isEmpty()) return;
            if (carried.isEmpty()) setCarried(taken);
            else carried.grow(1);
            savedData.changed();
        } else if (entryId < 0 || entry != null) {
            if (warehouse.insert(carried, BigInteger.ONE).signum() > 0) {
                carried.shrink(1);
                savedData.changed();
            }
        }
    }

    public KleinRecipeTransfer.Plan planRecipe(Recipe<?> recipe, boolean maxTransfer) {
        return KleinRecipeTransfer.plan(KleinRecipeTransfer.layout(recipe), recipeStock(KleinRecipeTransfer.drawer(recipe)), maxTransfer);
    }

    private Map<KleinWarehouse.ItemKey, KleinRecipeTransfer.Stock> recipeStock(int drawer) {
        Map<KleinWarehouse.ItemKey, KleinRecipeTransfer.Stock> stock = new LinkedHashMap<>();
        Container input = recipeInput(drawer);
        if (input == null) return stock;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            KleinRecipeTransfer.addStock(stock, stack, BigInteger.valueOf(stack.getCount()));
        }
        if (warehouse != null) {
            for (KleinWarehouse.Entry entry : warehouse.entries()) {
                KleinRecipeTransfer.addStock(stock, entry.template(), entry.amount());
            }
        } else {
            for (KleinEntryData entry : clientEntries.values()) {
                KleinRecipeTransfer.addStock(stock, entry.template(), entry.amount());
            }
        }
        for (int i = 0; i < CRAFT_RESULT_SLOT; i++) {
            Slot slot = slots.get(i);
            if (!slot.mayPickup(inventory.player)) continue;
            ItemStack stack = slot.getItem();
            KleinRecipeTransfer.addStock(stock, stack, BigInteger.valueOf(stack.getCount()));
        }
        return stock;
    }

    public void submitRecipeFill(ResourceLocation recipeId, int drawer, boolean maxTransfer) {
        if (drawerMode != drawer) {
            setDrawerMode(drawer);
            PacketDistributor.sendToServer(new KleinDrawerPayload(containerId, drawer));
        }
        PacketDistributor.sendToServer(new KleinCraftFillPayload(containerId, recipeId, nextSequence(), maxTransfer));
    }

    void handleCraftFill(KleinCraftFillPayload payload) {
        if (!(inventory.player instanceof ServerPlayer player) || !validAction(player)
                || payload.containerId() != containerId || !acceptSequence(payload.sequence())) return;
        var holder = level().getRecipeManager().byKey(payload.recipeId()).orElse(null);
        if (holder == null) return;

        Recipe<?> recipe = holder.value();
        int drawer = KleinRecipeTransfer.drawer(recipe);
        KleinRecipeTransfer.Plan plan = planRecipe(recipe, payload.maxTransfer());
        if (!plan.complete() || plan.grid().isEmpty()) return;
        List<ItemStack> grid = plan.grid();
        ItemStack cookingResult = ItemStack.EMPTY;
        if (recipe instanceof CraftingRecipe crafting) {
            if (!crafting.matches(CraftingInput.ofPositioned(3, 3, grid).input(), level())) return;
        } else if (recipe instanceof AbstractCookingRecipe cooking) {
            SingleRecipeInput input = new SingleRecipeInput(grid.getFirst());
            if (!cooking.matches(input, level())) return;
            cookingResult = cooking.assemble(input, level().registryAccess());
            if (cookingResult.isEmpty() || !cookingResult.isItemEnabled(level().enabledFeatures())) return;
        }

        Container input = recipeInput(drawer);
        recomputeGuard = true;
        try {
            for (ItemStack wanted : grid) {
                if (!wanted.isEmpty()) takeRecipeIngredients(input, wanted);
            }
            returnToWarehouse(input);
            if (drawer == DRAWER_SMELTING) {
                ItemStack output = smeltOutput.getItem(0);
                if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(output, cookingResult)) {
                    returnToWarehouse(smeltOutput);
                }
                queuedCookingRecipe = holder.id();
            }
            for (int i = 0; i < grid.size(); i++) input.setItem(i, grid.get(i));
            drawerMode = drawer;
        } finally {
            recomputeGuard = false;
        }
        savedData.changed();
        if (drawer == DRAWER_CRAFTING) updateCraftResult();
        else updateSmeltResult();
        broadcastChanges();
    }

    private Container recipeInput(int drawer) {
        return switch (drawer) {
            case DRAWER_CRAFTING -> craftSlots;
            case DRAWER_SMELTING -> smeltInput;
            default -> null;
        };
    }

    private void takeRecipeIngredients(Container input, ItemStack wanted) {
        int remaining = wanted.getCount();
        for (int i = 0; i < input.getContainerSize() && remaining > 0; i++) {
            if (ItemStack.isSameItemSameComponents(input.getItem(i), wanted)) {
                remaining -= input.removeItem(i, remaining).getCount();
            }
        }
        remaining -= warehouse.extractMatching(wanted, remaining);
        for (int i = 0; i < CRAFT_RESULT_SLOT && remaining > 0; i++) {
            Slot slot = slots.get(i);
            if (slot.mayPickup(inventory.player) && ItemStack.isSameItemSameComponents(slot.getItem(), wanted)) {
                remaining -= slot.remove(remaining).getCount();
            }
        }
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
        if (recomputeGuard) return;
        if (container == craftSlots) updateCraftResult();
        else if (container == smeltInput || container == smeltOutput) updateSmeltResult();
        else if (container == anvilLeft || container == anvilRight) updateAnvilResult();
    }

    private Level level() { return inventory.player.level(); }

    void updateCraftResult() {
        if (level().isClientSide) return;
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found = level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level());
        if (found.isPresent() && inventory.player instanceof ServerPlayer player
                && craftResult.setRecipeUsed(level(), player, found.get())) {
            ItemStack assembled = found.get().value().assemble(input, level().registryAccess());
            if (assembled.isItemEnabled(level().enabledFeatures())) result = assembled;
        }
        craftResult.setItem(0, result);
        if (inventory.player instanceof ServerPlayer player && player.connection != null) {
            player.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), CRAFT_RESULT_SLOT, result));
        }
    }

    void updateSmeltResult() {
        if (level().isClientSide || recomputeGuard) return;
        recomputeGuard = true;
        try {
            ItemStack input = smeltInput.getItem(0);
            while (!input.isEmpty()) {
                ItemStack produced = findSmelting(input);
                ItemStack output = smeltOutput.getItem(0);
                if (produced.isEmpty() || produced.getCount() > produced.getMaxStackSize()
                        || (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, produced)
                        || output.getCount() + produced.getCount() > output.getMaxStackSize()))) break;
                smeltInput.removeItem(0, 1);
                if (output.isEmpty()) smeltOutput.setItem(0, produced.copy());
                else output.grow(produced.getCount());
                input = smeltInput.getItem(0);
            }
            if (input.isEmpty()) queuedCookingRecipe = null;
        } finally {
            recomputeGuard = false;
        }
    }

    private ItemStack findSmelting(ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        if (queuedCookingRecipe != null) {
            var holder = level().getRecipeManager().byKey(queuedCookingRecipe).orElse(null);
            if (holder != null && holder.value() instanceof AbstractCookingRecipe cooking && cooking.matches(recipeInput, level())) {
                ItemStack result = cooking.assemble(recipeInput, level().registryAccess());
                return result.isItemEnabled(level().enabledFeatures()) ? result : ItemStack.EMPTY;
            }
            queuedCookingRecipe = null;
        }
        ItemStack smelting = resultOf(RecipeType.SMELTING, recipeInput);
        if (!smelting.isEmpty()) return smelting;
        ItemStack blasting = resultOf(RecipeType.BLASTING, recipeInput);
        if (!blasting.isEmpty()) return blasting;
        ItemStack smoking = resultOf(RecipeType.SMOKING, recipeInput);
        if (!smoking.isEmpty()) return smoking;
        ItemStack campfire = resultOf(RecipeType.CAMPFIRE_COOKING, recipeInput);
        if (!campfire.isEmpty()) return campfire;
        return ItemStack.EMPTY;
    }

    private <T extends net.minecraft.world.item.crafting.AbstractCookingRecipe> ItemStack resultOf(
            RecipeType<T> type, SingleRecipeInput recipeInput) {
        Optional<RecipeHolder<T>> found = level().getRecipeManager().getRecipeFor(type, recipeInput, level());
        if (found.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = found.get().value().assemble(recipeInput, level().registryAccess());
        return result.isEmpty() || !result.isItemEnabled(level().enabledFeatures()) ? ItemStack.EMPTY : result.copy();
    }

    public void setAnvilItemName(String name) {
        this.anvilItemName = name == null ? "" : name;
        updateAnvilResult();
        if (inventory.player instanceof ServerPlayer player && player.connection != null) {
            player.connection.send(new ClientboundContainerSetSlotPacket(
                    containerId, incrementStateId(), ANVIL_RESULT_SLOT, anvilResult.getItem(0)));
        }
        broadcastChanges();
    }

    public String anvilItemName() {
        return anvilItemName;
    }

    public int anvilCost() {
        return anvilCost;
    }

    void updateAnvilResult() {
        ItemStack left = anvilLeft.getItem(0);
        ItemStack right = anvilRight.getItem(0);
        if (left.isEmpty()) {
            anvilResult.setItem(0, ItemStack.EMPTY);
            anvilCost = 0;
            anvilMaterialCost = 0;
            if (inventory.player instanceof ServerPlayer player && player.connection != null) {
                player.connection.send(new ClientboundContainerSetSlotPacket(
                        containerId, incrementStateId(), ANVIL_RESULT_SLOT, ItemStack.EMPTY));
            }
            return;
        }

        internalAnvil.getSlot(0).set(left.copy());
        internalAnvil.getSlot(1).set(right.copy());
        internalAnvil.setItemName(anvilItemName);

        ItemStack result = internalAnvil.getSlot(2).getItem();
        anvilCost = internalAnvil.getCost();
        anvilMaterialCost = internalAnvil.repairItemCountCost;
        anvilResult.setItem(0, result.copy());

        if (inventory.player instanceof ServerPlayer player && player.connection != null) {
            player.connection.send(new ClientboundContainerSetSlotPacket(
                    containerId, incrementStateId(), ANVIL_RESULT_SLOT, result));
        }
    }

    private void syncChanges(Player player) {
        if (savedData != null) savedData.syncViewers(player.getUUID());
    }

    void syncFromWarehouse() {
        if (inventory.player instanceof ServerPlayer player && player.connection != null && player.containerMenu == this
                && syncedWarehouseRevision != warehouse.revision()) {
            syncedWarehouseRevision = warehouse.revision();
            List<KleinWarehouse.Entry> entries = warehouse.entries().stream().toList();
            if (entries.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new KleinSyncPayload(List.of(), true));
                return;
            }
            for (int start = 0; start < entries.size(); start += 64) {
                int end = Math.min(entries.size(), start + 64);
                PacketDistributor.sendToPlayer(player, new KleinSyncPayload(entries.subList(start, end).stream()
                        .map(entry -> new KleinEntryData(entry.id(), entry.template(), entry.amount())).toList(),
                        start == 0, end == entries.size()));
            }
        }
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (savedData == null) return;
        savedData.unwatch(player.getUUID(), this);
        flushDrawer(DRAWER_CRAFTING);
        flushDrawer(DRAWER_SMELTING);
        flushDrawer(DRAWER_ANVIL);
    }

    private void flushDrawer(int mode) {
        recomputeGuard = true;
        try {
            switch (mode) {
                case DRAWER_CRAFTING -> {
                    returnToWarehouse(craftSlots);
                    craftResult.clearContent();
                }
                case DRAWER_SMELTING -> {
                    returnToWarehouse(smeltInput);
                    returnToWarehouse(smeltOutput);
                    queuedCookingRecipe = null;
                }
                case DRAWER_ANVIL -> {
                    returnToWarehouse(anvilLeft);
                    returnToWarehouse(anvilRight);
                    anvilResult.clearContent();
                    anvilItemName = "";
                    anvilCost = 0;
                    anvilMaterialCost = 0;
                }
                default -> { }
            }
        } finally {
            recomputeGuard = false;
        }
    }

    private void returnToWarehouse(net.minecraft.world.Container container) {
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
        if (warehouse == null || !(inventory.player instanceof ServerPlayer player)) return;
        syncFromWarehouse();
    }

    public void applySync(KleinSyncPayload payload) {
        // Keep the displayed snapshot and its scroll range until the entire batch arrives.
        if (payload.reset()) pendingEntries.clear();
        payload.entries().forEach(entry -> {
            if (entry.amount().signum() == 0) pendingEntries.remove(entry.id());
            else pendingEntries.put(entry.id(), entry);
        });
        if (!payload.complete()) return;
        clientEntries.clear();
        clientEntries.putAll(pendingEntries);
        refilter();
    }

    public void setView(String query, int sortMode, boolean descending) {
        this.query = query.length() > 64 ? query.substring(0, 64) : query;
        this.sortMode = sortMode;
        this.descending = descending;
        scroll = 0;
        refilter();
    }

    void saveSort(KleinSortOrder sort) {
        setView(query, sort.mode(), sort.descending());
        if (savedData != null) savedData.setSortOrder(inventory.player.getUUID(), sort);
    }

    public void setCategory(int category) {
        int next = Math.max(CATEGORY_ALL, Math.min(CATEGORY_OTHER, category));
        if (this.category != next) {
            this.category = next;
            scroll = 0;
            refilter();
        }
    }

    public int category() { return category; }

    public int totalStoredTypes() { return clientEntries.size(); }

    public BigInteger totalStoredItems() {
        BigInteger sum = BigInteger.ZERO;
        for (KleinEntryData entry : clientEntries.values()) {
            sum = sum.add(entry.amount());
        }
        return sum;
    }

    private static boolean matchesCategory(ItemStack stack, int category) {
        if (category == CATEGORY_ALL) return true;
        boolean isBlock = stack.getItem() instanceof net.minecraft.world.item.BlockItem;
        boolean isGear = stack.isDamageableItem()
                || stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                || stack.getItem() instanceof net.minecraft.world.item.TieredItem
                || stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
                || stack.getItem() instanceof net.minecraft.world.item.ShieldItem
                || stack.getItem() instanceof net.minecraft.world.item.EnchantedBookItem;
        return switch (category) {
            case CATEGORY_BLOCKS -> isBlock;
            case CATEGORY_GEAR -> isGear;
            case CATEGORY_OTHER -> !isBlock && !isGear;
            default -> true;
        };
    }

    private void refilter() {
        String raw = query.trim();
        filtered.clear();

        if (raw.startsWith("@")) {
            String modQuery = raw.substring(1).trim().toLowerCase(Locale.ROOT);
            clientEntries.values().stream().filter(entry -> matchesCategory(entry.template(), category)).filter(entry -> {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(entry.template().getItem());
                if (key.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery)) return true;
                return ModList.get().getModContainerById(key.getNamespace())
                        .map(c -> c.getModInfo().getDisplayName().toLowerCase(Locale.ROOT).contains(modQuery))
                        .orElse(false);
            }).forEach(filtered::add);
        } else if (raw.startsWith("#")) {
            String tagQuery = raw.substring(1).trim().toLowerCase(Locale.ROOT);
            clientEntries.values().stream().filter(entry -> matchesCategory(entry.template(), category)).filter(entry ->
                entry.template().getTags().anyMatch(tag ->
                    tag.location().getPath().toLowerCase(Locale.ROOT).contains(tagQuery)
                    || tag.location().toString().toLowerCase(Locale.ROOT).contains(tagQuery)
                )
            ).forEach(filtered::add);
        } else {
            String needle = raw.toLowerCase(Locale.ROOT);
            clientEntries.values().stream().filter(entry -> matchesCategory(entry.template(), category)).filter(entry ->
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
    public boolean isStorageLocked(Slot slot) {
        return slot.container == inventory && (slot.getContainerSlot() == lockedInventorySlot
                || (slot.hasItem() && !KleinWarehouse.canStore(slot.getItem())));
    }
    public boolean autoDeposit() { return autoDeposit; }
    public void applyAutoDeposit(boolean enabled) { autoDeposit = enabled; }
    public void setAutoDeposit(boolean enabled) {
        autoDeposit = enabled;
        if (savedData != null && inventory.player instanceof ServerPlayer player) {
            savedData.setAutoDeposit(player.getUUID(), enabled);
        }
    }
    public Player inventoryPlayer() { return inventory.player; }
    public TransientCraftingContainer craftSlots() { return craftSlots; }
    public int drawerMode() { return drawerMode; }
    public void setDrawerMode(int mode) {
        int next = Math.max(DRAWER_NONE, Math.min(mode, DRAWER_ANVIL));
        if (next != drawerMode) {
            if (savedData != null && next == DRAWER_NONE) flushDrawer(drawerMode);
            if (next == DRAWER_NONE && drawerMode == DRAWER_ANVIL) anvilItemName = "";
            drawerMode = next;
            broadcastChanges();
        }
    }
    public void cycleDrawerMode(int mode) { setDrawerMode(drawerMode == mode ? DRAWER_NONE : mode); }

    public void virtualClick(KleinEntryData entry, int button, boolean shift) {
        if (button == 1 && (!shift || !getCarried().isEmpty())) {
            if (entry != null || !getCarried().isEmpty()) {
                PacketDistributor.sendToServer(new KleinActionPayload(entry == null ? -1 : entry.id(),
                        KleinActionPayload.RIGHT_CLICK, -1, nextSequence()));
            }
            return;
        }
        if (!getCarried().isEmpty()) {
            PacketDistributor.sendToServer(new KleinActionPayload(entry == null ? -1 : entry.id(),
                    KleinActionPayload.INSERT_CARRIED, -1, nextSequence()));
            return;
        }
        if (entry == null) return;
        int operation = shift ? KleinActionPayload.EXTRACT_INVENTORY
                : button == 2 ? KleinActionPayload.EXTRACT_HALF
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
        @Override public boolean mayPickup(Player player) {
            return anvilCost > 0 && (player.getAbilities().instabuild || player.experienceLevel >= anvilCost);
        }
        @Override public void onTake(Player player, ItemStack stack) {
            recomputeGuard = true;
            try {
                if (!player.getAbilities().instabuild && anvilCost > 0) {
                    player.giveExperienceLevels(-anvilCost);
                }
                net.neoforged.neoforge.common.CommonHooks.onAnvilRepair(
                        player, stack, anvilLeft.getItem(0), anvilRight.getItem(0));

                anvilLeft.setItem(0, ItemStack.EMPTY);
                if (anvilMaterialCost > 0) anvilRight.removeItem(0, anvilMaterialCost);
                else anvilRight.setItem(0, ItemStack.EMPTY);

                player.level().playSound(null, player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ANVIL_USE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            } finally {
                recomputeGuard = false;
            }
            updateAnvilResult();
            broadcastChanges();
            super.onTake(player, stack);
        }
    }

    private final class ProtectedSlot extends Slot {
        private ProtectedSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean mayPickup(Player player) { return getContainerSlot() != lockedInventorySlot; }
        @Override public boolean mayPlace(ItemStack stack) { return getContainerSlot() != lockedInventorySlot && super.mayPlace(stack); }
    }
}
