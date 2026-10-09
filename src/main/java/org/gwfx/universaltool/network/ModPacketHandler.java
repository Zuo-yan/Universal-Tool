package org.gwfx.universaltool.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.gwfx.universaltool.UniversalToolMod;

public final class ModPacketHandler {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UniversalToolMod.MODID).versioned("3");

        // 客户端发往服务端
        registrar.playToServer(SaveWaypointPayload.TYPE, SaveWaypointPayload.STREAM_CODEC, SaveWaypointPayload::handle);
        registrar.playToServer(DeleteWaypointPayload.TYPE, DeleteWaypointPayload.STREAM_CODEC, DeleteWaypointPayload::handle);
        registrar.playToServer(TeleportRequestPayload.TYPE, TeleportRequestPayload.STREAM_CODEC, TeleportRequestPayload::handle);
        registrar.playToServer(WriteScrollPayload.TYPE, WriteScrollPayload.STREAM_CODEC, WriteScrollPayload::handle);
        registrar.playToServer(PersonalSpaceActionPayload.TYPE, PersonalSpaceActionPayload.STREAM_CODEC,
                PersonalSpaceActionPayload::handle);
        registrar.playToServer(PersonalDimensionReadyPayload.TYPE, PersonalDimensionReadyPayload.STREAM_CODEC,
                PersonalDimensionReadyPayload::handle);

        // 服务端发往客户端
        registrar.playToClient(SyncWaypointsPayload.TYPE, SyncWaypointsPayload.STREAM_CODEC, SyncWaypointsPayload::handle);
        registrar.playToClient(PhoenixCinematicStartPayload.TYPE, PhoenixCinematicStartPayload.STREAM_CODEC, PhoenixCinematicStartPayload::handle);
        registrar.playToClient(PersonalSpaceScreenPayload.TYPE, PersonalSpaceScreenPayload.STREAM_CODEC,
                PersonalSpaceScreenPayload::handle);
        registrar.playToClient(PersonalSpaceResultPayload.TYPE, PersonalSpaceResultPayload.STREAM_CODEC,
                PersonalSpaceResultPayload::handle);
        registrar.playToClient(PersonalDimensionSyncPayload.TYPE, PersonalDimensionSyncPayload.STREAM_CODEC,
                PersonalDimensionSyncPayload::handle);
    }
}
