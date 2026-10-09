package org.gwfx.universaltool.client;

import org.gwfx.universaltool.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientWaypointCache {
    private static List<Waypoint> waypoints = new ArrayList<>();
    private static int maxCapacity = 12;

    public static synchronized void update(List<Waypoint> list, int capacity) {
        waypoints = new ArrayList<>(list);
        maxCapacity = capacity;
    }

    public static synchronized List<Waypoint> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public static synchronized int getMaxCapacity() {
        return maxCapacity;
    }
}
