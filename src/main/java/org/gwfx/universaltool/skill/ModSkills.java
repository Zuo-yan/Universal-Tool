package org.gwfx.universaltool.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能目录与星图拓扑定义（4 星座 × 8 星 = 32 技能：14 主动 + 18 被动）。
 *
 * <p>每个技能均为 3 级（Lv.3 解锁星辉觉醒质变）。
 * 满级（50 级）总预算 = 110 点，恰好等于全部 32 技能升满所需：14 × 4 + 18 × 3 = 110。
 */
public final class ModSkills {
    // ===== 破军 · 战魂 (POJUN) =====
    public static final String BATTLE_MOMENTUM = "battle_momentum";
    public static final String BLOODTHIRST = "bloodthirst";
    public static final String ARMOR_PIERCING = "armor_piercing";
    public static final String ASTRAL_CLEAVE = "astral_cleave";
    public static final String TOOL_RESONANCE = "tool_resonance";
    public static final String BERSERK = "berserk";
    public static final String LIGHTNING_SPEAR = "lightning_spear";
    public static final String THUNDER_JUDGMENT = "thunder_judgment";

    // ===== 玄武 · 壁垒 (XUANWU) =====
    public static final String IRON_PHYSIQUE = "iron_physique";
    public static final String SURVIVAL_FLOW = "survival_flow";
    public static final String COUNTER_GUARD = "counter_guard";
    public static final String LAST_STAND = "last_stand";
    public static final String DIAMOND_BULWARK = "diamond_bulwark";
    public static final String STARLIGHT_SANCTUARY = "starlight_sanctuary";
    public static final String DAMAGE_CLAMP = "damage_clamp";
    public static final String PHOENIX_EMBER = "phoenix_ember";

    // ===== 天枢 · 星移 (TIANSHU) =====
    public static final String SWIFT_PACE = "swift_pace";
    public static final String LIGHT_FEATHER = "light_feather";
    public static final String AIR_STEP = "air_step";
    public static final String GRAVITY_WELL = "gravity_well";
    public static final String FROZEN_PULL = "frozen_pull";
    public static final String OPPRESSIVE_FIELD = "oppressive_field";
    public static final String SPATIAL_ATTUNEMENT = "spatial_attunement";
    public static final String VOID_BLINK = "void_blink";

    // ===== 巧匠 · 秘工 (QIAOJIANG) =====
    public static final String FOCUS_MINING = "focus_mining";
    public static final String ITEM_MAGNET = "item_magnet";
    public static final String ORE_SIGHT = "ore_sight";
    public static final String EXPLORERS_EYE = "explorers_eye";
    public static final String VEIN_RESONANCE = "vein_resonance";
    public static final String ARCANE_TRANSMUTE = "arcane_transmute";
    public static final String HYPER_CONDUIT = "hyper_conduit";
    public static final String VOID_FORTUNE = "void_fortune";

    public static final int MAX_SKILL_LEVEL = 3;

    private static final int[] ACTIVE_COSTS = {1, 1, 2};
    private static final int[] PASSIVE_COSTS = {1, 1, 1};

    private static final Map<String, SkillDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        // ---------- 破军 · 战魂（右上象限） ----------
        passive(BATTLE_MOMENTUM, SkillConstellation.POJUN, SkillTier.INITIATE, "",
                SkillTrialSpec.of("kills_hostile", 3, "trial.universal_tool.battle_momentum"),
                78, -54);
        passive(BLOODTHIRST, SkillConstellation.POJUN, SkillTier.INTERMEDIATE, BATTLE_MOMENTUM,
                SkillTrialSpec.of("damage_dealt", 120, "trial.universal_tool.bloodthirst"),
                148, -42);
        passive(ARMOR_PIERCING, SkillConstellation.POJUN, SkillTier.INTERMEDIATE, BATTLE_MOMENTUM,
                SkillTrialSpec.of("kills_hostile", 15, "trial.universal_tool.armor_piercing"),
                104, -116);
        active(ASTRAL_CLEAVE, SkillConstellation.POJUN, SkillTier.INTERMEDIATE, ARMOR_PIERCING,
                100, 0,
                SkillTrialSpec.dual("damage_dealt", 300, "universal_tool_hits", 25, "trial.universal_tool.astral_cleave"),
                172, -112);
        passive(TOOL_RESONANCE, SkillConstellation.POJUN, SkillTier.ADVANCED, BLOODTHIRST,
                SkillTrialSpec.dual("universal_tool_hits", 40, "kills_hostile", 30, "trial.universal_tool.tool_resonance"),
                222, -52);
        active(BERSERK, SkillConstellation.POJUN, SkillTier.ADVANCED, ASTRAL_CLEAVE,
                360, 0,
                SkillTrialSpec.dual("combat_multi_kills", 2, "kills_hostile", 40, "trial.universal_tool.berserk"),
                146, -176);
        active(LIGHTNING_SPEAR, SkillConstellation.POJUN, SkillTier.ADVANCED, ASTRAL_CLEAVE,
                180, 30,
                SkillTrialSpec.of("ranged_or_skill_kills", 12, "trial.universal_tool.lightning_spear"),
                244, -138);
        active(THUNDER_JUDGMENT, SkillConstellation.POJUN, SkillTier.ULTIMATE, LIGHTNING_SPEAR,
                280, 0,
                SkillTrialSpec.dual("boss_kills", 1, "kills_hostile", 80, "trial.universal_tool.thunder_judgment"),
                274, -206);

