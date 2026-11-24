package org.gwfx.universaltool.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 玩家持久化的技能成长、星图学习、试炼进度、3 套构筑预设、星座共鸣与冷却状态。
 *
 * <p>纯数据模型（不依赖 Client 或具体世界实现），便于离线与 GameTest 单元测试。
 */
public final class PlayerSkillData {
    /**
     * 满级 50 级总点数预算 = 初始 2 + 升级 49 + 逢5里程碑(5×3=15) + 逢10大阶(4×8=32) + 50级终极(12) = 110。
     * 恰好等于 32 个技能全部学满所需点数（14 主动×4 + 18 被动×3 = 110）。
     */
    public static final int MAX_LEVEL = 50;
    public static final int INITIAL_LEVEL = 1;
    public static final int INITIAL_POINTS = 2;
    public static final int PRESET_COUNT = 3;
    public static final int MILESTONE_INTERVAL = 5;
    public static final int COMBAT_TICKS = 100; // 5 秒脱战窗口
    public static final long MILLIS_PER_TICK = 50L;
    public static final int PROGRESSION_VERSION = 3;

    /** 网络同步专用字段：客户端用于将服务端绝对时间戳对齐到本地时钟。 */
    public static final String SERVER_CLOCK_KEY = "ServerNowMillis";
    /** 网络同步专用字段：通知客户端当前世界是否启用了技能模块。 */
    public static final String SYSTEM_ENABLED_KEY = "SystemEnabled";

    private static final String[] DEFAULT_PRESET_NAMES = {"战魂星阵", "秘工星阵", "漫游星阵"};

    private int level = INITIAL_LEVEL;
    private int awardedPoints = INITIAL_POINTS;
    private long experience;
    private long experienceAtLevelStart;
    private long experienceAtMaxLevel;
    private int skillPoints = INITIAL_POINTS;
    private int lifetimeExperience;
    private long lastCombatTick;
    private final Map<String, Integer> levels = new LinkedHashMap<>();
    private final List<SkillPreset> presets = new ArrayList<>(PRESET_COUNT);
    private int activePresetIndex = 0;
    private final Map<String, Long> cooldownExpiry = new LinkedHashMap<>();
    private final Set<String> discoveries = new LinkedHashSet<>();
    private final Map<String, Integer> trialStats = new LinkedHashMap<>();

    public PlayerSkillData() {
        ensurePresetsInitialized();
    }

    private void ensurePresetsInitialized() {
        while (presets.size() < PRESET_COUNT) {
            int idx = presets.size();
            presets.add(new SkillPreset(DEFAULT_PRESET_NAMES[idx]));
        }
        if (activePresetIndex < 0 || activePresetIndex >= PRESET_COUNT) {
            activePresetIndex = 0;
        }
    }

    private SkillPreset currentPreset() {
        ensurePresetsInitialized();
        return presets.get(activePresetIndex);
    }

    public int level() { return level; }
    public StarRank starRank() { return StarRank.forLevel(level); }
    public long experience() { return experience; }
    public long experienceAtLevelStart() { return experienceAtLevelStart; }
    public long experienceAtMaxLevel() { return experienceAtMaxLevel; }
    public int skillPoints() { return skillPoints; }
    public int lifetimeExperience() { return lifetimeExperience; }
    public Map<String, Integer> levels() { return Map.copyOf(levels); }
    public List<String> activeLoadout() { return List.copyOf(currentPreset().activeLoadout()); }
    public List<String> passiveLoadout() { return List.copyOf(currentPreset().passiveLoadout()); }
    public int selectedActive() { return currentPreset().selectedActive(); }
    public int activePresetIndex() { return activePresetIndex; }
    public List<SkillPreset> presets() { return List.copyOf(presets); }

    public SkillPreset preset(int index) {
        ensurePresetsInitialized();
        return presets.get(Math.max(0, Math.min(PRESET_COUNT - 1, index)));
    }

    /** 前快后稳的 50 级经验曲线（Lv.1 起步 200 XP）。 */
    public int experienceForNextLevel() {
        if (level >= MAX_LEVEL) return 0;
        if (level < 10) return 200 + (level - 1) * 50;
        if (level < 20) return 650 + (level - 10) * 80;
        if (level < 30) return 1500 + (level - 20) * 110;
        if (level < 40) return 2650 + (level - 30) * 140;
        return 4100 + (level - 40) * 180;
    }

