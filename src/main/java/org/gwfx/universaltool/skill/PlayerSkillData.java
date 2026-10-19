package org.gwfx.universaltool.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Persistent player growth, learning, loadout, and cooldown state.
 *
 * <p>Pure data with no level or loader access, so progression rules stay testable. The attachment
 * registration lives in {@link SkillAttachments}.
 */
public final class PlayerSkillData {
    /**
     * Cap chosen so the total point budget exactly covers learning every skill to max level.
     * Budget = 1 initial + 49 levels + 18 milestone points = 68, matching the catalog.
     */
    public static final int MAX_LEVEL = 50;
    public static final int INITIAL_LEVEL = 1;
    public static final int INITIAL_POINTS = 1;
    public static final int MAX_ACTIVE_SLOTS = 6;
    public static final int MAX_PASSIVE_SLOTS = 8;
    public static final int BASE_PASSIVE_SLOTS = 4;
    public static final int ACTIVE_SLOTS_UNLOCK_LEVEL = 10;
    public static final int BASE_ACTIVE_SLOTS = 3;
    public static final int MILESTONE_INTERVAL = 5;
    public static final int COMBAT_TICKS = 200;
    public static final long MILLIS_PER_TICK = 50L;
    /** Network-only field: lets the client rebase absolute cooldown stamps onto its own clock. */
    public static final String SERVER_CLOCK_KEY = "ServerNowMillis";

    private int level = INITIAL_LEVEL;
    private int awardedPoints = INITIAL_POINTS;
    private static final int PROGRESSION_VERSION = 2;
    private long experience;
    private long experienceAtLevelStart;
    private long experienceAtMaxLevel;
    private int skillPoints = INITIAL_POINTS;
    private int lifetimeExperience;
    private long lastCombatTick;
    private final Map<String, Integer> levels = new LinkedHashMap<>();
    private final List<String> activeLoadout = new ArrayList<>();
    private final List<String> passiveLoadout = new ArrayList<>();
    private int selectedActive;
    private final Map<String, Long> cooldownExpiry = new LinkedHashMap<>();
    private final Set<String> discoveries = new LinkedHashSet<>();

    public int level() { return level; }
    public long experience() { return experience; }
    public long experienceAtLevelStart() { return experienceAtLevelStart; }
    public long experienceAtMaxLevel() { return experienceAtMaxLevel; }
    public int skillPoints() { return skillPoints; }
    public int lifetimeExperience() { return lifetimeExperience; }
    public Map<String, Integer> levels() { return Map.copyOf(levels); }
    public List<String> activeLoadout() { return List.copyOf(activeLoadout); }
    public List<String> passiveLoadout() { return List.copyOf(passiveLoadout); }
    public int selectedActive() { return selectedActive; }

    /** Smooth linear curve: 600 XP for the first level, then +100 per level. */
    public int experienceForNextLevel() {
        if (level >= MAX_LEVEL) return 0;
        return 600 + (level - 1) * 100;
    }

    public static int milestoneBonus(int targetLevel) {
        if (targetLevel % MILESTONE_INTERVAL != 0) return 0;
        if (targetLevel >= MAX_LEVEL) return 5;
        if (targetLevel % 10 == 0) return 2;
        return 1;
    }

    public static int totalMilestoneBonuses() {
        int total = 0;
        for (int l = 1; l <= MAX_LEVEL; l++) {
            total += milestoneBonus(l);
        }
        return total;
    }

    /** Total points a player can ever hold at {@link #MAX_LEVEL}, including milestone bonuses. */
    public static int totalPointBudget() {
        return INITIAL_POINTS + (MAX_LEVEL - 1) + totalMilestoneBonuses();
    }

    public static int pointBudgetAtLevel(int target) {
        int total = INITIAL_POINTS + target - 1;
        for (int i = 1; i <= target; i++) total += milestoneBonus(i);
        return total;
    }

    public int activeSlotCount() {
        return level >= ACTIVE_SLOTS_UNLOCK_LEVEL ? MAX_ACTIVE_SLOTS : BASE_ACTIVE_SLOTS;
    }

    public int passiveSlotCount() {
        if (level >= 40) return MAX_PASSIVE_SLOTS;
        if (level >= 30) return 7;
        if (level >= 20) return 6;
        if (level >= 10) return 5;
        return BASE_PASSIVE_SLOTS;
    }

    public boolean learned(String id) {
        return levels.containsKey(id);
    }

    public int learnedLevel(String id) {
        return levels.getOrDefault(id, 0);
    }

    public int addExperience(long amount) {
        return addExperience(amount, null);
    }

