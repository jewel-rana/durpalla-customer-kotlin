package com.durpalla.booking

import android.content.Context
import com.durpalla.booking.calling.ApiCallRepository
import com.durpalla.booking.calling.CallNotifications
import com.durpalla.booking.calling.CallRepository
import com.durpalla.booking.calling.CallStatus
import com.durpalla.booking.calling.FirestoreSignalingRepository
import com.durpalla.booking.calling.IncomingCallPayload
import com.durpalla.booking.calling.MockCallRepository
import com.durpalla.booking.core.s
import com.durpalla.booking.core.str
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.repo.*
import com.durpalla.booking.data.session.SessionStore
import com.durpalla.booking.l10n.LocaleHelper
import com.durpalla.booking.l10n.RemoteLocalizationService
import com.durpalla.booking.realtime.PusherManager
import com.durpalla.booking.security.WebViewRecaptchaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual DI container (master Flutter parity + calling + remote l10n). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val session = SessionStore(context, appScope)
    val apiClient = ApiClient(session)
    val recaptcha = WebViewRecaptchaProvider(context)
    val auth = AuthRepository(apiClient, session, recaptcha)
    val home = HomeRepository(apiClient)
    val trips = TripRepository(apiClient)
    val bookings = BookingRepository(apiClient)
    val payments = PaymentRepository(apiClient)
    val hotels = HotelRepository(apiClient)
    val profile = ProfileRepository(apiClient, session)
    val tours = TourRepository(apiClient)
    val entertainment = EntertainmentRepository(apiClient)
    val boats = BoatRentalRepository(apiClient)
    val experiences: ExperienceRepository = ApiExperienceRepository(tours, entertainment)
    val cart = CartStore(trips, appScope)
    val pusher = PusherManager()
    val l10n = RemoteLocalizationService(context.applicationContext, apiClient, appScope)

    /**
     * Driver calling. Mock by default ([BuildConfig.USE_MOCK_CALLS]).
     * Contract: docs/calling-contract.md (Durpalla/durpalla-driver).
     */
    val calls: CallRepository = if (BuildConfig.USE_MOCK_CALLS) {
        MockCallRepository(context.applicationContext, appScope)
    } else {
        ApiCallRepository(
            context.applicationContext,
            apiClient,
            session,
            FirestoreSignalingRepository(),
            appScope,
        )
    }

    init {
        appScope.launch {
            cart.syncFromServer()
            val code = session.language.first()
            LocaleHelper.apply(code)
            l10n.load(code)
        }
        appScope.launch {
            session.language.collect { code ->
                l10n.load(code)
            }
        }
        // Private user channel for secondary call ring / end events (contract §2).
        appScope.launch {
            session.loggedIn.collect { loggedIn ->
                if (!loggedIn) {
                    val uid = session.user.value.str("id", "_id")
                    if (uid.isNotBlank()) pusher.unsubscribeUser(uid)
                    return@collect
                }
                pusher.connect { session.token }
                val uid = session.user.value.str("id", "_id")
                if (uid.isNotBlank()) pusher.subscribeUser(uid)
            }
        }
        appScope.launch {
            session.user.map { it.str("id", "_id") }.distinctUntilChanged().collect { uid ->
                if (uid.isBlank() || !session.loggedIn.value) return@collect
                pusher.connect { session.token }
                pusher.subscribeUser(uid)
            }
        }
        appScope.launch {
            pusher.events.collect { e ->
                if (!e.channel.startsWith("private-user-")) return@collect
                when (e.name) {
                    "call.incoming" -> {
                        val call = IncomingCallPayload.fromPusher(e.data) ?: return@collect
                        calls.handleIncoming(
                            call.callId, call.tripIdInt, call.callerName, call.provider,
                        )
                        CallNotifications.showIncoming(appContext, call.callId, call.tripIdInt, call.callerName)
                    }
                    "call.ended", "call.declined", "call.missed" -> {
                        val callId = e.data.s("callId") ?: return@collect
                        val status = CallStatus.fromWire(
                            e.data.s("status") ?: e.name.removePrefix("call."),
                        ).takeIf { it.isTerminal } ?: CallStatus.ENDED
                        calls.onRemoteEnded(callId, status)
                        CallNotifications.cancelIncoming(appContext)
                    }
                }
            }
        }
    }
}
