package org.gwfx.universaltool.lottery;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LotteryValuator {

    public enum RarityTier {
        COMMON("gui.universal_tool.lottery.tier.common", 0xAAAAAA, 0L),
        UNCOMMON("gui.universal_tool.lottery.tier.uncommon", 0x55FF55, 50L),
        RARE("gui.universal_tool.lottery.tier.rare", 0x55FFFF, 250L),
        EPIC("gui.universal_tool.lottery.tier.epic", 0xFF55FF, 1500L),
        LEGENDARY("gui.universal_tool.lottery.tier.legendary", 0xFFAA00, 8000L),
        MYTHIC("gui.universal_tool.lottery.tier.mythic", 0xFF5555, 40000L);

        private final String translationKey;
        private final int color;
        private final long minPoints;

        RarityTier(String translationKey, int color, long minPoints) {
            this.translationKey = translationKey;
            this.color = color;
            this.minPoints = minPoints;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public Component getDisplayNameComponent() {
            return Component.translatable(translationKey);
        }

        public String getDisplayName() {
            return Component.translatable(translationKey).getString();
        }

        public int getColor() {
            return color;
        }

        public long getMinPoints() {
            return minPoints;
        }

        public RarityTier getNextTier() {
            return switch (this) {
                case COMMON -> UNCOMMON;
                case UNCOMMON -> RARE;
                case RARE -> EPIC;
                case EPIC -> LEGENDARY;
                case LEGENDARY -> MYTHIC;
                case MYTHIC -> null;
            };
        }
    }

    // 原版核心神物与奇珍的专属固定基准分
    private static final Map<Item, Long> SPECIAL_ITEM_VALUES = new HashMap<>();

    // 针对任意模组未附魔满耐久物品的基础单价动态并发缓存，防止重复计算
    private static final Map<Item, Long> BASE_UNIT_CACHE = new ConcurrentHashMap<>();

    static {
        // 终极神话级原版物品 (单件或数件即可冲击神话)
        registerSpecial(Items.DRAGON_EGG, 45000L);
        registerSpecial(Items.MACE, 30000L);
        registerSpecial(Items.HEAVY_CORE, 28000L);
        registerSpecial(Items.ENCHANTED_GOLDEN_APPLE, 25000L);

        // 传说级原版物品 (单件稳稳进入传说)
        registerSpecial(Items.BEACON, 18000L);
        registerSpecial(Items.NETHER_STAR, 15000L);
        registerSpecial(Items.ELYTRA, 12000L);
        registerSpecial(Items.CONDUIT, 8500L);
        registerSpecial(Items.RECOVERY_COMPASS, 10000L);

        // 史诗级原版珍宝 (单件高额估值)
        registerSpecial(Items.HEART_OF_THE_SEA, 6000L);
        registerSpecial(Items.DRAGON_HEAD, 6000L);
        registerSpecial(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 4000L);
        registerSpecial(Items.WITHER_SKELETON_SKULL, 3500L);
        registerSpecial(Items.TOTEM_OF_UNDYING, 2500L);
        registerSpecial(Items.SHULKER_BOX, 1800L);
        registerSpecial(Items.WHITE_SHULKER_BOX, 1800L);
        registerSpecial(Items.ORANGE_SHULKER_BOX, 1800L);
        registerSpecial(Items.MAGENTA_SHULKER_BOX, 1800L);
        registerSpecial(Items.LIGHT_BLUE_SHULKER_BOX, 1800L);
        registerSpecial(Items.YELLOW_SHULKER_BOX, 1800L);
        registerSpecial(Items.LIME_SHULKER_BOX, 1800L);
        registerSpecial(Items.PINK_SHULKER_BOX, 1800L);
        registerSpecial(Items.GRAY_SHULKER_BOX, 1800L);
        registerSpecial(Items.LIGHT_GRAY_SHULKER_BOX, 1800L);
        registerSpecial(Items.CYAN_SHULKER_BOX, 1800L);
        registerSpecial(Items.PURPLE_SHULKER_BOX, 1800L);
        registerSpecial(Items.BLUE_SHULKER_BOX, 1800L);
        registerSpecial(Items.BROWN_SHULKER_BOX, 1800L);
        registerSpecial(Items.GREEN_SHULKER_BOX, 1800L);
        registerSpecial(Items.RED_SHULKER_BOX, 1800L);
        registerSpecial(Items.BLACK_SHULKER_BOX, 1800L);
        registerSpecial(Items.ECHO_SHARD, 1200L);

        // 稀有级物品
        registerSpecial(Items.SHULKER_SHELL, 800L);
        registerSpecial(Items.GOLDEN_APPLE, 600L);
        registerSpecial(Items.TRIDENT, 3000L);
        registerSpecial(Items.NAUTILUS_SHELL, 300L);
        registerSpecial(Items.NAME_TAG, 150L);
        registerSpecial(Items.SADDLE, 200L);
    }

    private static void registerSpecial(Item item, long value) {
        if (item != null && item != Items.AIR) {
            SPECIAL_ITEM_VALUES.put(item, value);
        }
    }

    private LotteryValuator() {}

    /**
     * 清理动态估值缓存（供数据包或模组重载时调用）
     */
    public static void clearCache() {
        BASE_UNIT_CACHE.clear();
    }

    /**
     * 计算单件/整组物品的综合献祭点数
     */
    public static long evaluate(ItemStack stack) {
        if (stack.isEmpty() || stack.is(org.gwfx.universaltool.init.ModItems.VOID_SCRAP.get())) {
            return 0L;
        }

        long unitPoints = getUnitValue(stack);
        return unitPoints * stack.getCount();
    }

    /**
     * 单件物品的基础估值算法（支持全模组新加物品自适应探测与分级）
     */
    public static long getUnitValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }

        Item item = stack.getItem();
        long baseUnitValue = BASE_UNIT_CACHE.computeIfAbsent(item, LotteryValuator::computeRawBaseUnitValue);

        // 结合物品实时的磨损状态与附魔进行二次精确修正
        return applyDurabilityAndEnchantments(stack, baseUnitValue);
    }

    /**
     * 针对任意原版与模组物品的多层级自适应估值引擎
     */
    private static long computeRawBaseUnitValue(Item item) {
        if (item == org.gwfx.universaltool.init.ModItems.VOID_SCRAP.get()) {
            return 0L;
        }

        ItemStack dummyStack = new ItemStack(item);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        // 1. 优先检查整合包/数据包自定义标签 (#universal_tool:lottery_*)
        Long tagOverride = checkCustomDataPackTags(dummyStack);
        if (tagOverride != null) {
            return tagOverride;
        }

        // 2. 特殊奇珍神物命中表 (原版核心固定分)
        if (SPECIAL_ITEM_VALUES.containsKey(item)) {
            return SPECIAL_ITEM_VALUES.get(item);
        }

        // 锻造模板与唱片通用特征
        if (id != null && id.getPath().endsWith("_smithing_template")) {
            return 1500L;
        }
        if (id != null && id.getPath().startsWith("music_disc_")) {
            if (id.getPath().contains("pigstep") || id.getPath().contains("relic") ||
                id.getPath().contains("5") || id.getPath().contains("creator")) {
                return 2500L;
            }
            return 800L;
        }

        // 3. 原版经典材质层级 (下界合金、钻石、绿宝石、金、铁、铜、煤炭等)
        Long materialValue = evaluateByMaterialHierarchy(dummyStack, item, id);
        if (materialValue != null) {
            return materialValue;
        }

        // 4. 模组材料词法与通用科技/魔法合金体系探测 (无尽/觉醒龙/振金/精炼黑曜石/泰拉钢/钢/黄铜/锡等)
        Long modMaterialValue = evaluateModMaterialsAndKeywords(dummyStack, item, id);
        if (modMaterialValue != null) {
            return modMaterialValue;
        }

        // 5. 模组与原版装备/工具属性自适应探测 (基于 ArmorItem 防御与韧性、TieredItem 挖掘等级与伤害)
        Long equipmentValue = evaluateEquipmentByAttributes(dummyStack, item);
        if (equipmentValue != null) {
            return equipmentValue;
        }

        // 6. 模组食物与滋补品自适应 (基于营养值、饱和度与附加 Buff)
        Long foodValue = evaluateFoodComponent(dummyStack);
        if (foodValue != null) {
            return foodValue;
        }

        // 7. 模组通用标签加权与原生稀有度兜底 (NeoForge Common Tags: c:storage_blocks, c:gems, etc.)
        return evaluateFallbackWithTags(dummyStack, item, id);
    }

    /**
     * 整合包/数据包 Tag 规范优先覆盖
     */
    private static Long checkCustomDataPackTags(ItemStack stack) {
        if (hasTag(stack, "universal_tool", "lottery_mythic") || hasTag(stack, "universal_tool", "lottery/mythic")) {
            return 45000L;
        }
        if (hasTag(stack, "universal_tool", "lottery_legendary") || hasTag(stack, "universal_tool", "lottery/legendary")) {
            return 12000L;
        }
        if (hasTag(stack, "universal_tool", "lottery_epic") || hasTag(stack, "universal_tool", "lottery/epic")) {
            return 3000L;
        }
        if (hasTag(stack, "universal_tool", "lottery_rare") || hasTag(stack, "universal_tool", "lottery/rare")) {
            return 500L;
        }
        if (hasTag(stack, "universal_tool", "lottery_uncommon") || hasTag(stack, "universal_tool", "lottery/uncommon")) {
            return 100L;
        }
        if (hasTag(stack, "universal_tool", "lottery_common") || hasTag(stack, "universal_tool", "lottery/common")) {
            return 15L;
        }
        return null;
    }

    /**
     * 核心原版材质层级识别
     */
    private static Long evaluateByMaterialHierarchy(ItemStack stack, Item item, ResourceLocation id) {
        if (id == null) return null;
        String path = id.getPath().toLowerCase();

        // --- A. 下界合金阶梯 (Netherite Tier) ---
        if (path.contains("netherite")) {
            if (isStorageBlock(stack, path)) {
                return 10800L; // 9 * 1200L，单个即达传说 (LEGENDARY)
            }
            if (path.contains("ingot")) return 1200L;
            if (path.contains("scrap")) return 300L;
            if (path.contains("chestplate")) return 6500L;
            if (path.contains("leggings")) return 5500L;
            if (path.contains("helmet") || path.contains("boots")) return 4000L;
            if (path.contains("sword") || path.contains("pickaxe") || path.contains("axe") ||
                path.contains("shovel") || path.contains("hoe")) {
                return 3500L;
            }
            return 1200L;
        }
        if (path.equals("ancient_debris")) {
            return 400L;
        }

        // --- B. 钻石阶梯 (Diamond Tier) ---
        if (path.contains("diamond")) {
            if (isStorageBlock(stack, path)) {
                return 810L; // 9 * 90L，20个即达 16200L (传说 LEGENDARY)
            }
            if (path.contains("ore")) return 150L;
            if (path.equals("diamond")) return 90L;
            if (path.contains("chestplate")) return 750L;
            if (path.contains("leggings")) return 650L;
            if (path.contains("helmet") || path.contains("boots")) return 400L;
            if (path.contains("sword") || path.contains("pickaxe") || path.contains("axe") ||
                path.contains("shovel") || path.contains("hoe")) {
                return 320L;
            }
            return 90L;
        }

        // --- C. 绿宝石阶梯 (Emerald Tier) ---
        if (path.contains("emerald")) {
            if (isStorageBlock(stack, path)) return 540L; // 9 * 60L
            if (path.contains("ore")) return 120L;
            if (path.equals("emerald")) return 60L;
            return 60L;
        }

        // --- D. 黄金阶梯 (Gold / Golden Tier) ---
        if (path.contains("gold") || path.contains("golden")) {
            if (isStorageBlock(stack, path)) return 225L; // 9 * 25L
            if (path.contains("raw_") || path.contains("ore")) return 35L;
            if (path.contains("nugget")) return 3L;
            if (path.contains("ingot") || path.equals("gold")) return 25L;
            if (path.contains("apple")) return 600L;
            if (path.contains("chestplate")) return 70L;
            if (path.contains("leggings")) return 60L;
            if (path.contains("helmet") || path.contains("boots")) return 40L;
            if (path.contains("sword") || path.contains("pickaxe") || path.contains("axe") ||
                path.contains("shovel") || path.contains("hoe")) {
                return 30L;
            }
            return 25L;
        }

        // --- E. 铁阶梯 (Iron Tier) ---
        if (path.contains("iron")) {
            if (isStorageBlock(stack, path)) return 108L; // 9 * 12L
            if (path.contains("raw_") || path.contains("ore")) return 18L;
            if (path.contains("nugget")) return 1L;
            if (path.contains("ingot") || path.equals("iron")) return 12L;
            if (path.contains("chestplate")) return 90L;
            if (path.contains("leggings")) return 80L;
            if (path.contains("helmet") || path.contains("boots")) return 50L;
            if (path.contains("sword") || path.contains("pickaxe") || path.contains("axe") ||
                path.contains("shovel") || path.contains("hoe")) {
                return 40L;
            }
            return 12L;
        }

        // --- F. 锁子甲阶梯 (Chainmail Tier) ---
        if (path.contains("chainmail")) {
            if (path.contains("chestplate")) return 80L;
            if (path.contains("leggings")) return 70L;
            if (path.contains("helmet") || path.contains("boots")) return 45L;
            return 50L;
        }

        // --- G. 铜阶梯 (Copper Tier) ---
        if (path.contains("copper")) {
            if (isStorageBlock(stack, path)) return 27L; // 9 * 3L
            if (path.contains("raw_") || path.contains("ore")) return 5L;
            if (path.contains("ingot") || path.equals("copper")) return 3L;
            return 3L;
        }

        // --- H. 煤炭与木炭阶梯 (Coal / Charcoal Tier) ---
        if (path.contains("coal") || path.contains("charcoal")) {
            if (isStorageBlock(stack, path)) return 36L; // 9 * 4L
            if (path.contains("ore")) return 6L;
            return 4L;
        }

        // --- I. 红石 / 青金石 / 石英 / 紫水晶 ---
        if (path.contains("redstone") || path.contains("lapis") || path.contains("quartz")) {
            if (isStorageBlock(stack, path)) return 54L;
            if (path.contains("ore")) return 10L;
            return 6L;
        }
        if (path.contains("amethyst")) {
            if (isStorageBlock(stack, path)) return 32L;
            if (path.contains("shard")) return 8L;
            return 8L;
        }

        // --- J. 皮革阶梯 (Leather Tier) ---
        if (path.contains("leather")) {
            if (path.equals("leather")) return 2L;
            if (path.contains("horse_armor")) return 30L;
            if (path.contains("chestplate")) return 20L;
            if (path.contains("leggings")) return 16L; // 皮革护腿基础分 16，稳定在普通 (COMMON)
            if (path.contains("helmet") || path.contains("boots")) return 12L;
            return 4L;
        }

        return null;
    }

    /**
     * 针对主流科技/魔法/冒险模组材料的通用自适应词法分析
     */
    private static Long evaluateModMaterialsAndKeywords(ItemStack stack, Item item, ResourceLocation id) {
        if (id == null) return null;
        String path = id.getPath().toLowerCase();

        // 1. 终极宇宙/奇点神级材料 (Cosmic / Singularity / End-game Mod Tier)
        if (path.contains("infinity") || path.contains("neutronium") || path.contains("chaos") ||
            path.contains("draconium") || path.contains("awakened") || path.contains("allthemodium") ||
            path.contains("vibranium") || path.contains("unobtainium") || path.contains("antimatter") ||
            path.contains("singularity") || path.contains("stellar")) {

            if (isStorageBlock(stack, path)) return 225000L; // 存储块直接破神话
            if (path.contains("nugget")) return 2500L;
            if (path.contains("ore") || path.contains("raw_")) return 30000L;
            return 25000L; // 锭 / 奇点 / 核心
        }

        // 2. 高阶科技与魔法奇珍材料 (Advanced Mod Tier)
        if (path.contains("refined_obsidian") || path.contains("refined_glowstone") || path.contains("terrasteel") ||
            path.contains("uranium") || path.contains("iridium") || path.contains("platinum") ||
            path.contains("titanium") || path.contains("tungsten") || path.contains("palladium") ||
            path.contains("elementium") || path.contains("dark_matter") || path.contains("alfsteel")) {

            if (isStorageBlock(stack, path)) return 14400L; // 9 * 1600L，单块即入传说
            if (path.contains("nugget")) return 160L;
            if (path.contains("ore") || path.contains("raw_")) return 2200L;
            return 1600L; // 锭 / 宝石
        }

        // 3. 中阶工业与魔法合金 (Mid Industrial / Alloy Tier)
        if (path.contains("steel") || path.contains("manasteel") || path.contains("osmium") ||
            path.contains("brass") || path.contains("zinc") || path.contains("silver") ||
            path.contains("invar") || path.contains("electrum") || path.contains("bronze") ||
            path.contains("constantan") || path.contains("fluix") || path.contains("certus_quartz") ||
            path.contains("ruby") || path.contains("sapphire")) {

            if (isStorageBlock(stack, path)) return 450L; // 9 * 50L
            if (path.contains("nugget")) return 5L;
            if (path.contains("ore") || path.contains("raw_")) return 75L;
            return 50L; // 锭 / 宝石
        }

        // 4. 初阶通用基础模组金属 (Basic Mod Tier)
        if (path.contains("tin") || path.contains("lead") || path.contains("nickel") || path.contains("aluminum")) {
            if (isStorageBlock(stack, path)) return 90L; // 9 * 10L
            if (path.contains("nugget")) return 1L;
            if (path.contains("ore") || path.contains("raw_")) return 15L;
            return 10L; // 锭
        }

        return null;
    }

    /**
     * 模组装备与工具基于原生物理属性的动态分析
     */
    private static Long evaluateEquipmentByAttributes(ItemStack stack, Item item) {
        if (item instanceof ArmorItem armorItem) {
            int defense = armorItem.getDefense();
            float toughness = armorItem.getToughness();

            // 基础属性积分
            long score = (long) defense * 75L + (long) (toughness * 400L);

            // 如果是高防御/高韧性神级护甲（如下界合金级或模组超神甲）
            if (defense >= 8 || toughness >= 3.0f) {
                score += 2500L;
            } else if (defense >= 5 || toughness >= 1.0f) {
                score += 300L;
            }

            return Math.max(15L, score);
        }

        if (item instanceof TieredItem tieredItem) {
            Tier tier = tieredItem.getTier();
            int uses = tier.getUses();
            float speed = tier.getSpeed();
            float dmg = tier.getAttackDamageBonus();
            int enchValue = tier.getEnchantmentValue();

            long toolScore = (long) (dmg * 120L + speed * 40L + enchValue * 25L + (uses / 4L));
            return Math.max(10L, toolScore);
        }

        return null;
    }

    /**
     * 模组食物与滋补品自适应
     */
    private static Long evaluateFoodComponent(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            long foodScore = (long) (food.nutrition() * 4L + food.saturation() * 6L);
            var effects = food.effects();
            if (!effects.isEmpty()) {
                foodScore += (long) effects.size() * 300L;
                for (var effect : effects) {
                    var inst = effect.effect();
                    if (inst.getDuration() > 600 || inst.getAmplifier() >= 1) {
                        foodScore += 1200L;
                    }
                }
            }
            return Math.max(2L, foodScore);
        }
        return null;
    }

    /**
     * 模组通用标签加权与原版稀有度泛化算法
     */
    private static long evaluateFallbackWithTags(ItemStack stack, Item item, ResourceLocation id) {
        // 1. 原生稀有度阶梯底分
        Rarity rarity = stack.getRarity();
        long basePoints = switch (rarity) {
            case COMMON -> 2L;
            case UNCOMMON -> 20L;
            case RARE -> 120L;
            case EPIC -> 600L;
        };

        // 2. NeoForge / Common 通用标签加权系数
        double tagMultiplier = 1.0;
        if (hasTag(stack, "c", "storage_blocks")) {
            tagMultiplier = Math.max(tagMultiplier, 9.0);
        } else if (hasTag(stack, "c", "gems")) {
            tagMultiplier = Math.max(tagMultiplier, 4.0);
        } else if (hasTag(stack, "c", "ingots")) {
            tagMultiplier = Math.max(tagMultiplier, 2.5);
        } else if (hasTag(stack, "c", "ores")) {
            tagMultiplier = Math.max(tagMultiplier, 3.0);
        } else if (hasTag(stack, "c", "raw_materials")) {
            tagMultiplier = Math.max(tagMultiplier, 2.0);
        } else if (hasTag(stack, "c", "dusts")) {
            tagMultiplier = Math.max(tagMultiplier, 1.2);
        } else if (hasTag(stack, "c", "nuggets")) {
            tagMultiplier = Math.max(tagMultiplier, 0.3);
        }

        // 3. 不可堆叠单件非装备物品基础微调（例如鞍、指南针等）
        if (stack.getMaxStackSize() == 1 && !stack.isDamageableItem()) {
            basePoints = Math.max(basePoints, 30L);
        }

        long finalUnit = (long) Math.round(basePoints * tagMultiplier);
        return Math.max(1L, finalUnit);
    }

    private static boolean isStorageBlock(ItemStack stack, String path) {
        return path.contains("block") || hasTag(stack, "c", "storage_blocks");
    }

    /**
     * 综合耐久损耗与真实附魔价值
     */
    private static long applyDurabilityAndEnchantments(ItemStack stack, long baseValue) {
        long value = baseValue;

        // 耐久度损耗折算（残损装备按比例衰减，最低保留 35% 残值）
        if (stack.isDamageableItem()) {
            int maxDmg = stack.getMaxDamage();
            int curDmg = stack.getDamageValue();
            double durabilityRatio = 1.0 - ((double) curDmg / (double) Math.max(1, maxDmg));
            double factor = 0.35 + 0.65 * Math.max(0.0, Math.min(1.0, durabilityRatio));
            value = (long) Math.round(value * factor);
        }

        // 附魔价值计算
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) {
            int totalEnchLevels = 0;
            for (var entry : enchantments.entrySet()) {
                totalEnchLevels += entry.getIntValue();
            }
            value += (long) enchantments.size() * 120L + (long) totalEnchLevels * 80L;
        }

        return Math.max(1L, value);
    }

    /**
     * 根据点数推导稀有度品阶（用于 GUI 显示评级）
     */
    public static RarityTier getTierFromPoints(long points) {
        if (points >= RarityTier.MYTHIC.getMinPoints()) return RarityTier.MYTHIC;
        if (points >= RarityTier.LEGENDARY.getMinPoints()) return RarityTier.LEGENDARY;
        if (points >= RarityTier.EPIC.getMinPoints()) return RarityTier.EPIC;
        if (points >= RarityTier.RARE.getMinPoints()) return RarityTier.RARE;
        if (points >= RarityTier.UNCOMMON.getMinPoints()) return RarityTier.UNCOMMON;
        return RarityTier.COMMON;
    }

    /**
     * 计算成功高阶暴击几率百分比（0 ~ 95%）
     */
    public static int getOddsPercent(long points) {
        if (points <= 0) return 0;
        if (points <= 5) return 1;

        double logVal = Math.log10(points);
        double percent = 10.0 + (logVal - 1.0) * 17.5;
        return (int) Math.min(95, Math.max(1, Math.round(percent)));
    }

    private static boolean hasTag(ItemStack stack, String namespace, String path) {
        TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.fromNamespaceAndPath(namespace, path));
        return stack.is(tagKey);
    }
}