    /**
     * Adds growth experience and settles every level crossing.
     *
     * <p>Each level grants one point, and every {@link #MILESTONE_INTERVAL} levels grants one extra.
     * The milestone is evaluated per level reached, so a single large award cannot skip it.
     */
    public int addExperience(long amount, Consumer<SkillDefinition> onUnlock) {
        if (amount <= 0) return 0;
        if (level >= MAX_LEVEL) {
            experienceAtMaxLevel += amount;
            return 0;
        }
        long safe = Math.min(amount, Integer.MAX_VALUE);
        experience += safe;
        lifetimeExperience = (int) Math.min(Integer.MAX_VALUE, (long) lifetimeExperience + safe);
        int gained = 0;
        unlockEligibleSkills(onUnlock);
        while (level < MAX_LEVEL) {
            int need = experienceForNextLevel();
            if (experience < need) break;
            experience -= need;
            experienceAtLevelStart += need;
            level++;
            gained++;
            int due = pointBudgetAtLevel(level);
            int grant = Math.max(0, due - awardedPoints);
            skillPoints += grant;
            awardedPoints += grant;
            unlockEligibleSkills(onUnlock);
        }
        if (level >= MAX_LEVEL) {
            experienceAtMaxLevel += experience;
            experience = 0;
        }
        return gained;
    }

    /** Unlocks learning qualification for all active skills at once. */
    public int unlockAllSkills(Consumer<SkillDefinition> onUnlock) {
        int unlocked = 0;
        for (SkillDefinition definition : ModSkills.actives()) {
            String key = "skill:" + definition.id();
            if (!discoveries.contains(key)) {
                discoveries.add(key);
                if (onUnlock != null) onUnlock.accept(definition);
                unlocked++;
            }
        }
        return unlocked;
    }

    public int unlockEligibleSkills(Consumer<SkillDefinition> onUnlock) {
        int unlocked = 0;
        for (SkillDefinition definition : ModSkills.actives()) {
            String key = "skill:" + definition.id();
            if (!discoveries.contains(key) && meetsUnlock(definition.id())) {
                discoveries.add(key);
                if (onUnlock != null) onUnlock.accept(definition);
                unlocked++;
            }
        }
        return unlocked;
    }

    /** Passives are always learnable with points; actives need their own unlock discovery. */
    public boolean isUnlocked(String id) {
        SkillDefinition definition = ModSkills.find(id).orElse(null);
        if (definition == null) return false;
        if (definition.type() == SkillType.PASSIVE) return true;
        return discoveries.contains("skill:" + id);
    }

    private boolean meetsUnlock(String id) {
        return switch (id) {
            case ModSkills.COUNTER_GUARD -> discoveries.contains("combat:counter_kill");
            case ModSkills.GRAVITY_WELL -> discoveries.contains("combat:multi_kill");
            case ModSkills.FROZEN_PULL -> discoveries.contains("combat:hostile_kill");
            case ModSkills.OPPRESSIVE_FIELD -> discoveries.contains("combat:three_nearby");
            case ModSkills.ORE_SIGHT -> discoveries.contains("mine:ore");
            case ModSkills.AIR_STEP -> discoveries.contains("explore:high_place");
            case ModSkills.LIGHTNING_SPEAR -> discoveries.contains("combat:hostile_kill");
            case ModSkills.BERSERK -> discoveries.contains("combat:ten_kills");
            default -> false;
        };
    }

    public boolean learnOrUpgrade(String id) {
        SkillDefinition definition = ModSkills.find(id).orElse(null);
        if (definition == null || !isUnlocked(id)) return false;
        int current = learnedLevel(id);
        if (current >= definition.maxLevel()) return false;
        int cost = definition.costAt(current + 1);
        if (cost == Integer.MAX_VALUE || skillPoints < cost) return false;
        skillPoints -= cost;
        levels.put(id, current + 1);
        return true;
    }

    /** Sets growth to the level cap and fills every formal skill within the point budget. */
    public int maximizeAllSkills() {
        level = MAX_LEVEL;
        awardedPoints = totalPointBudget();
        experience = 0;
        skillPoints = totalPointBudget() - ModSkills.totalCost();
        boolean needsActiveUnlock = false;
        for (SkillDefinition definition : ModSkills.all()) {
            levels.put(definition.id(), definition.maxLevel());
            if (definition.type() == SkillType.ACTIVE
                    && !discoveries.contains("skill:" + definition.id())) {
                discoveries.add("skill:" + definition.id());
                needsActiveUnlock = true;
            }
        }
        activeLoadout.clear();
        passiveLoadout.clear();
        selectedActive = 0;
        return ModSkills.all().size() + (needsActiveUnlock ? 1 : 0);
    }

    public boolean addDiscovery(String id) {
        return id != null && !id.isBlank() && discoveries.add(id);
    }

    public boolean record(String id) {
        return addDiscovery(id);
    }

