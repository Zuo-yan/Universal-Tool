package org.gwfx.universaltool.wheelchair;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/** Tracks the jump input edge and the thrust budget for the current takeoff. */
final class WheelchairJump {
    static final int SUSTAIN_TICKS = 16;
    private static final int SURFACE_CLEARANCE_TICKS = 4;

    private boolean held;
    private boolean pressed;
    private int remaining;
    private Direction launchFace = Direction.DOWN;

    void input(boolean down) {
        pressed |= down && !held;
        held = down;
        if (!held) {
            remaining = 0;
        }
    }

    Thrust step(boolean canLaunch, Direction face) {
        boolean launch = pressed && canLaunch;
        pressed = false;
        if (launch) {
            launchFace = face;
            remaining = held ? SUSTAIN_TICKS : 0;
            return new Thrust(true, false, face);
        }
        if (remaining > 0) {
            boolean clearingSurface = launchFace != Direction.DOWN
                    && SUSTAIN_TICKS - remaining < SURFACE_CLEARANCE_TICKS;
            remaining--;
            return clearingSurface ? new Thrust(false, false, launchFace) : Thrust.LIFT;
        }
        return Thrust.OFF;
    }

    void reset() {
        held = false;
        pressed = false;
        remaining = 0;
    }

    record Thrust(boolean launch, boolean lift, @Nullable Direction exhaust) {
        private static final Thrust OFF = new Thrust(false, false, null);
        private static final Thrust LIFT = new Thrust(false, true, Direction.DOWN);

        boolean active() {
            return exhaust != null;
        }
    }
}
