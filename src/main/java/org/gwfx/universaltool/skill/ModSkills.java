package org.gwfx.universaltool.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill catalog.
 *
 * <p>Every skill has three levels. Costs are tuned so the full point budget at
 * {@link PlayerSkillData#MAX_LEVEL} exactly covers learning everything to max level:
 * 9 actives x (1+1+2) + 4 passives x (1+1+1) = 48 points.
 */
public final class ModSkills {
    public static final String COUNTER_GUARD = "counter_guard";
    public static final String GRAVITY_WELL = "gravity_well";
    public static final String FROZEN_PULL = "frozen_pull";
    public static final String OPPRESSIVE_FIELD = "oppressive_field";
    public static final String ORE_SIGHT = "ore_sight";
    public static final String AIR_STEP = "air_step";
    public static final String LIGHTNING_SPEAR = "lightning_spear";
    public static final String BERSERK = "berserk";

    public static final String BATTLE_MOMENTUM = "battle_momentum";
    public static final String FOCUS_MINING = "focus_mining";
    public static final String LAST_STAND = "last_stand";
    public static final String SURVIVAL_FLOW = "survival_flow";
    public static final String SWIFT_PACE = "swift_pace";
    public static final String LIGHT_FEATHER = "light_feather";
    public static final String IRON_PHYSIQUE = "iron_physique";
    public static final String ARMOR_PIERCING = "armor_piercing";
    public static final String BLOODTHIRST = "bloodthirst";
    public static final String ITEM_MAGNET = "item_magnet";

    public static final int MAX_SKILL_LEVEL = 3;

    private static final int[] ACTIVE_COSTS = {1, 1, 2};
    private static final int[] PASSIVE_COSTS = {1, 1, 1};

    private static final Map<String, SkillDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        active(COUNTER_GUARD, 120, 0, "unlock.counter_guard");
        active(GRAVITY_WELL, 180, 0, "unlock.gravity_well");
        active(FROZEN_PULL, 240, 0, "unlock.frozen_pull");
        active(OPPRESSIVE_FIELD, 300, 0, "unlock.oppressive_field");
        active(ORE_SIGHT, 600, 0, "unlock.ore_sight");
        active(AIR_STEP, 100, 0, "unlock.air_step");
        active(LIGHTNING_SPEAR, 180, 30, "unlock.lightning_spear");
        active(BERSERK, 360, 0, "unlock.berserk");

        passive(BATTLE_MOMENTUM, "unlock.battle_momentum");
        passive(FOCUS_MINING, "unlock.focus_mining");
        passive(LAST_STAND, "unlock.last_stand");
        passive(SURVIVAL_FLOW, "unlock.survival_flow");
        passive(SWIFT_PACE, "unlock.swift_pace");
        passive(LIGHT_FEATHER, "unlock.light_feather");
        passive(IRON_PHYSIQUE, "unlock.iron_physique");
        passive(ARMOR_PIERCING, "unlock.armor_piercing");
        passive(BLOODTHIRST, "unlock.bloodthirst");
        passive(ITEM_MAGNET, "unlock.item_magnet");
    }

    private static void active(String id, int cooldownTicks, int chargeTicks, String unlockKey) {
        DEFINITIONS.put(id, new SkillDefinition(id, SkillType.ACTIVE, MAX_SKILL_LEVEL, ACTIVE_COSTS,
                cooldownTicks, chargeTicks, unlockKey));
    }

    private static void passive(String id, String unlockKey) {
        DEFINITIONS.put(id, new SkillDefinition(id, SkillType.PASSIVE, MAX_SKILL_LEVEL, PASSIVE_COSTS,
                0, 0, unlockKey));
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

    /** Total points needed to learn every skill to max level. */
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
