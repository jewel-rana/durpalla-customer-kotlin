package com.durpalla.booking.data.repo

import com.durpalla.booking.core.*
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.net.requireSuccess
import com.durpalla.booking.data.session.SessionStore
import com.durpalla.booking.security.RecaptchaProvider
import kotlinx.serialization.json.*
import java.util.UUID

/** Port of lib/services/auth_api.dart (uses API_AUTH_BASE_URL == API_BASE_URL, i.e. auth endpoints under the base). */
class AuthRepository(private val api: ApiClient, private val session: SessionStore, private val recaptcha: RecaptchaProvider) {

    private fun tokenOf(r: JsonObject) = r.s("token") ?: r.s("access_token") ?: r.o("data").s("token") ?: r.o("data").s("access_token")
    private fun userOf(r: JsonObject) = r.o("user") ?: r.o("data").o("user") ?: r.o("data").o("customer")

    /** `POST auth/check` → step login | otp | register (same inference as check_mobile_screen.dart). */
    suspend fun checkStep(mobile: String): String {
        val r = api.postForm("auth/check", mapOf("mobile" to mobile, "recaptchaToken" to recaptcha.token("login")))
        if (!r.success && r.s("step") == null && r.o("data").s("step") == null) throw ApiException(r.message ?: "Could not continue")
        val raw = (r.s("step") ?: r.o("data").s("step"))?.lowercase()?.trim()
        if (raw != null) {
            if (raw.startsWith("log")) return "login"
            if (raw == "otp") return "otp"
            if (raw.startsWith("reg") || raw == "signup" || raw == "sign_up") return "register"
        }
        val exists = r.s("exists") ?: r.o("data").s("exists") ?: r.s("found") ?: r.o("data").s("found") ?: r.s("account_exists")
        return if (exists == "true") "login" else "otp"
    }

    suspend fun sendOtp(mobile: String): JsonObject =
        api.postForm("auth/otp/send", mapOf("mobile" to mobile, "recaptchaToken" to recaptcha.token("login")))

    /** `POST auth/verify` {mobile, otp, type, recaptchaToken}. Saves token when returned. */
    suspend fun verifyOtp(mobile: String, otp: String, type: String = "register"): JsonObject {
        val r = api.postForm("auth/verify", mapOf("mobile" to mobile, "otp" to otp, "type" to type, "recaptchaToken" to recaptcha.token("login")))
        val t = tokenOf(r)
        if (r.success && !t.isNullOrEmpty()) session.saveLogin(t, userOf(r))
        return r
    }

    /** `POST auth/login` {mobile, password, device_id, device_platform, fcm_token, recaptchaToken}. */
    suspend fun login(mobile: String, password: String): JsonObject {
        val device = session.deviceId().orEmpty()
        val r = api.postForm("auth/login", mapOf(
            "mobile" to mobile, "password" to password.trim(), "device_id" to device,
            "device_platform" to "android", "fcm_token" to device, "recaptchaToken" to recaptcha.token("login")))
        val t = tokenOf(r)
        if (r.success && !t.isNullOrEmpty()) session.saveLogin(t, userOf(r)) else r.requireSuccess("Login failed")
        return r
    }

    /** `POST auth/register`. */
    suspend fun register(name: String, email: String, mobile: String, password: String, otp: String): JsonObject {
        val device = session.deviceId().orEmpty()
        val r = api.postForm("auth/register", mapOf(
            "name" to name, "email" to email, "mobile" to mobile, "password" to password, "confirm_password" to password,
            "otp_code" to otp, "device_id" to device, "device_platform" to "android", "fcm_token" to device,
            "recaptchaToken" to recaptcha.token("login")))
        r.requireSuccess("Registration failed")
        val t = tokenOf(r)
        if (!t.isNullOrEmpty()) session.saveLogin(t, userOf(r)) else login(mobile, password)
        return r
    }