    public boolean hasDiscovery(String id) {
        return discoveries.contains(id);
    }

    public Set<String> discoveries() {
        return Set.copyOf(discoveries);
    }

    public void clearCombat() {
        lastCombatTick = 0;
    }

    public void markCombat(long gameTime) {
        lastCombatTick = gameTime;
    }

    public long lastCombatGameTime() {
        return lastCombatTick;
    }

    public boolean inCombat(long gameTime) {
        return lastCombatTick > 0 && gameTime - lastCombatTick < COMBAT_TICKS;
    }

    public long combatRemaining(long gameTime) {
        if (!inCombat(gameTime)) return 0;
        return Math.max(0, COMBAT_TICKS - (gameTime - lastCombatTick));
    }

    public boolean setSelectedActive(int index) {
        if (index < 0 || index >= activeLoadout.size() || index >= activeSlotCount()) return false;
        selectedActive = index;
        return true;
    }

    public String selectedSkill() {
        if (activeLoadout.isEmpty()) return "";
        if (selectedActive < 0 || selectedActive >= activeLoadout.size()) selectedActive = 0;
        return activeLoadout.get(selectedActive);
    }

    public boolean setLoadout(boolean active, List<String> requested) {
        int capacity = active ? activeSlotCount() : passiveSlotCount();
        if (requested.size() > capacity) return false;
        for (String id : requested) {
            SkillDefinition def = ModSkills.find(id).orElse(null);
            if (def == null || def.type() != (active ? SkillType.ACTIVE : SkillType.PASSIVE) || !learned(id)) {
                return false;
            }
        }
        if (new LinkedHashSet<>(requested).size() != requested.size()) return false;
        if (active) {
            activeLoadout.clear();
            activeLoadout.addAll(requested);
            selectedActive = activeLoadout.isEmpty() ? 0 : Math.min(selectedActive, activeLoadout.size() - 1);
        } else {
            passiveLoadout.clear();
            passiveLoadout.addAll(requested);
        }
        return true;
    }

    public boolean addToLoadout(boolean active, String id) {
        SkillDefinition definition = ModSkills.find(id).orElse(null);
        if (definition == null || definition.type() != (active ? SkillType.ACTIVE : SkillType.PASSIVE)
                || !learned(id)) return false;
        List<String> loadout = active ? activeLoadout : passiveLoadout;
        int capacity = active ? activeSlotCount() : passiveSlotCount();
        if (loadout.contains(id)) return true;
        if (loadout.size() >= capacity) return false;
        loadout.add(id);
        if (active) selectedActive = loadout.size() - 1;
        return true;
    }

    public boolean removeFromLoadout(boolean active, int index) {
        List<String> loadout = active ? activeLoadout : passiveLoadout;
        if (index < 0 || index >= loadout.size()) return false;
        loadout.remove(index);
        if (active) {
            selectedActive = loadout.isEmpty() ? 0 : Math.min(selectedActive, loadout.size() - 1);
        }
        return true;
    }

    /** Refunds every spent point, clears learning and loadouts, and keeps unlock discoveries. */
    public int resetSkill(String id) {
        Integer level = levels.remove(id);
        if (level == null || level <= 0) return 0;
        SkillDefinition def = ModSkills.find(id).orElse(null);
        int returned = 0;
        if (def != null) {
            for (int i = 1; i <= level; i++) {
                int cost = def.costAt(i);
                if (cost != Integer.MAX_VALUE) returned += cost;
            }
        }
        skillPoints += returned;
        activeLoadout.remove(id);
        if (selectedActive >= activeLoadout.size()) {
            selectedActive = activeLoadout.isEmpty() ? 0 : activeLoadout.size() - 1;
        }
        passiveLoadout.remove(id);
        return returned;
    }

