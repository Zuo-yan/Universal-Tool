package org.gwfx.universaltool.sportscar;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModCreativeTabs;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModRecipes;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;
import org.gwfx.universaltool.recipe.PolymerizerRecipeInput;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 海湾 GT 跑车服务端 GameTest 测试集：
 * 覆盖聚合器配方、物品放置、双座乘员与安全下车、鼠标转向+油门前进、
 * 倒车与反向转向、手刹漂移+氮气加速与回充、自动迎宾门开合、1格台阶攀爬以及空载回收。
 */
@PrefixGameTestTemplate(false)
public final class SportsCarGameTests {

    private static final ResourceLocation SPORTS_CAR_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "gulf_gt_car_polymerize");

    private SportsCarGameTests() {
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 60)
    public static void polymerizerRecipeProducesSportsCar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Optional<RecipeHolder<?>> recipeByKey = level.getRecipeManager().byKey(SPORTS_CAR_RECIPE_ID);
        helper.assertTrue(recipeByKey.isPresent(),
                "Recipe " + SPORTS_CAR_RECIPE_ID + " must exist in RecipeManager");
        helper.assertTrue(recipeByKey.get().value() instanceof PolymerizerRecipe,
                "Recipe " + SPORTS_CAR_RECIPE_ID + " must be a PolymerizerRecipe");

        List<ItemStack> ingredients = new ArrayList<>(12);
        for (int i = 0; i < 2; i++) ingredients.add(new ItemStack(ModItems.VOID_ESSENCE.get()));
        for (int i = 0; i < 2; i++) ingredients.add(new ItemStack(ModItems.UNIVERSAL_REPAIR_MATERIAL.get()));
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(Items.NETHERITE_INGOT));
        for (int i = 0; i < 2; i++) ingredients.add(new ItemStack(Items.REDSTONE_BLOCK));
        for (int i = 0; i < 2; i++) ingredients.add(new ItemStack(Items.TINTED_GLASS));

        PolymerizerRecipeInput input = new PolymerizerRecipeInput(ingredients);
        Optional<RecipeHolder<PolymerizerRecipe>> match = level.getRecipeManager().getRecipeFor(
                ModRecipes.POLYMERIZING_TYPE.get(), input, level);
        helper.assertTrue(match.isPresent(), "Gulf GT Sports Car 12-slot polymerizer recipe must match");
        helper.assertTrue(match.get().id().equals(SPORTS_CAR_RECIPE_ID),
                "Matched recipe id must be " + SPORTS_CAR_RECIPE_ID + ", got " + match.get().id());

        ItemStack output = match.get().value().assemble(input, level.registryAccess());
        helper.assertTrue(output.is(ModItems.GULF_GT_CAR.get()) && output.getCount() == 1,
                "Polymerizer output must be 1x GULF_GT_CAR, got " + output);

        // 缺少任意 1 个材料时不得匹配
        ingredients.remove(ingredients.size() - 1);
        helper.assertTrue(!match.get().value().matches(new PolymerizerRecipeInput(ingredients), level),
                "Incomplete ingredients must not match sports car recipe");

        // 校验创造栏注册
        CreativeModeTab tab = ModCreativeTabs.UNIVERSAL_TOOL_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), false, level.registryAccess()));
        boolean inTab = tab.getDisplayItems().stream()
                .anyMatch(stack -> stack.is(ModItems.GULF_GT_CAR.get()));
        helper.assertTrue(inTab, "UNIVERSAL_TOOL_TAB must contain GULF_GT_CAR");

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 80)
    public static void itemPlacementDualSeatAndSafeDismount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(-3, 12, -3));
        BlockPos maxCorner = helper.absolutePos(new BlockPos(5, 15, 5));
        List<ChunkPos> forced = forceChunksForRegion(level, origin, maxCorner);

        try {
            buildFlatPlatform(level, helper, -3, 5, 12, -3, 5);
            BlockPos centerFloor = helper.absolutePos(new BlockPos(1, 12, 1));

            ServerPlayer driver = mockPlayer(level.getServer(), level, "car-driver");
            driver.setYRot(180.0F); // 面朝 -Z
            driver.setPos(centerFloor.getX() + 0.5D, centerFloor.getY() + 1.0D, centerFloor.getZ() + 4.0D);

            // 1. 物品放置测试
            ItemStack carStack = new ItemStack(ModItems.GULF_GT_CAR.get());
            driver.setItemInHand(InteractionHand.MAIN_HAND, carStack);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(centerFloor).add(0.0D, 0.5D, 0.0D),
                    Direction.UP,
                    centerFloor,
                    false
            );
            InteractionResult placeResult = carStack.useOn(new UseOnContext(driver, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(placeResult.consumesAction(), "SportsCarItem useOn must place car, got=" + placeResult);
            helper.assertTrue(carStack.isEmpty(), "Survival item useOn must consume 1x GULF_GT_CAR");

            List<SportsCarEntity> spawned = level.getEntitiesOfClass(
                    SportsCarEntity.class, new AABB(centerFloor.above()).inflate(2.5D));
            helper.assertTrue(spawned.size() == 1, "Expected 1 SportsCarEntity, found " + spawned.size());
            SportsCarEntity car = spawned.get(0);
            helper.assertTrue(Math.abs(Mth.wrapDegrees(car.getCarYaw() - 180.0F)) < 1.0F,
                    "Placed car yaw must match player yaw 180, got " + car.getCarYaw());

            // 碰撞阻挡测试：紧贴实体箱放阻挡方块时不得生成第二辆车
            BlockPos blockedFloor = helper.absolutePos(new BlockPos(4, 12, 4));
            level.setBlockAndUpdate(blockedFloor.above(), Blocks.STONE.defaultBlockState());
            ItemStack blockedStack = new ItemStack(ModItems.GULF_GT_CAR.get());
            driver.setItemInHand(InteractionHand.MAIN_HAND, blockedStack);
            BlockHitResult blockedHit = new BlockHitResult(
                    Vec3.atCenterOf(blockedFloor).add(0.0D, 0.5D, 0.0D),
                    Direction.UP,
                    blockedFloor,
                    false
            );
            InteractionResult blockedRes = blockedStack.useOn(new UseOnContext(driver, InteractionHand.MAIN_HAND, blockedHit));
            helper.assertTrue(!blockedRes.consumesAction() && !blockedStack.isEmpty(),
                    "Item useOn must fail when space is obstructed");

            // 2. 双座上车测试（0=主驾左舵，1=副驾右座，第3人不可上车）
            driver.setShiftKeyDown(false);
            InteractionResult r1 = car.interact(driver, InteractionHand.MAIN_HAND);
            helper.assertTrue(r1.consumesAction() && driver.getVehicle() == car, "Driver must mount seat 0");
            helper.assertTrue(car.isDriver(driver) && car.getSeatIndex(driver) == 0,
                    "First passenger must be driver (seat 0)");

            ServerPlayer passenger = mockPlayer(level.getServer(), level, "car-passenger");
            passenger.setPos(car.getX() + 1.5D, car.getY(), car.getZ());
            InteractionResult r2 = car.interact(passenger, InteractionHand.MAIN_HAND);
            helper.assertTrue(r2.consumesAction() && passenger.getVehicle() == car, "Second player must mount seat 1");
            helper.assertTrue(!car.isDriver(passenger) && car.getSeatIndex(passenger) == 1,
                    "Second passenger must be seat 1 and not driver");

            ServerPlayer third = mockPlayer(level.getServer(), level, "car-third");
            InteractionResult r3 = car.interact(third, InteractionHand.MAIN_HAND);
            helper.assertTrue(!r3.consumesAction() && third.getVehicle() == null,
                    "Third player must not mount a full 2-seater car");

            // 3. 验证左右舵座椅世界坐标与安全下车点
            Vec3 seat0 = car.getSeatWorldPosition(0, 1.0F);
            Vec3 seat1 = car.getSeatWorldPosition(1, 1.0F);
            // carYaw=180 (-Z) 时，主驾局部 -X 对应世界 -X，副驾局部 +X 对应世界 +X
            helper.assertTrue(seat0.x < car.getX() - 0.35D && seat1.x > car.getX() + 0.35D,
                    "Driver seat must be on -X and passenger on +X at yaw=180; seat0=" + seat0 + ", seat1=" + seat1);

            Vec3 driverDismount = car.getDismountLocationForPassenger(driver);
            Vec3 passengerDismount = car.getDismountLocationForPassenger(passenger);
            helper.assertTrue(driverDismount.x < car.getX() - 1.2D,
                    "Driver dismount must favor left door (-X), got " + driverDismount + ", carX=" + car.getX());
            helper.assertTrue(passengerDismount.x > car.getX() + 1.2D,
                    "Passenger dismount must favor right door (+X), got " + passengerDismount + ", carX=" + car.getX());

            driver.stopRiding();
            passenger.stopRiding();
            car.discard();
            helper.succeed();
        } finally {
            releaseForcedChunks(level, forced);
        }
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 120)
    public static void mouseAimSteeringAndForwardThrottle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = forceChunksForRegion(
                level,
                helper.absolutePos(new BlockPos(-10, 12, -16)),
                helper.absolutePos(new BlockPos(8, 15, 14))
        );
        buildFlatPlatform(level, helper, -10, 8, 12, -16, 14);

        SportsCarEntity car = ModEntities.GULF_GT_CAR.get().create(level);
        helper.assertTrue(car != null, "SportsCarEntity must instantiate");
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 10)));
        car.setPos(start.x, start.y, start.z);
        car.setCarYaw(180.0F);
        level.addFreshEntity(car);

        // 阶段 1：静止状态下改变鼠标目标视角，车身不得原地打转
        car.setServerInput(0.0F, 0.0F, 90.0F, false, false);
        helper.runAfterDelay(5, () -> {
            if (Math.abs(Mth.wrapDegrees(car.getCarYaw() - 180.0F)) > 0.05F) {
                car.discard();
                releaseForcedChunks(level, forced);
                helper.fail("Car must not spin in place when stationary, yaw=" + car.getCarYaw());
                return;
            }

            // 阶段 2：全油门沿 -Z 直线加速 16 tick
            car.setServerInput(1.0F, 0.0F, 180.0F, false, false);
            helper.runAfterDelay(16, () -> {
                if (car.getZ() >= start.z - 5.0D || car.getForwardSpeed() < 0.55F || car.isBraking()) {
                    car.discard();
                    releaseForcedChunks(level, forced);
                    helper.fail("Forward throttle failed: z=" + car.getZ() + " (start=" + start.z
                            + "), fwdSpeed=" + car.getForwardSpeed() + ", braking=" + car.isBraking());
                    return;
                }

                // 阶段 3：保持油门并将鼠标目标转向角拉向 +X 方向（yaw = -120° / 240°，即向右转向？
                // 注：yaw=180(-Z) 减小到 120(+X? computeForward(120) = (-sin120, 0, cos120) = (-0.866, 0, -0.5) 向左前方 -X!)
                car.setServerInput(1.0F, 0.0F, 120.0F, false, false);
                helper.runAfterDelay(14, () -> {
                    try {
                        float yaw = car.getCarYaw();
                        helper.assertTrue(yaw < 155.0F && yaw > 105.0F,
                                "Car yaw must smoothly track mouse targetYaw=120 from 180, got " + yaw);
                        helper.assertTrue(car.getX() < start.x - 0.8D,
                                "Car must steer toward -X when yaw moves toward 120, x=" + car.getX());
                        helper.succeed();
                    } finally {
                        car.discard();
                        releaseForcedChunks(level, forced);
                    }
                });
            });
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 90)
    public static void reverseDrivingAndReverseSteering(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = forceChunksForRegion(
                level,
                helper.absolutePos(new BlockPos(-6, 12, -4)),
                helper.absolutePos(new BlockPos(8, 15, 16))
        );
        buildFlatPlatform(level, helper, -6, 8, 12, -4, 16);

        SportsCarEntity car = ModEntities.GULF_GT_CAR.get().create(level);
        helper.assertTrue(car != null, "SportsCarEntity must instantiate");
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 2)));
        car.setPos(start.x, start.y, start.z);
        car.setCarYaw(180.0F);
        level.addFreshEntity(car);

        // 倒车 + 按住 A 键 (steerOverride=+1.0F)，鼠标视角仍保持 180°（不得强制把车头甩回 180°）
        car.setServerInput(-1.0F, 1.0F, 180.0F, false, false);
        helper.runAfterDelay(18, () -> {
            try {
                helper.assertTrue(car.getZ() > start.z + 2.5D,
                        "Reversing at yaw=180 must move toward +Z: startZ=" + start.z + ", z=" + car.getZ());
                helper.assertTrue(car.getForwardSpeed() < -0.20F,
                        "Forward speed must be negative when reversing, got " + car.getForwardSpeed());
                helper.assertTrue(car.isBraking(), "Tail brake light state must be active while reversing");
                float yawDelta = Mth.wrapDegrees(car.getCarYaw() - 180.0F);
                helper.assertTrue(yawDelta > 10.0F,
                        "Reverse steering with A key must rotate yaw positively without snapping to mouse targetYaw, yawDelta=" + yawDelta);
                helper.succeed();
            } finally {
                car.discard();
                releaseForcedChunks(level, forced);
            }
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 120)
    public static void handbrakeDriftAndNitroBoostAndRegen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = forceChunksForRegion(
                level,
                helper.absolutePos(new BlockPos(-14, 12, -26)),
                helper.absolutePos(new BlockPos(12, 15, 14))
        );
        buildFlatPlatform(level, helper, -14, 12, 12, -26, 14);

        SportsCarEntity car = ModEntities.GULF_GT_CAR.get().create(level);
        helper.assertTrue(car != null, "SportsCarEntity must instantiate");
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 10)));
        car.setPos(start.x, start.y, start.z);
        car.setCarYaw(180.0F);
        level.addFreshEntity(car);

        // 阶段 1：开启氮气全油门加速 14 tick
        car.setServerInput(1.0F, 0.0F, 180.0F, false, true);
        helper.runAfterDelay(14, () -> {
            float nitroAfterBoost = car.getNitroEnergy();
            double speedAfterBoost = car.getForwardSpeed();
            if (!car.isNitroActive() || nitroAfterBoost >= 88.0F
                    || speedAfterBoost <= SportsCarEntity.NORMAL_MAX_FORWARD_SPEED + 0.12D) {
                car.discard();
                releaseForcedChunks(level, forced);
                helper.fail("Nitro boost check failed: active=" + car.isNitroActive()
                        + ", nitro=" + nitroAfterBoost + ", speed=" + speedAfterBoost);
                return;
            }

            // 阶段 2：切入手刹漂移 + 左转 10 tick，验证漂移状态与加快回充氮气
            car.setServerInput(1.0F, 1.0F, 120.0F, true, false);
            helper.runAfterDelay(10, () -> {
                float nitroAfterDrift = car.getNitroEnergy();
                if (!car.isDrifting() || !car.isBraking() || car.isNitroActive()
                        || nitroAfterDrift <= nitroAfterBoost + 10.0F) {
                    car.discard();
                    releaseForcedChunks(level, forced);
                    helper.fail("Drift & nitro regen failed: drifting=" + car.isDrifting()
                            + ", braking=" + car.isBraking()
                            + ", nitroBefore=" + nitroAfterBoost + ", nitroAfter=" + nitroAfterDrift);
                    return;
                }

                // 阶段 3：松开所有操作 10 tick，验证退出漂移并持续回充
                car.setServerInput(0.0F, 0.0F, car.getCarYaw(), false, false);
                helper.runAfterDelay(10, () -> {
                    try {
                        helper.assertTrue(!car.isDrifting(), "Car must exit drifting after releasing handbrake");
                        helper.assertTrue(car.getNitroEnergy() > nitroAfterDrift + 4.0F,
                                "Nitro must continue regenerating when idle: before=" + nitroAfterDrift
                                        + ", after=" + car.getNitroEnergy());
                        helper.succeed();
                    } finally {
                        car.discard();
                        releaseForcedChunks(level, forced);
                    }
                });
            });
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void welcomeDoorsOpenOnProximityAndCloseWhenAwayOrDriving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = forceChunksForRegion(
                level,
                helper.absolutePos(new BlockPos(-4, 12, -10)),
                helper.absolutePos(new BlockPos(8, 15, 8))
        );
        buildFlatPlatform(level, helper, -4, 8, 12, -10, 8);

        SportsCarEntity car = ModEntities.GULF_GT_CAR.get().create(level);
        helper.assertTrue(car != null, "SportsCarEntity must instantiate");
        Vec3 carPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 13, 3)));
        car.setPos(carPos.x, carPos.y, carPos.z);
        car.setCarYaw(180.0F);
        level.addFreshEntity(car);

        ServerPlayer nearbyPlayer = mockPlayer(level.getServer(), level, "door-proximity-player");
        nearbyPlayer.setPos(carPos.x + 18.0D, carPos.y, carPos.z);
        level.addNewPlayer(nearbyPlayer);

        // 阶段 1：无人靠近且静止时迎宾门应关闭
        helper.runAfterDelay(4, () -> {
            if (car.getDoorOpenProgress() > 0.01F) {
                cleanupDoorTest(level, forced, car, nearbyPlayer);
                helper.fail("Doors must stay closed when no player is nearby, progress=" + car.getDoorOpenProgress());
                return;
            }

            // 阶段 2：玩家靠近至 2.0 格内（<= 3.5 格），迎宾门应在约 12 tick 内打开
            nearbyPlayer.setPos(carPos.x - 2.0D, carPos.y, carPos.z);
            helper.runAfterDelay(12, () -> {
                if (car.getDoorOpenProgress() < 0.85F) {
                    cleanupDoorTest(level, forced, car, nearbyPlayer);
                    helper.fail("Doors must open when player is within 3.5 blocks, progress=" + car.getDoorOpenProgress());
                    return;
                }

                // 阶段 3：玩家远离至 10 格开外，迎宾门应自动关闭
                nearbyPlayer.setPos(carPos.x + 18.0D, carPos.y, carPos.z);
                helper.runAfterDelay(12, () -> {
                    try {
                        helper.assertTrue(car.getDoorOpenProgress() < 0.05F,
                                "Doors must close after nearby player leaves, progress=" + car.getDoorOpenProgress());
                        helper.succeed();
                    } finally {
                        cleanupDoorTest(level, forced, car, nearbyPlayer);
                    }
                });
            });
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 90)
    public static void oneBlockStepAutoClimbAndTerrainPitch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = forceChunksForRegion(
                level,
                helper.absolutePos(new BlockPos(-2, 12, -2)),
                helper.absolutePos(new BlockPos(4, 16, 13))
        );
        // 底层跑道 y=12，z=0..4 额外堆高 1 格台阶 (y=13 实心石块，顶面 y=14)
        buildFlatPlatform(level, helper, -2, 4, 12, -2, 13);
        for (int x = -2; x <= 4; x++) {
            for (int z = -2; z <= 4; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 13, z)), Blocks.STONE.defaultBlockState());
            }
        }

        SportsCarEntity car = ModEntities.GULF_GT_CAR.get().create(level);
        helper.assertTrue(car != null, "SportsCarEntity must instantiate");
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 10)));
        car.setPos(start.x, start.y, start.z);
        car.setCarYaw(180.0F);
        level.addFreshEntity(car);
        car.setServerInput(0.85F, 0.0F, 180.0F, false, false);

        float[] maxPitchSeen = new float[]{0.0F};
        observeStepClimb(helper, level, forced, car, start.y, maxPitchSeen, 0);
    }

    private static void observeStepClimb(GameTestHelper helper,
                                         ServerLevel level,
                                         List<ChunkPos> forced,
                                         SportsCarEntity car,
                                         double startY,
                                         float[] maxPitchSeen,
                                         int tick) {
        helper.runAfterDelay(1, () -> {
            maxPitchSeen[0] = Math.max(maxPitchSeen[0], car.getTerrainPitch());
            if (car.getY() >= startY + 0.95D && tick >= 12) {
                try {
                    helper.assertTrue(maxPitchSeen[0] > 3.0F,
                            "Terrain pitch must tilt nose-up while climbing 1-block step, maxPitch=" + maxPitchSeen[0]);
                    helper.succeed();
                } finally {
                    car.discard();
                    releaseForcedChunks(level, forced);
                }
                return;
            }
            if (tick >= 40) {
                car.discard();
                releaseForcedChunks(level, forced);
                helper.fail("Car failed to climb 1-block step within 40 ticks: startY=" + startY
                        + ", y=" + car.getY() + ", z=" + car.getZ() + ", maxPitch=" + maxPitchSeen[0]);
                return;
            }
            observeStepClimb(helper, level, forced, car, startY, maxPitchSeen, tick + 1);
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 60)
    public static void pickupWhenEmptyAndBlockedWhenOccupied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = forceChunksForRegion(
                level,
                helper.absolutePos(new BlockPos(-1, 12, -1)),
                helper.absolutePos(new BlockPos(3, 15, 3))
        );
        try {
            buildFlatPlatform(level, helper, -1, 3, 12, -1, 3);
            Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 1)));

            ServerPlayer player = mockPlayer(level.getServer(), level, "car-pickup-test");
            player.setPos(center.x, center.y, center.z);
            player.getInventory().clearContent();

            SportsCarEntity car1 = ModEntities.GULF_GT_CAR.get().create(level);
            helper.assertTrue(car1 != null, "First SportsCarEntity must instantiate");
            car1.setPos(center.x, center.y, center.z);
            level.addFreshEntity(car1);

            // 有人乘坐时禁止潜行右键或空手左键回收
            helper.assertTrue(player.startRiding(car1, true), "Player must mount car1");
            player.setShiftKeyDown(true);
            InteractionResult occupiedShift = car1.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!occupiedShift.consumesAction() && !car1.isRemoved(),
                    "Shift+right-click must NOT pick up occupied car");
            boolean occupiedHurt = car1.hurt(level.damageSources().playerAttack(player), 1.0F);
            helper.assertTrue(!occupiedHurt && !car1.isRemoved(),
                    "Empty-hand attack must NOT pick up occupied car");

            // 下车空载后：潜行 + 右键无损回收
            player.stopRiding();
            player.setShiftKeyDown(true);
            InteractionResult emptyShift = car1.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(emptyShift.consumesAction() && car1.isRemoved(),
                    "Shift+right-click on empty car must pick up and discard entity");
            helper.assertTrue(player.getInventory().countItem(ModItems.GULF_GT_CAR.get()) == 1,
                    "Shift+right-click must return 1x GULF_GT_CAR");

            // 空载时：空手左键敲击无损回收
            player.getInventory().clearContent();
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            SportsCarEntity car2 = ModEntities.GULF_GT_CAR.get().create(level);
            helper.assertTrue(car2 != null, "Second SportsCarEntity must instantiate");
            car2.setPos(center.x, center.y, center.z);
            level.addFreshEntity(car2);

            boolean emptyHurt = car2.hurt(level.damageSources().playerAttack(player), 1.0F);
            helper.assertTrue(emptyHurt && car2.isRemoved(),
                    "Empty-hand left-click on empty car must pick up and discard entity");
            helper.assertTrue(player.getInventory().countItem(ModItems.GULF_GT_CAR.get()) == 1,
                    "Empty-hand left-click must return 1x GULF_GT_CAR");

            helper.succeed();
        } finally {
            releaseForcedChunks(level, forced);
        }
    }

    // ==================== 测试辅助方法 ====================

    private static void cleanupDoorTest(ServerLevel level, List<ChunkPos> forced,
                                        SportsCarEntity car, ServerPlayer nearbyPlayer) {
        car.discard();
        level.removePlayerImmediately(nearbyPlayer, Entity.RemovalReason.DISCARDED);
        releaseForcedChunks(level, forced);
    }

    private static void buildFlatPlatform(ServerLevel level, GameTestHelper helper,
                                          int minX, int maxX, int floorY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, floorY, z)),
                        Blocks.STONE.defaultBlockState());
                for (int y = floorY + 1; y <= floorY + 3; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static List<ChunkPos> forceChunksForRegion(ServerLevel level, BlockPos minPos, BlockPos maxPos) {
        int minCx = minPos.getX() >> 4;
        int maxCx = maxPos.getX() >> 4;
        int minCz = minPos.getZ() >> 4;
        int maxCz = maxPos.getZ() >> 4;
        Set<ChunkPos> existing = new HashSet<>();
        for (long packed : level.getForcedChunks()) {
            existing.add(new ChunkPos(packed));
        }
        List<ChunkPos> added = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (!existing.contains(cp)) {
                    level.setChunkForced(cx, cz, true);
                    added.add(cp);
                }
            }
        }
        return added;
    }

    private static void releaseForcedChunks(ServerLevel level, List<ChunkPos> added) {
        for (ChunkPos cp : added) {
            level.setChunkForced(cp.x, cp.z, false);
        }
    }

    private static ServerPlayer mockPlayer(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
            }
        };
        return player;
    }
}
