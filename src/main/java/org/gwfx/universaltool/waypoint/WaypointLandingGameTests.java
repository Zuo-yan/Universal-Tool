package org.gwfx.universaltool.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class WaypointLandingGameTests {
    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void landingRequiresSupportClearanceAndSafeBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(0, 14, 0));
        Vec3 target = Vec3.atBottomCenterOf(feet);
        helper.assertTrue(WaypointLanding.find(level, target) == null, "A destination above the void must be rejected");

        level.setBlockAndUpdate(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        helper.assertTrue(WaypointLanding.find(level, target) == null, "A hazardous floor must be rejected");

        level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(target.equals(WaypointLanding.find(level, target)), "The exact safe landing must be retained");
        level.setBlockAndUpdate(feet, Blocks.OAK_FENCE.defaultBlockState());
        BlockPos nearby = feet.east();
        level.setBlockAndUpdate(nearby.below(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(Vec3.atBottomCenterOf(nearby).equals(WaypointLanding.find(level, target)),
                "An obstructed destination must use a nearby safe standing position");
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.below(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(nearby.below(), Blocks.AIR.defaultBlockState());
        helper.succeed();
    }
}