    public void resetAllocations() {
        int returned = 0;
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            SkillDefinition def = ModSkills.find(entry.getKey()).orElse(null);
            if (def != null) {
                for (int i = 1; i <= entry.getValue(); i++) {
                    int cost = def.costAt(i);
                    if (cost != Integer.MAX_VALUE) returned += cost;
                }
            }
        }
        skillPoints += returned;
        levels.clear();
        activeLoadout.clear();
        passiveLoadout.clear();
        selectedActive = 0;
    }

    public boolean onCooldown(String id) {
        return cooldownExpiry.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

    public long cooldownRemainingTicks(String id) {
        long remaining = cooldownRemainingMillis(id);
        return remaining <= 0 ? 0 : (remaining + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK;
    }

    public long cooldownRemainingMillis(String id) {
        return Math.max(0, cooldownExpiry.getOrDefault(id, 0L) - System.currentTimeMillis());
    }

    /** Cooldowns use wall-clock time so they keep recovering while the server is offline. */
    public void startCooldown(String id, int ticks) {
        cooldownExpiry.put(id, System.currentTimeMillis() + ticks * MILLIS_PER_TICK);
    }

    public Optional<SkillDefinition> selectedDefinition() {
        return ModSkills.find(selectedSkill());
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Level", level);
        tag.putInt("ProgressionVersion", PROGRESSION_VERSION);
        tag.putInt("AwardedPoints", awardedPoints);
        tag.putLong("Experience", experience);
        tag.putLong("ExperienceAtLevelStart", experienceAtLevelStart);
        tag.putLong("ExperienceAtMaxLevel", experienceAtMaxLevel);
        tag.putInt("SkillPoints", skillPoints);
        tag.putInt("LifetimeExperience", lifetimeExperience);
        tag.putLong("LastCombatTick", lastCombatTick);
        tag.putInt("SelectedActive", selectedActive);
        CompoundTag learned = new CompoundTag();
        levels.forEach(learned::putInt);
        tag.put("Levels", learned);
        tag.put("ActiveLoadout", stringList(activeLoadout));
        tag.put("PassiveLoadout", stringList(passiveLoadout));
        tag.put("Discoveries", stringList(discoveries.stream().toList()));
        CompoundTag cooldowns = new CompoundTag();
        cooldownExpiry.forEach(cooldowns::putLong);
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    /** Serializes for the network, adding the server clock so the client can rebase cooldowns. */
    public CompoundTag serializeForSync(long serverNowMillis) {
        CompoundTag tag = serialize();
        tag.putLong(SERVER_CLOCK_KEY, serverNowMillis);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        deserialize(tag, System.currentTimeMillis());
    }

    public void deserialize(CompoundTag tag, long localNowMillis) {
        level = tag.contains("Level") ? Math.max(1, Math.min(MAX_LEVEL, tag.getInt("Level"))) : INITIAL_LEVEL;
        experience = Math.max(0, tag.getLong("Experience"));
        experienceAtLevelStart = Math.max(0, tag.getLong("ExperienceAtLevelStart"));
        experienceAtMaxLevel = Math.max(0, tag.getLong("ExperienceAtMaxLevel"));
        skillPoints = Math.max(0, tag.contains("SkillPoints") ? tag.getInt("SkillPoints") : INITIAL_POINTS);
        lifetimeExperience = Math.max(0, tag.getInt("LifetimeExperience"));
        lastCombatTick = tag.getLong("LastCombatTick");
        selectedActive = Math.max(0, tag.getInt("SelectedActive"));
        levels.clear();
        if (tag.contains("Levels", Tag.TAG_COMPOUND)) {
            CompoundTag learned = tag.getCompound("Levels");
            for (String id : learned.getAllKeys()) {
                int value = learned.getInt(id);
                if (ModSkills.find(id).isPresent() && value > 0) levels.put(id, value);
            }
        }
        int spent = 0;
        for (var entry : levels.entrySet()) {
            SkillDefinition def = ModSkills.find(entry.getKey()).orElseThrow();
            for (int i = 1; i <= entry.getValue(); i++) spent += def.costAt(i);
        }
        awardedPoints = Math.max(skillPoints + spent, tag.getInt("AwardedPoints"));
        if (tag.getInt("ProgressionVersion") < PROGRESSION_VERSION) {
            if (level < 40) experience = experience * 4;
            else experience = 0;
            experienceAtMaxLevel = 0;
        }
        activeLoadout.clear();
        passiveLoadout.clear();
        discoveries.clear();
        cooldownExpiry.clear();
        readStrings(tag, "ActiveLoadout", activeLoadout);
        readStrings(tag, "PassiveLoadout", passiveLoadout);
        List<String> loadedDiscoveries = new ArrayList<>();
        readStrings(tag, "Discoveries", loadedDiscoveries);
        discoveries.addAll(loadedDiscoveries);
        long clockOffset = tag.contains(SERVER_CLOCK_KEY) ? localNowMillis - tag.getLong(SERVER_CLOCK_KEY) : 0L;
        if (tag.contains("Cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cooldowns = tag.getCompound("Cooldowns");
            for (String id : cooldowns.getAllKeys()) {
                cooldownExpiry.put(id, cooldowns.getLong(id) + clockOffset);
            }
        }
        activeLoadout.removeIf(id -> !learned(id));
        passiveLoadout.removeIf(id -> !learned(id));
        selectedActive = activeLoadout.isEmpty() ? 0 : Math.min(selectedActive, activeLoadout.size() - 1);
    }

    private static ListTag stringList(List<String> values) {
        ListTag result = new ListTag();
        values.forEach(value -> result.add(StringTag.valueOf(value)));
        return result;
    }

    private static void readStrings(CompoundTag tag, String key, List<String> output) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) output.add(list.getString(i));
    }
}