        // ---------- 玄武 · 壁垒（右下象限） ----------
        passive(IRON_PHYSIQUE, SkillConstellation.XUANWU, SkillTier.INITIATE, "",
                SkillTrialSpec.of("damage_taken", 20, "trial.universal_tool.iron_physique"),
                78, 54);
        passive(SURVIVAL_FLOW, SkillConstellation.XUANWU, SkillTier.INTERMEDIATE, IRON_PHYSIQUE,
                SkillTrialSpec.dual("crops_harvested", 12, "damage_taken", 60, "trial.universal_tool.survival_flow"),
                148, 42);
        active(COUNTER_GUARD, SkillConstellation.XUANWU, SkillTier.INTERMEDIATE, IRON_PHYSIQUE,
                120, 0,
                SkillTrialSpec.dual("counter_kills", 3, "damage_taken", 80, "trial.universal_tool.counter_guard"),
                104, 116);
        passive(LAST_STAND, SkillConstellation.XUANWU, SkillTier.INTERMEDIATE, SURVIVAL_FLOW,
                SkillTrialSpec.dual("low_health_survives", 2, "damage_taken", 150, "trial.universal_tool.last_stand"),
                214, 56);
        passive(DIAMOND_BULWARK, SkillConstellation.XUANWU, SkillTier.ADVANCED, COUNTER_GUARD,
                SkillTrialSpec.of("damage_taken", 220, "trial.universal_tool.diamond_bulwark"),
                172, 116);
        active(STARLIGHT_SANCTUARY, SkillConstellation.XUANWU, SkillTier.ADVANCED, LAST_STAND,
                320, 0,
                SkillTrialSpec.dual("damage_taken", 320, "phoenix_rebirths", 1, "trial.universal_tool.starlight_sanctuary"),
                252, 116);
        passive(DAMAGE_CLAMP, SkillConstellation.XUANWU, SkillTier.ADVANCED, DIAMOND_BULWARK,
                SkillTrialSpec.dual("heavy_hits_survived", 3, "damage_taken", 400, "trial.universal_tool.damage_clamp"),
                148, 182);
        passive(PHOENIX_EMBER, SkillConstellation.XUANWU, SkillTier.ULTIMATE, STARLIGHT_SANCTUARY,
                SkillTrialSpec.dual("phoenix_rebirths", 1, "damage_taken", 600, "trial.universal_tool.phoenix_ember"),
                274, 196);

        // ---------- 天枢 · 星移（左上象限） ----------
        passive(SWIFT_PACE, SkillConstellation.TIANSHU, SkillTier.INITIATE, "",
                SkillTrialSpec.of("biomes_discovered", 2, "trial.universal_tool.swift_pace"),
                -78, -54);
        passive(LIGHT_FEATHER, SkillConstellation.TIANSHU, SkillTier.INTERMEDIATE, SWIFT_PACE,
                SkillTrialSpec.dual("fall_damage_taken", 12, "high_place_reached", 1, "trial.universal_tool.light_feather"),
                -148, -42);
        active(AIR_STEP, SkillConstellation.TIANSHU, SkillTier.INTERMEDIATE, SWIFT_PACE,
                100, 0,
                SkillTrialSpec.of("high_place_reached", 1, "trial.universal_tool.air_step"),
                -104, -116);
        active(GRAVITY_WELL, SkillConstellation.TIANSHU, SkillTier.INTERMEDIATE, AIR_STEP,
                180, 0,
                SkillTrialSpec.of("nearby_crowd_fights", 3, "trial.universal_tool.gravity_well"),
                -172, -112);
        active(FROZEN_PULL, SkillConstellation.TIANSHU, SkillTier.ADVANCED, GRAVITY_WELL,
                240, 0,
                SkillTrialSpec.of("kills_hostile", 25, "trial.universal_tool.frozen_pull"),
                -146, -176);
        active(OPPRESSIVE_FIELD, SkillConstellation.TIANSHU, SkillTier.ADVANCED, GRAVITY_WELL,
                300, 0,
                SkillTrialSpec.of("nearby_crowd_fights", 8, "trial.universal_tool.oppressive_field"),
                -234, -130);
        passive(SPATIAL_ATTUNEMENT, SkillConstellation.TIANSHU, SkillTier.ADVANCED, LIGHT_FEATHER,
                SkillTrialSpec.dual("teleports", 3, "biomes_discovered", 6, "trial.universal_tool.spatial_attunement"),
                -222, -52);
        active(VOID_BLINK, SkillConstellation.TIANSHU, SkillTier.ULTIMATE, SPATIAL_ATTUNEMENT,
                140, 0,
                SkillTrialSpec.dual("pocket_realm_visits", 1, "dimensions_visited", 2, "trial.universal_tool.void_blink"),
                -274, -198);

