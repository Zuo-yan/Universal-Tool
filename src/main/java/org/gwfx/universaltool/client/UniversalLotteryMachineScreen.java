package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.lottery.LotteryRollActionPayload;
import org.gwfx.universaltool.lottery.LotteryValuator;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineBlockEntity;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineMenu;
import org.joml.Matrix4f;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniversalLotteryMachineScreen extends AbstractContainerScreen<UniversalLotteryMachineMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);

    // 轮盘 8 扇区对应图标与主题色
    private static final ItemStack[] WHEEL_SECTOR_ITEMS = new ItemStack[]{
            new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.NETHERITE_INGOT),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.NETHER_STAR),
            new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.ENCHANTED_BOOK),
            new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.CHEST)
    };

    private static final int[] WHEEL_SECTOR_COLORS = new int[]{
            0xFFD97706, // 0: Gold
            0xFF334155, // 1: Netherite Slate
            0xFF0284C7, // 2: Diamond Cyan
            0xFF7C3AED, // 3: Nether Star Mythic Purple
            0xFF059669, // 4: Eye Emerald
            0xFFDB2777, // 5: Book Magenta
            0xFF64748B, // 6: Iron Silver
            0xFFEA580C  // 7: Mystery Orange
    };

    private static final String[] WHEEL_SECTOR_NAMES = new String[]{
            "黄金之实", "下界之核", "璀璨钻石", "虚空神星", "末影之眼", "远古秘卷", "炼金重金", "神秘盲盒"
    };

    // 老虎机 7 种滚轮符号
    private static final ItemStack[] SLOT_SYMBOLS = new ItemStack[]{
            new ItemStack(Items.NETHER_STAR),           // 0: ⭐ 虚空神星 (Grand Jackpot)
            new ItemStack(Items.NETHERITE_INGOT),       // 1: ⬛ 下界合金
            new ItemStack(Items.GOLDEN_APPLE),           // 2: 🍎 金苹果
            new ItemStack(Items.DIAMOND),                // 3: 💎 钻石
            new ItemStack(Items.ENDER_EYE),              // 4: 👁 末影之眼
            new ItemStack(Items.CHORUS_FRUIT),           // 5: 🍇 紫颂果
            new ItemStack(ModItems.VOID_SCRAP.get())    // 6: ⚙ 虚空残片
    };

    private static final String[] SLOT_SYMBOL_NAMES = new String[]{
            "虚空神星", "下界合金", "黄金之实", "璀璨钻石", "末影之眼", "虚空紫颂", "虚空残片"
    };

    // 客户端视图状态
    private int clientSelectedTab = 0; // 0 = 骰子, 1 = 轮盘, 2 = 老虎机
    private int clientWheelBet = 1;
    private int clientSlotMultiplier = 1; // 1, 2, 5

    // 动画状态：3D 骰子三轴旋转物理与定盘
    private float diceRotX = 25.0F;
    private float diceRotY = 45.0F;
    private float diceRotZ = 0.0F;
    private float diceTargetRotX = 0.0F;
    private float diceTargetRotY = 0.0F;
    private float diceTargetRotZ = 0.0F;
    private float diceLockStartX = 0.0F;
    private float diceLockStartY = 0.0F;
    private float diceLockStartZ = 0.0F;
    private boolean diceLocking = false;

    private float wheelAngle = 0.0F;
    private float wheelStartAngle = 0.0F;
    private float needleJiggle = 0.0F;
    private float leverPullTimer = 0.0F;
    private boolean wasRolling = false;

    // 老虎机滚轮动态偏移
    private float reel0Scroll = 0.0F;
    private float reel1Scroll = 0.0F;
    private float reel2Scroll = 0.0F;

    public UniversalLotteryMachineScreen(UniversalLotteryMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 218;
        this.inventoryLabelX = 17;
        this.inventoryLabelY = 124;
        this.clientSelectedTab = menu.getActiveMode();
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 6;
    }

    /**
     * 重写 renderLabels：清空原版自动绘制的标题与背包文字，彻底杜绝所有双层文字重叠 bug
     */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 原版 AbstractContainerScreen 会在此处自动绘制 this.title 与 playerInventoryTitle，
        // 这会导致与我们自定义的金色顶栏和规整背包标签产生叠印。在此留空以完全使用自定义渲染。
    }

    @Override
    public void containerTick() {
        super.containerTick();

        boolean isRolling = this.menu.isRolling();
        int activeMode = this.menu.getActiveMode();

        // 当机器正在抽奖时，强制视图跟随当前抽奖模式
        if (isRolling) {
            this.clientSelectedTab = activeMode;
        }

        // 捕捉抽奖启动瞬间，初始化动画基准
        if (!wasRolling && isRolling) {
            this.wheelStartAngle = this.wheelAngle % 360.0F;
            if (activeMode == UniversalLotteryMachineBlockEntity.MODE_SLOT) {
                this.leverPullTimer = 1.0F; // 触发拉杆下拉动画
            }
        }
        wasRolling = isRolling;

        // 操纵杆回弹计时
        if (this.leverPullTimer > 0.0F) {
            this.leverPullTimer -= 0.1F;
            if (this.leverPullTimer < 0.0F) this.leverPullTimer = 0.0F;
        }

        // 模式 0: 3D 骰子三轴立体翻滚与定格更新
        if (isRolling && activeMode == UniversalLotteryMachineBlockEntity.MODE_DICE) {
            int rollTicks = this.menu.getRollTicks();
            if (rollTicks > 15) {
                this.diceRotX += 26.0F;
                this.diceRotY += 34.0F;
                this.diceRotZ += 18.0F;
                this.diceLocking = false;
            } else {
                if (!this.diceLocking) {
                    this.diceLocking = true;
                    this.diceLockStartX = this.diceRotX;
                    this.diceLockStartY = this.diceRotY;
                    this.diceLockStartZ = this.diceRotZ;

                    int targetPip = this.menu.getDiceTargetNumber();
                    float[] targetAngles = getDiceEulerAngles(targetPip);

                    this.diceTargetRotX = getNextAlignedAngle(this.diceLockStartX, targetAngles[0], 360.0F);
                    this.diceTargetRotY = getNextAlignedAngle(this.diceLockStartY, targetAngles[1], 360.0F);
                    this.diceTargetRotZ = getNextAlignedAngle(this.diceLockStartZ, targetAngles[2], 360.0F);
                }

                float p = Mth.clamp((15.0F - rollTicks) / 15.0F, 0.0F, 1.0F);
                float eased = easeOutBack(p);
                this.diceRotX = Mth.lerp(eased, this.diceLockStartX, this.diceTargetRotX);
                this.diceRotY = Mth.lerp(eased, this.diceLockStartY, this.diceTargetRotY);
                this.diceRotZ = Mth.lerp(eased, this.diceLockStartZ, this.diceTargetRotZ);
            }
        } else {
            this.diceLocking = false;
            int targetPip = this.menu.getDiceTargetNumber();
            float[] targetAngles = getDiceEulerAngles(targetPip);
            // 待机时在定格点数基础上微幅呼吸摆动，确保点数时刻清晰可见
            float idleTime = (System.currentTimeMillis() % 100000L) * 0.002F;
            this.diceRotX = targetAngles[0] + (float) Math.sin(idleTime) * 3.0F;
            this.diceRotY = targetAngles[1] + (float) Math.cos(idleTime * 0.8F) * 3.0F;
            this.diceRotZ = targetAngles[2];
        }

        // 模式 1: 轮盘平滑减速物理
        if (isRolling && activeMode == UniversalLotteryMachineBlockEntity.MODE_WHEEL) {
            int targetSlice = this.menu.getWheelTargetSlice();
            // 目标扇区中心需对齐 12 点钟指针 (270度)
            float targetAngle = (270.0F - targetSlice * 45.0F + 3600.0F) % 360.0F;
            int totalTicks = this.menu.getTotalRollDurationTicks();
            float progress = Mth.clamp(1.0F - (float) this.menu.getRollTicks() / totalTicks, 0.0F, 1.0F);

            float totalSpins = 1800.0F; // 5 圈旋转缓冲
            float delta = totalSpins + ((targetAngle - (this.wheelStartAngle % 360.0F) + 360.0F) % 360.0F);
            this.wheelAngle = this.wheelStartAngle + delta * easeOutCubic(progress);

            // 指针过格轻微抖动
            float sectorPass = ((this.wheelAngle - 270.0F) % 45.0F + 45.0F) % 45.0F;
            this.needleJiggle = (float) Math.sin((sectorPass / 45.0F) * Math.PI) * 10.0F * (1.0F - progress * 0.7F);
        } else {
            this.wheelAngle += 0.3F;
            this.needleJiggle = 0.0F;
        }

        // 模式 2: 老虎机滚轮依次停滞物理 (支持连抽快速轮转)
        if (isRolling && activeMode == UniversalLotteryMachineBlockEntity.MODE_SLOT) {
            int rollTicks = this.menu.getRollTicks();
            int totalTicks = this.menu.getTotalRollDurationTicks();
            boolean isShort = (totalTicks <= 30);
            int r1Stop = isShort ? 16 : 25;
            int r2Stop = isShort ? 8 : 12;

            if (rollTicks == totalTicks) {
                this.leverPullTimer = 1.0F; // 每次连抽开始下拉机械把手
            }

            // 滚轮 1 锁止
            if (rollTicks > r1Stop) {
                this.reel0Scroll += 16.0F;
            } else {
                float p = Mth.clamp((float) (r1Stop - rollTicks) / 8.0F, 0.0F, 1.0F);
                this.reel0Scroll = Mth.lerp(easeOutBack(p), this.reel0Scroll, this.menu.getSlotReel1() * 18.0F);
            }

            // 滚轮 2 锁止
            if (rollTicks > r2Stop) {
                this.reel1Scroll += 18.0F;
            } else {
                float p = Mth.clamp((float) (r2Stop - rollTicks) / 8.0F, 0.0F, 1.0F);
                this.reel1Scroll = Mth.lerp(easeOutBack(p), this.reel1Scroll, this.menu.getSlotReel2() * 18.0F);
            }

            // 滚轮 3 锁止
            if (rollTicks > 0) {
                this.reel2Scroll += 20.0F;
            } else {
                this.reel2Scroll = this.menu.getSlotReel3() * 18.0F;
            }
        } else {
            this.reel0Scroll = this.menu.getSlotReel1() * 18.0F;
            this.reel1Scroll = this.menu.getSlotReel2() * 18.0F;
            this.reel2Scroll = this.menu.getSlotReel3() * 18.0F;
        }
    }

    private int getCurrentDisplayOdds() {
        if (this.menu.isRolling()) {
            return this.menu.getOddsPercent();
        }
        if (!this.menu.hasInput()) return 0;
        ItemStack stack = this.menu.getInputStack();
        long unitVal = LotteryValuator.getUnitValue(stack);
        int bet = (this.clientSelectedTab == 1) ? this.clientWheelBet : 1;
        bet = Math.max(1, Math.min(stack.getCount(), bet));
        long totalPts = unitVal * bet;
        return LotteryValuator.getOddsPercent(totalPts);
    }

    private static float[] getDiceEulerAngles(int pip) {
        return switch (pip) {
            case 1 -> new float[]{0.0F, 0.0F, 0.0F};       // +Z 正对相机
            case 2 -> new float[]{0.0F, -90.0F, 0.0F};     // +X 转向相机
            case 3 -> new float[]{90.0F, 0.0F, 0.0F};      // -Y 转向相机
            case 4 -> new float[]{-90.0F, 0.0F, 0.0F};     // +Y 转向相机
            case 5 -> new float[]{0.0F, 90.0F, 0.0F};      // -X 转向相机
            case 6 -> new float[]{0.0F, 180.0F, 0.0F};     // -Z 转向相机
            default -> new float[]{0.0F, 0.0F, 0.0F};
        };
    }

    private static float getNextAlignedAngle(float current, float targetMod, float minForward) {
        float curMod = (current % 360.0F + 360.0F) % 360.0F;
        float delta = (targetMod - curMod + 360.0F) % 360.0F;
        return current + minForward + delta;
    }

    /**
     * 重写容器物品悬停提示：将估值无缝追加到输入槽物品的 Tooltip 下方，彻底避免双框叠印
     */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> list = new ArrayList<>(super.getTooltipFromContainerItem(stack));

        if (this.hoveredSlot != null && this.hoveredSlot.index == UniversalLotteryMachineBlockEntity.SLOT_INPUT) {
            long unitVal = LotteryValuator.getUnitValue(stack);
            int bet = (this.clientSelectedTab == 1) ? this.clientWheelBet : (this.clientSelectedTab == 2 ? this.clientSlotMultiplier : stack.getCount());
            bet = Math.max(1, Math.min(stack.getCount(), bet));
            long currentPoints = unitVal * bet;
            LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(currentPoints);

            list.add(Component.empty());
            list.add(Component.literal("§6§l✦ 万能估值 ✦"));
            list.add(Component.literal("§7当前押注点数: §e" + NUMBER_FORMAT.format(currentPoints) + (stack.getMaxStackSize() > 1 && this.clientSelectedTab != 0 ? " §8(单件 " + NUMBER_FORMAT.format(unitVal) + " ×" + bet + ")" : "")));
            list.add(Component.literal("§7当前评级: " + tier.getDisplayName()));

            LotteryValuator.RarityTier next = tier.getNextTier();
            if (next != null) {
                long needed = Math.max(0, next.getMinPoints() - currentPoints);
                list.add(Component.literal("§7升至 [" + next.getDisplayName() + "] 需补: §f" + NUMBER_FORMAT.format(needed)));
            } else {
                list.add(Component.literal("§d★ 已达至高神话评级！"));
            }
        }

        return list;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int relX = this.leftPos;
        int relY = this.topPos;

        // 1. 左侧 3 个玩法切换标签悬停提示
        int tabX = relX - 42;
        if (mouseX >= tabX && mouseX < relX) {
            if (mouseY >= relY + 22 && mouseY <= relY + 48) {
                renderMultilineTooltip(guiGraphics, "§e§l[玩法 1] 虚空骰子\n§7全额押注，3D命运天体骰子翻滚，保底高阶超神！", mouseX, mouseY);
            } else if (mouseY >= relY + 52 && mouseY <= relY + 78) {
                renderMultilineTooltip(guiGraphics, "§d§l[玩法 2] 虚空轮盘\n§7可单次微量下注，8分星轨轮盘，极具视觉冲击！", mouseX, mouseY);
            } else if (mouseY >= relY + 82 && mouseY <= relY + 108) {
                renderMultilineTooltip(guiGraphics, "§6§l[玩法 3] 虚空老虎机\n§7支持 [1抽] [2连] [5连] 连抽机制，连续旋转自动开奖！", mouseX, mouseY);
            }
        }

        // 2. 抽奖/旋转/拉动核心按钮悬浮提示
        int btnX = relX + 144;
        int btnY = relY + 76;
        int btnW = 44;
        int btnH = 24;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            int bet = (this.clientSelectedTab == 1) ? this.clientWheelBet : (this.clientSelectedTab == 2 ? this.clientSlotMultiplier : 1);
            if (this.menu.canRoll(this.clientSelectedTab, bet)) {
                String action = (this.clientSelectedTab == 1) ? "旋转轮盘" : (this.clientSelectedTab == 2 ? (bet > 1 ? (bet + "连抽") : "拉动老虎机") : "投掷骰子");
                guiGraphics.renderTooltip(this.font, Component.literal("§e点击启动" + action + "！(消耗 ×" + bet + ")"), mouseX, mouseY);
            } else if (this.menu.isRolling()) {
                guiGraphics.renderTooltip(this.font, Component.literal("§b命运转折中，虚空折跃中..."), mouseX, mouseY);
            } else if (!this.menu.hasInput()) {
                guiGraphics.renderTooltip(this.font, Component.literal("§7请先在左侧放入物品"), mouseX, mouseY);
            } else if (this.menu.hasOutput()) {
                guiGraphics.renderTooltip(this.font, Component.literal("§c请先取出右侧的产出奖励"), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.literal("§c物品数量不足以支撑本次下注 (需 ×" + bet + ")"), mouseX, mouseY);
            }
        }

        // 3. 老虎机模式：右侧机体外机械拉杆点击提示与 1x/2x/5x 连抽说明
        if (this.clientSelectedTab == 2) {
            int leverX = relX + 176;
            int leverY = relY + 20;
            if (mouseX >= leverX && mouseX <= leverX + 20 && mouseY >= leverY && mouseY <= leverY + 46) {
                renderMultilineTooltip(guiGraphics, "§e§l✦ 机械拉杆 ✦\n§7点击立刻拉下把手启动老虎机！", mouseX, mouseY);
            }

            int inX = relX + UniversalLotteryMachineMenu.INPUT_SLOT_X - 1;
            int inY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y - 1;
            int slotBtnY = inY + 23;
            int[] mults = new int[]{1, 2, 5};
            for (int i = 0; i < 3; i++) {
                int bx = relX + 6 + i * 16;
                if (mouseX >= bx && mouseX <= bx + 14 && mouseY >= slotBtnY && mouseY <= slotBtnY + 13) {
                    int m = mults[i];
                    String tip = switch (m) {
                        case 1 -> "§e§l✦ 单次拉霸 ✦\n§7消耗 1 个物品进行 1 次标准转动。";
                        case 2 -> "§e§l✦ 2连抽 (出货即停) ✦\n§7最多连续转动 2 次。\n§a若提前摇出 3 连大奖立即停机，并退还余料！\n§7若未出大奖，送入最佳战利品。";
                        default -> "§e§l✦ 5连抽 (出货即停) ✦\n§7最多连续转动 5 次。\n§a若提前摇出 3 连大奖立即停机，并退还余料！\n§7若未出大奖，送入 5 抽中品质最高的战利品。";
                    };
                    renderMultilineTooltip(guiGraphics, tip, mouseX, mouseY);
                    break;
                }
            }
        }

        // 4. 空输入槽时的占位提示
        int inX = relX + UniversalLotteryMachineMenu.INPUT_SLOT_X - 1;
        int inY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y - 1;
        if (!this.menu.hasInput() && mouseX >= inX && mouseX <= inX + 20 && mouseY >= inY && mouseY <= inY + 20) {
            guiGraphics.renderTooltip(this.font, Component.literal("§7放入任意物品进行估值押注 (虚空残片除外)"), mouseX, mouseY);
        }

        // 5. 幸运仪表盘悬浮提示 (模式 0 与 模式 1)
        if (this.clientSelectedTab != 2) {
            int meterX = relX + 70;
            int meterY = relY + 84;
            if (mouseX >= meterX && mouseX <= meterX + 56 && mouseY >= meterY && mouseY <= meterY + 22) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§b§l虚空幸运几率: §e" + this.menu.getOddsPercent() + "%"));
                tooltip.add(Component.literal("§7物品价值越高，摇中顶级神物与暴击的几率越大。"));
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }

        // 6. 轮盘模式：鼠标悬停扇区展示定向领域说明
        if (this.clientSelectedTab == 1) {
            int cx = relX + 98;
            int cy = relY + 54;
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist >= 6 && dist <= 30) {
                double mouseAngle = (Math.toDegrees(Math.atan2(dx, -dy)) - (this.wheelAngle % 360.0F) + 3600.0F) % 360.0F;
                int sector = ((int) Math.floor((mouseAngle + 22.5) / 45.0)) % 8;
                String[] sectorDescs = new String[]{
                    "§6§l[0] 黄金之实\n§7金苹果、附魔金苹果、强效补给食物包",
                    "§8§l[1] 下界之核\n§7下界合金锭、远古残骸、锻造模板与合金碎片",
                    "§b§l[2] 璀璨钻石\n§7钻石块、钻石、绿宝石与海晶核心",
                    "§5§l[3] 虚空神星 (Grand Jackpot)\n§d下界之星、龙蛋、潮涌核心、不死图腾与信标",
                    "§2§l[4] 末影之眼\n§7末影之眼、潜影壳、末影箱与回响碎片",
                    "§d§l[5] 远古秘卷\n§7高阶宝藏附魔书与全套强化神装",
                    "§7§l[6] 炼金重金\n§7铁块、金块、铜块工业工程大礼包",
                    "§c§l[7] 神秘盲盒\n§750%几率开出意外神物，或赠送大量虚空残片"
                };
                renderMultilineTooltip(guiGraphics, sectorDescs[sector], mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int relX = this.leftPos;
        int relY = this.topPos;

        // 1. 绘制左侧 3 个模式突出标签
        renderModeTabs(guiGraphics, relX, relY, mouseX, mouseY);

        // 2. 背景主面板 (深邃石质星空暗色 + 双层古典鎏金框)
        guiGraphics.fill(relX, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFF12141C);
        guiGraphics.renderOutline(relX, relY, this.imageWidth, this.imageHeight, 0xFFD4AF37);
        guiGraphics.renderOutline(relX + 2, relY + 2, this.imageWidth - 4, this.imageHeight - 4, 0xFF353B48);

        // 3. 顶部饰带与唯一主标题 (完全水平居中)
        guiGraphics.fill(relX + 4, relY + 4, relX + this.imageWidth - 4, relY + 18, 0xFF1E212B);
        guiGraphics.renderOutline(relX + 4, relY + 4, this.imageWidth - 8, 14, 0xFF6A5328);

        String titleStr = switch (this.clientSelectedTab) {
            case 1 -> "✦ 万能抽奖机：虚空轮盘 ✦";
            case 2 -> "✦ 万能抽奖机：虚空老虎机 ✦";
            default -> "✦ 万能抽奖机：虚空骰子 ✦";
        };
        int titleW = this.font.width(titleStr);
        guiGraphics.drawString(this.font, titleStr, relX + (this.imageWidth - titleW) / 2, relY + 7, 0xFFFDE047, false);

        // 4. 左侧输入槽
        int inX = relX + UniversalLotteryMachineMenu.INPUT_SLOT_X - 1;
        int inY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y - 1;
        guiGraphics.fill(inX, inY, inX + 20, inY + 20, 0xFF0A0C12);
        guiGraphics.renderOutline(inX, inY, 20, 20, 0xFFD4AF37);

        // 输入槽上方标签居中对齐于槽位中线 (relX + 29)
        String slotLabel = (this.clientSelectedTab == 0) ? "骰子" : "下注";
        guiGraphics.drawString(this.font, slotLabel, relX + 29 - this.font.width(slotLabel) / 2, relY + 34, 0xFFE0E0E0, false);

        // 5. 根据所选标签渲染左侧附属控制
        renderLeftSlotControls(guiGraphics, relX, relY, inX, inY, mouseX, mouseY);

        // 6. 中央核心游戏区域 (模式 0: 3D 骰子天体舱; 模式 1: 轮盘; 模式 2: 老虎机滚轮)
        switch (this.clientSelectedTab) {
            case 1 -> renderWheelMode(guiGraphics, relX, relY, partialTick);
            case 2 -> renderSlotMode(guiGraphics, relX, relY, partialTick);
            default -> renderDiceMode(guiGraphics, relX, relY, partialTick);
        }

        // 7. 右侧奖励槽位
        int outX = relX + UniversalLotteryMachineMenu.OUTPUT_SLOT_X - 1;
        int outY = relY + UniversalLotteryMachineMenu.OUTPUT_SLOT_Y - 1;
        guiGraphics.fill(outX, outY, outX + 20, outY + 20, 0xFF0F0A1C);
        guiGraphics.renderOutline(outX, outY, 20, 20, 0xFFFACC15);

        // 奖励标签居中对齐于槽位中线 (relX + 167)
        String rewardLabel = "奖励";
        guiGraphics.drawString(this.font, rewardLabel, relX + 167 - this.font.width(rewardLabel) / 2, relY + 34, 0xFFFFE082, false);

        // 8. 右侧操作核心按钮 (ROLL / SPIN / PULL)
        renderActionButton(guiGraphics, relX, relY, mouseX, mouseY);

        // 9. 玩家背包与快捷栏 (居中对齐)
        renderPlayerInventoryGrid(guiGraphics, relX, relY);
    }

    /**
     * 绘制左侧外突的 3 个玩法切换标签
     */
    private void renderModeTabs(GuiGraphics guiGraphics, int relX, int relY, int mouseX, int mouseY) {
        String[] tabNames = new String[]{"🎲 骰子", "🎡 轮盘", "🎰 拉霸"};
        for (int i = 0; i < 3; i++) {
            int tabY = relY + 22 + i * 30;
            int tabX = relX - 42;
            int tabW = 44;
            int tabH = 26;

            boolean isActive = (this.clientSelectedTab == i);
            boolean isHovered = mouseX >= tabX && mouseX < relX && mouseY >= tabY && mouseY <= tabY + tabH;

            int bgColor = isActive ? 0xFF2A154A : (isHovered ? 0xFF1C1E2B : 0xFF12141D);
            int borderColor = isActive ? 0xFFFACC15 : (isHovered ? 0xFF9CA3AF : 0xFF4B5563);

            guiGraphics.fill(tabX, tabY, relX, tabY + tabH, bgColor);
            guiGraphics.renderOutline(tabX, tabY, tabW, tabH, borderColor);
            if (isActive) {
                // 活动标签右侧与主窗口无缝相连，擦除右边框
                guiGraphics.fill(relX - 1, tabY + 1, relX + 1, tabY + tabH - 1, bgColor);
            }

            int textColor = isActive ? 0xFFFFFFFF : (isHovered ? 0xFFE2E8F0 : 0xFF94A3B8);
            // 标签文本水平与垂直严格居中
            int tx = tabX + (tabW - this.font.width(tabNames[i])) / 2;
            int ty = tabY + (tabH - 9) / 2;
            guiGraphics.drawString(this.font, tabNames[i], tx, ty, textColor, true);
        }
    }

    /**
     * 绘制输入槽下方的各模式专属控件
     */
    private void renderLeftSlotControls(GuiGraphics guiGraphics, int relX, int relY, int inX, int inY, int mouseX, int mouseY) {
        long currentPoints = this.menu.getEstimatedPoints();
        boolean hasItem = this.menu.hasInput() || this.menu.isRolling();

        if (this.clientSelectedTab == 0) {
            // 模式 0: 胶囊点数 (紧贴输入槽下方，完全不遮挡槽位)
            if (hasItem && currentPoints > 0) {
                String shortPts = currentPoints >= 100000L ? (currentPoints / 1000L + "k") : String.valueOf(currentPoints);
                int ptsW = this.font.width(shortPts);
                int boxW = Math.max(22, ptsW + 4);
                int boxX = inX + 10 - boxW / 2;
                guiGraphics.fill(boxX, inY + 22, boxX + boxW, inY + 32, 0xEE0F131C);
                guiGraphics.renderOutline(boxX, inY + 22, boxW, 10, 0xFF6A5328);
                guiGraphics.drawString(this.font, shortPts, inX + 10 - ptsW / 2, inY + 23, 0xFFFACC15, false);
            }

            // 6 阶稀有度指示标：双列整齐徽章，列间距加大至 5px，彻底解决色块压住上一列字的问题
            // Col 0: bx = relX + 5 (dot: 5..9, text: 11..29)
            // Col 1: bx = relX + 34 (dot: 34..38, text: 40..58)
            LotteryValuator.RarityTier currentTier = this.menu.getTier();
            LotteryValuator.RarityTier[] tiers = LotteryValuator.RarityTier.values();
            for (int i = 0; i < tiers.length; i++) {
                LotteryValuator.RarityTier tier = tiers[i];
                boolean active = hasItem && (currentTier == tier);
                int row = i / 2;
                int col = i % 2;
                int bx = (col == 0) ? (relX + 5) : (relX + 34);
                int by = relY + 80 + row * 13;
                drawRarityBadge(guiGraphics, bx, by, tier.getDisplayName(), tier.getColor(), active);
            }
        } else if (this.clientSelectedTab == 1) {
            // 模式 1: 轮盘单次下注数量调整 [-] xN [+]
            int btnY = inY + 23;
            int minusX = relX + 8;
            int plusX = relX + 38;

            boolean hoverMinus = mouseX >= minusX && mouseX <= minusX + 12 && mouseY >= btnY && mouseY <= btnY + 12;
            boolean hoverPlus = mouseX >= plusX && mouseX <= plusX + 12 && mouseY >= btnY && mouseY <= btnY + 12;

            guiGraphics.fill(minusX, btnY, minusX + 12, btnY + 12, hoverMinus ? 0xFF4B5563 : 0xFF1F2937);
            guiGraphics.renderOutline(minusX, btnY, 12, 12, 0xFFD4AF37);
            guiGraphics.drawString(this.font, "-", minusX + 4, btnY + 2, 0xFFFFFFFF, false);

            String betStr = "×" + this.clientWheelBet;
            guiGraphics.drawString(this.font, betStr, relX + 29 - this.font.width(betStr) / 2, btnY + 2, 0xFFFACC15, false);

            guiGraphics.fill(plusX, btnY, plusX + 12, btnY + 12, hoverPlus ? 0xFF4B5563 : 0xFF1F2937);
            guiGraphics.renderOutline(plusX, btnY, 12, 12, 0xFFD4AF37);
            guiGraphics.drawString(this.font, "+", plusX + 3, btnY + 2, 0xFFFFFFFF, false);

            // 快捷概率一览 (留足间距，不与物品栏标签冲突)
            guiGraphics.drawString(this.font, "§7普通 50%", relX + 6, relY + 83, 0xFF94A3B8, false);
            guiGraphics.drawString(this.font, "§b稀有 28%", relX + 6, relY + 92, 0xFF38BDF8, false);
            guiGraphics.drawString(this.font, "§d史诗 16%", relX + 6, relY + 101, 0xFFC084FC, false);
            guiGraphics.drawString(this.font, "§6传说  6%", relX + 6, relY + 110, 0xFFFBBF24, false);
        } else {
            // 模式 2: 老虎机连抽按钮 [1x] [2x] [5x]
            int[] mults = new int[]{1, 2, 5};
            int btnY = inY + 23;
            for (int i = 0; i < 3; i++) {
                int m = mults[i];
                int bx = relX + 6 + i * 16;
                boolean selected = (this.clientSlotMultiplier == m);
                boolean hovered = mouseX >= bx && mouseX <= bx + 14 && mouseY >= btnY && mouseY <= btnY + 13;

                int color = selected ? 0xFF7C3AED : (hovered ? 0xFF374151 : 0xFF1F2937);
                int border = selected ? 0xFFFACC15 : 0xFF4B5563;

                guiGraphics.fill(bx, btnY, bx + 14, btnY + 13, color);
                guiGraphics.renderOutline(bx, btnY, 14, 13, border);

                String label = m + "x";
                int tx = bx + (14 - this.font.width(label)) / 2;
                guiGraphics.drawString(this.font, label, tx, btnY + 3, selected ? 0xFFFFFFFF : 0xFF9CA3AF, false);
            }

            // 中奖说明
            guiGraphics.drawString(this.font, "§d★ 3连大奖", relX + 6, relY + 85, 0xFFE879F9, false);
            guiGraphics.drawString(this.font, "§e★ 2连保本", relX + 6, relY + 96, 0xFFFDE047, false);
            guiGraphics.drawString(this.font, "§7★ 散牌安慰", relX + 6, relY + 107, 0xFF94A3B8, false);
        }
    }

    /**
     * 模式 0: 虚空骰子 (3D 立体旋转天体骰子与几率表盘)
     */
    private void renderDiceMode(GuiGraphics guiGraphics, int relX, int relY, float partialTick) {
        int chamberX = relX + 76;
        int chamberY = relY + 28;
        int chamberSize = 44;

        guiGraphics.fill(chamberX, chamberY, chamberX + chamberSize, chamberY + chamberSize, 0xFF0A0714);
        guiGraphics.renderOutline(chamberX, chamberY, chamberSize, chamberSize, 0xFF8A5CE6);
        guiGraphics.renderOutline(chamberX - 1, chamberY - 1, chamberSize + 2, chamberSize + 2, 0xFFD4AF37);

        // 渲染 3D 翻滚天体骰子
        renderCelestialDie(guiGraphics, chamberX + chamberSize / 2, chamberY + chamberSize / 2, partialTick);

        // 幸运与折跃表盘
        int meterX = relX + 70;
        int meterY = relY + 84;
        guiGraphics.fill(meterX, meterY, meterX + 56, meterY + 22, 0xFF141720);
        guiGraphics.renderOutline(meterX, meterY, 56, 22, 0xFF4A3B22);

        int odds = getCurrentDisplayOdds();
        guiGraphics.fill(meterX + 4, meterY + 4, meterX + 28, meterY + 7, 0xFFDC2626);
        guiGraphics.fill(meterX + 28, meterY + 4, meterX + 52, meterY + 7, 0xFF2563EB);

        int needlePos = meterX + 4 + (int) Math.round(48.0 * (odds / 100.0));
        guiGraphics.fill(needlePos - 1, meterY + 2, needlePos + 1, meterY + 9, 0xFFFACC15);

        String oddsText = odds + "%";
        guiGraphics.drawString(this.font, oddsText, meterX + (56 - this.font.width(oddsText)) / 2, meterY + 11, 0xFFFDE047, false);
    }

    /**
     * 模式 1: 虚空轮盘 (8 扇区可旋转大轮盘 + 12 点钟指针)
     */
    private void renderWheelMode(GuiGraphics guiGraphics, int relX, int relY, float partialTick) {
        int centerX = relX + 98;
        int centerY = relY + 54;

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);

        // 旋转轮盘底盘
        pose.mulPose(Axis.ZP.rotationDegrees(this.wheelAngle));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        VertexConsumer buffer = guiGraphics.bufferSource().getBuffer(RenderType.gui());

        // 1. 批量绘制 8 个彩色扇面 (半径 6 到 28)
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 45.0F));
            drawWheelSector(pose.last().pose(), buffer, WHEEL_SECTOR_COLORS[i]);
            pose.popPose();
        }

        // 刷新 GUI 顶点批次，保证后续调用 renderItem 不会破坏或冲突 BufferBuilder 状态
        guiGraphics.flush();
        RenderSystem.enableCull();

        // 2. 扇面上绘制对应的物品微缩图标 (半径 18 处)
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 45.0F));
            pose.translate(-6, -24, 0);
            pose.scale(0.75F, 0.75F, 1.0F);
            guiGraphics.renderItem(WHEEL_SECTOR_ITEMS[i], 0, 0);
            pose.popPose();
        }

        // 外圈金边环与外沿铆钉
        for (int i = 0; i < 8; i++) {
            float a = i * 45.0F * Mth.DEG_TO_RAD;
            int px = (int) (29.0F * Mth.sin(a));
            int py = (int) (-29.0F * Mth.cos(a));
            guiGraphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFDE047);
        }

        pose.popPose();

        // 轮盘中央金星轴心
        guiGraphics.fill(centerX - 5, centerY - 5, centerX + 5, centerY + 5, 0xFFD4AF37);
        guiGraphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFFA855F7);
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xFFFFFFFF);

        // 12 点钟方向金色指针 (带过格颤动动画)
        pose.pushPose();
        pose.translate(centerX, centerY - 29, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(this.needleJiggle));

        guiGraphics.fill(-3, -7, 4, -1, 0xFFD4AF37);
        guiGraphics.fill(-2, -1, 3, 1, 0xFFFACC15);
        guiGraphics.fill(-1, 1, 2, 3, 0xFFEF4444); // 尖端红宝石
        guiGraphics.fill(0, 3, 1, 5, 0xFFEF4444);

        pose.popPose();

        // 轮盘下方幸运值进度条 (文字居中，下方为高科技进度条)
        int meterX = relX + 70;
        int meterY = relY + 86;
        guiGraphics.fill(meterX, meterY, meterX + 56, meterY + 16, 0xFF111827);
        guiGraphics.renderOutline(meterX, meterY, 56, 16, 0xFFD4AF37);

        int odds = getCurrentDisplayOdds();
        int fillW = (int) (52.0F * (odds / 100.0F));
        // 底部 2px 科技紫光进度细条
        guiGraphics.fill(meterX + 2, meterY + 12, meterX + 2 + fillW, meterY + 14, 0xFFA855F7);

        String luckText = "幸运: " + odds + "%";
        // 文字在 16px 高度框体内完美垂直居中 (meterY + 2，至 11px)
        guiGraphics.drawString(this.font, luckText, meterX + (56 - this.font.width(luckText)) / 2, meterY + 2, 0xFFFDE047, false);
    }

    private void drawWheelSector(Matrix4f matrix, VertexConsumer buffer, int color) {
        float rIn = 6.0F;
        float rOut = 28.0F;
        int steps = 4;
        float startAngle = -22.5F * Mth.DEG_TO_RAD;
        float endAngle = 22.5F * Mth.DEG_TO_RAD;
        float step = (endAngle - startAngle) / steps;

        for (int i = 0; i < steps; i++) {
            float a0 = startAngle + i * step;
            float a1 = startAngle + (i + 1) * step;

            float x0In = rIn * Mth.sin(a0);
            float y0In = -rIn * Mth.cos(a0);
            float x0Out = rOut * Mth.sin(a0);
            float y0Out = -rOut * Mth.cos(a0);

            float x1In = rIn * Mth.sin(a1);
            float y1In = -rIn * Mth.cos(a1);
            float x1Out = rOut * Mth.sin(a1);
            float y1Out = -rOut * Mth.cos(a1);

            // 严格采用 CCW 逆时针顶点顺序，确保在所有平台着色器中均能正确呈现
            buffer.addVertex(matrix, x0In, y0In, 0.0F).setColor(color);
            buffer.addVertex(matrix, x1In, y1In, 0.0F).setColor(color);
            buffer.addVertex(matrix, x1Out, y1Out, 0.0F).setColor(color);
            buffer.addVertex(matrix, x0Out, y0Out, 0.0F).setColor(color);
        }
    }

    /**
     * 模式 2: 虚空老虎机 (三轴机械滚轮 + 霓虹中轴激光 + 机械拉杆 + 听牌悬念)
     */
    private void renderSlotMode(GuiGraphics guiGraphics, int relX, int relY, float partialTick) {
        int bannerY = relY + 23;

        boolean isRolling = this.menu.isRolling();
        int rollTicks = this.menu.getRollTicks();
        int totalTicks = this.menu.getTotalRollDurationTicks();
        boolean extended = (totalTicks == UniversalLotteryMachineBlockEntity.EXTENDED_ROLL_DURATION_TICKS);
        boolean isSuspense = isRolling && extended && rollTicks < 32;

        String jpText;
        int textColor = 0xFFFDE047;

        if (isSuspense) {
            // 听牌悬念：霓虹粉红狂暴快闪
            boolean flash = ((rollTicks / 3) % 2 == 0);
            textColor = flash ? 0xFFF43F5E : 0xFFFACC15;
            jpText = "⚠ 听牌连击中 ⚠";
        } else if (isRolling) {
            int total = this.menu.getMultiRollRequested();
            int remaining = this.menu.getRemainingMultiRolls();
            if (total > 1) {
                int current = Math.min(total, Math.max(1, total - remaining));
                jpText = "★ 连抽冲刺中: [" + current + " / " + total + "] ★";
            } else {
                jpText = "★ JACKPOT x10 ★";
            }
        } else {
            int total = this.menu.getMultiRollRequested();
            int s1 = this.menu.getSlotReel1();
            int s2 = this.menu.getSlotReel2();
            int s3 = this.menu.getSlotReel3();
            if (s1 == 0 && s2 == 0 && s3 == 0) {
                jpText = (total > 1) ? "★ GRAND JACKPOT! 终极大奖出货! ★" : "★ GRAND JACKPOT! ★";
                textColor = 0xFFFFD700;
            } else if (s1 == s2 && s2 == s3) {
                jpText = (total > 1) ? "★ 提前命中大奖! 自动止损返还 ★" : "★ 3连大奖全中! ★";
                textColor = 0xFFFDE047;
            } else if (s1 == s2 || s2 == s3 || s1 == s3) {
                jpText = (total > 1) ? "✦ 连抽完成: 保留最佳双连 ✦" : "✦ 双连保本 ✦";
                textColor = 0xFF93C5FD;
            } else if (total > 1) {
                jpText = "★ " + total + "抽完毕，精选最佳战利品 ★";
                textColor = 0xFFFACC15;
            } else {
                jpText = "✦ 散牌安慰 ✦";
                textColor = 0xFF9CA3AF;
            }
        }

        // 彻底移除拘束固定小边框，文字在机柜顶部自然居中舒展呈现
        int centerX = relX + 98;
        int textW = this.font.width(jpText);
        guiGraphics.drawString(this.font, jpText, centerX - textW / 2, bannerY, textColor, true);

        // 老虎机机体机柜 (3 轴滚轮显示框)
        int cabinetX = relX + 54;
        int cabinetY = relY + 36;
        int cabinetW = 88;
        int cabinetH = 62;

        guiGraphics.fill(cabinetX, cabinetY, cabinetX + cabinetW, cabinetY + cabinetH, 0xFF0D0F18);
        int cabinetBorder = isSuspense ? 0xFFF43F5E : 0xFFD4AF37;
        guiGraphics.renderOutline(cabinetX, cabinetY, cabinetW, cabinetH, cabinetBorder);

        // 3 轴滚轮列
        int[] reelX = new int[]{cabinetX + 4, cabinetX + 32, cabinetX + 60};
        float[] scrolls = new float[]{this.reel0Scroll, this.reel1Scroll, this.reel2Scroll};

        // 开启剪裁视口，确保滚轮符号仅在机柜内可见
        guiGraphics.enableScissor(cabinetX + 2, cabinetY + 2, cabinetX + cabinetW - 2, cabinetY + cabinetH - 2);

        int paylineY = cabinetY + 23;

        for (int r = 0; r < 3; r++) {
            int cx = reelX[r];
            guiGraphics.fill(cx, cabinetY + 2, cx + 24, cabinetY + cabinetH - 2, 0xFF1A1C28);
            guiGraphics.renderOutline(cx, cabinetY + 2, 24, cabinetH - 4, 0xFF374151);

            float scroll = scrolls[r];
            int totalSyms = SLOT_SYMBOLS.length;
            int baseIdx = ((int) Math.floor(scroll / 18.0F)) % totalSyms;
            float offsetFrac = scroll % 18.0F;

            // 绘制当前可见的上下各 3 个符号
            for (int k = -2; k <= 2; k++) {
                int symIdx = (baseIdx + k + totalSyms * 100) % totalSyms;
                int drawY = paylineY + k * 18 - (int) offsetFrac;
                guiGraphics.renderItem(SLOT_SYMBOLS[symIdx], cx + 4, drawY);
            }
        }

        guiGraphics.disableScissor();

        // 中轴发光激光赔率线
        guiGraphics.fill(cabinetX + 1, paylineY + 8, cabinetX + cabinetW - 1, paylineY + 9, 0xFFE879F9);
        guiGraphics.fill(cabinetX + 2, paylineY + 7, cabinetX + cabinetW - 2, paylineY + 8, 0x40E879F9);

        // 若当前 3 滚轮全中，展示超级赢家光环
        if (!this.menu.isRolling() && this.menu.getSlotReel1() == this.menu.getSlotReel2()
                && this.menu.getSlotReel2() == this.menu.getSlotReel3()) {
            for (int r = 0; r < 3; r++) {
                guiGraphics.renderOutline(reelX[r] + 2, paylineY - 2, 20, 20, 0xFFFACC15);
            }
        }

        // 右侧机柜外机械操纵杆 (外置于机柜右侧，绝不遮挡奖励槽位)
        renderLever(guiGraphics, relX + 185, relY + 44);
    }

    /**
     * 绘制老虎机机械拉杆 (位于机体最右侧侧翼)
     */
    private void renderLever(GuiGraphics guiGraphics, int pivotX, int pivotY) {
        // 基座 (位于 relX + 182 到 192，完全在奖励槽右侧)
        guiGraphics.fill(pivotX - 3, pivotY + 12, pivotX + 7, pivotY + 20, 0xFF475569);
        guiGraphics.renderOutline(pivotX - 3, pivotY + 12, 10, 8, 0xFFD4AF37);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(pivotX + 2, pivotY + 14, 0);

        // 下拉动画角度 (正常 -25 度，拉下时向右下偏转至 +35 度)
        float pullAngle = -25.0F + (float) Math.sin(this.leverPullTimer * Math.PI) * 60.0F;
        pose.mulPose(Axis.ZP.rotationDegrees(pullAngle));

        // 杆体
        guiGraphics.fill(-2, -18, 2, 0, 0xFFE2E8F0);
        guiGraphics.renderOutline(-2, -18, 4, 18, 0xFF94A3B8);

        // 亮红圆球顶把手
        guiGraphics.fill(-5, -24, 5, -16, 0xFFDC2626);
        guiGraphics.fill(-3, -22, 1, -19, 0xFFFCA5A5); // 高光

        pose.popPose();
    }

    /**
     * 绘制右侧执行操作按钮
     */
    private void renderActionButton(GuiGraphics guiGraphics, int relX, int relY, int mouseX, int mouseY) {
        int btnX = relX + 144;
        int btnY = relY + 76;
        int btnW = 44;
        int btnH = 24;

        int bet = (this.clientSelectedTab == 1) ? this.clientWheelBet : (this.clientSelectedTab == 2 ? this.clientSlotMultiplier : 1);
        boolean canRoll = this.menu.canRoll(this.clientSelectedTab, bet);
        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        int btnColor = canRoll ? (hovered ? 0xFFF59E0B : 0xFFD97706) : 0xFF4B5563;
        int btnBorder = canRoll ? 0xFFFDE68A : 0xFF374151;

        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, btnBorder);

        String btnLabel;
        if (this.menu.isRolling()) {
            btnLabel = "ROLL...";
        } else {
            btnLabel = switch (this.clientSelectedTab) {
                case 1 -> "旋转";
                case 2 -> (this.clientSlotMultiplier > 1 ? (this.clientSlotMultiplier + "连抽") : "拉霸");
                default -> "掷骰";
            };
        }

        int labelColor = canRoll ? 0xFFFFFFFF : 0xFF9CA3AF;
        guiGraphics.drawString(this.font, btnLabel, btnX + (btnW - this.font.width(btnLabel)) / 2, btnY + 8, labelColor, true);

        // 消耗提示 (位于按钮正下方，字色温润清晰)
        if (this.clientSelectedTab != 0) {
            String costText = (this.clientSelectedTab == 2 && bet > 1) ? ("最多 ×" + bet) : ("消耗 ×" + bet);
            int cw = this.font.width(costText);
            guiGraphics.drawString(this.font, costText, btnX + (btnW - cw) / 2, btnY + 26, 0xFF94A3B8, false);
        }
    }

    /**
     * 绘制玩家背包与快捷栏网格
     */
    private void renderPlayerInventoryGrid(GuiGraphics guiGraphics, int relX, int relY) {
        guiGraphics.drawString(this.font, "物品栏", relX + 17, relY + 124, 0xFF9E9E9E, false);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = relX + 17 + col * 18;
                int sy = relY + 134 + row * 18;
                guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0xFF181B24);
                guiGraphics.renderOutline(sx - 1, sy - 1, 18, 18, 0xFF374151);
            }
        }

        for (int col = 0; col < 9; col++) {
            int sx = relX + 17 + col * 18;
            int sy = relY + 192;
            guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0xFF181B24);
            guiGraphics.renderOutline(sx - 1, sy - 1, 18, 18, 0xFF374151);
        }
    }

    private void drawRarityBadge(GuiGraphics guiGraphics, int x, int y, String label, int color, boolean active) {
        int dotColor = active ? color : 0xFF3A3E4A;
        if (active) {
            guiGraphics.fill(x - 1, y + 1, x + 5, y + 7, color | 0x40000000);
            guiGraphics.fill(x, y + 2, x + 4, y + 6, dotColor);
            guiGraphics.drawString(this.font, label, x + 6, y, color, true);
        } else {
            guiGraphics.fill(x, y + 2, x + 4, y + 6, dotColor);
            guiGraphics.drawString(this.font, label, x + 6, y, 0xFF5B6270, false);
        }
    }

    /**
     * 模式 0 核心动画：3D 立体悬浮翻滚天体骰子 (带真实 1~6 骰子面与金边红心点数)
     */
    private void renderCelestialDie(GuiGraphics guiGraphics, int centerX, int centerY, float partialTick) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 40.0F);

        float rx = this.diceRotX + (this.menu.isRolling() ? partialTick * 26.0F : partialTick * 0.8F);
        float ry = this.diceRotY + (this.menu.isRolling() ? partialTick * 34.0F : partialTick * 1.2F);
        float rz = this.diceRotZ + (this.menu.isRolling() ? partialTick * 18.0F : partialTick * 0.5F);

        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.flush();

        float s = 9.0F; // 骰子半长，构成 18x18x18 的精巧立方体

        // 绘制 6 个带点数的 3D 骰子面 (相反面点数和为 7)
        // 面 1: 正面 (+Z)
        pose.pushPose();
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 1);
        pose.popPose();

        // 面 6: 背面 (-Z)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 6);
        pose.popPose();

        // 面 2: 右面 (+X)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 2);
        pose.popPose();

        // 面 5: 左面 (-X)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 5);
        pose.popPose();

        // 面 3: 顶面 (-Y)
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 3);
        pose.popPose();

        // 面 4: 底面 (+Y)
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 4);
        pose.popPose();

        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        pose.popPose();
    }

    /**
     * 绘制 3D 骰子的单个面与点数
     */
    private void drawDiceFace(GuiGraphics guiGraphics, float s, int pips) {
        int is = (int) s;
        // 骰子面底色：深邃虚空黑曜石底板
        guiGraphics.fill(-is, -is, is, is, 0xFF181028);
        // 内圈微光
        guiGraphics.fill(-is + 1, -is + 1, is - 1, is - 1, 0xFF2B1C47);
        // 鎏金细边
        guiGraphics.renderOutline(-is, -is, is * 2, is * 2, 0xFFD4AF37);

        // 绘制骰子点数 pips
        int pipColor = (pips == 1) ? 0xFFEF4444 : 0xFFFDE047; // 1 点为红心虚空大点，其余为金星点
        int d = 4; // 点距中心的间距

        switch (pips) {
            case 1 -> guiGraphics.fill(-1, -1, 2, 2, pipColor); // 3x3 醒目红心
            case 2 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 3 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, 0, 0, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 4 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, d, -d, pipColor);
                drawPip(guiGraphics, -d, d, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 5 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, d, -d, pipColor);
                drawPip(guiGraphics, 0, 0, pipColor);
                drawPip(guiGraphics, -d, d, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 6 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, -d, 0, pipColor);
                drawPip(guiGraphics, -d, d, pipColor);
                drawPip(guiGraphics, d, -d, pipColor);
                drawPip(guiGraphics, d, 0, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
        }
    }

    private void drawPip(GuiGraphics guiGraphics, int px, int py, int color) {
        guiGraphics.fill(px - 1, py - 1, px + 1, py + 1, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int relX = this.leftPos;
            int relY = this.topPos;

            // 1. 切换左侧 3 个模式标签
            int tabX = relX - 42;
            if (mouseX >= tabX && mouseX < relX && !this.menu.isRolling()) {
                for (int i = 0; i < 3; i++) {
                    int ty = relY + 22 + i * 30;
                    if (mouseY >= ty && mouseY <= ty + 26) {
                        this.clientSelectedTab = i;
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.2F);
                        }
                        return true;
                    }
                }
            }

            // 2. 模式 1: 轮盘单次下注微调 [-] [+]
            if (this.clientSelectedTab == 1 && !this.menu.isRolling()) {
                int btnY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y + 22;
                int minusX = relX + 8;
                int plusX = relX + 38;

                if (mouseX >= minusX && mouseX <= minusX + 12 && mouseY >= btnY && mouseY <= btnY + 12) {
                    if (this.clientWheelBet > 1) {
                        this.clientWheelBet--;
                        playClickSound();
                    }
                    return true;
                }
                if (mouseX >= plusX && mouseX <= plusX + 12 && mouseY >= btnY && mouseY <= btnY + 12) {
                    int maxStack = this.menu.hasInput() ? this.menu.getInputStack().getCount() : 64;
                    if (this.clientWheelBet < maxStack) {
                        this.clientWheelBet++;
                        playClickSound();
                    }
                    return true;
                }
            }

            // 3. 模式 2: 老虎机连抽选择 [1x] [2x] [5x]
            if (this.clientSelectedTab == 2 && !this.menu.isRolling()) {
                int[] mults = new int[]{1, 2, 5};
                int btnY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y + 22;
                for (int i = 0; i < 3; i++) {
                    int bx = relX + 6 + i * 16;
                    if (mouseX >= bx && mouseX <= bx + 14 && mouseY >= btnY && mouseY <= btnY + 13) {
                        this.clientSlotMultiplier = mults[i];
                        playClickSound();
                        return true;
                    }
                }

                // 老虎机右侧拉杆点击
                int leverX = relX + 176;
                int leverY = relY + 20;
                if (mouseX >= leverX && mouseX <= leverX + 20 && mouseY >= leverY && mouseY <= leverY + 46) {
                    triggerAction();
                    return true;
                }
            }

            // 4. 右侧行动主按钮
            int btnX = relX + 144;
            int btnY = relY + 76;
            int btnW = 44;
            int btnH = 24;

            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                triggerAction();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void triggerAction() {
        int bet = (this.clientSelectedTab == 1) ? this.clientWheelBet : (this.clientSelectedTab == 2 ? this.clientSlotMultiplier : 1);
        if (this.menu.canRoll(this.clientSelectedTab, bet)) {
            playClickSound();
            PacketDistributor.sendToServer(new LotteryRollActionPayload(this.clientSelectedTab, bet));
        }
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
        }
    }

    private void renderMultilineTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        List<Component> list = new ArrayList<>();
        for (String line : text.split("\n")) {
            list.add(Component.literal(line));
        }
        guiGraphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
    }

    private static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
    }
}
