package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.lottery.LotteryRollActionPayload;
import org.gwfx.universaltool.lottery.LotteryValuator;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineBlockEntity;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineMenu;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniversalLotteryMachineScreen extends AbstractContainerScreen<UniversalLotteryMachineMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);

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
            list.add(Component.translatable("gui.universal_tool.lottery.valuation.header"));
            if (stack.getMaxStackSize() > 1 && this.clientSelectedTab != 0) {
                list.add(Component.translatable("gui.universal_tool.lottery.valuation.points_detail",
                        NUMBER_FORMAT.format(currentPoints), NUMBER_FORMAT.format(unitVal), bet));
            } else {
                list.add(Component.translatable("gui.universal_tool.lottery.valuation.points",
                        NUMBER_FORMAT.format(currentPoints)));
            }
            list.add(Component.translatable("gui.universal_tool.lottery.valuation.tier", tier.getDisplayNameComponent()));

            LotteryValuator.RarityTier next = tier.getNextTier();
            if (next != null) {
                long needed = Math.max(0, next.getMinPoints() - currentPoints);
                list.add(Component.translatable("gui.universal_tool.lottery.valuation.next_tier",
                        next.getDisplayNameComponent(), NUMBER_FORMAT.format(needed)));
            } else {
                list.add(Component.translatable("gui.universal_tool.lottery.valuation.max_tier"));
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
                renderMultilineTooltip(guiGraphics, Component.translatable("gui.universal_tool.lottery.tab.dice.tooltip"), mouseX, mouseY);
            } else if (mouseY >= relY + 52 && mouseY <= relY + 78) {
                renderMultilineTooltip(guiGraphics, Component.translatable("gui.universal_tool.lottery.tab.wheel.tooltip"), mouseX, mouseY);
            } else if (mouseY >= relY + 82 && mouseY <= relY + 108) {
                renderMultilineTooltip(guiGraphics, Component.translatable("gui.universal_tool.lottery.tab.slot.tooltip"), mouseX, mouseY);
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
                Component action = switch (this.clientSelectedTab) {
                    case 1 -> Component.translatable("gui.universal_tool.lottery.start.wheel");
                    case 2 -> (bet > 1
                            ? Component.translatable("gui.universal_tool.lottery.start.slot_multi", bet)
                            : Component.translatable("gui.universal_tool.lottery.start.slot_single"));
                    default -> Component.translatable("gui.universal_tool.lottery.start.dice");
                };
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.lottery.tooltip.click_start", action, bet), mouseX, mouseY);
            } else if (this.menu.isRolling()) {
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.lottery.tooltip.rolling"), mouseX, mouseY);
            } else if (!this.menu.hasInput()) {
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.lottery.tooltip.no_input"), mouseX, mouseY);
            } else if (this.menu.hasOutput()) {
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.lottery.tooltip.has_output"), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.lottery.tooltip.not_enough", bet), mouseX, mouseY);
            }
        }

        // 3. 老虎机模式：右侧机体外机械拉杆点击提示与 1x/2x/5x 连抽说明
        if (this.clientSelectedTab == 2) {
            int leverX = relX + 176;
            int leverY = relY + 20;
            if (mouseX >= leverX && mouseX <= leverX + 20 && mouseY >= leverY && mouseY <= leverY + 46) {
                renderMultilineTooltip(guiGraphics, Component.translatable("gui.universal_tool.lottery.tooltip.lever"), mouseX, mouseY);
            }

            int inY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y - 1;
            int slotBtnY = inY + 23;
            int[] mults = new int[]{1, 2, 5};
            for (int i = 0; i < 3; i++) {
                int bx = relX + 6 + i * 16;
                if (mouseX >= bx && mouseX <= bx + 14 && mouseY >= slotBtnY && mouseY <= slotBtnY + 13) {
                    int m = mults[i];
                    Component tip = Component.translatable("gui.universal_tool.lottery.tooltip.slot_mult." + m);
                    renderMultilineTooltip(guiGraphics, tip, mouseX, mouseY);
                    break;
                }
            }
        }

        // 4. 空输入槽时的占位提示
        int inX = relX + UniversalLotteryMachineMenu.INPUT_SLOT_X - 1;
        int inY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y - 1;
        if (!this.menu.hasInput() && mouseX >= inX && mouseX <= inX + 20 && mouseY >= inY && mouseY <= inY + 20) {
            guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.lottery.tooltip.input_hint"), mouseX, mouseY);
        }

        // 5. 幸运仪表盘悬浮提示 (模式 0 与 模式 1)
        if (this.clientSelectedTab != 2) {
            int meterX = relX + 70;
            int meterY = relY + 84;
            if (mouseX >= meterX && mouseX <= meterX + 56 && mouseY >= meterY && mouseY <= meterY + 22) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.universal_tool.lottery.tooltip.luck_title", this.menu.getOddsPercent()));
                tooltip.add(Component.translatable("gui.universal_tool.lottery.tooltip.luck_desc"));
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
                renderMultilineTooltip(guiGraphics, Component.translatable("gui.universal_tool.lottery.wheel.sector." + sector), mouseX, mouseY);
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

        Component titleComp = switch (this.clientSelectedTab) {
            case 1 -> Component.translatable("gui.universal_tool.lottery.title.wheel");
            case 2 -> Component.translatable("gui.universal_tool.lottery.title.slot");
            default -> Component.translatable("gui.universal_tool.lottery.title.dice");
        };
        int titleW = this.font.width(titleComp);
        guiGraphics.drawString(this.font, titleComp, relX + (this.imageWidth - titleW) / 2, relY + 7, 0xFFFDE047, false);

        // 4. 左侧输入槽
        int inX = relX + UniversalLotteryMachineMenu.INPUT_SLOT_X - 1;
        int inY = relY + UniversalLotteryMachineMenu.INPUT_SLOT_Y - 1;
        guiGraphics.fill(inX, inY, inX + 20, inY + 20, 0xFF0A0C12);
        guiGraphics.renderOutline(inX, inY, 20, 20, 0xFFD4AF37);

        // 输入槽上方标签居中对齐于槽位中线 (relX + 29)
        Component slotLabel = Component.translatable(this.clientSelectedTab == 0
                ? "gui.universal_tool.lottery.slot_label.dice"
                : "gui.universal_tool.lottery.slot_label.bet");
        guiGraphics.drawString(this.font, slotLabel, relX + 29 - this.font.width(slotLabel) / 2, relY + 34, 0xFFE0E0E0, false);

        // 5. 根据所选标签渲染左侧附属控制
        renderLeftSlotControls(guiGraphics, relX, relY, inX, inY, mouseX, mouseY);

        // 6. 中央核心游戏区域 (委托子模式渲染器)
        switch (this.clientSelectedTab) {
            case 1 -> LotterySubModeRenderers.renderWheelMode(
                    guiGraphics, this.font, relX, relY, this.wheelAngle, this.needleJiggle, getCurrentDisplayOdds());
            case 2 -> LotterySubModeRenderers.renderSlotMode(
                    guiGraphics, this.font, this.menu, relX, relY,
                    this.reel0Scroll, this.reel1Scroll, this.reel2Scroll, this.leverPullTimer);
            default -> LotterySubModeRenderers.renderDiceMode(
                    guiGraphics, this.font, this.menu, relX, relY, partialTick,
                    this.diceRotX, this.diceRotY, this.diceRotZ, getCurrentDisplayOdds());
        }

        // 7. 右侧奖励槽位
        int outX = relX + UniversalLotteryMachineMenu.OUTPUT_SLOT_X - 1;
        int outY = relY + UniversalLotteryMachineMenu.OUTPUT_SLOT_Y - 1;
        guiGraphics.fill(outX, outY, outX + 20, outY + 20, 0xFF0F0A1C);
        guiGraphics.renderOutline(outX, outY, 20, 20, 0xFFFACC15);

        // 奖励标签居中对齐于槽位中线 (relX + 167)
        Component rewardLabel = Component.translatable("gui.universal_tool.lottery.slot_label.reward");
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
        Component[] tabNames = new Component[]{
                Component.translatable("gui.universal_tool.lottery.tab.dice"),
                Component.translatable("gui.universal_tool.lottery.tab.wheel"),
                Component.translatable("gui.universal_tool.lottery.tab.slot")
        };
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
                guiGraphics.fill(relX - 1, tabY + 1, relX + 1, tabY + tabH - 1, bgColor);
            }

            int textColor = isActive ? 0xFFFFFFFF : (isHovered ? 0xFFE2E8F0 : 0xFF94A3B8);
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
            if (hasItem && currentPoints > 0) {
                String shortPts = currentPoints >= 100000L ? (currentPoints / 1000L + "k") : String.valueOf(currentPoints);
                int ptsW = this.font.width(shortPts);
                int boxW = Math.max(22, ptsW + 4);
                int boxX = inX + 10 - boxW / 2;
                guiGraphics.fill(boxX, inY + 22, boxX + boxW, inY + 32, 0xEE0F131C);
                guiGraphics.renderOutline(boxX, inY + 22, boxW, 10, 0xFF6A5328);
                guiGraphics.drawString(this.font, shortPts, inX + 10 - ptsW / 2, inY + 23, 0xFFFACC15, false);
            }

            LotteryValuator.RarityTier currentTier = this.menu.getTier();
            LotteryValuator.RarityTier[] tiers = LotteryValuator.RarityTier.values();
            for (int i = 0; i < tiers.length; i++) {
                LotteryValuator.RarityTier tier = tiers[i];
                boolean active = hasItem && (currentTier == tier);
                int row = i / 2;
                int col = i % 2;
                int bx = (col == 0) ? (relX + 5) : (relX + 34);
                int by = relY + 80 + row * 13;
                LotterySubModeRenderers.drawRarityBadge(guiGraphics, this.font, bx, by, tier.getDisplayNameComponent(), tier.getColor(), active);
            }
        } else if (this.clientSelectedTab == 1) {
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

            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.wheel.odds_common"), relX + 6, relY + 83, 0xFF94A3B8, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.wheel.odds_rare"), relX + 6, relY + 92, 0xFF38BDF8, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.wheel.odds_epic"), relX + 6, relY + 101, 0xFFC084FC, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.wheel.odds_legend"), relX + 6, relY + 110, 0xFFFBBF24, false);
        } else {
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

            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.slot.rule_3"), relX + 6, relY + 85, 0xFFE879F9, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.slot.rule_2"), relX + 6, relY + 96, 0xFFFDE047, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.slot.rule_1"), relX + 6, relY + 107, 0xFF94A3B8, false);
        }
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

        Component btnLabel;
        if (this.menu.isRolling()) {
            btnLabel = Component.translatable("gui.universal_tool.lottery.action.rolling");
        } else {
            btnLabel = switch (this.clientSelectedTab) {
                case 1 -> Component.translatable("gui.universal_tool.lottery.action.wheel");
                case 2 -> (this.clientSlotMultiplier > 1
                        ? Component.translatable("gui.universal_tool.lottery.action.slot_multi", this.clientSlotMultiplier)
                        : Component.translatable("gui.universal_tool.lottery.action.slot_single"));
                default -> Component.translatable("gui.universal_tool.lottery.action.dice");
            };
        }

        int labelColor = canRoll ? 0xFFFFFFFF : 0xFF9CA3AF;
        guiGraphics.drawString(this.font, btnLabel, btnX + (btnW - this.font.width(btnLabel)) / 2, btnY + 8, labelColor, true);

        // 消耗提示 (位于按钮正下方，字色温润清晰)
        if (this.clientSelectedTab != 0) {
            Component costText = Component.translatable(
                    (this.clientSelectedTab == 2 && bet > 1)
                            ? "gui.universal_tool.lottery.cost.max"
                            : "gui.universal_tool.lottery.cost.exact",
                    bet);
            int cw = this.font.width(costText);
            guiGraphics.drawString(this.font, costText, btnX + (btnW - cw) / 2, btnY + 26, 0xFF94A3B8, false);
        }
    }

    /**
     * 绘制玩家背包与快捷栏网格
     */
    private void renderPlayerInventoryGrid(GuiGraphics guiGraphics, int relX, int relY) {
        guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.lottery.inventory"), relX + 17, relY + 124, 0xFF9E9E9E, false);

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

    private void renderMultilineTooltip(GuiGraphics guiGraphics, Component text, int mouseX, int mouseY) {
        List<Component> list = new ArrayList<>();
        for (String line : text.getString().split("\n")) {
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