    public static int milestoneBonus(int targetLevel) {
        if (targetLevel % MILESTONE_INTERVAL != 0) return 0;
        if (targetLevel >= MAX_LEVEL) return 12;
        if (targetLevel % 10 == 0) return 8;
        return 3;
    }

    public static int totalMilestoneBonuses() {
        int total = 0;
        for (int l = 1; l <= MAX_LEVEL; l++) {
            total += milestoneBonus(l);
        }
        return total;
    }

    /** 满级总点数预算（110 点）。 */
    public static int totalPointBudget() {
        return INITIAL_POINTS + (MAX_LEVEL - 1) + totalMilestoneBonuses();
    }

    public static int pointBudgetAtLevel(int target) {
        int safe = Math.max(INITIAL_LEVEL, Math.min(MAX_LEVEL, target));
        int total = INITIAL_POINTS + safe - 1;
        for (int i = 1; i <= safe; i++) total += milestoneBonus(i);
        return total;
    }

    public int activeSlotCount() {
        return starRank().activeSlots();
    }

    public int passiveSlotCount() {
        return starRank().passiveSlots();
    }

    public boolean learned(String id) {
        return levels.getOrDefault(id, 0) > 0;
    }

    public int learnedLevel(String id) {
        return levels.getOrDefault(id, 0);
    }

    public boolean isAwakened(String id) {
        return learnedLevel(id) >= ModSkills.MAX_SKILL_LEVEL;
    }

    public int addExperience(long amount) {
        return addExperience(amount, null);
    }

