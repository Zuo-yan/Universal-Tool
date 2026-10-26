package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;

/** Aggregate quantities live here, never in an ItemStack or a physical container slot. */
public final class KleinWarehouse {
    private record ItemKey(Item item, DataComponentPatch components) {
        static ItemKey of(ItemStack stack) { return new ItemKey(stack.getItem(), stack.getComponentsPatch()); }
    }
    static final int MAX_AMOUNT_DIGITS = 128;
    static final BigInteger MAX_AMOUNT = BigInteger.TEN.pow(MAX_AMOUNT_DIGITS).subtract(BigInteger.ONE);

    public static final class Entry {
        private final long id;
        private final ItemStack template;
        private BigInteger amount;

        Entry(long id, ItemStack template, BigInteger amount) {
            this.id = id;
            this.template = template.copyWithCount(1);
            this.amount = amount;
        }

        public long id() { return id; }
        public ItemStack template() { return template.copy(); }
        public BigInteger amount() { return amount; }
        public String debugState() { return id + ":" + template + "=" + amount; }
        boolean singleInstance() { return template.getMaxStackSize() == 1; }
    }

    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
    private final Map<ItemKey, Long> stackableTypes = new HashMap<>();
    private final Map<ItemKey, List<Long>> singleTypes = new HashMap<>();
    private long nextId = 1;

    public Collection<Entry> entries() { return entries.values(); }
    public java.math.BigInteger amount(long id) {
        Entry entry = entries.get(id);
        return entry == null ? java.math.BigInteger.ZERO : entry.amount;
    }
    public Entry entry(long id) { return entries.get(id); }
    public int size() { return entries.size(); }
    long nextId() { return nextId; }

    public BigInteger insert(ItemStack source, BigInteger requested) {
        if (source.isEmpty() || source.getItem() instanceof KleinBottleItem || requested.signum() <= 0) {
            return BigInteger.ZERO;
        }
        ItemStack type = source.copyWithCount(1);
        ItemKey key = ItemKey.of(type);
        BigInteger accepted = requested.min(MAX_AMOUNT);
        if (type.getMaxStackSize() == 1) {
            List<Long> ids = singleTypes.computeIfAbsent(key, ignored -> new ArrayList<>());
            BigInteger remaining = accepted;
            while (remaining.signum() > 0) {
                addEntry(nextId++, type, BigInteger.ONE);
                ids.add(nextId - 1);
                remaining = remaining.subtract(BigInteger.ONE);
            }
            return accepted;
        }
        Entry entry = entries.get(stackableTypes.get(key));
        if (entry == null) {
            entry = new Entry(nextId++, type, BigInteger.ZERO);
            entries.put(entry.id, entry);
            stackableTypes.put(key, entry.id);
        }
        accepted = accepted.min(MAX_AMOUNT.subtract(entry.amount));
        entry.amount = entry.amount.add(accepted);
        return accepted;
    }

    public ItemStack extract(long id, int requested) {
        Entry entry = entries.get(id);
        if (entry == null || requested <= 0) return ItemStack.EMPTY;
        int count = (int) entry.amount.min(BigInteger.valueOf(Math.min(requested, entry.template.getMaxStackSize()))).longValue();
        if (count == 0) return ItemStack.EMPTY;
        ItemStack result = entry.template.copyWithCount(count);
        removeAmount(entry, BigInteger.valueOf(count));
        return result;
    }

    public boolean containsBottle() {
        return entries.values().stream().anyMatch(entry -> entry.template.getItem() instanceof KleinBottleItem);
    }

    void addLoaded(long id, ItemStack template, BigInteger amount) {
        addEntry(id, template, amount);
        nextId = Math.max(nextId, id + 1);
        ItemKey key = ItemKey.of(template);
        if (template.getMaxStackSize() == 1) singleTypes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(id);
        else stackableTypes.putIfAbsent(key, id);
    }

    private void addEntry(long id, ItemStack template, BigInteger amount) {
        entries.put(id, new Entry(id, template, amount));
    }

    private void removeAmount(Entry entry, BigInteger count) {
        entry.amount = entry.amount.subtract(count);
        if (entry.amount.signum() > 0) return;
        entries.remove(entry.id);
        ItemKey key = ItemKey.of(entry.template);
        if (entry.singleInstance()) {
            List<Long> ids = singleTypes.get(key);
            if (ids != null) {
                ids.remove(entry.id);
                if (ids.isEmpty()) singleTypes.remove(key);
            }
        } else {
            stackableTypes.remove(key);
        }
    }
}