    suspend fun forgotStart(mobile: String) = api.postForm("auth/forgot", mapOf("mobile" to mobile))
    suspend fun forgotVerify(mobile: String, otp: String) =
        api.postForm("auth/verify", mapOf("mobile" to mobile, "otp_code" to otp, "type" to "forgot", "recaptchaToken" to recaptcha.token("login")))
    suspend fun forgotReset(mobile: String, password: String): JsonObject {
        val r = api.postJson("auth/reset", jsonOf("mobile" to mobile, "password" to password, "confirm_password" to password, "recaptchaToken" to recaptcha.token("login")))
        val t = tokenOf(r); if (r.success && t != null) session.saveLogin(t, userOf(r))
        return r
    }

    /** `POST auth/push/bind` {token, platform} — called after login and on FCM token refresh. */
    suspend fun bindPushToken() {
        val id = session.deviceId() ?: return
        if (!session.isAuthenticated) return
        runCatching { api.postJson("auth/push/bind", jsonOf("token" to id, "platform" to "android")) }
    }

    suspend fun logout() = session.clear()
}

class HomeRepository(private val api: ApiClient) {
    /** `GET public/app-config` → sections toggles. */
    suspend fun appConfig(): JsonObject = api.get("public/app-config").o("data") ?: JsonObject(emptyMap())
    /** `GET public/popular-upcoming-trips`. */
    suspend fun popularTrips(): List<TripSummary> =
        api.get("public/popular-upcoming-trips").a("data").objects().mapNotNull { TripSummary.from(it, it.str("service_type", "type", default = "launch")) }
    /** `GET hotel/home/top`. */
    suspend fun topHotels(): List<Hotel> = api.getJson("hotel/home/top", mapOf("limit" to 10)).a("data").objects().mapNotNull(Hotel::from)
    /** `GET offers`. */
    suspend fun offers(): List<Offer> = api.getJson("offers").a("data").objects().map {
        Offer(it.i("id") ?: 0, it.str("title", "name"), it.str("subtitle", "description", "details"), it.s("image") ?: it.s("banner") ?: it.s("photo"), it.s("code") ?: it.s("coupon_code"))
    }
}

class TripRepository(private val api: ApiClient) {
    /** `GET search?type=&trip_from=&trip_to=&trip_date=YYYY-MM-DD` (search/kind_*.dart). */
    suspend fun search(type: String, from: String, to: String, date: String): List<TripSummary> {
        val json = api.getJson("search", mapOf("type" to type, "trip_from" to from, "trip_to" to to, "trip_date" to date))
        if (!json.success) throw ApiException(json.message ?: "Search could not be completed.")
        val data = json["data"]
        val list = when (data) {
            is JsonArray -> data.objects()
            is JsonObject -> (data.a("trips") ?: data.a("results") ?: data.a("items") ?: data.a("list") ?: data.a("rows") ?: data.a("data")).objects()
            else -> emptyList()
        }
        return list.mapNotNull { TripSummary.from(it, type) }
    }

    /** `GET trip/ghat-suggestions?query=&exclude=&type=` with local fallback list. */
    suspend fun ghatSuggestions(q: String, exclude: String? = null, type: String? = null): List<String> {
        if (q.isBlank()) return emptyList()
        val list = runCatching {
            api.getJson("trip/ghat-suggestions", mapOf("query" to q.trim(), "exclude" to exclude?.takeIf { it.isNotBlank() }, "type" to type))
                .a("data").let(::parseSuggestions)
        }.getOrDefault(emptyList()).filter { !it.equals(exclude, true) }
        return list.ifEmpty { GhatFallback.matches(q, exclude) }
    }

    /** `GET city/suggestion?q=` (hotel search). */
    suspend fun citySuggestions(q: String): List<String> =
        if (q.isBlank()) emptyList() else runCatching { parseSuggestions(api.getJson("city/suggestion", mapOf("q" to q)).a("data")) }.getOrDefault(emptyList())

