package org.gwfx.universaltool.wheelchair;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
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
 * Server-side GameTests for the All-Terrain Wheelchair recipe, wall/ceiling climbing,
 * SpaceX boost ramming, and one-click pickup.
 */
@PrefixGameTestTemplate(false)
public final class AllTerrainWheelchairGameTests {

    private static final ResourceLocation WHEELCHAIR_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "all_terrain_wheelchair_polymerize");

    private AllTerrainWheelchairGameTests() {
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void polymerizerRecipeProducesWheelchair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Optional<RecipeHolder<?>> recipeByKey = level.getRecipeManager().byKey(WHEELCHAIR_RECIPE_ID);
        helper.assertTrue(recipeByKey.isPresent(),
                "Recipe " + WHEELCHAIR_RECIPE_ID + " must exist in RecipeManager");
        helper.assertTrue(recipeByKey.get().value() instanceof PolymerizerRecipe,
                "Recipe " + WHEELCHAIR_RECIPE_ID + " must be a PolymerizerRecipe");

        List<ItemStack> ingredients = new ArrayList<>(12);
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(ModItems.VOID_ESSENCE.get()));
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(ModItems.UNIVERSAL_REPAIR_MATERIAL.get()));
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(Items.NETHERITE_INGOT));

        PolymerizerRecipeInput input = new PolymerizerRecipeInput(ingredients);
        Optional<RecipeHolder<PolymerizerRecipe>> match = level.getRecipeManager().getRecipeFor(
                ModRecipes.POLYMERIZING_TYPE.get(), input, level);
        helper.assertTrue(match.isPresent(), "All-Terrain Wheelchair 4+4+4 polymerizer recipe must match");
        helper.assertTrue(match.get().id().equals(WHEELCHAIR_RECIPE_ID),
                "Matched recipe id must be " + WHEELCHAIR_RECIPE_ID + ", got " + match.get().id());

        ItemStack output = match.get().value().assemble(input, level.registryAccess());
        helper.assertTrue(output.is(ModItems.ALL_TERRAIN_WHEELCHAIR.get()) && output.getCount() == 1,
                "Polymerizer output must be 1x ALL_TERRAIN_WHEELCHAIR, got " + output);

        // Removing one netherite ingot must fail matching
        ingredients.remove(ingredients.size() - 1);
        helper.assertTrue(!match.get().value().matches(new PolymerizerRecipeInput(ingredients), level),
                "Incomplete ingredients must not match wheelchair recipe");

        // Verify creative tab registration
        CreativeModeTab tab = ModCreativeTabs.UNIVERSAL_TOOL_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), false, level.registryAccess()));
        boolean inTab = tab.getDisplayItems().stream()
                .anyMatch(stack -> stack.is(ModItems.ALL_TERRAIN_WHEELCHAIR.get()));
        helper.assertTrue(inTab, "UNIVERSAL_TOOL_TAB must contain ALL_TERRAIN_WHEELCHAIR");

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 120)
    public static void floorToWallToCeilingConcaveClimb(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Elevated arena (y=12..17) avoids any GameTest 3x3x3 perimeter barrier at ground level
        List<ChunkPos> forcedChunks = forceChunksForRegion(level, helper.absolutePos(new BlockPos(0, 12, 0)),
                helper.absolutePos(new BlockPos(2, 17, 5)));

        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 5; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 12, z)), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 17, z)), Blocks.STONE.defaultBlockState());
            }
            for (int y = 13; y <= 16; y++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 0)), Blocks.STONE.defaultBlockState());
                for (int z = 1; z <= 5; z++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
                }
            }
        }

        AllTerrainWheelchairEntity wc = ModEntities.ALL_TERRAIN_WHEELCHAIR.get().create(level);
        helper.assertTrue(wc != null, "AllTerrainWheelchairEntity must instantiate");

        Vec3 startPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 4)));
        wc.setPos(startPos.x, startPos.y, startPos.z);
        wc.setAttachedFace(Direction.DOWN);
        // On DOWN, yaw=180 points forward along -Z (NORTH)
        wc.setWheelchairYaw(180.0F);
        helper.assertTrue(wc.getWorldForward().z < -0.99D,
                "Initial forward at yaw=180 on DOWN must point NORTH (-Z), got " + wc.getWorldForward());

        level.addFreshEntity(wc);
        wc.setServerInput(1.0F, 0.0F, false, false);

        ClimbState state = new ClimbState(startPos.y, forcedChunks);
        observeClimbTick(helper, level, wc, state, 0);
    }

    private static final class ClimbState {
        final double startY;
        final List<ChunkPos> forcedChunks;
        boolean sawFloor = true;
        boolean sawNorthWall;
        boolean sawCeiling;
        int ceilingHoldTicks;

        ClimbState(double startY, List<ChunkPos> forcedChunks) {
            this.startY = startY;
            this.forcedChunks = forcedChunks;
        }
    }

    private static void observeClimbTick(GameTestHelper helper,
                                         ServerLevel level,
                                         AllTerrainWheelchairEntity wc,
                                         ClimbState state,
                                         int tick) {
        helper.runAfterDelay(1, () -> {
            Direction face = wc.getAttachedFace();
            if (face == Direction.NORTH) {
                state.sawNorthWall = true;
            } else if (state.sawNorthWall && face == Direction.UP) {
                state.sawCeiling = true;
                state.ceilingHoldTicks++;
            } else if (state.sawCeiling && face != Direction.UP) {
                releaseForcedChunks(level, state.forcedChunks);
                helper.fail("Wheelchair detached prematurely from ceiling UP to " + face
                        + " at pos=" + wc.position());
                return;
            }

            if (state.sawFloor && state.sawNorthWall && state.sawCeiling && state.ceilingHoldTicks >= 3) {
                helper.assertTrue(wc.getY() > state.startY + 2.4D,
                        "Wheelchair on ceiling must remain elevated near ceiling; startY=" + state.startY
                                + ", currentY=" + wc.getY());
                helper.assertTrue(wc.getWorldNormal().y < -0.99D,
                        "Ceiling surface normal must point -Y, got " + wc.getWorldNormal());
                wc.discard();
                releaseForcedChunks(level, state.forcedChunks);
                helper.succeed();
                return;
            }

            if (tick >= 70) {
                releaseForcedChunks(level, state.forcedChunks);
                helper.fail("Did not complete DOWN -> NORTH -> UP climb within 70 ticks: sawFloor="
                        + state.sawFloor + ", sawNorthWall=" + state.sawNorthWall
                        + ", sawCeiling=" + state.sawCeiling + ", currentFace=" + face
                        + ", pos=" + wc.position() + ", vel=" + wc.getDeltaMovement());
                return;
            }

            observeClimbTick(helper, level, wc, state, tick + 1);
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void boostIncreasesSpeedAndRamsHostileMob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Elevated runway (y=12, z=0..6) avoids any GameTest 3x3x3 perimeter barrier at ground level
        List<ChunkPos> forcedChunks = forceChunksForRegion(level, helper.absolutePos(new BlockPos(0, 12, 0)),
                helper.absolutePos(new BlockPos(2, 15, 6)));

        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 6; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 12, z)), Blocks.STONE.defaultBlockState());
                for (int y = 13; y <= 15; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
                }
            }
        }

        AllTerrainWheelchairEntity wc = ModEntities.ALL_TERRAIN_WHEELCHAIR.get().create(level);
        helper.assertTrue(wc != null, "AllTerrainWheelchairEntity must instantiate");

        Vec3 startPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 5)));
        wc.setPos(startPos.x, startPos.y, startPos.z);
        wc.setAttachedFace(Direction.DOWN);
        wc.setWheelchairYaw(180.0F); // -Z (NORTH)
        level.addFreshEntity(wc);

        // Phase 1: normal drive for 3 ticks
        wc.setServerInput(1.0F, 0.0F, false, false);
        double[] lastZ = new double[]{wc.getZ()};
        double[] maxNormalStep = new double[]{0.0D};
        double[] maxBoostStep = new double[]{0.0D};

        stepBoostTest(helper, level, forcedChunks, wc, lastZ, maxNormalStep, maxBoostStep,
                null, 0.0F, 0.0D, 0);
    }

    private static void stepBoostTest(GameTestHelper helper,
                                      ServerLevel level,
                                      List<ChunkPos> forcedChunks,
                                      AllTerrainWheelchairEntity wc,
                                      double[] lastZ,
                                      double[] maxNormalStep,
                                      double[] maxBoostStep,
                                      Zombie zombie,
                                      float initialZombieHp,
                                      double initialZombieZ,
                                      int tick) {
        helper.runAfterDelay(1, () -> {
            double stepDist = lastZ[0] - wc.getZ();
            lastZ[0] = wc.getZ();

            if (tick < 3) {
                maxNormalStep[0] = Math.max(maxNormalStep[0], stepDist);
                if (tick == 2) {
                    helper.assertTrue(!wc.isBoosting(), "Wheelchair must not be boosting before boost input");
                    helper.assertTrue(maxNormalStep[0] > 0.2D
                                    && maxNormalStep[0] <= AllTerrainWheelchairEntity.BASE_MAX_SPEED + 0.02D,
                            "Normal max step must stay within BASE_MAX_SPEED, got " + maxNormalStep[0]
                                    + " (tickCount=" + wc.tickCount + ", pos=" + wc.position()
                                    + ", vel=" + wc.getDeltaMovement() + ")");

                    // Spawn Zombie directly ahead along -Z and activate SpaceX Boost
                    Vec3 zPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 13, 2)));
                    Zombie spawned = EntityType.ZOMBIE.create(level);
                    helper.assertTrue(spawned != null, "Zombie must instantiate");
                    spawned.setPos(zPos.x, zPos.y, zPos.z);
                    spawned.setPersistenceRequired();
                    level.addFreshEntity(spawned);

                    wc.setServerInput(1.0F, 0.0F, false, true);
                    stepBoostTest(helper, level, forcedChunks, wc, lastZ, maxNormalStep, maxBoostStep,
                            spawned, spawned.getHealth(), spawned.getZ(), tick + 1);
                    return;
                }
                stepBoostTest(helper, level, forcedChunks, wc, lastZ, maxNormalStep, maxBoostStep,
                        zombie, initialZombieHp, initialZombieZ, tick + 1);
                return;
            }

            // Phase 2: boosting + ramming (ticks 3..6)
            maxBoostStep[0] = Math.max(maxBoostStep[0], stepDist);
            helper.assertTrue(wc.isBoosting(), "Wheelchair must report boosting when throttle & boost are active");

            if (tick >= 6) {
                helper.assertTrue(maxBoostStep[0] > maxNormalStep[0] + 0.25D
                                && maxBoostStep[0] > AllTerrainWheelchairEntity.BASE_MAX_SPEED + 0.15D,
                        "Boosted speed must exceed normal speed: normal=" + maxNormalStep[0]
                                + ", boost=" + maxBoostStep[0]);
                helper.assertTrue(zombie.getHealth() < initialZombieHp,
                        "Boost ram must damage hostile Zombie: before=" + initialZombieHp
                                + ", after=" + zombie.getHealth());
                helper.assertTrue(zombie.getZ() < initialZombieZ - 0.3D
                                || zombie.getDeltaMovement().z < -0.5D,
                        "Boost ram must knock Zombie forward (-Z): beforeZ=" + initialZombieZ
                                + ", afterZ=" + zombie.getZ() + ", vel=" + zombie.getDeltaMovement());

                wc.discard();
                zombie.discard();
                releaseForcedChunks(level, forcedChunks);
                helper.succeed();
                return;
            }

            stepBoostTest(helper, level, forcedChunks, wc, lastZ, maxNormalStep, maxBoostStep,
                    zombie, initialZombieHp, initialZombieZ, tick + 1);
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void shiftRightClickAndAttackPickUpWheelchair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Center at (1, 1, 1) inside 3x3x3 empty structure (x,z in 0..2) so 1.1F hitbox stays clear of perimeter
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos floorPos = helper.absolutePos(new BlockPos(1, 1, 1));

        ServerPlayer player = mockPlayer(level.getServer(), level, "wheelchair-pickup-test");
        player.setPos(floorPos.getX() + 0.5D, floorPos.getY() + 1.0D, floorPos.getZ() + 0.5D);
        player.getInventory().clearContent();

        // 1. Shift + Right-Click pickup
        AllTerrainWheelchairEntity wc1 = ModEntities.ALL_TERRAIN_WHEELCHAIR.get().create(level);
        helper.assertTrue(wc1 != null, "First wheelchair must instantiate");
        wc1.setPos(floorPos.getX() + 0.5D, floorPos.getY() + 1.0D, floorPos.getZ() + 0.5D);
        level.addFreshEntity(wc1);

        player.setShiftKeyDown(true);
        InteractionResult shiftResult = wc1.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(shiftResult.consumesAction(), "Shift+right-click must consume action");
        helper.assertTrue(wc1.isRemoved(), "Shift+right-click must discard wheelchair entity");
        helper.assertTrue(player.getInventory().countItem(ModItems.ALL_TERRAIN_WHEELCHAIR.get()) == 1,
                "Shift+right-click must return 1x ALL_TERRAIN_WHEELCHAIR to inventory");

        // 2. Player attack (left-click) pickup
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);

        AllTerrainWheelchairEntity wc2 = ModEntities.ALL_TERRAIN_WHEELCHAIR.get().create(level);
        helper.assertTrue(wc2 != null, "Second wheelchair must instantiate");
        wc2.setPos(floorPos.getX() + 0.5D, floorPos.getY() + 1.0D, floorPos.getZ() + 0.5D);
        level.addFreshEntity(wc2);

        boolean hurtHandled = wc2.hurt(level.damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(hurtHandled, "Player attack must be handled by wheelchair");
        helper.assertTrue(wc2.isRemoved(), "Player attack must discard wheelchair entity");
        helper.assertTrue(player.getInventory().countItem(ModItems.ALL_TERRAIN_WHEELCHAIR.get()) == 1,
                "Player attack must return 1x ALL_TERRAIN_WHEELCHAIR to inventory");

        // 3. Item placement via useOn spawns entity & non-shift right-click mounts player
        ItemStack stack = player.getInventory().getItem(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(floorPos).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                floorPos,
                false
        );
        InteractionResult useOnResult = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(useOnResult.consumesAction(), "Item useOn must deploy wheelchair, got=" + useOnResult);
        helper.assertTrue(stack.isEmpty(), "Survival item useOn must consume the item");

        List<AllTerrainWheelchairEntity> placed = level.getEntitiesOfClass(
                AllTerrainWheelchairEntity.class,
                new AABB(floorPos.above()).inflate(1.5D));
        helper.assertTrue(placed.size() == 1, "Exactly one wheelchair entity must be spawned by useOn");
        AllTerrainWheelchairEntity wc3 = placed.get(0);
        helper.assertTrue(wc3.getAttachedFace() == Direction.DOWN,
                "Placing on block UP face must attach to DOWN");

        player.setShiftKeyDown(false);
        InteractionResult mountResult = wc3.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(mountResult.consumesAction(), "Non-shift right-click must mount player");
        helper.assertTrue(player.getVehicle() == wc3, "Player must be riding deployed wheelchair");

        player.stopRiding();
        wc3.discard();
        helper.succeed();
    }

    private static List<ChunkPos> forceChunksForRegion(ServerLevel level, BlockPos minPos, BlockPos maxPos) {
        int minCx = SectionPosCoord(minPos.getX());
        int maxCx = SectionPosCoord(maxPos.getX());
        int minCz = SectionPosCoord(minPos.getZ());
        int maxCz = SectionPosCoord(maxPos.getZ());
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

    private static int SectionPosCoord(int blockCoord) {
        return blockCoord >> 4;
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