        // ---------- 巧匠 · 秘工（左下象限） ----------
        passive(FOCUS_MINING, SkillConstellation.QIAOJIANG, SkillTier.INITIATE, "",
                SkillTrialSpec.of("blocks_mined", 20, "trial.universal_tool.focus_mining"),
                -78, 54);
        passive(ITEM_MAGNET, SkillConstellation.QIAOJIANG, SkillTier.INTERMEDIATE, FOCUS_MINING,
                SkillTrialSpec.of("blocks_mined", 80, "trial.universal_tool.item_magnet"),
                -148, 42);
        active(ORE_SIGHT, SkillConstellation.QIAOJIANG, SkillTier.INTERMEDIATE, FOCUS_MINING,
                600, 0,
                SkillTrialSpec.of("ores_mined", 12, "trial.universal_tool.ore_sight"),
                -104, 116);
        passive(EXPLORERS_EYE, SkillConstellation.QIAOJIANG, SkillTier.INTERMEDIATE, ITEM_MAGNET,
                SkillTrialSpec.dual("structures_discovered", 1, "biomes_discovered", 4, "trial.universal_tool.explorers_eye"),
                -214, 56);
        active(VEIN_RESONANCE, SkillConstellation.QIAOJIANG, SkillTier.ADVANCED, ORE_SIGHT,
                240, 0,
                SkillTrialSpec.of("ores_mined", 50, "trial.universal_tool.vein_resonance"),
                -172, 116);
        active(ARCANE_TRANSMUTE, SkillConstellation.QIAOJIANG, SkillTier.ADVANCED, VEIN_RESONANCE,
                200, 0,
                SkillTrialSpec.dual("polymerizer_crafts", 2, "ores_mined", 100, "trial.universal_tool.arcane_transmute"),
                -148, 182);
        passive(HYPER_CONDUIT, SkillConstellation.QIAOJIANG, SkillTier.ADVANCED, EXPLORERS_EYE,
                SkillTrialSpec.dual("klein_pickups", 1, "blocks_mined", 250, "trial.universal_tool.hyper_conduit"),
                -252, 116);
        passive(VOID_FORTUNE, SkillConstellation.QIAOJIANG, SkillTier.ULTIMATE, HYPER_CONDUIT,
                SkillTrialSpec.dual("lottery_rolls", 3, "ores_mined", 180, "trial.universal_tool.void_fortune"),
                -274, 196);
    }

    private static void active(String id, SkillConstellation constellation, SkillTier tier,
                               String prerequisiteId, int cooldownTicks, int chargeTicks,
                               SkillTrialSpec trial, float mapX, float mapY) {
        DEFINITIONS.put(id, new SkillDefinition(id, constellation, SkillType.ACTIVE, tier,
                MAX_SKILL_LEVEL, ACTIVE_COSTS, cooldownTicks, chargeTicks, prerequisiteId, trial, mapX, mapY));
    }

    private static void passive(String id, SkillConstellation constellation, SkillTier tier,
                                String prerequisiteId, SkillTrialSpec trial, float mapX, float mapY) {
        DEFINITIONS.put(id, new SkillDefinition(id, constellation, SkillType.PASSIVE, tier,
                MAX_SKILL_LEVEL, PASSIVE_COSTS, 0, 0, prerequisiteId, trial, mapX, mapY));
    }

    public static Optional<SkillDefinition> find(String id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static Collection<SkillDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static Collection<SkillDefinition> actives() {
        return DEFINITIONS.values().stream().filter(skill -> skill.type() == SkillType.ACTIVE).toList();
    }

    public static Collection<SkillDefinition> passives() {
        return DEFINITIONS.values().stream().filter(skill -> skill.type() == SkillType.PASSIVE).toList();
    }

    public static List<SkillDefinition> byConstellation(SkillConstellation constellation) {
        return DEFINITIONS.values().stream().filter(skill -> skill.constellation() == constellation).toList();
    }

    /** 查找直接依赖指定技能的下游子节点列表。 */
    public static List<SkillDefinition> childrenOf(String skillId) {
        List<SkillDefinition> children = new ArrayList<>();
        for (SkillDefinition def : DEFINITIONS.values()) {
            if (skillId.equals(def.prerequisiteId())) {
                children.add(def);
            }
        }
        return children;
    }

    /** 将全部 32 技能升到满级所需总点数（110 点）。 */
    public static int totalCost() {
        int total = 0;
        for (SkillDefinition definition : DEFINITIONS.values()) {
            for (int level = 1; level <= definition.maxLevel(); level++) {
                total += definition.costAt(level);
            }
        }
        return total;
    }

    private ModSkills() {
    }
}