    /** 增加成长经验并结算跨级与星阶突破、同时检查试炼解锁。 */
    public int addExperience(long amount, Consumer<SkillDefinition> onUnlock) {
        if (amount <= 0) return 0;
        if (level >= MAX_LEVEL) {
            experienceAtMaxLevel += amount;
            lifetimeExperience = (int) Math.min(Integer.MAX_VALUE, (long) lifetimeExperience + amount);
            unlockEligibleSkills(onUnlock);
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

    // ===== 试炼统计与双轨解锁 =====

    public int stat(String key) {
        return trialStats.getOrDefault(key, 0);
    }

    public void setStat(String key, int value) {
        if (key != null && !key.isBlank()) {
            trialStats.put(key, Math.max(0, value));
        }
    }

    public int incrementStat(String key, int delta) {
        return incrementStat(key, delta, null);
    }

    public int incrementStat(String key, int delta, Consumer<SkillDefinition> onUnlock) {
        if (key == null || key.isBlank() || delta <= 0) return 0;
        int next = Math.min(Integer.MAX_VALUE / 2, stat(key) + delta);
        trialStats.put(key, next);
        return unlockEligibleSkills(onUnlock);
    }

    public record TrialProgress(int current, int target, boolean usingAlt, float ratio) {}

    public TrialProgress trialProgress(SkillDefinition def) {
        if (def == null || def.trial() == null) {
            return new TrialProgress(1, 1, false, 1.0F);
        }
        if (discoveries.contains("skill:" + def.id())) {
            int t = def.trial().target();
            return new TrialProgress(t, t, false, 1.0F);
        }
        SkillTrialSpec spec = def.trial();
        int mainVal = Math.min(spec.target(), stat(spec.statKey()));
        float mainRatio = mainVal / (float) Math.max(1, spec.target());
        if (spec.hasAlt()) {
            int altVal = Math.min(spec.altTarget(), stat(spec.altStatKey()));
            float altRatio = altVal / (float) Math.max(1, spec.altTarget());
            if (altRatio > mainRatio) {
                return new TrialProgress(altVal, spec.altTarget(), true, Math.min(1.0F, altRatio));
            }
        }
        return new TrialProgress(mainVal, spec.target(), false, Math.min(1.0F, mainRatio));
    }

    public boolean isTrialCompleted(SkillDefinition def) {
        if (def == null) return false;
        if (discoveries.contains("skill:" + def.id())) return true;
        SkillTrialSpec spec = def.trial();
        if (spec == null) return true;
        if (stat(spec.statKey()) >= spec.target()) return true;
        return spec.hasAlt() && stat(spec.altStatKey()) >= spec.altTarget();
    }

    public boolean isPrerequisiteMet(SkillDefinition def) {
        if (def == null) return false;
        return !def.hasPrerequisite() || learned(def.prerequisiteId());
    }

    /** 统一解锁判断：被动和主动都需要完成各自试炼（或管理员解锁）。 */
    public boolean isUnlocked(String id) {
        SkillDefinition def = ModSkills.find(id).orElse(null);
        if (def == null) return false;
        return discoveries.contains("skill:" + id) || isTrialCompleted(def);
    }

    /** 是否满足星图学习/升级条件：试炼已达成 + 前置星图节点已点亮 + 等级未满。 */
    public boolean canLearnOrUpgrade(String id) {
        SkillDefinition def = ModSkills.find(id).orElse(null);
        if (def == null) return false;
        int current = learnedLevel(id);
        if (current >= def.maxLevel()) return false;
        if (!isUnlocked(id) || !isPrerequisiteMet(def)) return false;
        int cost = def.costAt(current + 1);
        return cost != Integer.MAX_VALUE && skillPoints >= cost;
    }

    /** 管理员命令用：立即解锁全部 32 技能试炼资格。 */
    public int unlockAllSkills(Consumer<SkillDefinition> onUnlock) {
        int unlocked = 0;
        for (SkillDefinition definition : ModSkills.all()) {
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
        for (SkillDefinition definition : ModSkills.all()) {
            String key = "skill:" + definition.id();
            if (!discoveries.contains(key) && isTrialCompleted(definition)) {
                discoveries.add(key);
                if (onUnlock != null) onUnlock.accept(definition);
                unlocked++;
            }
        }
        return unlocked;
    }

    public boolean learnOrUpgrade(String id) {
        if (!canLearnOrUpgrade(id)) return false;
        SkillDefinition def = ModSkills.find(id).orElseThrow();
        int current = learnedLevel(id);
        int cost = def.costAt(current + 1);
        skillPoints -= cost;
        levels.put(id, current + 1);
        return true;
    }

    /** 管理员命令用：直接设为 Lv.50 星穹主宰并点满全部 32 技能。 */
    public int maximizeAllSkills() {
        level = MAX_LEVEL;
        awardedPoints = totalPointBudget();
        experience = 0;
        skillPoints = totalPointBudget() - ModSkills.totalCost();
        int changed = 0;
        for (SkillDefinition definition : ModSkills.all()) {
            levels.put(definition.id(), definition.maxLevel());
            if (discoveries.add("skill:" + definition.id())) {
                changed++;
            }
        }
        for (SkillPreset preset : presets) {
            preset.clear();
        }
        return ModSkills.all().size() + (changed > 0 ? 1 : 0);
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

    // ===== 战斗计时 =====

    public void clearCombat() { lastCombatTick = 0; }
    public void markCombat(long gameTime) { lastCombatTick = gameTime; }
    public long lastCombatGameTime() { return lastCombatTick; }

    public boolean inCombat(long gameTime) {
        return lastCombatTick > 0 && gameTime - lastCombatTick < COMBAT_TICKS;
    }

    public long combatRemaining(long gameTime) {
        if (!inCombat(gameTime)) return 0;
        return Math.max(0, COMBAT_TICKS - (gameTime - lastCombatTick));
    }

    // ===== 构筑预设与装配 =====

    public boolean switchPreset(int index) {
        if (index < 0 || index >= PRESET_COUNT) return false;
        activePresetIndex = index;
        sanitizePreset(currentPreset());
        return true;
    }

    public boolean renamePreset(int index, String name) {
        if (index < 0 || index >= PRESET_COUNT || name == null || name.isBlank()) return false;
        preset(index).setName(name);
        return true;
    }

    private void sanitizePreset(SkillPreset preset) {
        preset.activeLoadout().removeIf(id -> !learned(id));
        preset.passiveLoadout().removeIf(id -> !learned(id));
        while (preset.activeLoadout().size() > activeSlotCount()) {
            preset.activeLoadout().remove(preset.activeLoadout().size() - 1);
        }
        while (preset.passiveLoadout().size() > passiveSlotCount()) {
            preset.passiveLoadout().remove(preset.passiveLoadout().size() - 1);
        }
        if (preset.activeLoadout().isEmpty()) {
            preset.setSelectedActive(0);
        } else if (preset.selectedActive() >= preset.activeLoadout().size()) {
            preset.setSelectedActive(preset.activeLoadout().size() - 1);
        }
    }

    public boolean setSelectedActive(int index) {
        SkillPreset preset = currentPreset();
        if (index < 0 || index >= preset.activeLoadout().size() || index >= activeSlotCount()) return false;
        preset.setSelectedActive(index);
        return true;
    }

    public String selectedSkill() {
        SkillPreset preset = currentPreset();
        if (preset.activeLoadout().isEmpty()) return "";
        if (preset.selectedActive() < 0 || preset.selectedActive() >= preset.activeLoadout().size()) {
            preset.setSelectedActive(0);
        }
        return preset.activeLoadout().get(preset.selectedActive());
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
        SkillPreset preset = currentPreset();
        List<String> target = active ? preset.activeLoadout() : preset.passiveLoadout();
        target.clear();
        target.addAll(requested);
        if (active) {
            preset.setSelectedActive(target.isEmpty() ? 0 : Math.min(preset.selectedActive(), target.size() - 1));
        }
        return true;
    }

    public boolean addToLoadout(boolean active, String id) {
        SkillDefinition definition = ModSkills.find(id).orElse(null);
        if (definition == null || definition.type() != (active ? SkillType.ACTIVE : SkillType.PASSIVE)
                || !learned(id)) return false;
        SkillPreset preset = currentPreset();
        List<String> loadout = active ? preset.activeLoadout() : preset.passiveLoadout();
        int capacity = active ? activeSlotCount() : passiveSlotCount();
        if (loadout.contains(id)) return true;
        if (loadout.size() >= capacity) return false;
        loadout.add(id);
        if (active) preset.setSelectedActive(loadout.size() - 1);
        return true;
    }

    public boolean removeFromLoadout(boolean active, int index) {
        SkillPreset preset = currentPreset();
        List<String> loadout = active ? preset.activeLoadout() : preset.passiveLoadout();
        if (index < 0 || index >= loadout.size()) return false;
        loadout.remove(index);
        if (active) {
            preset.setSelectedActive(loadout.isEmpty() ? 0 : Math.min(preset.selectedActive(), loadout.size() - 1));
        }
        return true;
    }

    // ===== 星座共鸣查询 =====

    public int constellationEquippedCount(SkillConstellation constellation) {
        SkillPreset preset = currentPreset();
        int count = 0;
        for (String id : preset.activeLoadout()) {
            SkillDefinition def = ModSkills.find(id).orElse(null);
            if (def != null && def.constellation() == constellation) count++;
        }
        for (String id : preset.passiveLoadout()) {
            SkillDefinition def = ModSkills.find(id).orElse(null);
            if (def != null && def.constellation() == constellation) count++;
        }
        return count;
    }

    /**
     * 返回指定星座当前实际生效的共鸣阶层：0=无，1=2件共鸣，2=4件共鸣。
     */
    public int resonanceTier(SkillConstellation constellation) {
        int maxAllowed = starRank().maxResonanceTier();
        if (maxAllowed <= 0) return 0;
        int equipped = constellationEquippedCount(constellation);
        if (equipped >= 4 && maxAllowed >= 2) return 2;
        if (equipped >= 2 && maxAllowed >= 1) return 1;
        return 0;
    }

    // ===== 免费洗点（级联保护星图前置拓扑，永久保留试炼进度与解锁资格） =====

    /**
     * 退还所选技能及其依赖子树的所有已投入技能点，返回被退点技能 ID 列表与总返还点数。
     */
    public record ResetResult(int refundedPoints, List<String> clearedIds) {}

    public ResetResult resetSkillCascade(String rootId) {
        if (!learned(rootId)) {
            return new ResetResult(0, List.of());
        }
        List<String> toClear = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(rootId);
        visited.add(rootId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!learned(cur)) continue;
            toClear.add(cur);
            for (SkillDefinition child : ModSkills.childrenOf(cur)) {
                if (learned(child.id()) && visited.add(child.id())) {
                    queue.add(child.id());
                }
            }
        }
        int refunded = 0;
        for (String id : toClear) {
            Integer lv = levels.remove(id);
            if (lv != null && lv > 0) {
                SkillDefinition def = ModSkills.find(id).orElse(null);
                if (def != null) {
                    for (int i = 1; i <= lv; i++) {
                        int cost = def.costAt(i);
                        if (cost != Integer.MAX_VALUE) refunded += cost;
                    }
                }
            }
            for (SkillPreset preset : presets) {
                preset.activeLoadout().remove(id);
                preset.passiveLoadout().remove(id);
                sanitizePreset(preset);
            }
        }
        skillPoints += refunded;
        return new ResetResult(refunded, List.copyOf(toClear));
    }

    public int resetSkill(String id) {
        return resetSkillCascade(id).refundedPoints();
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
        for (SkillPreset preset : presets) {
            preset.clear();
        }
    }

    // ===== 冷却 =====

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

    /** 冷却按墙上时钟毫秒保存，服务器停机后也会自然恢复。 */
    public void startCooldown(String id, int ticks) {
        if (ticks <= 0) {
            cooldownExpiry.remove(id);
            return;
        }
        cooldownExpiry.put(id, System.currentTimeMillis() + ticks * MILLIS_PER_TICK);
    }

    /** 按比例缩短所有正在冷却中的主动技能剩余冷却时间。 */
    public void reduceAllCooldownsByRatio(float reductionFraction) {
        if (reductionFraction <= 0) return;
        long now = System.currentTimeMillis();
        float keep = Math.max(0.0F, 1.0F - reductionFraction);
        for (Map.Entry<String, Long> entry : cooldownExpiry.entrySet()) {
            long rem = entry.getValue() - now;
            if (rem > 0) {
                entry.setValue(now + Math.round(rem * keep));
            }
        }
    }

    /** 缩短所有正在冷却中的主动技能固定秒数/毫秒。 */
    public void reduceAllCooldownsMillis(long millis) {
        if (millis <= 0) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : cooldownExpiry.entrySet()) {
            long rem = entry.getValue() - now;
            if (rem > 0) {
                entry.setValue(now + Math.max(0L, rem - millis));
            }
        }
    }

    /** 立即清零所有主动技能冷却。 */
    public void clearAllCooldowns() {
        cooldownExpiry.clear();
    }

    public Optional<SkillDefinition> selectedDefinition() {
        return ModSkills.find(selectedSkill());
    }

    // ===== NBT 序列化 =====

    public CompoundTag serialize() {
        ensurePresetsInitialized();
        CompoundTag tag = new CompoundTag();
        tag.putInt("ProgressionVersion", PROGRESSION_VERSION);
        tag.putInt("Level", level);
        tag.putInt("AwardedPoints", awardedPoints);
        tag.putLong("Experience", experience);
        tag.putLong("ExperienceAtLevelStart", experienceAtLevelStart);
        tag.putLong("ExperienceAtMaxLevel", experienceAtMaxLevel);
        tag.putInt("SkillPoints", skillPoints);
        tag.putInt("LifetimeExperience", lifetimeExperience);
        tag.putLong("LastCombatTick", lastCombatTick);
        tag.putInt("ActivePresetIndex", activePresetIndex);

        CompoundTag learned = new CompoundTag();
        levels.forEach(learned::putInt);
        tag.put("Levels", learned);

        ListTag presetList = new ListTag();
        for (SkillPreset preset : presets) {
            presetList.add(preset.serialize());
        }
        tag.put("Presets", presetList);

        tag.put("Discoveries", stringList(discoveries.stream().toList()));

        CompoundTag statsTag = new CompoundTag();
        trialStats.forEach(statsTag::putInt);
        tag.put("TrialStats", statsTag);

        CompoundTag cooldowns = new CompoundTag();
        cooldownExpiry.forEach(cooldowns::putLong);
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    /** 网络同步打包：附加服务端时钟与全局开关状态。 */
    public CompoundTag serializeForSync(long serverNowMillis, boolean systemEnabled) {
        CompoundTag tag = serialize();
        tag.putLong(SERVER_CLOCK_KEY, serverNowMillis);
        tag.putBoolean(SYSTEM_ENABLED_KEY, systemEnabled);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        deserialize(tag, System.currentTimeMillis());
    }

    public void deserialize(CompoundTag tag, long localNowMillis) {
        levels.clear();
        discoveries.clear();
        trialStats.clear();
        cooldownExpiry.clear();
        presets.clear();
        ensurePresetsInitialized();

        // v3 全新体系，无需迁移旧版本：若非空且版本号 < 3 则初始化为全新的 v3 默认状态
        if (tag == null || tag.isEmpty() || tag.getInt("ProgressionVersion") < PROGRESSION_VERSION) {
            level = INITIAL_LEVEL;
            awardedPoints = INITIAL_POINTS;
            experience = 0;
            experienceAtLevelStart = 0;
            experienceAtMaxLevel = 0;
            skillPoints = INITIAL_POINTS;
            lifetimeExperience = 0;
            lastCombatTick = 0;
            activePresetIndex = 0;
            return;
        }

        level = tag.contains("Level") ? Math.max(1, Math.min(MAX_LEVEL, tag.getInt("Level"))) : INITIAL_LEVEL;
        experience = Math.max(0, tag.getLong("Experience"));
        experienceAtLevelStart = Math.max(0, tag.getLong("ExperienceAtLevelStart"));
        experienceAtMaxLevel = Math.max(0, tag.getLong("ExperienceAtMaxLevel"));
        skillPoints = Math.max(0, tag.contains("SkillPoints") ? tag.getInt("SkillPoints") : INITIAL_POINTS);
        lifetimeExperience = Math.max(0, tag.getInt("LifetimeExperience"));
        lastCombatTick = tag.getLong("LastCombatTick");
        activePresetIndex = Math.max(0, Math.min(PRESET_COUNT - 1, tag.getInt("ActivePresetIndex")));

        if (tag.contains("Levels", Tag.TAG_COMPOUND)) {
            CompoundTag learned = tag.getCompound("Levels");
            for (String id : learned.getAllKeys()) {
                int value = learned.getInt(id);
                SkillDefinition def = ModSkills.find(id).orElse(null);
                if (def != null && value > 0) {
                    levels.put(id, Math.min(def.maxLevel(), value));
                }
            }
        }
        int spent = 0;
        for (var entry : levels.entrySet()) {
            SkillDefinition def = ModSkills.find(entry.getKey()).orElseThrow();
            for (int i = 1; i <= entry.getValue(); i++) spent += def.costAt(i);
        }
        awardedPoints = Math.max(skillPoints + spent, tag.getInt("AwardedPoints"));

        if (tag.contains("Presets", Tag.TAG_LIST)) {
            ListTag presetList = tag.getList("Presets", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(PRESET_COUNT, presetList.size()); i++) {
                presets.get(i).deserialize(presetList.getCompound(i), DEFAULT_PRESET_NAMES[i]);
            }
        }

        List<String> loadedDiscoveries = new ArrayList<>();
        readStrings(tag, "Discoveries", loadedDiscoveries);
        discoveries.addAll(loadedDiscoveries);

        if (tag.contains("TrialStats", Tag.TAG_COMPOUND)) {
            CompoundTag statsTag = tag.getCompound("TrialStats");
            for (String key : statsTag.getAllKeys()) {
                trialStats.put(key, Math.max(0, statsTag.getInt(key)));
            }
        }

        long clockOffset = tag.contains(SERVER_CLOCK_KEY) ? localNowMillis - tag.getLong(SERVER_CLOCK_KEY) : 0L;
        if (tag.contains("Cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cooldowns = tag.getCompound("Cooldowns");
            for (String id : cooldowns.getAllKeys()) {
                cooldownExpiry.put(id, cooldowns.getLong(id) + clockOffset);
            }
        }

        for (SkillPreset preset : presets) {
            sanitizePreset(preset);
        }
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
