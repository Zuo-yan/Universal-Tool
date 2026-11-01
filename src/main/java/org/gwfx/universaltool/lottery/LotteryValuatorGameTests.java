package org.gwfx.universaltool.lottery;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.init.ModItems;

@PrefixGameTestTemplate(false)
public final class LotteryValuatorGameTests {

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testNetheriteBlockEvaluation(GameTestHelper helper) {
        // 1. 单个下界合金块：必须直接达到传说 (LEGENDARY)
        ItemStack singleNetheriteBlock = new ItemStack(Items.NETHERITE_BLOCK, 1);
        long singlePoints = LotteryValuator.evaluate(singleNetheriteBlock);
        LotteryValuator.RarityTier singleTier = LotteryValuator.getTierFromPoints(singlePoints);

        helper.assertTrue(singlePoints >= 8000L,
                "Single netherite block must be at least 8000 points, got: " + singlePoints);
        helper.assertTrue(singleTier == LotteryValuator.RarityTier.LEGENDARY,
                "Single netherite block tier must be LEGENDARY, got: " + singleTier);

        // 2. 整组（64个）下界合金块：必须达到神话 (MYTHIC)
        ItemStack stackOfNetherite = new ItemStack(Items.NETHERITE_BLOCK, 64);
        long stackPoints = LotteryValuator.evaluate(stackOfNetherite);
        LotteryValuator.RarityTier stackTier = LotteryValuator.getTierFromPoints(stackPoints);

        helper.assertTrue(stackPoints >= 40000L,
                "64 netherite blocks must easily exceed 40000 mythic threshold, got: " + stackPoints);
        helper.assertTrue(stackTier == LotteryValuator.RarityTier.MYTHIC,
                "64 netherite blocks tier must be MYTHIC, got: " + stackTier);

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testLeatherLeggingsEvaluation(GameTestHelper helper) {
        // 皮革护腿：必须在普通 (COMMON) 档位
        ItemStack leatherLeggings = new ItemStack(Items.LEATHER_LEGGINGS, 1);
        long points = LotteryValuator.evaluate(leatherLeggings);
        LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(points);

        helper.assertTrue(points < 50L,
                "Leather leggings points should be strictly below 50, got: " + points);
        helper.assertTrue(tier == LotteryValuator.RarityTier.COMMON,
                "Leather leggings must be COMMON tier, got: " + tier);

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testTwentyDiamondBlocksEvaluation(GameTestHelper helper) {
        // 20个钻石块：必须评上传说 (LEGENDARY)
        ItemStack twentyDiamondBlocks = new ItemStack(Items.DIAMOND_BLOCK, 20);
        long points = LotteryValuator.evaluate(twentyDiamondBlocks);
        LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(points);

        helper.assertTrue(points >= 8000L,
                "20 diamond blocks must be at least 8000 points, got: " + points);
        helper.assertTrue(tier == LotteryValuator.RarityTier.LEGENDARY,
                "20 diamond blocks must qualify as LEGENDARY, got: " + tier);

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testMythicArtifacts(GameTestHelper helper) {
        // 龙蛋：单件直接达到神话 (MYTHIC)
        ItemStack dragonEgg = new ItemStack(Items.DRAGON_EGG, 1);
        long points = LotteryValuator.evaluate(dragonEgg);
        LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(points);

        helper.assertTrue(tier == LotteryValuator.RarityTier.MYTHIC,
                "Dragon egg must be MYTHIC, got: " + tier);

        // 下界之星：单件达到传说 (LEGENDARY)
        ItemStack netherStar = new ItemStack(Items.NETHER_STAR, 1);
        long starPoints = LotteryValuator.evaluate(netherStar);
        LotteryValuator.RarityTier starTier = LotteryValuator.getTierFromPoints(starPoints);

        helper.assertTrue(starTier == LotteryValuator.RarityTier.LEGENDARY,
                "Nether star must be LEGENDARY, got: " + starTier);

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testWheelModeSpin(GameTestHelper helper) {
        // 测试虚空轮盘模式
        // 扇区 3 为虚空神星 (Mythic 档位)，应产出高阶神物
        ItemStack mythicWheelReward = LotteryLootPool.rollWheel(3, 10000L, helper.getLevel().random, helper.getLevel());
        helper.assertTrue(!mythicWheelReward.isEmpty(), "Wheel mythic sector must return a reward");

        // 扇区 6 为重金属 (Uncommon 档位)
        ItemStack uncommonWheelReward = LotteryLootPool.rollWheel(6, 100L, helper.getLevel().random, helper.getLevel());
        helper.assertTrue(!uncommonWheelReward.isEmpty(), "Wheel uncommon sector must return a reward");

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testSlotModeReelPrizes(GameTestHelper helper) {
        // 测试老虎机模式
        // 1. 三连虚空神星 (0, 0, 0) -> 超级大奖 JACKPOT x10
        ItemStack jackpotReward = LotteryLootPool.rollSlot(0, 0, 0, 10.0F, 5000L, helper.getLevel().random, helper.getLevel());
        helper.assertTrue(!jackpotReward.isEmpty(), "Slot jackpot must return reward");

        // 2. 三连下界合金 (1, 1, 1) -> 5x
        ItemStack tripleNetheriteReward = LotteryLootPool.rollSlot(1, 1, 1, 5.0F, 1000L, helper.getLevel().random, helper.getLevel());
        helper.assertTrue(!tripleNetheriteReward.isEmpty(), "Slot triple netherite must return reward");

        // 3. 散牌安慰奖
        ItemStack consolation = LotteryLootPool.rollSlot(1, 3, 5, 0.7F, 100L, helper.getLevel().random, helper.getLevel());
        helper.assertTrue(!consolation.isEmpty(), "Slot consolation must return reward");

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 50)
    public static void testVoidScrapEvaluationAndDrop(GameTestHelper helper) {
        // 1. 虚空残片绝对不可估值进行抽奖 (点数必须为 0)
        ItemStack voidScrap = new ItemStack(ModItems.VOID_SCRAP.get(), 16);
        long scrapPoints = LotteryValuator.evaluate(voidScrap);
        helper.assertTrue(scrapPoints == 0L, "Void scrap must evaluate to 0 points, got: " + scrapPoints);

        // 2. 低价值献祭（普通品阶）进行多次抽奖，必须能抽到虚空残片安慰奖
        boolean receivedVoidScrap = false;
        for (int i = 0; i < 20; i++) {
            ItemStack reward = LotteryLootPool.roll(10L, helper.getLevel().random, helper.getLevel());
            if (reward.is(ModItems.VOID_SCRAP.get())) {
                receivedVoidScrap = true;
                break;
            }
        }
        helper.assertTrue(receivedVoidScrap, "Low tier roll must have high chance to yield void scrap");

        helper.succeed();
    }
}
