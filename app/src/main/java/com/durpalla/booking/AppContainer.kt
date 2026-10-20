package com.durpalla.booking

import android.content.Context
import com.durpalla.booking.calling.ApiCallRepository
import com.durpalla.booking.calling.CallRepository
import com.durpalla.booking.calling.FirestoreSignalingRepository
import com.durpalla.booking.calling.MockCallRepository
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.repo.*
import com.durpalla.booking.data.session.SessionStore
import com.durpalla.booking.realtime.PusherManager
import com.durpalla.booking.security.WebViewRecaptchaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI container (master Flutter parity + in-app calling). */
class AppContainer(context: Context) {
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

    /**
     * Driver calling. Mock by default ([BuildConfig.USE_MOCK_CALLS]).
     * TODO(calling): Durpalla/durpalla-driver docs/calling-contract.md
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
        appScope.launch { cart.syncFromServer() }
    }
}
