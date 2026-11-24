package org.gwfx.universaltool.lottery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModItems;

import javax.annotation.Nullable;

public class UniversalLotteryMachineBlockEntity extends BlockEntity implements MenuProvider, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int TOTAL_SLOTS = 2;
    public static final int DEFAULT_ROLL_DURATION_TICKS = 50; // 2.5 秒
    public static final int EXTENDED_ROLL_DURATION_TICKS = 75; // 3.75 秒 (听牌悬念)
    public static final int ROLL_DURATION_TICKS = DEFAULT_ROLL_DURATION_TICKS;

    public static final int MODE_DICE = 0;
    public static final int MODE_WHEEL = 1;
    public static final int MODE_SLOT = 2;

    private static final int[] SLOTS_TOP = new int[]{SLOT_INPUT};
    private static final int[] SLOTS_BOTTOM = new int[]{SLOT_OUTPUT};
    private static final int[] SLOTS_SIDES = new int[]{SLOT_INPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private int rollingState = 0; // 0 = IDLE, 1 = ROLLING
    private int rollTicks = 0;
    private long totalPointsSacrificed = 0L;

    private int activeMode = MODE_DICE;
    private int betCount = 1;
    private int wheelTargetSlice = 0;
    private int slotReel1 = 0;
    private int slotReel2 = 0;
    private int slotReel3 = 0;
    private int diceTargetNumber = 1;
    private boolean isNearMiss = false;
    private int totalRollDurationTicks = DEFAULT_ROLL_DURATION_TICKS;
    private int remainingMultiRolls = 0;
    private int multiRollRequested = 1;
    private long singleRollPoints = 0L;
    private int unusedReservedItems = 0;
    private ItemStack reservedInputItem = ItemStack.EMPTY;
    private ItemStack bestRewardSoFar = ItemStack.EMPTY;
    private float bestMultiplierSoFar = 0.0F;
    private ItemStack pendingReward = ItemStack.EMPTY;
    private final NonNullList<ItemStack> pendingExtraRewards = NonNullList.create();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> rollingState;
                case 1 -> rollTicks;
                case 2 -> (int) (calculateCurrentPoints() & 0xFFFF);
                case 3 -> (int) ((calculateCurrentPoints() >> 16) & 0xFFFF);
                case 4 -> LotteryValuator.getOddsPercent(calculateCurrentPoints());
                case 5 -> LotteryValuator.getTierFromPoints(calculateCurrentPoints()).ordinal();
                case 6 -> activeMode;
                case 7 -> wheelTargetSlice;
                case 8 -> slotReel1;
                case 9 -> slotReel2;
                case 10 -> slotReel3;
                case 11 -> multiRollRequested > 1 ? multiRollRequested : betCount;
                case 12 -> diceTargetNumber;
                case 13 -> remainingMultiRolls;
                case 14 -> totalRollDurationTicks;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> rollingState = value;
                case 1 -> rollTicks = value;
                case 6 -> activeMode = value;
                case 7 -> wheelTargetSlice = value;
                case 8 -> slotReel1 = value;
                case 9 -> slotReel2 = value;
                case 10 -> slotReel3 = value;
                case 11 -> betCount = value;
                case 12 -> diceTargetNumber = value;
                case 13 -> remainingMultiRolls = value;
                case 14 -> totalRollDurationTicks = value;
            }
        }

        @Override
        public int getCount() {
            return 15;
        }
    };

    public UniversalLotteryMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNIVERSAL_LOTTERY_MACHINE_BE.get(), pos, state);
    }

    private long calculateCurrentPoints() {
        if (rollingState == 1) {
            return totalPointsSacrificed;
        }
        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty()) return 0L;
        if (activeMode == MODE_DICE) {
            return LotteryValuator.evaluate(input);
        } else {
            int actualBet = Math.max(1, Math.min(input.getCount(), betCount));
            return LotteryValuator.getUnitValue(input) * actualBet;
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, UniversalLotteryMachineBlockEntity be) {
        if (level.isClientSide) {
            if (be.rollingState == 1) {
                Direction facing = state.getValue(UniversalLotteryMachineBlock.FACING);
                double cx = pos.getX() + 0.5 + facing.getStepX() * 0.28;
                double cy = pos.getY() + 1.34;
                double cz = pos.getZ() + 0.5 + facing.getStepZ() * 0.28;
                if (level.random.nextFloat() < 0.6F) {
                    level.addParticle(ParticleTypes.PORTAL,
                            cx + (level.random.nextDouble() - 0.5) * 0.2,
                            cy + (level.random.nextDouble() - 0.5) * 0.2,
                            cz + (level.random.nextDouble() - 0.5) * 0.2,
                            (level.random.nextDouble() - 0.5) * 0.3,
                            (level.random.nextDouble() - 0.5) * 0.3,
                            (level.random.nextDouble() - 0.5) * 0.3);
                }
            }
            return;
        }

        if (be.rollingState == 1) {
            be.rollTicks--;

            if (be.activeMode == MODE_SLOT) {
                boolean extended = (be.totalRollDurationTicks == EXTENDED_ROLL_DURATION_TICKS);
                int r1Stop = extended ? 48 : 25;
                int r2Stop = extended ? 32 : 12;

                if (be.rollTicks == r1Stop) {
                    level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.9F, 1.2F);
                } else if (be.rollTicks == r2Stop) {
                    level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.9F, 1.4F);
                    if (extended) {
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.9F, 1.8F);
                    }
                } else if (extended && be.rollTicks < r2Stop && be.rollTicks > 0) {
                    // 听牌悬念期：紧张低沉心跳音 + 滑行棘轮音
                    if (be.rollTicks % 3 == 0) {
                        float heartPitch = 0.8F + (1.0F - (float) be.rollTicks / r2Stop) * 0.4F;
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, heartPitch);
                    }
                    if (be.rollTicks % 6 == 0) {
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.5F, 1.5F);
                    }
                } else if (!extended && be.rollTicks % 5 == 0 && be.rollTicks > 0) {
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.5F, 1.6F);
                }
            } else if (be.activeMode == MODE_WHEEL) {
                // 轮盘音效：随着减速，指针跳格拨动的间隔逐渐拉长
                float progress = 1.0F - (float) be.rollTicks / be.totalRollDurationTicks;
                int interval = Math.max(2, (int) (2 + progress * progress * 8));
                if (be.rollTicks % interval == 0) {
                    float pitch = 1.2F + (1.0F - progress) * 0.6F;
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.6F, pitch);
                }
            } else {
                // 骰子模式：前期升调翻滚，后期落地清脆碰击
                if (be.rollTicks > 15) {
                    if (be.rollTicks % 5 == 0) {
                        float pitch = 0.8F + (1.0F - (float) be.rollTicks / be.totalRollDurationTicks) * 0.8F;
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.7F, pitch);
                    }
                } else if (be.rollTicks == 15 || be.rollTicks == 8 || be.rollTicks == 2) {
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 1.4F);
                }
            }

            if (be.rollTicks <= 0) {
                be.finishRoll((ServerLevel) level, pos);
            }
            be.setChanged();
        }
    }

    public boolean isRolling() {
        return rollingState == 1;
    }

    public int getRollTicks() {
        return rollTicks;
    }

    public int getActiveMode() {
        return activeMode;
    }

    public int getDiceTargetNumber() {
        return diceTargetNumber;
    }

    public boolean isNearMiss() {
        return isNearMiss;
    }

    public int getTotalRollDurationTicks() {
        return totalRollDurationTicks;
    }

    public int getWheelTargetSlice() {
        return wheelTargetSlice;
    }

    public int getSlotReel1() {
        return slotReel1;
    }

    public int getSlotReel2() {
        return slotReel2;
    }

    public int getSlotReel3() {
        return slotReel3;
    }

    public int getRemainingMultiRolls() {
        return remainingMultiRolls;
    }

    public int getMultiRollRequested() {
        return multiRollRequested;
    }

    public void setActiveMode(int mode) {
        this.activeMode = Math.max(0, Math.min(2, mode));
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean canStartRoll(int mode, int requestedBet) {
        if (rollingState == 1) return false;
        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty() || input.is(ModItems.VOID_SCRAP.get()) || LotteryValuator.evaluate(input) <= 0 || input.getCount() < requestedBet) return false;
        return items.get(SLOT_OUTPUT).isEmpty();
    }

    public boolean startRoll(Player player, int mode, int requestedBet) {
        if (rollingState == 1 || level == null) {
            return false;
        }

        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty() || input.is(ModItems.VOID_SCRAP.get()) || LotteryValuator.evaluate(input) <= 0) return false;
        if (!items.get(SLOT_OUTPUT).isEmpty()) return false;

        this.activeMode = Math.max(0, Math.min(2, mode));

        int actualBet;
        if (activeMode == MODE_DICE) {
            actualBet = input.getCount(); // 一次性全额献祭
        } else {
            actualBet = Math.max(1, Math.min(input.getCount(), requestedBet));
        }

        this.betCount = actualBet;
        ItemStack betStack = input.copyWithCount(actualBet);
        long basePoints = LotteryValuator.evaluate(betStack);
        this.totalPointsSacrificed = player instanceof net.minecraft.server.level.ServerPlayer sp
                ? org.gwfx.universaltool.skill.SkillModSynergyHandler.adjustLotteryPoints(sp, basePoints)
                : basePoints;
        this.singleRollPoints = LotteryValuator.getUnitValue(input);

        // 扣除下注物品
        input.shrink(actualBet);
        if (input.isEmpty()) {
            items.set(SLOT_INPUT, ItemStack.EMPTY);
        }

        RandomSource random = level.random;
        ServerLevel serverLevel = (ServerLevel) level;
        this.pendingExtraRewards.clear();

        // 根据模式预先计算开奖结果与对应动画终点
        if (activeMode == MODE_DICE) {
            this.unusedReservedItems = 0;
            this.reservedInputItem = ItemStack.EMPTY;
            this.bestRewardSoFar = ItemStack.EMPTY;
            this.bestMultiplierSoFar = 0.0F;
            this.totalRollDurationTicks = DEFAULT_ROLL_DURATION_TICKS;
            this.diceTargetNumber = calculateDiceNumber(totalPointsSacrificed, random);
            this.pendingReward = LotteryLootPool.rollDice(this.diceTargetNumber, totalPointsSacrificed, random, serverLevel);
            this.remainingMultiRolls = 0;
            this.multiRollRequested = 1;
        } else if (activeMode == MODE_WHEEL) {
            this.unusedReservedItems = 0;
            this.reservedInputItem = ItemStack.EMPTY;
            this.bestRewardSoFar = ItemStack.EMPTY;
            this.bestMultiplierSoFar = 0.0F;
            this.totalRollDurationTicks = DEFAULT_ROLL_DURATION_TICKS;
            this.wheelTargetSlice = calculateWheelTargetSector(totalPointsSacrificed, random);
            this.pendingReward = LotteryLootPool.rollWheelSector(this.wheelTargetSlice, this.betCount, totalPointsSacrificed, random, serverLevel, this.pendingExtraRewards);
            this.remainingMultiRolls = 0;
            this.multiRollRequested = 1;
        } else {
            // 老虎机连抽机制：本次总连抽数为 actualBet (1, 2, 5)，支持提前出货自动止损退还
            this.reservedInputItem = input.copyWithCount(1);
            this.unusedReservedItems = actualBet - 1; // 首抽扣 1 个，余量保留待转
            this.bestRewardSoFar = ItemStack.EMPTY;
            this.bestMultiplierSoFar = 0.0F;
            this.multiRollRequested = actualBet;
            this.remainingMultiRolls = actualBet - 1;
            this.totalRollDurationTicks = (multiRollRequested > 1) ? 26 : DEFAULT_ROLL_DURATION_TICKS;

            SlotResult slotRes = calculateSlotSymbolsWithNearMiss(1, singleRollPoints, random);
            this.slotReel1 = slotRes.s1;
            this.slotReel2 = slotRes.s2;
            this.slotReel3 = slotRes.s3;
            this.isNearMiss = slotRes.isNearMiss;
            this.pendingReward = LotteryLootPool.rollSlot(slotReel1, slotReel2, slotReel3, slotRes.multiplier, 1, singleRollPoints, random, serverLevel, this.pendingExtraRewards);
        }

        this.rollingState = 1;
        this.rollTicks = this.totalRollDurationTicks;

        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
        if (activeMode == MODE_SLOT) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.9F, 1.3F);
        } else {
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9F, 1.4F);
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        org.gwfx.universaltool.skill.SkillModSynergyHandler.onLotteryRollCompleted(
                serverLevel, worldPosition, player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
        return true;
    }

    public boolean startRoll(Player player) {
        return startRoll(player, this.activeMode, this.betCount);
    }

    private int calculateDiceNumber(long points, RandomSource random) {
        LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(points);
        if (tier == LotteryValuator.RarityTier.MYTHIC || tier == LotteryValuator.RarityTier.LEGENDARY) {
            // 双骰取大，且保底最低为 3 点（绝无 1~2 点毁灭性大折损）
            int d1 = 1 + random.nextInt(6);
            int d2 = 1 + random.nextInt(6);
            int high = Math.max(d1, d2);
            return Math.max(3, high);
        } else if (tier == LotteryValuator.RarityTier.EPIC) {
            // 史诗：双骰取大 (Advantage)
            int d1 = 1 + random.nextInt(6);
            int d2 = 1 + random.nextInt(6);
            return Math.max(d1, d2);
        } else if (tier == LotteryValuator.RarityTier.RARE) {
            // 稀有：掷 1d6，若出 1 则重掷 1 次 (防大失败)
            int d = 1 + random.nextInt(6);
            if (d == 1) {
                d = 1 + random.nextInt(6);
            }
            return d;
        } else {
            // 普通 / 罕见：纯掷 1d6
            return 1 + random.nextInt(6);
        }
    }

    private static class SlotResult {
        int s1, s2, s3;
        boolean isNearMiss;
        boolean extendedTicks;
        float multiplier;
    }

    private SlotResult calculateSlotSymbolsWithNearMiss(int betMultiplier, long points, RandomSource random) {
        LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(points);
        int roll = random.nextInt(100);

        int bonus = (betMultiplier >= 10) ? 8 : (betMultiplier >= 5 ? 4 : 0);

        // 1. Grand Jackpot: 3x ⭐ (Symbol 0)
        int jackpotChance = ((tier == LotteryValuator.RarityTier.MYTHIC) ? 15 :
                            (tier == LotteryValuator.RarityTier.LEGENDARY) ? 8 :
                            (tier == LotteryValuator.RarityTier.EPIC) ? 4 : 2) + bonus;
        if (roll < jackpotChance) {
            SlotResult res = new SlotResult();
            res.s1 = 0; res.s2 = 0; res.s3 = 0;
            res.isNearMiss = false;
            res.extendedTicks = true;
            res.multiplier = 10.0F;
            return res;
        }

        // 2. Triple Match: 3x of the same symbol (1..6)
        int tripleChance = jackpotChance + ((tier == LotteryValuator.RarityTier.MYTHIC || tier == LotteryValuator.RarityTier.LEGENDARY) ? 25 : 15) + bonus * 2;
        if (roll < tripleChance) {
            int sym = pickSymbolByTier(tier, random);
            SlotResult res = new SlotResult();
            res.s1 = sym; res.s2 = sym; res.s3 = sym;
            res.isNearMiss = false;
            res.extendedTicks = (sym <= 3);
            res.multiplier = calculateSlotMultiplier(new int[]{sym, sym, sym});
            return res;
        }

        // 3. Double Match / Near Miss
        int doubleChance = tripleChance + 35 + bonus;
        if (roll < doubleChance) {
            int sym = pickSymbolByTier(tier, random);
            boolean nearMiss = (sym <= 3) && random.nextBoolean();
            if (nearMiss) {
                int offset = random.nextBoolean() ? 1 : 6;
                int diffSym = (sym + offset) % 7;
                SlotResult res = new SlotResult();
                res.s1 = sym; res.s2 = sym; res.s3 = diffSym;
                res.isNearMiss = true;
                res.extendedTicks = true;
                res.multiplier = 1.5F;
                return res;
            } else {
                int other = (sym + 1 + random.nextInt(6)) % 7;
                int order = random.nextInt(3);
                int[] s = switch (order) {
                    case 0 -> new int[]{sym, sym, other};
                    case 1 -> new int[]{sym, other, sym};
                    default -> new int[]{other, sym, sym};
                };
                SlotResult res = new SlotResult();
                res.s1 = s[0]; res.s2 = s[1]; res.s3 = s[2];
                res.isNearMiss = false;
                res.extendedTicks = false;
                res.multiplier = 1.5F;
                return res;
            }
        }

        // 4. Consolation: 3 distinct symbols
        int s1 = random.nextInt(7);
        int s2 = (s1 + 1 + random.nextInt(6)) % 7;
        int s3 = (s2 + 1 + random.nextInt(5)) % 7;
        if (s3 == s1) s3 = (s3 + 1) % 7;
        SlotResult res = new SlotResult();
        res.s1 = s1; res.s2 = s2; res.s3 = s3;
        res.isNearMiss = false;
        res.extendedTicks = false;
        res.multiplier = 0.7F;
        return res;
    }

    private int calculateWheelTargetSector(long points, RandomSource random) {
        LotteryValuator.RarityTier tier = LotteryValuator.getTierFromPoints(points);
        int roll = random.nextInt(100);

        if (tier == LotteryValuator.RarityTier.MYTHIC) {
            if (roll < 40) return 3; // Mythic
            if (roll < 70) return 1; // Legendary
            if (roll < 90) return 5; // Epic
            return (random.nextBoolean() ? 0 : 2); // Rare
        } else if (tier == LotteryValuator.RarityTier.LEGENDARY) {
            if (roll < 20) return 3; // Mythic
            if (roll < 55) return 1; // Legendary
            if (roll < 80) return 5; // Epic
            return (random.nextBoolean() ? 0 : 2); // Rare
        } else if (tier == LotteryValuator.RarityTier.EPIC) {
            if (roll < 8) return 3; // Mythic
            if (roll < 26) return 1; // Legendary
            if (roll < 60) return 5; // Epic
            if (roll < 85) return (random.nextBoolean() ? 0 : 2); // Rare
            return (random.nextBoolean() ? 4 : 6); // Uncommon
        } else if (tier == LotteryValuator.RarityTier.RARE) {
            if (roll < 3) return 3;
            if (roll < 11) return 1;
            if (roll < 31) return 5;
            if (roll < 71) return (random.nextBoolean() ? 0 : 2);
            return (random.nextBoolean() ? 4 : 6);
        } else {
            if (roll < 1) return 3;
            if (roll < 5) return 1;
            if (roll < 15) return 5;
            if (roll < 45) return (random.nextBoolean() ? 0 : 2);
            if (roll < 80) return (random.nextBoolean() ? 4 : 6);
            return 7; // Mystery
        }
    }

    private int pickSymbolByTier(LotteryValuator.RarityTier tier, RandomSource random) {
        if (tier == LotteryValuator.RarityTier.MYTHIC || tier == LotteryValuator.RarityTier.LEGENDARY) {
            return random.nextBoolean() ? 1 : 2; // Netherite or Golden Apple
        } else if (tier == LotteryValuator.RarityTier.EPIC) {
            return random.nextBoolean() ? 2 : 3; // Golden Apple or Diamond
        } else if (tier == LotteryValuator.RarityTier.RARE) {
            return random.nextBoolean() ? 3 : 4; // Diamond or Ender Eye
        } else {
            return random.nextBoolean() ? 5 : 6; // Chorus Fruit or Void Scrap
        }
    }

    private float calculateSlotMultiplier(int[] symbols) {
        int s1 = symbols[0], s2 = symbols[1], s3 = symbols[2];
        if (s1 == 0 && s2 == 0 && s3 == 0) {
            return 10.0F; // Grand Jackpot x10!
        }
        if (s1 == s2 && s2 == s3) {
            return switch (s1) {
                case 1 -> 5.0F; // 3x Netherite
                case 2 -> 4.0F; // 3x Golden Apple
                case 3 -> 3.0F; // 3x Diamond
                case 4 -> 2.5F; // 3x Ender Eye
                case 5 -> 2.0F; // 3x Chorus Fruit
                default -> 1.5F; // 3x Void Scrap
            };
        }
        if (s1 == s2 || s2 == s3 || s1 == s3) {
            return 1.5F; // Double Match
        }
        return 0.7F; // Consolation
    }

    private void finishRoll(ServerLevel level, BlockPos pos) {
        if (activeMode == MODE_DICE) {
            ItemStack reward = this.pendingReward.isEmpty() ? LotteryLootPool.roll(totalPointsSacrificed, level.random, level) : this.pendingReward;
            if (items.get(SLOT_OUTPUT).isEmpty()) {
                items.set(SLOT_OUTPUT, reward);
            } else {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, reward);
            }

            if (diceTargetNumber == 6) {
                // 6点大暴击：烟花与图腾庆典
                level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.2F);
                level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 35, 0.4, 0.4, 0.4, 0.15);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.15);
            } else if (diceTargetNumber == 1) {
                // 1点大失败：虚空吞噬低鸣与传送门粒子
                level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
                level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 35, 0.3, 0.3, 0.3, 0.2);
            } else {
                level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1.1F);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.25, 0.25, 0.25, 0.1);
            }

            this.pendingReward = ItemStack.EMPTY;
            this.rollingState = 0;
            this.rollTicks = 0;
            this.totalPointsSacrificed = 0L;
            this.singleRollPoints = 0L;
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
            return;
        }

        if (activeMode == MODE_WHEEL) {
            ItemStack reward = this.pendingReward.isEmpty() ? LotteryLootPool.roll(totalPointsSacrificed, level.random, level) : this.pendingReward;
            if (items.get(SLOT_OUTPUT).isEmpty()) {
                items.set(SLOT_OUTPUT, reward);
            } else {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, reward);
            }

            if (!this.pendingExtraRewards.isEmpty()) {
                for (ItemStack extra : this.pendingExtraRewards) {
                    if (!extra.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, extra);
                    }
                }
                this.pendingExtraRewards.clear();
            }

            boolean isGrandJackpot = (wheelTargetSlice == 3);
            if (isGrandJackpot) {
                level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.15);
            } else {
                level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1.1F);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.25, 0.25, 0.25, 0.1);
            }

            this.pendingReward = ItemStack.EMPTY;
            this.rollingState = 0;
            this.rollTicks = 0;
            this.totalPointsSacrificed = 0L;
            this.singleRollPoints = 0L;
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
            return;
        }

        // 老虎机连抽 / 单抽机制：自动搜寻最佳结果，提前中大奖立即停机并返还余料
        boolean isGrandJackpot = (slotReel1 == 0 && slotReel2 == 0 && slotReel3 == 0);
        boolean isTriple = (slotReel1 == slotReel2 && slotReel2 == slotReel3);
        boolean isJackpotHit = (isGrandJackpot || isTriple);

        ItemStack currentRollReward = this.pendingReward.isEmpty()
                ? LotteryLootPool.roll(singleRollPoints > 0 ? singleRollPoints : totalPointsSacrificed, level.random, level)
                : this.pendingReward;
        float currentMult = calculateSlotMultiplier(new int[]{slotReel1, slotReel2, slotReel3});

        if (this.bestRewardSoFar.isEmpty() || currentMult > this.bestMultiplierSoFar) {
            this.bestRewardSoFar = currentRollReward.copy();
            this.bestMultiplierSoFar = currentMult;
        }

        if (isJackpotHit) {
            // ★ 命中 3 连同花或顶级大奖！立即触发胜者停机！
            this.remainingMultiRolls = 0;
            items.set(SLOT_OUTPUT, currentRollReward);
            this.pendingReward = ItemStack.EMPTY;
            this.bestRewardSoFar = ItemStack.EMPTY;
            this.bestMultiplierSoFar = 0.0F;

            // 自动止损：将剩余未使用的原料退还到输入槽
            refundUnusedReservedItems(level, pos);

            // 盛大胜者庆典
            level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 35, 0.4, 0.4, 0.4, 0.15);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.15);

            this.rollingState = 0;
            this.rollTicks = 0;
            this.totalPointsSacrificed = 0L;
            this.singleRollPoints = 0L;
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
            return;
        }

        // 未中 3 连同花，但还有后续连抽次数
        if (this.remainingMultiRolls > 0) {
            this.remainingMultiRolls--;
            if (this.unusedReservedItems > 0) {
                this.unusedReservedItems--;
            }
            this.pendingReward = ItemStack.EMPTY;

            if (isNearMiss) {
                level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.7F, 0.9F);
            } else {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.7F, 1.4F);
            }

            this.rollingState = 1;
            this.totalRollDurationTicks = 26;
            this.rollTicks = this.totalRollDurationTicks;

            SlotResult nextRes = calculateSlotSymbolsWithNearMiss(1, singleRollPoints, level.random);
            this.slotReel1 = nextRes.s1;
            this.slotReel2 = nextRes.s2;
            this.slotReel3 = nextRes.s3;
            this.isNearMiss = nextRes.isNearMiss;
            this.pendingReward = LotteryLootPool.rollSlot(slotReel1, slotReel2, slotReel3, nextRes.multiplier, 1, singleRollPoints, level.random, level, this.pendingExtraRewards);

            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.8F, 1.3F);
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
            return;
        }

        // 连抽全部结束或单抽结束（未中 3 连），精选最佳战利品送入产出槽
        ItemStack finalBest = !this.bestRewardSoFar.isEmpty() ? this.bestRewardSoFar : currentRollReward;
        items.set(SLOT_OUTPUT, finalBest);
        this.bestRewardSoFar = ItemStack.EMPTY;
        this.bestMultiplierSoFar = 0.0F;
        this.pendingReward = ItemStack.EMPTY;
        this.unusedReservedItems = 0;
        this.reservedInputItem = ItemStack.EMPTY;

        if (isNearMiss) {
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.9F, 0.8F);
        } else {
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1.1F);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.4F);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.12);

        this.rollingState = 0;
        this.rollTicks = 0;
        this.totalPointsSacrificed = 0L;
        this.singleRollPoints = 0L;
        this.remainingMultiRolls = 0;
        setChanged();
        level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
    }

    private void refundUnusedReservedItems(ServerLevel level, BlockPos pos) {
        if (!this.reservedInputItem.isEmpty() && this.unusedReservedItems > 0) {
            int refundCount = this.unusedReservedItems;
            ItemStack currentInput = items.get(SLOT_INPUT);
            if (currentInput.isEmpty()) {
                items.set(SLOT_INPUT, this.reservedInputItem.copyWithCount(refundCount));
            } else if (ItemStack.isSameItemSameComponents(currentInput, this.reservedInputItem)) {
                int space = currentInput.getMaxStackSize() - currentInput.getCount();
                int toAdd = Math.min(space, refundCount);
                currentInput.grow(toAdd);
                int leftover = refundCount - toAdd;
                if (leftover > 0) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.reservedInputItem.copyWithCount(leftover));
                }
            } else {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.reservedInputItem.copyWithCount(refundCount));
            }
            this.unusedReservedItems = 0;
            this.reservedInputItem = ItemStack.EMPTY;
        }
    }

    public void dropExtraContents(Level level, BlockPos pos) {
        if (!this.reservedInputItem.isEmpty() && this.unusedReservedItems > 0) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.reservedInputItem.copyWithCount(this.unusedReservedItems));
            this.unusedReservedItems = 0;
            this.reservedInputItem = ItemStack.EMPTY;
        }
        if (!this.bestRewardSoFar.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.bestRewardSoFar);
            this.bestRewardSoFar = ItemStack.EMPTY;
        }
        if (!this.pendingReward.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.pendingReward);
            this.pendingReward = ItemStack.EMPTY;
        }
        if (!this.pendingExtraRewards.isEmpty()) {
            for (ItemStack extra : this.pendingExtraRewards) {
                if (!extra.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, extra);
                }
            }
            this.pendingExtraRewards.clear();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        rollingState = tag.getInt("RollingState");
        rollTicks = tag.getInt("RollTicks");
        totalPointsSacrificed = tag.getLong("TotalPointsSacrificed");
        activeMode = tag.getInt("ActiveMode");
        betCount = Math.max(1, tag.getInt("BetCount"));
        wheelTargetSlice = tag.getInt("WheelTargetSlice");
        slotReel1 = tag.getInt("SlotReel1");
        slotReel2 = tag.getInt("SlotReel2");
        slotReel3 = tag.getInt("SlotReel3");
        diceTargetNumber = Math.max(1, tag.getInt("DiceTargetNumber"));
        isNearMiss = tag.getBoolean("IsNearMiss");
        totalRollDurationTicks = tag.getInt("TotalRollDurationTicks");
        if (totalRollDurationTicks <= 0) totalRollDurationTicks = DEFAULT_ROLL_DURATION_TICKS;
        remainingMultiRolls = tag.getInt("RemainingMultiRolls");
        multiRollRequested = Math.max(1, tag.getInt("MultiRollRequested"));
        unusedReservedItems = tag.getInt("UnusedReservedItems");
        if (tag.contains("ReservedInputItem")) {
            reservedInputItem = ItemStack.parse(registries, tag.getCompound("ReservedInputItem")).orElse(ItemStack.EMPTY);
        }
        if (tag.contains("BestRewardSoFar")) {
            bestRewardSoFar = ItemStack.parse(registries, tag.getCompound("BestRewardSoFar")).orElse(ItemStack.EMPTY);
        }
        bestMultiplierSoFar = tag.getFloat("BestMultiplierSoFar");
        if (tag.contains("PendingReward")) {
            pendingReward = ItemStack.parse(registries, tag.getCompound("PendingReward")).orElse(ItemStack.EMPTY);
        }
        pendingExtraRewards.clear();
        if (tag.contains("PendingExtraRewards", 9)) {
            ListTag extraList = tag.getList("PendingExtraRewards", 10);
            for (int i = 0; i < extraList.size(); i++) {
                ItemStack.parse(registries, extraList.getCompound(i)).ifPresent(pendingExtraRewards::add);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("RollingState", rollingState);
        tag.putInt("RollTicks", rollTicks);
        tag.putLong("TotalPointsSacrificed", totalPointsSacrificed);
        tag.putInt("ActiveMode", activeMode);
        tag.putInt("BetCount", betCount);
        tag.putInt("WheelTargetSlice", wheelTargetSlice);
        tag.putInt("SlotReel1", slotReel1);
        tag.putInt("SlotReel2", slotReel2);
        tag.putInt("SlotReel3", slotReel3);
        tag.putInt("DiceTargetNumber", diceTargetNumber);
        tag.putBoolean("IsNearMiss", isNearMiss);
        tag.putInt("TotalRollDurationTicks", totalRollDurationTicks);
        tag.putInt("RemainingMultiRolls", remainingMultiRolls);
        tag.putInt("MultiRollRequested", multiRollRequested);
        tag.putInt("UnusedReservedItems", unusedReservedItems);
        if (!reservedInputItem.isEmpty()) {
            tag.put("ReservedInputItem", reservedInputItem.save(registries));
        }
        if (!bestRewardSoFar.isEmpty()) {
            tag.put("BestRewardSoFar", bestRewardSoFar.save(registries));
        }
        tag.putFloat("BestMultiplierSoFar", bestMultiplierSoFar);
        if (!pendingReward.isEmpty()) {
            tag.put("PendingReward", pendingReward.save(registries));
        }
        if (!pendingExtraRewards.isEmpty()) {
            ListTag extraList = new ListTag();
            for (ItemStack s : pendingExtraRewards) {
                if (!s.isEmpty()) {
                    extraList.add(s.save(registries));
                }
            }
            tag.put("PendingExtraRewards", extraList);
        }
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 2, worldPosition.getZ() + 1);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.universal_tool.universal_lottery_machine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new UniversalLotteryMachineMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- Container 实现 ---

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && rollingState == 0;
    }

    // --- WorldlyContainer 实现 (漏斗与自动化支持) ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) return SLOTS_BOTTOM;
        if (side == Direction.UP) return SLOTS_TOP;
        return SLOTS_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == SLOT_INPUT && rollingState == 0;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT && rollingState == 0;
    }
}
