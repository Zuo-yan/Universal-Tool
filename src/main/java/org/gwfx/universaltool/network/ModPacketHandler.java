package org.gwfx.universaltool.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.klein.KleinActionPayload;
import org.gwfx.universaltool.klein.KleinCraftFillPayload;
import org.gwfx.universaltool.klein.KleinDrawerPayload;
import org.gwfx.universaltool.klein.KleinOpenPayload;
import org.gwfx.universaltool.klein.KleinRenamePayload;
import org.gwfx.universaltool.klein.KleinSyncPayload;

public final class ModPacketHandler {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UniversalToolMod.MODID).versioned("12");

        // 客户端发往服务端
        registrar.playToServer(SaveWaypointPayload.TYPE, SaveWaypointPayload.STREAM_CODEC, SaveWaypointPayload::handle);
        registrar.playToServer(DeleteWaypointPayload.TYPE, DeleteWaypointPayload.STREAM_CODEC, DeleteWaypointPayload::handle);
        registrar.playToServer(TeleportRequestPayload.TYPE, TeleportRequestPayload.STREAM_CODEC, TeleportRequestPayload::handle);
        registrar.playToServer(WriteScrollPayload.TYPE, WriteScrollPayload.STREAM_CODEC, WriteScrollPayload::handle);
        registrar.playToServer(PersonalSpaceActionPayload.TYPE, PersonalSpaceActionPayload.STREAM_CODEC,
                PersonalSpaceActionPayload::handle);
        registrar.playToServer(PersonalDimensionReadyPayload.TYPE, PersonalDimensionReadyPayload.STREAM_CODEC,
                PersonalDimensionReadyPayload::handle);
        registrar.playToServer(SkillActionPayload.TYPE, SkillActionPayload.STREAM_CODEC, SkillActionPayload::handle);
        registrar.playToServer(SelectActiveSkillPayload.TYPE, SelectActiveSkillPayload.STREAM_CODEC,
                SelectActiveSkillPayload::handle);
        registrar.playToServer(CastActiveSkillPayload.TYPE, CastActiveSkillPayload.STREAM_CODEC,
                CastActiveSkillPayload::handle);
        registrar.playToServer(CancelChargePayload.TYPE, CancelChargePayload.STREAM_CODEC,
                CancelChargePayload::handle);
        registrar.playToServer(RequestSkillScreenPayload.TYPE, RequestSkillScreenPayload.STREAM_CODEC,
                RequestSkillScreenPayload::handle);
        registrar.playToServer(KleinActionPayload.TYPE, KleinActionPayload.STREAM_CODEC, KleinActionPayload::handle);
        registrar.playToServer(KleinCraftFillPayload.TYPE, KleinCraftFillPayload.STREAM_CODEC, KleinCraftFillPayload::handle);
        registrar.playToServer(KleinDrawerPayload.TYPE, KleinDrawerPayload.STREAM_CODEC, KleinDrawerPayload::handle);
        registrar.playToServer(KleinOpenPayload.TYPE, KleinOpenPayload.STREAM_CODEC, KleinOpenPayload::handle);
        registrar.playToServer(KleinRenamePayload.TYPE, KleinRenamePayload.STREAM_CODEC, KleinRenamePayload::handle);

        registrar.playToClient(OreSightPayload.TYPE, OreSightPayload.STREAM_CODEC, OreSightPayload::handle);
        registrar.playToClient(CounterGuardEffectPayload.TYPE, CounterGuardEffectPayload.STREAM_CODEC,
                CounterGuardEffectPayload::handle);

        // 服务端发往客户端
        registrar.playToClient(SyncWaypointsPayload.TYPE, SyncWaypointsPayload.STREAM_CODEC, SyncWaypointsPayload::handle);
        registrar.playToClient(PhoenixCinematicStartPayload.TYPE, PhoenixCinematicStartPayload.STREAM_CODEC, PhoenixCinematicStartPayload::handle);
        registrar.playToClient(PersonalSpaceScreenPayload.TYPE, PersonalSpaceScreenPayload.STREAM_CODEC,
                PersonalSpaceScreenPayload::handle);
        registrar.playToClient(PersonalSpaceResultPayload.TYPE, PersonalSpaceResultPayload.STREAM_CODEC,
                PersonalSpaceResultPayload::handle);
        registrar.playToClient(PersonalDimensionSyncPayload.TYPE, PersonalDimensionSyncPayload.STREAM_CODEC,
                PersonalDimensionSyncPayload::handle);
        registrar.playToClient(SyncSkillDataPayload.TYPE, SyncSkillDataPayload.STREAM_CODEC,
                SyncSkillDataPayload::handle);
        registrar.playToClient(OpenSkillScreenPayload.TYPE, OpenSkillScreenPayload.STREAM_CODEC,
                OpenSkillScreenPayload::handle);
        registrar.playToClient(KleinSyncPayload.TYPE, KleinSyncPayload.STREAM_CODEC, KleinSyncPayload::handle);
    }
}