    private fun parseSuggestions(arr: JsonArray?): List<String> = arr?.mapNotNull { e ->
        when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> listOf("name", "city", "title", "ghat_name", "place_name", "label", "trip_from").firstNotNullOfOrNull { e.s(it) }
            else -> null
        }
    }?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** `GET trip/{id}`. */
    suspend fun tripDetails(tripId: Int): JsonObject = api.get("trip/$tripId").requireSuccess("Could not load trip").o("data") ?: JsonObject(emptyMap())

    /** `POST cart/add` (form) {trip_id, item_id, type} + X-Idempotency-Key + X-Socket-Id. */
    suspend fun lockItem(tripId: Int, type: String, itemId: Int, socketId: String?): JsonObject {
        val headers = buildMap {
            put("X-Idempotency-Key", UUID.randomUUID().toString())
            if (!socketId.isNullOrEmpty()) put("X-Socket-Id", socketId)
        }
        val r = api.postForm("cart/add", mapOf("trip_id" to "$tripId", "item_id" to "$itemId", "type" to type), headers)
        if (r.success) return r
        val code = r.i("http_status") ?: 0
        throw ApiException((r.message ?: if (code in listOf(409, 410, 423)) "Item unavailable. Please pick another." else "Failed to lock item") + " ($code)", code)
    }

    /** `POST cart/unlock` {lock_id, item_id}. Already-gone locks count as released. */
    suspend fun releaseLock(lockId: String, itemId: Int?): Boolean {
        val m = api.postJson("cart/unlock", jsonOf("lock_id" to lockId, "item_id" to itemId?.toString()))
        if (m.success) return true
        val code = m.s("code") ?: m.s("status"); val msg = m.message.orEmpty().lowercase()
        return code == "404" || code == "409" || listOf("already", "expired", "not found").any { msg.contains(it) }
    }

    /** `GET cart`. */
    suspend fun cart(): JsonObject = api.get("cart")
}

class BookingRepository(private val api: ApiClient) {
    /** `POST booking/confirm` {items:[{item_id,type,price,lock_id,meta,for_self,passengers}], amount}. Returns booking id. */
    suspend fun confirm(items: List<CartItem>, total: Double, passengerName: String?, passengerMobile: String?): Pair<Int, JsonObject> {
        val payload = jsonOf(
            "items" to items.map {
                mapOf("item_id" to it.itemId, "type" to it.type, "price" to it.price, "lock_id" to it.lockId,
                    "meta" to it.meta, "for_self" to if (passengerName.isNullOrBlank()) 1 else 0,
                    "passengers" to if (passengerName.isNullOrBlank()) null else listOf(mapOf("name" to passengerName, "mobile" to passengerMobile)))
            },
            "amount" to total,
        )
        val r = api.postJson("booking/confirm", payload).requireSuccess("Booking failed")
        val bm = r.o("booking") ?: r.o("data")
        val id = bm.i("id") ?: r.i("booking_id", "order_id") ?: throw ApiException("Booking created but ID missing")
        return id to r
    }

    /** `GET my/bookings?page=`. */
    suspend fun myBookings(page: Int = 1): Pair<List<Booking>, Int> {
        val r = api.get("my/bookings", mapOf("page" to page)).requireSuccess("Could not load bookings")
        val b = r.o("bookings") ?: r.o("data")
        return b.a("data").objects().map(Booking::from) to (b.i("last_page") ?: 1)
    }

    /** `GET my/booking/android/{id}`. */
    suspend fun bookingDetails(id: Int): JsonObject = api.get("my/booking/android/$id").requireSuccess("Could not load booking")

    /** `POST booking/item/{itemId}/cancel`. */
    suspend fun cancelItem(itemId: Int): JsonObject = api.postJson("booking/item/$itemId/cancel", JsonObject(emptyMap())).requireSuccess("Cancel failed")
}

class PaymentRepository(private val api: ApiClient) {
    /** `GET gateway?booking_id=`. */
    suspend fun gateways(bookingId: Int): List<Gateway> {
        val r = api.get("gateway", mapOf("booking_id" to bookingId)).requireSuccess("Failed to load gateways")
        val raw = r["gateways"] ?: r["data"]
        return (raw as? JsonArray).objects().mapNotNull { g -> val id = g.i("id") ?: return@mapNotNull null; Gateway(id, g.str("name").ifEmpty { return@mapNotNull null }, g.s("icon")) }
    }

