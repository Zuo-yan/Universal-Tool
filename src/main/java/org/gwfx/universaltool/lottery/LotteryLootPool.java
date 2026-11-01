package org.gwfx.universaltool.lottery;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.gwfx.universaltool.init.ModItems;

import java.util.*;

public final class LotteryLootPool {

    private static final Set<String> BLACKLIST_KEYWORDS = Set.of(
            "command_block", "barrier", "structure_void", "structure_block",
            "jigsaw", "debug_stick", "light", "bundle", "test", "creative",
            "dummy", "air", "marker", "operator", "bedrock", "end_portal",
            "spawner", "reinforced_deepslate"
    );

    private static final List<Item> COMMON_POOL = new ArrayList<>();
    private static final List<Item> UNCOMMON_POOL = new ArrayList<>();
    private static final List<Item> RARE_POOL = new ArrayList<>();
    private static final List<Item> EPIC_POOL = new ArrayList<>();
    private static final List<Item> MYTHIC_POOL = new ArrayList<>();

    private static boolean initialized = false;

    private LotteryLootPool() {}

    public static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        COMMON_POOL.clear();
        UNCOMMON_POOL.clear();
        RARE_POOL.clear();
        EPIC_POOL.clear();
        MYTHIC_POOL.clear();

        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || isBlacklisted(id, item)) {
                continue;
            }

            ItemStack dummyStack = new ItemStack(item);
            long unitValue = LotteryValuator.getUnitValue(dummyStack);

            if (unitValue >= LotteryValuator.RarityTier.LEGENDARY.getMinPoints()) {
                MYTHIC_POOL.add(item);
            } else if (unitValue >= LotteryValuator.RarityTier.EPIC.getMinPoints()) {
                EPIC_POOL.add(item);
            } else if (unitValue >= LotteryValuator.RarityTier.RARE.getMinPoints()) {
                RARE_POOL.add(item);
            } else if (unitValue >= LotteryValuator.RarityTier.UNCOMMON.getMinPoints()) {
                UNCOMMON_POOL.add(item);
            } else {
                COMMON_POOL.add(item);
            }
        }

        // 兜底保障
        if (COMMON_POOL.isEmpty()) COMMON_POOL.add(Items.COBBLESTONE);
        if (UNCOMMON_POOL.isEmpty()) UNCOMMON_POOL.add(Items.IRON_INGOT);
        if (RARE_POOL.isEmpty()) RARE_POOL.add(Items.DIAMOND_BLOCK);
        if (EPIC_POOL.isEmpty()) EPIC_POOL.add(Items.NETHERITE_INGOT);
        if (MYTHIC_POOL.isEmpty()) MYTHIC_POOL.add(Items.NETHERITE_BLOCK);

        initialized = true;
    }

    public static synchronized void invalidate() {
        initialized = false;
    }

    private static boolean isBlacklisted(ResourceLocation id, Item item) {
        if (item == Items.AIR) return true;
        String path = id.getPath().toLowerCase();
        for (String kw : BLACKLIST_KEYWORDS) {
            if (path.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 核心抽奖结算逻辑
     * 遵循期望返还率与投入品质保底
     */
    public static ItemStack roll(long totalPointsSacrificed, RandomSource random, ServerLevel level) {
        ensureInitialized();

        if (totalPointsSacrificed <= 0) {
            return new ItemStack(ModItems.VOID_SCRAP.get(), 1);
        }

        // 1. 目标返还价值：期望为投入点数的 70%，带 ±25% 的自然浮动
        double variance = 0.75 + random.nextDouble() * 0.5; // 0.75 ~ 1.25
        long targetValue = Math.max(1L, Math.round(totalPointsSacrificed * 0.70 * variance));

        // 2. 超级暴击判定 (2% 几率直接翻 2.5 倍)
        boolean jackpot = random.nextFloat() < 0.02f;
        if (jackpot) {
            targetValue = (long) (targetValue * 2.5);
        }

        // 3. 决定抽取的奖品品阶（附带投入品阶的向下兜底保底机制）
        LotteryValuator.RarityTier inputTier = LotteryValuator.getTierFromPoints(totalPointsSacrificed);
        int odds = LotteryValuator.getOddsPercent(totalPointsSacrificed);
        int rollScore = random.nextInt(100);

        List<Item> chosenPool;
        LotteryValuator.RarityTier rolledTier;

        // 根据投入品阶与随机暴击裁定产出品质
        if (jackpot || inputTier == LotteryValuator.RarityTier.MYTHIC) {
            // 投入神话：极大概率出神话，保底史诗
            if (rollScore < 60 && !MYTHIC_POOL.isEmpty()) {
                chosenPool = MYTHIC_POOL;
                rolledTier = LotteryValuator.RarityTier.MYTHIC;
            } else {
                chosenPool = !EPIC_POOL.isEmpty() ? EPIC_POOL : MYTHIC_POOL;
                rolledTier = LotteryValuator.RarityTier.EPIC;
            }
        } else if (inputTier == LotteryValuator.RarityTier.LEGENDARY) {
            // 投入传说：高概率出神话/史诗，保底稀有
            if (rollScore < 30 && !MYTHIC_POOL.isEmpty()) {
                chosenPool = MYTHIC_POOL;
                rolledTier = LotteryValuator.RarityTier.MYTHIC;
            } else if (rollScore < 80 && !EPIC_POOL.isEmpty()) {
                chosenPool = EPIC_POOL;
                rolledTier = LotteryValuator.RarityTier.EPIC;
            } else {
                chosenPool = !RARE_POOL.isEmpty() ? RARE_POOL : EPIC_POOL;
                rolledTier = LotteryValuator.RarityTier.RARE;
            }
        } else if (inputTier == LotteryValuator.RarityTier.EPIC) {
            // 投入史诗：概率冲传说/史诗，保底罕见
            if (rollScore < (odds / 3) && !MYTHIC_POOL.isEmpty()) {
                chosenPool = MYTHIC_POOL;
                rolledTier = LotteryValuator.RarityTier.MYTHIC;
            } else if (rollScore < odds && !EPIC_POOL.isEmpty()) {
                chosenPool = EPIC_POOL;
                rolledTier = LotteryValuator.RarityTier.EPIC;
            } else if (!RARE_POOL.isEmpty()) {
                chosenPool = RARE_POOL;
                rolledTier = LotteryValuator.RarityTier.RARE;
            } else {
                chosenPool = UNCOMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.UNCOMMON;
            }
        } else if (inputTier == LotteryValuator.RarityTier.RARE) {
            // 投入稀有
            if (rollScore < odds && !RARE_POOL.isEmpty()) {
                chosenPool = RARE_POOL;
                rolledTier = LotteryValuator.RarityTier.RARE;
            } else if (!UNCOMMON_POOL.isEmpty()) {
                chosenPool = UNCOMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.UNCOMMON;
            } else {
                chosenPool = COMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.COMMON;
            }
        } else if (inputTier == LotteryValuator.RarityTier.UNCOMMON) {
            if (rollScore < odds && !UNCOMMON_POOL.isEmpty()) {
                chosenPool = UNCOMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.UNCOMMON;
            } else {
                chosenPool = COMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.COMMON;
            }
        } else {
            // 普通品阶
            if (rollScore < odds && !UNCOMMON_POOL.isEmpty()) {
                chosenPool = UNCOMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.UNCOMMON;
            } else {
                chosenPool = COMMON_POOL;
                rolledTier = LotteryValuator.RarityTier.COMMON;
            }
        }

        if (chosenPool.isEmpty()) {
            chosenPool = COMMON_POOL;
            rolledTier = LotteryValuator.RarityTier.COMMON;
        }

        // 4. 品阶过低（普通档）时，有较大几率（65%）产出虚空残片安慰奖
        if (rolledTier == LotteryValuator.RarityTier.COMMON && random.nextFloat() < 0.65f) {
            int scrapCount = random.nextInt(3) + 1;
            return new ItemStack(ModItems.VOID_SCRAP.get(), scrapCount);
        }

        Item chosenItem = chosenPool.get(random.nextInt(chosenPool.size()));
        ItemStack resultStack = new ItemStack(chosenItem);

        // 5. 可堆叠物品数量换算 (遵循返还总价值)
        if (resultStack.getMaxStackSize() > 1) {
            long singleValue = LotteryValuator.getUnitValue(resultStack);
            int count = (int) Math.max(1L, Math.min(resultStack.getMaxStackSize(), targetValue / Math.max(1L, singleValue)));
            resultStack.setCount(count);
        } else if (resultStack.isEnchantable() && level != null) {
            // 5. 装备附魔增强
            if (rolledTier == LotteryValuator.RarityTier.EPIC || rolledTier == LotteryValuator.RarityTier.MYTHIC) {
                tryEnchant(resultStack, random, level, 30);
            } else if (rolledTier == LotteryValuator.RarityTier.RARE) {
                tryEnchant(resultStack, random, level, 15);
            }
        }

        return resultStack;
    }

    /**
     * 虚空骰子核心开奖逻辑 (基于真实点数 1~6)
     */
    public static ItemStack rollDice(int targetNumber, long totalPointsSacrificed, RandomSource random, ServerLevel level) {
        ensureInitialized();
        if (totalPointsSacrificed <= 0) {
            return new ItemStack(ModItems.VOID_SCRAP.get(), 1);
        }

        // 1 点：大失败 (Fumble) -> 投入被虚空吞噬，仅掉落少量虚空残片
        if (targetNumber <= 1) {
            int scrapCount = 1 + random.nextInt(3);
            return new ItemStack(ModItems.VOID_SCRAP.get(), scrapCount);
        }

        // 2~6 点对应阶梯倍率
        double multiplier = switch (targetNumber) {
            case 2 -> 0.40; // 折损
            case 3 -> 0.80; // 保本略折
            case 4 -> 1.25; // 小赚 25%
            case 5 -> 1.80; // 丰厚 80%
            case 6 -> 2.80; // 超级暴击 (CRITICAL HIT!)
            default -> 1.0;
        };

        // 自然浮动 ±15%
        multiplier *= (0.85 + random.nextDouble() * 0.30);
        long targetValue = Math.max(1L, Math.round(totalPointsSacrificed * multiplier));

        LotteryValuator.RarityTier inputTier = LotteryValuator.getTierFromPoints(totalPointsSacrificed);
        LotteryValuator.RarityTier rolledTier;

        if (targetNumber == 6) {
            // 6 点超级暴击：必定高阶 (神话或史诗)
            if (inputTier == LotteryValuator.RarityTier.MYTHIC || inputTier == LotteryValuator.RarityTier.LEGENDARY) {
                rolledTier = LotteryValuator.RarityTier.MYTHIC;
            } else if (inputTier == LotteryValuator.RarityTier.EPIC) {
                rolledTier = LotteryValuator.RarityTier.MYTHIC;
            } else {
                rolledTier = LotteryValuator.RarityTier.RARE;
            }
        } else if (targetNumber == 5) {
            LotteryValuator.RarityTier next = inputTier.getNextTier();
            rolledTier = (next != null && random.nextBoolean()) ? next : inputTier;
        } else if (targetNumber == 4) {
            rolledTier = inputTier;
        } else if (targetNumber == 3) {
            rolledTier = inputTier;
        } else {
            // 2 点折损
            rolledTier = getLowerTier(inputTier);
        }

        List<Item> pool = getPoolForTier(rolledTier);
        if (pool.isEmpty()) pool = COMMON_POOL;

        Item chosenItem = pool.get(random.nextInt(pool.size()));
        ItemStack resultStack = new ItemStack(chosenItem);

        if (resultStack.getMaxStackSize() > 1) {
            long singleVal = LotteryValuator.getUnitValue(resultStack);
            int count = (int) Math.max(1L, Math.min(resultStack.getMaxStackSize(), targetValue / Math.max(1L, singleVal)));
            resultStack.setCount(count);
        } else if (resultStack.isEnchantable() && level != null) {
            int enchLevel = (targetNumber == 6) ? 30 : (targetNumber == 5 ? 20 : 10);
            tryEnchant(resultStack, random, level, enchLevel);
        }

        return resultStack;
    }

    /**
     * 虚空轮盘开奖逻辑 (严格按 8 大主题领域定向产出，数量乘以下注倍数)
     */
    public static ItemStack rollWheelSector(int sectorIndex, int betMultiplier, long totalPointsSacrificed, RandomSource random, ServerLevel level, List<ItemStack> extraDrops) {
        ensureInitialized();
        int safeBet = Math.max(1, betMultiplier);
        return switch (sectorIndex) {
            case 0 -> rollSectorFood(safeBet, totalPointsSacrificed, random, level);
            case 1 -> rollSectorAlloy(safeBet, totalPointsSacrificed, random, level);
            case 2 -> rollSectorDiamond(safeBet, totalPointsSacrificed, random, level);
            case 3 -> rollSectorMythicStar(safeBet, totalPointsSacrificed, random, level, extraDrops);
            case 4 -> rollSectorEnder(safeBet, totalPointsSacrificed, random, level);
            case 5 -> rollSectorEnchantTome(safeBet, totalPointsSacrificed, random, level, extraDrops);
            case 6 -> rollSectorIndustry(safeBet, totalPointsSacrificed, random, level);
            case 7 -> rollSectorMystery(safeBet, totalPointsSacrificed, random, level, extraDrops);
            default -> roll(totalPointsSacrificed, random, level);
        };
    }

    public static ItemStack rollWheelSector(int sectorIndex, long totalPointsSacrificed, RandomSource random, ServerLevel level) {
        return rollWheelSector(sectorIndex, 1, totalPointsSacrificed, random, level, null);
    }

    private static ItemStack rollSectorFood(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level) {
        // 0: 黄金之实 (补给与强效炼金)
        Item item;
        float r = random.nextFloat();
        if (totalPoints >= LotteryValuator.RarityTier.EPIC.getMinPoints() && r < 0.35F) {
            item = Items.ENCHANTED_GOLDEN_APPLE;
        } else if (totalPoints >= LotteryValuator.RarityTier.RARE.getMinPoints() || r < 0.60F) {
            item = Items.GOLDEN_APPLE;
        } else {
            Item[] cand = new Item[]{Items.GOLDEN_CARROT, Items.GLISTERING_MELON_SLICE, Items.CHORUS_FRUIT, Items.HONEY_BOTTLE};
            item = cand[random.nextInt(cand.length)];
        }
        return createSizedReward(item, betMultiplier);
    }

    private static ItemStack rollSectorAlloy(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level) {
        // 1: 下界之核 (合金与残骸重金属)
        Item item;
        float r = random.nextFloat();
        if (r < 0.45F) {
            item = Items.NETHERITE_INGOT;
        } else if (r < 0.70F) {
            item = Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        } else if (r < 0.90F) {
            item = Items.ANCIENT_DEBRIS;
        } else {
            item = Items.NETHERITE_SCRAP;
        }
        return createSizedReward(item, betMultiplier);
    }

    private static ItemStack rollSectorDiamond(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level) {
        // 2: 璀璨钻石 (珠宝与晶核)
        Item item;
        float r = random.nextFloat();
        if (totalPoints >= LotteryValuator.RarityTier.EPIC.getMinPoints() && r < 0.35F) {
            item = Items.DIAMOND_BLOCK;
        } else if (r < 0.70F) {
            item = Items.DIAMOND;
        } else if (r < 0.85F) {
            item = Items.EMERALD_BLOCK;
        } else {
            item = Items.HEART_OF_THE_SEA;
        }
        return createSizedReward(item, betMultiplier);
    }

    private static ItemStack rollSectorMythicStar(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level, List<ItemStack> extraDrops) {
        // 3: 虚空神星 (至尊神话秘宝 Grand Jackpot)
        Item item;
        float r = random.nextFloat();
        if (r < 0.40F) {
            item = Items.NETHER_STAR;
        } else if (r < 0.65F) {
            item = Items.TOTEM_OF_UNDYING;
        } else if (r < 0.85F) {
            item = Items.BEACON;
        } else {
            item = Items.DRAGON_EGG;
        }
        ItemStack main = createSizedReward(item, betMultiplier);
        if (betMultiplier >= 5 && extraDrops != null) {
            extraDrops.add(new ItemStack(Items.TOTEM_OF_UNDYING, Math.min(8, betMultiplier)));
            extraDrops.add(new ItemStack(Items.BEACON, Math.min(4, Math.max(1, betMultiplier / 2))));
        }
        return main;
    }

    private static ItemStack rollSectorEnder(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level) {
        // 4: 末影之眼 (维度与折跃遗物)
        Item item;
        float r = random.nextFloat();
        if (r < 0.35F) {
            item = Items.ENDER_EYE;
        } else if (r < 0.60F) {
            item = Items.SHULKER_SHELL;
        } else if (r < 0.80F) {
            item = Items.ENDER_CHEST;
        } else if (r < 0.95F) {
            item = Items.ECHO_SHARD;
        } else {
            item = Items.ENDER_PEARL;
        }
        return createSizedReward(item, betMultiplier);
    }

    private static ItemStack rollSectorEnchantTome(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level, List<ItemStack> extraDrops) {
        // 5: 远古秘卷 (单件神装 + 多倍物料包)
        Item[] gear = new Item[]{Items.ENCHANTED_BOOK, Items.DIAMOND_SWORD, Items.DIAMOND_CHESTPLATE, Items.BOW, Items.CROSSBOW, Items.TRIDENT};
        Item chosen = gear[random.nextInt(gear.length)];
        ItemStack stack = new ItemStack(chosen);
        if (level != null) {
            int enchLevel = totalPoints >= LotteryValuator.RarityTier.EPIC.getMinPoints() ? 30 : 20;
            tryEnchant(stack, random, level, enchLevel);
        }
        if (betMultiplier > 1 && extraDrops != null) {
            // 超出倍数换算为极品附魔矿物大礼包
            extraDrops.add(new ItemStack(Items.DIAMOND_BLOCK, Math.min(64, (betMultiplier - 1) * 2)));
            extraDrops.add(new ItemStack(Items.EXPERIENCE_BOTTLE, Math.min(64, (betMultiplier - 1) * 6)));
            extraDrops.add(new ItemStack(Items.LAPIS_BLOCK, Math.min(64, (betMultiplier - 1) * 3)));
        }
        return stack;
    }

    private static ItemStack rollSectorIndustry(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level) {
        // 6: 炼金重金 (基础工业建材工程包)
        Item[] materials = new Item[]{Items.IRON_BLOCK, Items.GOLD_BLOCK, Items.COPPER_BLOCK, Items.REDSTONE_BLOCK, Items.HOPPER, Items.PISTON, Items.SLIME_BLOCK};
        Item chosen = materials[random.nextInt(materials.length)];
        return createSizedReward(chosen, betMultiplier);
    }

    private static ItemStack rollSectorMystery(int betMultiplier, long totalPoints, RandomSource random, ServerLevel level, List<ItemStack> extraDrops) {
        // 7: 神秘盲盒 (意外惊喜)
        if (random.nextBoolean()) {
            LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(totalPoints);
            List<Item> pool = getPoolForTier(tier.getNextTier() != null ? tier.getNextTier() : tier);
            if (pool.isEmpty()) pool = COMMON_POOL;
            return createSizedReward(pool.get(random.nextInt(pool.size())), betMultiplier);
        } else {
            int totalScrap = (8 + random.nextInt(9)) * betMultiplier;
            int mainScrap = Math.min(64, totalScrap);
            if (totalScrap > 64 && extraDrops != null) {
                extraDrops.add(new ItemStack(ModItems.VOID_SCRAP.get(), Math.min(64, totalScrap - 64)));
            }
            return new ItemStack(ModItems.VOID_SCRAP.get(), mainScrap);
        }
    }

    private static ItemStack createSizedReward(Item item, int multiplier) {
        ItemStack stack = new ItemStack(item);
        if (stack.getMaxStackSize() > 1) {
            int baseCount;
            if (item == Items.NETHER_STAR || item == Items.DRAGON_EGG || item == Items.HEART_OF_THE_SEA
                    || item == Items.BEACON || item == Items.TOTEM_OF_UNDYING
                    || item == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE || item == Items.ENCHANTED_GOLDEN_APPLE) {
                baseCount = 1;
            } else if (item == Items.NETHERITE_INGOT || item == Items.ANCIENT_DEBRIS
                    || item == Items.DIAMOND_BLOCK || item == Items.EMERALD_BLOCK) {
                baseCount = 1;
            } else if (item == Items.DIAMOND || item == Items.EMERALD || item == Items.GOLDEN_APPLE
                    || item == Items.NETHERITE_SCRAP || item == Items.ENDER_CHEST || item == Items.SHULKER_SHELL) {
                baseCount = 2;
            } else if (item == Items.GOLDEN_CARROT || item == Items.GLISTERING_MELON_SLICE
                    || item == Items.ENDER_PEARL || item == Items.ECHO_SHARD || item == Items.END_ROD || item == Items.CHORUS_FRUIT) {
                baseCount = 3;
            } else if (item == Items.IRON_BLOCK || item == Items.GOLD_BLOCK || item == Items.COPPER_BLOCK
                    || item == Items.REDSTONE_BLOCK || item == Items.SLIME_BLOCK || item == Items.HOPPER || item == Items.PISTON) {
                baseCount = 3;
            } else {
                baseCount = 4;
            }
            int count = Math.max(1, Math.min(stack.getMaxStackSize(), baseCount * multiplier));
            stack.setCount(count);
        }
        return stack;
    }

    private static LotteryValuator.RarityTier getLowerTier(LotteryValuator.RarityTier tier) {
        return switch (tier) {
            case MYTHIC -> LotteryValuator.RarityTier.LEGENDARY;
            case LEGENDARY -> LotteryValuator.RarityTier.EPIC;
            case EPIC -> LotteryValuator.RarityTier.RARE;
            case RARE -> LotteryValuator.RarityTier.UNCOMMON;
            default -> LotteryValuator.RarityTier.COMMON;
        };
    }

    /**
     * 虚空轮盘开奖逻辑 (兼容旧调用)
     */
    public static ItemStack rollWheel(int sectorIndex, long totalPointsSacrificed, RandomSource random, ServerLevel level) {
        return rollWheelSector(sectorIndex, 1, totalPointsSacrificed, random, level, null);
    }

    /**
     * 虚空老虎机开奖逻辑 (产出数量直接乘以下注倍数)
     */
    public static ItemStack rollSlot(int s1, int s2, int s3, float multiplier, int betMultiplier, long totalPointsSacrificed, RandomSource random, ServerLevel level, List<ItemStack> extraDrops) {
        ensureInitialized();
        int safeBet = Math.max(1, betMultiplier);

        if (s1 == 0 && s2 == 0 && s3 == 0) {
            // Grand Jackpot: 10x!
            ItemStack star = new ItemStack(Items.NETHER_STAR, Math.min(64, safeBet));
            if (safeBet > 1 && extraDrops != null) {
                extraDrops.add(new ItemStack(Items.TOTEM_OF_UNDYING, Math.min(8, safeBet)));
                extraDrops.add(new ItemStack(Items.BEACON, Math.min(4, Math.max(1, safeBet / 2))));
            }
            return star;
        } else if (s1 == 6 && s2 == 6 && s3 == 6) {
            // 三连虚空残片：海量虚空残片大爆发！
            int totalScrap = (16 + random.nextInt(9)) * safeBet;
            int mainCount = Math.min(64, totalScrap);
            if (totalScrap > 64 && extraDrops != null) {
                extraDrops.add(new ItemStack(ModItems.VOID_SCRAP.get(), Math.min(64, totalScrap - 64)));
            }
            return new ItemStack(ModItems.VOID_SCRAP.get(), mainCount);
        } else if (s1 == s2 && s2 == s3) {
            // 三连全中：基础数量 × safeBet
            return switch (s1) {
                case 1 -> new ItemStack(Items.NETHERITE_INGOT, Math.min(64, Math.max(1, (1 + random.nextInt(2)) * safeBet)));
                case 2 -> new ItemStack(Items.GOLDEN_APPLE, Math.min(64, (2 + random.nextInt(2)) * safeBet));
                case 3 -> new ItemStack(Items.DIAMOND, Math.min(64, (3 + random.nextInt(3)) * safeBet));
                case 4 -> new ItemStack(Items.ENDER_EYE, Math.min(64, (3 + random.nextInt(2)) * safeBet));
                case 5 -> new ItemStack(Items.CHORUS_FRUIT, Math.min(64, (6 + random.nextInt(4)) * safeBet));
                default -> new ItemStack(ModItems.VOID_SCRAP.get(), Math.min(64, 8 * safeBet));
            };
        } else if (s1 == s2 || s2 == s3 || s1 == s3) {
            // 双连保本：基础数量 × safeBet
            int matchSym = (s1 == s2 || s1 == s3) ? s1 : s2;
            return switch (matchSym) {
                case 0 -> new ItemStack(Items.NETHER_STAR, 1);
                case 1 -> new ItemStack(Items.NETHERITE_SCRAP, Math.min(64, safeBet));
                case 2 -> new ItemStack(Items.GOLDEN_CARROT, Math.min(64, 2 * safeBet));
                case 3 -> new ItemStack(Items.DIAMOND, Math.min(64, safeBet));
                case 4 -> new ItemStack(Items.ENDER_PEARL, Math.min(64, 2 * safeBet));
                case 5 -> new ItemStack(Items.CHORUS_FRUIT, Math.min(64, 3 * safeBet));
                default -> new ItemStack(ModItems.VOID_SCRAP.get(), Math.min(64, 4 * safeBet));
            };
        } else {
            // 散牌安慰奖：虚空残片 × safeBet
            return new ItemStack(ModItems.VOID_SCRAP.get(), Math.min(64, (1 + random.nextInt(2)) * safeBet));
        }
    }

    public static ItemStack rollSlot(int s1, int s2, int s3, float multiplier, long totalPointsSacrificed, RandomSource random, ServerLevel level) {
        return rollSlot(s1, s2, s3, multiplier, 1, totalPointsSacrificed, random, level, null);
    }

    private static List<Item> getPoolForTier(LotteryValuator.RarityTier tier) {
        return switch (tier) {
            case MYTHIC, LEGENDARY -> !MYTHIC_POOL.isEmpty() ? MYTHIC_POOL : EPIC_POOL;
            case EPIC -> !EPIC_POOL.isEmpty() ? EPIC_POOL : RARE_POOL;
            case RARE -> !RARE_POOL.isEmpty() ? RARE_POOL : UNCOMMON_POOL;
            case UNCOMMON -> !UNCOMMON_POOL.isEmpty() ? UNCOMMON_POOL : COMMON_POOL;
            case COMMON -> COMMON_POOL;
        };
    }

    private static void tryEnchant(ItemStack stack, RandomSource random, ServerLevel level, int enchantLevel) {
        try {
            var registryAccess = level.registryAccess();
            var enchantRegistry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
            EnchantmentHelper.enchantItem(random, stack, enchantLevel, registryAccess, Optional.empty());
        } catch (Throwable ignored) {
            // 兼容性保护，附魔失败保留白板
        }
    }
}
