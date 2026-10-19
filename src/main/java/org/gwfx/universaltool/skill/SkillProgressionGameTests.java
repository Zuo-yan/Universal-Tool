package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.UUID;

/**
 * Server-side checks for the skill rules that are easy to get subtly wrong: per-level milestone
 * points, the budget/cost balance, respec refunds, offline cooldown recovery, slot expansion, and
 * the "placed then mined" exclusion.
 *
 * <p>Registered only by the isolated island game-test run configuration, matching the existing
 * private-space tests, so the normal client/server runs are unaffected.
 */
@PrefixGameTestTemplate(false)
public final class SkillProgressionGameTests {
    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void migrationAndDefense(GameTestHelper helper) {
        CompoundTag old = new CompoundTag();
        old.putInt("Level", 9);
        old.putLong("Experience", 175);
        old.putInt("SkillPoints", 11);
        PlayerSkillData data = new PlayerSkillData();
        data.deserialize(old);
        helper.assertTrue(data.experience() == 700, "Half-level progress must be preserved");
        data.deserialize(data.serialize());
        helper.assertTrue(data.experience() == 700, "Migration must run only once");
        old.putInt("Level", 40);
        old.putInt("SkillPoints", 62);
        old.putLong("ExperienceAtMaxLevel", 999999);
        data.deserialize(old);
        helper.assertTrue(data.level() == 40 && data.experience() == 0, "Old capped XP must not advance levels");
        data.addExperience(data.experienceForNextLevel());
        helper.assertTrue(data.skillPoints() == 62, "No duplicate point grants");
        data.addExperience(1_000_000);
        helper.assertTrue(data.level() == 50 && data.skillPoints() == 68, "Migrated graduation budget must be 68");
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(world.getServer(), world, "defense-test");
        var skills = SkillManager.data(player);
        skills.maximizeAllSkills();
        skills.addToLoadout(false, ModSkills.DIAMOND_BULWARK);
        SkillManager.applyPassiveEffects(player);
        helper.assertTrue(player.getArmorValue() == 20, "Rank three armor must be 20");
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) == 8, "Rank three toughness must be 8");
        skills.removeFromLoadout(false, 0);
        skills.addToLoadout(false, ModSkills.DAMAGE_CLAMP);
        SkillManager.applyPassiveEffects(player);
        helper.assertTrue(player.getArmorValue() == 0, "Unequip must remove armor");
        player.setInvulnerable(false);
        for (int tick = 0; tick < 65; tick++) player.tick();
        player.setHealth(20);
        player.hurt(world.damageSources().generic(), 100);
        helper.assertTrue(Math.abs(player.getHealth() - 14) < 0.01, "Actual damage must cap at six HP, health=" + player.getHealth());
        player.invulnerableTime = 0;
        player.hurt(world.damageSources().fellOutOfWorld(), 100);
        helper.assertTrue(player.getHealth() == 0, "Void must bypass cap");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void milestonePointsAreNotSkippedOnMultiLevelAward(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        // Level 1 -> 10 in one award must still pay the level-5 and level-10 milestone points.
        int needed = 0;
        for (int level = 1; level < 10; level++) {
            needed += 600 + (level - 1) * 100;
        }
        int gained = data.addExperience(needed);
        helper.assertTrue(gained == 9, "Award must cross exactly nine levels, got " + gained);
        helper.assertTrue(data.level() == 10, "Expected level 10, got " + data.level());
        int expected = PlayerSkillData.INITIAL_POINTS + 9 + PlayerSkillData.milestoneBonus(5) + PlayerSkillData.milestoneBonus(10);
        helper.assertTrue(data.skillPoints() == expected,
                "Milestones at 5 and 10 must both pay: expected " + expected + ", got " + data.skillPoints());
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void pointBudgetExactlyCoversEverySkill(GameTestHelper helper) {
        int budget = PlayerSkillData.totalPointBudget();
        int cost = ModSkills.totalCost();
        helper.assertTrue(budget == cost,
                "Max-level budget must equal the cost of learning everything: " + budget + " vs " + cost);
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void respecRefundsSpentPointsAndKeepsUnlocks(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        data.addExperience(4000);
        int before = data.skillPoints();
        data.addDiscovery("combat:hostile_kill");
        helper.assertTrue(data.unlockEligibleSkills(null) > 0, "A discovery must unlock its active skill");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.FROZEN_PULL), "Unlocked active must be learnable");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.FOCUS_MINING), "Passive must be learnable with points");
        helper.assertTrue(data.addToLoadout(true, ModSkills.FROZEN_PULL), "Learned active must equip");
        int spent = before - data.skillPoints();
        helper.assertTrue(spent > 0, "Learning must consume points");

        data.resetAllocations();
        helper.assertTrue(data.skillPoints() == before,
                "Respec must refund exactly what was spent: expected " + before + ", got " + data.skillPoints());
        helper.assertTrue(!data.learned(ModSkills.FROZEN_PULL), "Respec must clear learning");
        helper.assertTrue(data.activeLoadout().isEmpty(), "Respec must clear the active loadout");
        helper.assertTrue(data.isUnlocked(ModSkills.FROZEN_PULL),
                "Respec must keep the active skill's unlock qualification");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void resetOnlyReturnsSelectedSkillsSpentPoints(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        data.addExperience(4_000);
        int before = data.skillPoints();
        helper.assertTrue(data.learnOrUpgrade(ModSkills.FOCUS_MINING), "First passive should be learnable");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.SURVIVAL_FLOW), "Second passive should be learnable");
        int refunded = data.resetSkill(ModSkills.FOCUS_MINING);
        helper.assertTrue(refunded > 0, "Selected skill should refund points");
        helper.assertTrue(!data.learned(ModSkills.FOCUS_MINING), "Selected skill must be cleared");
        helper.assertTrue(data.learned(ModSkills.SURVIVAL_FLOW), "Other learned skills must remain");
        int remainingSpent = ModSkills.find(ModSkills.SURVIVAL_FLOW)
                .orElseThrow().costAt(1);
        helper.assertTrue(data.skillPoints() == before - remainingSpent,
                "Only the selected skill may change the point balance");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void cooldownsRecoverAcrossOfflineTime(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        long now = System.currentTimeMillis();
        data.startCooldown(ModSkills.BERSERK, 200);
        helper.assertTrue(data.onCooldown(ModSkills.BERSERK), "A fresh cooldown must be active");

        // Persisted expiry must be an absolute wall-clock stamp, not a game-tick counter, which is
        // what lets the cooldown keep recovering while the server is stopped.
        CompoundTag saved = data.serialize();
        long stored = saved.getCompound("Cooldowns").getLong(ModSkills.BERSERK);
        long expected = now + 200 * PlayerSkillData.MILLIS_PER_TICK;
        helper.assertTrue(Math.abs(stored - expected) < 5_000,
                "Cooldown must be stored as absolute wall-clock time, got " + stored);

        // Reloading the save preserves the running cooldown.
        PlayerSkillData reloaded = new PlayerSkillData();
        reloaded.deserialize(saved);
        helper.assertTrue(reloaded.onCooldown(ModSkills.BERSERK),
                "Cooldown must survive a save/load cycle");

        // A stamp that already elapsed in real time is no longer on cooldown, even though no game
        // ticks advanced in between: this is the offline-recovery guarantee.
        CompoundTag elapsed = new CompoundTag();
        CompoundTag cooldowns = new CompoundTag();
        cooldowns.putLong(ModSkills.BERSERK, System.currentTimeMillis() - 1);
        elapsed.put("Cooldowns", cooldowns);
        PlayerSkillData recovered = new PlayerSkillData();
        recovered.deserialize(elapsed);
        helper.assertTrue(!recovered.onCooldown(ModSkills.BERSERK),
                "A cooldown whose wall-clock window elapsed must be recovered");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void activeSlotsExpandAtLevelTen(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        helper.assertTrue(data.activeSlotCount() == PlayerSkillData.BASE_ACTIVE_SLOTS,
                "Below level 10 the active slots must stay at the base count");
        data.addExperience(1_000_000);
        helper.assertTrue(data.level() == PlayerSkillData.MAX_LEVEL,
                "A large award must reach the cap");
        helper.assertTrue(data.activeSlotCount() == PlayerSkillData.MAX_ACTIVE_SLOTS,
                "At level 10 and above the active slots must expand to the maximum");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void maxCommandFillsEveryFormalSkill(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        int changed = data.maximizeAllSkills();
        helper.assertTrue(changed >= ModSkills.all().size(), "Max command must fill every formal skill");
        helper.assertTrue(data.level() == PlayerSkillData.MAX_LEVEL,
                "Max command must set growth to the level cap");
        helper.assertTrue(data.activeSlotCount() == PlayerSkillData.MAX_ACTIVE_SLOTS,
                "Max command must open every active slot");
        helper.assertTrue(data.passiveSlotCount() == PlayerSkillData.MAX_PASSIVE_SLOTS,
                "Max command must open every passive slot");
        helper.assertTrue(data.skillPoints() == 0,
                "The complete point budget must be allocated to the maxed skills");
        helper.assertTrue(data.activeLoadout().isEmpty() && data.passiveLoadout().isEmpty(),
                "Max command must leave loadouts empty for the player to rebuild");
        for (SkillDefinition definition : ModSkills.all()) {
            helper.assertTrue(data.learnedLevel(definition.id()) == definition.maxLevel(),
                    "Skill was not maxed: " + definition.id());
            helper.assertTrue(data.isUnlocked(definition.id()),
                    "Skill unlock was not preserved: " + definition.id());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void ironPhysiqueDoublesMaxHealth(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerPlayer player = mockPlayer(server, overworld, "physique-test");
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();
        data.addToLoadout(false, ModSkills.IRON_PHYSIQUE);
        SkillManager.applyPassiveEffects(player);
        helper.assertTrue(player.getMaxHealth() == 40.0F,
                "Iron Physique at level 3 must set max health to 40.0, got " + player.getMaxHealth());
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 200)
    public static void placedBlocksDoNotGrantMiningExperience(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerPlayer player = mockPlayer(server, overworld, "placed-block-test");
        PlayerSkillData data = SkillManager.data(player);
        long before = data.experience() + data.lifetimeExperience();

        BlockPos pos = new BlockPos(64, 100, 64);
        BlockState stone = Blocks.STONE.defaultBlockState();
        overworld.setBlockAndUpdate(pos, stone);
        BlockSnapshot snapshot = BlockSnapshot.create(ResourceKey.create(Registries.DIMENSION,
                Level.OVERWORLD.location()), overworld, pos);
        SkillManager.onBlockPlaced(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), player));

        // Re-mining the block this player just placed must not count as growth experience.
        SkillManager.onBlockBreak(new BlockEvent.BreakEvent(overworld, pos, stone, player));
        long afterPlaced = data.experience() + data.lifetimeExperience();
        helper.assertTrue(afterPlaced == before,
                "Placing and then mining the same block must not grant experience: " + before + " -> " + afterPlaced);

        // A naturally generated ore at a different position still counts.
        BlockPos ore = pos.offset(4, 0, 0);
        overworld.setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
        SkillManager.onBlockBreak(new BlockEvent.BreakEvent(overworld, ore,
                Blocks.DIAMOND_ORE.defaultBlockState(), player));
        long afterOre = data.experience() + data.lifetimeExperience();
        helper.assertTrue(afterOre > afterPlaced, "Mining an untouched ore must grant experience");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void oppressiveFieldMinimumAndGrowth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level.getServer(), level, "field-growth");
        BlockPos origin = helper.absolutePos(new BlockPos(1, 3, 1));
        player.setPos(origin.getX(), origin.getY(), origin.getZ());
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();
        data.resetSkill(ModSkills.OPPRESSIVE_FIELD);
        data.learnOrUpgrade(ModSkills.OPPRESSIVE_FIELD);
        data.addToLoadout(true, ModSkills.OPPRESSIVE_FIELD);
        var target = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        helper.assertTrue(target != null, "Zombie must exist");
        target.setPos(player.getX() + 11.5, player.getY(), player.getZ());
        level.addFreshEntity(target);
        SkillManager.castSelected(player);
        var weakness = target.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        helper.assertTrue(weakness != null && weakness.getDuration() >= 120 && weakness.getAmplifier() == 2,
                "Rank 1 must apply Weakness III for 6 seconds at 11.5 blocks");
        target.discard();
        helper.assertTrue(SkillManager.oppressiveRadius(3) == 18
                && SkillManager.oppressiveDurationTicks(3) == 200, "Rank 3 must reach 18 blocks and 10 seconds");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void airStepKeepsPropellingUntilRelease(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level.getServer(), level, "air-hold");
        player.setPos(helper.absolutePos(new BlockPos(1, 8, 1)).getCenter());
        player.setOnGround(false);
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();
        data.addToLoadout(true, ModSkills.AIR_STEP);
        SkillManager.handleCast(player, false);
        helper.assertTrue(!data.onCooldown(ModSkills.AIR_STEP), "Holding must not start cooldown early");
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        SkillManager.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        helper.assertTrue(player.getDeltaMovement().length() > 0.5, "Hold tick must replenish thrust");
        SkillManager.handleCast(player, true);
        helper.assertTrue(data.onCooldown(ModSkills.AIR_STEP), "Release must start cooldown");
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        SkillManager.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        helper.assertTrue(player.getDeltaMovement().length() == 0, "Released skill must stop adding thrust");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void airStepStopsAtRankTimeLimit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level.getServer(), level, "air-timeout");
        player.setPos(helper.absolutePos(new BlockPos(1, 8, 1)).getCenter());
        player.setOnGround(false);
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();
        data.addToLoadout(true, ModSkills.AIR_STEP);
        SkillManager.handleCast(player, false);
        helper.runAfterDelay(61, () -> {
            SkillManager.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
            helper.assertTrue(data.onCooldown(ModSkills.AIR_STEP), "Rank 3 must auto-finish after 60 ticks");
            helper.succeed();
        });
    }

    private static ServerPlayer mockPlayer(MinecraftServer server, ServerLevel level, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(server, level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection,
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
            }
        };
        return player;
    }

    private SkillProgressionGameTests() {
    }
}
