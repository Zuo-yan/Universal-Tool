package org.gwfx.universaltool.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.klein.KleinActionPayload;
import org.gwfx.universaltool.klein.KleinAutoDepositPayload;
import org.gwfx.universaltool.klein.KleinCraftFillPayload;
import org.gwfx.universaltool.klein.KleinDrawerPayload;
import org.gwfx.universaltool.klein.KleinOpenPayload;
import org.gwfx.universaltool.klein.KleinRenamePayload;
import org.gwfx.universaltool.klein.KleinSyncPayload;
import org.gwfx.universaltool.naiwa.DidiOrderPayload;
import org.gwfx.universaltool.naiwa.DidiStatusPayload;
import org.gwfx.universaltool.naiwa.NaiwaLaughSoundPayload;
import org.gwfx.universaltool.phainon.PhainonEmptySwingPayload;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;
import org.gwfx.universaltool.phainon.MeteorShowerPayload;
import org.gwfx.universaltool.sportscar.SportsCarInputPayload;
import org.gwfx.universaltool.wheelchair.WheelchairInputPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModPacketHandler {

    private static final Map<CustomPacketPayload.Type<?>, IPayloadHandler<?>> CLIENT_HANDLERS = new ConcurrentHashMap<>();

    public static <T extends CustomPacketPayload> void registerClientHandler(
            CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
        CLIENT_HANDLERS.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> IPayloadHandler<T> dispatchClient(CustomPacketPayload.Type<T> type) {
        return (payload, context) -> {
            IPayloadHandler<T> handler = (IPayloadHandler<T>) CLIENT_HANDLERS.get(type);
            if (handler != null) {
                handler.handle(payload, context);
            }
        };
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UniversalToolMod.MODID).versioned("27");

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
        registrar.playBidirectional(KleinAutoDepositPayload.TYPE, KleinAutoDepositPayload.STREAM_CODEC,
                KleinAutoDepositPayload::handle);
        registrar.playToServer(KleinCraftFillPayload.TYPE, KleinCraftFillPayload.STREAM_CODEC, KleinCraftFillPayload::handle);
        registrar.playToServer(KleinDrawerPayload.TYPE, KleinDrawerPayload.STREAM_CODEC, KleinDrawerPayload::handle);
        registrar.playToServer(KleinOpenPayload.TYPE, KleinOpenPayload.STREAM_CODEC, KleinOpenPayload::handle);
        registrar.playToServer(KleinRenamePayload.TYPE, KleinRenamePayload.STREAM_CODEC, KleinRenamePayload::handle);
        registrar.playToServer(org.gwfx.universaltool.lottery.LotteryRollActionPayload.TYPE,
                org.gwfx.universaltool.lottery.LotteryRollActionPayload.STREAM_CODEC,
                org.gwfx.universaltool.lottery.LotteryRollActionPayload::handle);
        registrar.playToServer(WheelchairInputPayload.TYPE, WheelchairInputPayload.STREAM_CODEC,
                WheelchairInputPayload::handle);
        registrar.playToServer(SportsCarInputPayload.TYPE, SportsCarInputPayload.STREAM_CODEC,
                SportsCarInputPayload::handle);
        registrar.playToServer(DidiOrderPayload.TYPE, DidiOrderPayload.STREAM_CODEC,
                DidiOrderPayload::handle);
        registrar.playToServer(PhainonEmptySwingPayload.TYPE, PhainonEmptySwingPayload.STREAM_CODEC,
                PhainonEmptySwingPayload::handle);

        // 服务端发往客户端 (dispatched to ClientPayloadHandlers registered on client setup)
        registrar.playToClient(OreSightPayload.TYPE, OreSightPayload.STREAM_CODEC,
                dispatchClient(OreSightPayload.TYPE));
        registrar.playToClient(CounterGuardEffectPayload.TYPE, CounterGuardEffectPayload.STREAM_CODEC,
                dispatchClient(CounterGuardEffectPayload.TYPE));
        registrar.playToClient(SyncWaypointsPayload.TYPE, SyncWaypointsPayload.STREAM_CODEC,
                dispatchClient(SyncWaypointsPayload.TYPE));
        registrar.playToClient(PhoenixCinematicStartPayload.TYPE, PhoenixCinematicStartPayload.STREAM_CODEC,
                dispatchClient(PhoenixCinematicStartPayload.TYPE));
        registrar.playToClient(PersonalSpaceScreenPayload.TYPE, PersonalSpaceScreenPayload.STREAM_CODEC,
                dispatchClient(PersonalSpaceScreenPayload.TYPE));
        registrar.playToClient(PersonalSpaceResultPayload.TYPE, PersonalSpaceResultPayload.STREAM_CODEC,
                dispatchClient(PersonalSpaceResultPayload.TYPE));
        registrar.playToClient(PersonalDimensionSyncPayload.TYPE, PersonalDimensionSyncPayload.STREAM_CODEC,
                dispatchClient(PersonalDimensionSyncPayload.TYPE));
        registrar.playToClient(SyncSkillDataPayload.TYPE, SyncSkillDataPayload.STREAM_CODEC,
                dispatchClient(SyncSkillDataPayload.TYPE));
        registrar.playToClient(OpenSkillScreenPayload.TYPE, OpenSkillScreenPayload.STREAM_CODEC,
                dispatchClient(OpenSkillScreenPayload.TYPE));
        registrar.playToClient(SkillNotificationPayload.TYPE, SkillNotificationPayload.STREAM_CODEC,
                dispatchClient(SkillNotificationPayload.TYPE));
        registrar.playToClient(WormholeLiveVistaPayload.TYPE, WormholeLiveVistaPayload.STREAM_CODEC,
                dispatchClient(WormholeLiveVistaPayload.TYPE));
        registrar.playToClient(NaiwaLaughSoundPayload.TYPE, NaiwaLaughSoundPayload.STREAM_CODEC,
                dispatchClient(NaiwaLaughSoundPayload.TYPE));
        registrar.playToClient(DidiStatusPayload.TYPE, DidiStatusPayload.STREAM_CODEC,
                dispatchClient(DidiStatusPayload.TYPE));
        registrar.playToClient(MeteorShowerPayload.TYPE, MeteorShowerPayload.STREAM_CODEC,
                dispatchClient(MeteorShowerPayload.TYPE));
        registrar.playToClient(PhainonSkillEffectPayload.TYPE, PhainonSkillEffectPayload.STREAM_CODEC,
                dispatchClient(PhainonSkillEffectPayload.TYPE));
        registrar.optional().playToClient(SanctuaryDomeEffectPayload.TYPE, SanctuaryDomeEffectPayload.STREAM_CODEC,
                dispatchClient(SanctuaryDomeEffectPayload.TYPE));
        registrar.playToClient(KleinSyncPayload.TYPE, KleinSyncPayload.STREAM_CODEC, KleinSyncPayload::handle);
    }
}
