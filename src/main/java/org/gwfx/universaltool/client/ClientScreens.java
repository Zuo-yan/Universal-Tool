package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.gwfx.universaltool.network.PersonalSpaceResultPayload;

/** Client-only screen construction; common items and payload classes never reference Screen. */
public final class ClientScreens {
    private ClientScreens() {}
    public static void openCodex() { Minecraft.getInstance().setScreen(new CodexScreen()); }
    public static void openSkills() {
        Minecraft.getInstance().setScreen(new SkillScreen());
    }
    public static void openPersonalSpace(BlockPos gatePos, int mode, String ownerName, String environmentId) {
        openPersonalSpace(gatePos, mode, ownerName, environmentId, "");
    }
    public static void openPersonalSpace(BlockPos gatePos, int mode, String ownerName,
                                         String environmentId, String spaceDimension) {
        Minecraft.getInstance().setScreen(new PersonalSpaceScreen(
                gatePos, mode, ownerName, environmentId, spaceDimension));
    }

    /**
     * Routes a server verdict back to the screen that asked for it.
     *
     * <p>Silently dropped when the player already closed the screen or opened a different one; the
     * reply carries no state the client cannot re-derive by acting again.
     */
    public static void handlePersonalSpaceResult(PersonalSpaceResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof PersonalSpaceScreen screen) {
            screen.onServerResult(payload);
        }
    }
}
