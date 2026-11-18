package org.gwfx.universaltool.client;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.naiwa.NaiwaLaughSoundPayload;
import org.gwfx.universaltool.naiwa.DidiStatusPayload;
import org.gwfx.universaltool.network.CounterGuardEffectPayload;
import org.gwfx.universaltool.network.ModPacketHandler;
import org.gwfx.universaltool.network.OpenSkillScreenPayload;
import org.gwfx.universaltool.network.OreSightPayload;
import org.gwfx.universaltool.network.PersonalDimensionSyncPayload;
import org.gwfx.universaltool.network.PersonalSpaceResultPayload;
import org.gwfx.universaltool.network.PersonalSpaceScreenPayload;
import org.gwfx.universaltool.network.PhoenixCinematicStartPayload;
import org.gwfx.universaltool.network.SyncSkillDataPayload;
import org.gwfx.universaltool.network.SyncWaypointsPayload;
import org.gwfx.universaltool.network.WormholeLiveVistaPayload;
import org.gwfx.universaltool.waypoint.Waypoint;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-only handlers for all server-to-client (S2C) custom payloads.
 * Kept strictly in {@code client/} so common payload records and {@link ModPacketHandler}
 * never trigger client class loading on a dedicated server.
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {
    }

    public static void registerAll() {
        ModPacketHandler.registerClientHandler(OreSightPayload.TYPE, ClientPayloadHandlers::handleOreSight);
        ModPacketHandler.registerClientHandler(CounterGuardEffectPayload.TYPE, ClientPayloadHandlers::handleCounterGuardEffect);
        ModPacketHandler.registerClientHandler(SyncWaypointsPayload.TYPE, ClientPayloadHandlers::handleSyncWaypoints);
        ModPacketHandler.registerClientHandler(PhoenixCinematicStartPayload.TYPE, ClientPayloadHandlers::handlePhoenixCinematicStart);
        ModPacketHandler.registerClientHandler(PersonalSpaceScreenPayload.TYPE, ClientPayloadHandlers::handlePersonalSpaceScreen);
        ModPacketHandler.registerClientHandler(PersonalSpaceResultPayload.TYPE, ClientPayloadHandlers::handlePersonalSpaceResult);
        ModPacketHandler.registerClientHandler(PersonalDimensionSyncPayload.TYPE, ClientPayloadHandlers::handlePersonalDimensionSync);
        ModPacketHandler.registerClientHandler(SyncSkillDataPayload.TYPE, ClientPayloadHandlers::handleSyncSkillData);
        ModPacketHandler.registerClientHandler(OpenSkillScreenPayload.TYPE, ClientPayloadHandlers::handleOpenSkillScreen);
        ModPacketHandler.registerClientHandler(WormholeLiveVistaPayload.TYPE, ClientPayloadHandlers::handleWormholeLiveVista);
        ModPacketHandler.registerClientHandler(NaiwaLaughSoundPayload.TYPE, ClientPayloadHandlers::handleNaiwaLaughSound);
        ModPacketHandler.registerClientHandler(DidiStatusPayload.TYPE,
                (payload, context) -> context.enqueueWork(() -> DidiDaidaPage.receiveStatus(payload)));
    }

    public static void handleNaiwaLaughSound(NaiwaLaughSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NaiwaLaughSoundPlayer.handle(
                payload.entityId(), payload.durationTicks(), payload.stop()));
    }

    public static void handleWormholeLiveVista(WormholeLiveVistaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PhotoManager.registerLiveVista(
                payload.vistaId(), payload.width(), payload.height(), payload.compressedRgb()));
    }

    public static void handleSyncWaypoints(SyncWaypointsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Set<String> previousPhotos = new HashSet<>();
            for (Waypoint wp : ClientWaypointCache.getWaypoints()) {
                if (wp.photoId() != null && !wp.photoId().isEmpty()) {
                    previousPhotos.add(wp.photoId());
                }
            }
            Set<String> currentPhotos = new HashSet<>();
            for (Waypoint wp : payload.waypoints()) {
                if (wp.photoId() != null && !wp.photoId().isEmpty()) {
                    currentPhotos.add(wp.photoId());
                }
            }
            ClientWaypointCache.update(payload.waypoints(), payload.maxCapacity());
            for (String oldPhoto : previousPhotos) {
                if (!currentPhotos.contains(oldPhoto)) {
                    PhotoManager.releasePhoto(oldPhoto);
                }
            }
        });
    }

    public static void handleOreSight(OreSightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OreSightMarkers.update(payload.data()));
    }

    public static void handleCounterGuardEffect(CounterGuardEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CounterGuardEffectRenderer.apply(payload));
    }

    public static void handlePhoenixCinematicStart(PhoenixCinematicStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Direction facing = Direction.from2DDataValue(payload.facingIndex());
            PhoenixCinematicController.startCinematic(payload.podPos(), facing, payload.dimensionId(), payload.totalTicks());
        });
    }

    public static void handlePersonalSpaceScreen(PersonalSpaceScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientScreens.openPersonalSpace(
                payload.gatePos(), payload.mode(), payload.ownerName(),
                payload.environmentId(), payload.spaceDimension()));
    }

    public static void handlePersonalSpaceResult(PersonalSpaceResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientScreens.handlePersonalSpaceResult(payload));
    }

    public static void handlePersonalDimensionSync(PersonalDimensionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PersonalDimensionClientRegistry.apply(payload));
    }

    public static void handleSyncSkillData(SyncSkillDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSkillCache.update(payload.data()));
    }

    public static void handleOpenSkillScreen(OpenSkillScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(ClientScreens::openSkills);
    }
}