    /** `POST payment/make` {order_id, booking_id, gateway_id, amount} → payment URL (PaymentNavigator.extractUrl). */
    suspend fun make(bookingId: Int, gatewayId: Int, amount: Double): Pair<String, Int> {
        val r = api.postJson("payment/make", jsonOf("order_id" to bookingId, "booking_id" to bookingId, "gateway_id" to gatewayId, "amount" to amount))
            .requireSuccess("Payment init failed")
        val data = r.o("data")
        val url = listOf("paymentURL", "paymentUrl", "payment_url", "redirectURL", "redirectUrl", "redirect_url", "checkout_url", "url", "bkashURL", "bkashUrl")
            .firstNotNullOfOrNull { r.s(it) ?: data.s(it) } ?: throw ApiException("No payment URL from server")
        return url to (data.i("booking_id") ?: bookingId)
    }

    /** `GET payment/verify?booking_id=`. */
    suspend fun verify(bookingId: Int): JsonObject = api.get("payment/verify", mapOf("booking_id" to bookingId))
}

class HotelRepository(private val api: ApiClient) {
    suspend fun search(city: String, checkIn: String, checkOut: String, adults: Int = 2): List<Hotel> {
        val r = api.getJson("hotel/search", mapOf("city" to city, "check_in" to checkIn, "check_out" to checkOut, "adults" to adults))
        val d = r["data"]
        val list = if (d is JsonArray) d.objects() else (d.obj().a("hotels") ?: d.obj().a("data") ?: d.obj().a("items")).objects()
        return list.mapNotNull(Hotel::from)
    }
    suspend fun details(id: Int): JsonObject = api.getJson("hotel/$id").o("data") ?: JsonObject(emptyMap())
    suspend fun rooms(id: Int, checkIn: String, checkOut: String, adults: Int = 2): List<JsonObject> =
        api.getJson("hotel/$id/rooms", mapOf("check_in" to checkIn, "check_out" to checkOut, "adults" to adults)).requireSuccess().a("data").objects()
    suspend fun quote(body: JsonObject) = api.postJson("hotel/quote", body).requireSuccess("Quote failed")
    suspend fun hold(body: JsonObject) = api.postJson("hotel/hold", body, mapOf("Idempotency-Key" to UUID.randomUUID().toString())).requireSuccess("Hold failed")
    suspend fun releaseHold(holdId: Int) = api.delete("hotel/hold/$holdId")
    suspend fun confirm(holdId: Int) = api.postJson("hotel/booking/confirm", jsonOf("hold_id" to holdId)).requireSuccess("Confirm failed")
    suspend fun review(id: Int, rating: Double, text: String) = api.postJson("hotel/$id/reviews", jsonOf("rating" to rating, "text" to text))
    suspend fun favourites(): List<Hotel> = api.getJson("my/favourite/hotels").a("data").objects().mapNotNull { (it.o("hotel") ?: it).let(Hotel::from) }
    suspend fun addFavourite(id: Int) = api.postJson("my/favourite/hotels", jsonOf("hotel_id" to id))
    suspend fun removeFavourite(id: Int) = api.delete("my/favourite/hotels/$id")
}

class ProfileRepository(private val api: ApiClient, private val session: SessionStore) {
    /** `GET my/profile`. */
    suspend fun profile(): JsonObject {
        val r = api.get("my/profile").requireSuccess()
        val u = r.o("data") ?: r.o("user") ?: r
        session.saveUser(u)
        return u
    }
    /** `PUT my/profile` via multipart POST + `_method=PUT` {name, email, phone, avatar}. */
    suspend fun update(name: String, email: String, phone: String?): JsonObject {
        val r = api.putMultipart("my/profile", buildMap { put("name", name); put("email", email); phone?.let { put("phone", it) } }, null).requireSuccess("Update failed")
        (r.o("data") ?: r.o("user"))?.let { session.saveUser(it) }
        return r
    }
}

/** Mirrors lib/data/ghat_fallback_suggestions.dart (subset). */
object GhatFallback {
    private val ghats = listOf("Dhaka (Sadar Ghat)", "Barishal", "Bhola", "Patuakhali", "Chandpur", "Hatiya", "Madaripur", "Jhalokati", "Pirojpur",
        "Barguna", "Chattogram", "Cox's Bazar", "Sylhet", "Khulna", "Rajshahi", "Mawa", "Teknaf", "Saint Martin")
    fun matches(q: String, exclude: String?) = ghats.filter { it.contains(q.trim(), true) && !it.equals(exclude, true) }
}
