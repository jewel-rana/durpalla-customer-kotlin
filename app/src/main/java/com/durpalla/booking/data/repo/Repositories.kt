package com.durpalla.booking.data.repo

import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.net.requireSuccess
import com.durpalla.booking.data.session.SessionStore
import kotlinx.serialization.json.*
import java.util.UUID

class HomeRepository(private val api: ApiClient) {
    /** `GET public/app-config?platform=android` (master). */
    suspend fun appConfig(): JsonObject =
        api.get("public/app-config", mapOf("platform" to "android")).o("data") ?: JsonObject(emptyMap())

    suspend fun popularTrips(): List<TripSummary> =
        api.get("public/popular-upcoming-trips").a("data").objects()
            .mapNotNull { TripSummary.from(it, it.str("service_type", "type", default = "launch")) }

    suspend fun topHotels(): List<Hotel> =
        api.getJson("hotel/home/top", mapOf("limit" to 10)).a("data").objects().mapNotNull(Hotel::from)

    suspend fun siteInit(): JsonObject =
        runCatching { api.getJson("site/init").o("data") ?: JsonObject(emptyMap()) }.getOrDefault(JsonObject(emptyMap()))

    suspend fun faq(): List<Pair<String, String>> = runCatching {
        val r = api.getJson("faq")
        val arr = r.a("data") ?: r.o("data").a("faqs") ?: r.o("data").a("items") ?: r.a("faqs")
        arr.objects().mapNotNull { m ->
            val q = m.str("question", "title", "q")
            val a = m.str("answer", "body", "content", "a")
            if (q.isNotBlank() && a.isNotBlank()) q to a else null
        }
    }.getOrDefault(emptyList())

    suspend fun cmsPage(slug: String): Pair<String, String>? = runCatching {
        val normalized = when (slug) {
            "return-and-refund-policy", "cancellation-policy", "refund-policy" -> "cancellation-and-refund-policy"
            "terms" -> "terms-and-conditions"
            "privacy" -> "privacy-policy"
            else -> slug
        }
        val r = api.getJson("page/$normalized")
        val d = r.o("data") ?: r.o("page") ?: r
        val title = d.str("title", "name")
        val rawHtml = d.str("content", "body", "description", "html")
        val clean = rawHtml
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|li|h[1-6])>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
        if (clean.isNotBlank()) title to clean else null
    }.getOrNull()

    suspend fun offers(): List<Offer> = api.getJson("offers").a("data").objects().map {
        Offer(
            it.i("id") ?: 0,
            it.str("title", "name"),
            it.str("subtitle", "description", "details"),
            MediaUrl.resolve(it.s("image") ?: it.s("banner") ?: it.s("photo")),
            it.s("code") ?: it.s("coupon_code"),
        )
    }
}

class TripRepository(private val api: ApiClient) {
    /**
     * Transport search: `GET search?type=launch|bus|train|flight`.
     * Boat uses [BoatRentalRepository.search], not this.
     */
    suspend fun search(
        type: String,
        from: String,
        to: String,
        date: String,
        fromLocationId: Int? = null,
        toLocationId: Int? = null,
    ): List<TripSummary> {
        require(type != "boat") { "Boat search uses boat-rental/search" }
        val query = buildMap<String, Any?> {
            put("type", type)
            put("trip_from", from)
            put("trip_to", to)
            put("trip_date", date)
            if (fromLocationId != null && fromLocationId > 0) put("from_location_id", fromLocationId)
            if (toLocationId != null && toLocationId > 0) put("to_location_id", toLocationId)
        }
        val json = api.getJson("search", query)
        if (!json.success) throw ApiException(json.message ?: AppCtx.str(R.string.err_search_failed))
        val data = json["data"]
        val list = when (data) {
            is JsonArray -> data.objects()
            is JsonObject -> (data.a("trips") ?: data.a("results") ?: data.a("items") ?: data.a("list") ?: data.a("rows") ?: data.a("data")).objects()
            else -> emptyList()
        }
        return list.mapNotNull { TripSummary.from(it, type) }
    }

    suspend fun ghatPlaceSuggestions(q: String = "", exclude: String? = null, type: String? = null): List<GhatPlace> {
        val list = runCatching {
            val res = api.getJson(
                "trip/ghat-suggestions",
                mapOf(
                    "query" to q.trim().takeIf { it.isNotEmpty() },
                    "exclude" to exclude?.takeIf { it.isNotBlank() },
                    "type" to type,
                ),
            )
            val arr = res.a("data") ?: res.o("data").a("items") ?: res.o("data").a("ghats")
            arr?.mapNotNull { e ->
                when (e) {
                    is JsonObject -> {
                        val id = e.i("id", "location_id", "ghat_id") ?: return@mapNotNull null
                        val name = listOf("name", "city", "title", "ghat_name", "place_name", "label", "trip_from")
                            .firstNotNullOfOrNull { e.s(it) }?.trim().orEmpty()
                        if (id > 0 && name.isNotEmpty()) GhatPlace(id, name) else null
                    }
                    else -> null
                }
            }.orEmpty()
        }.getOrDefault(emptyList()).filter { !it.name.equals(exclude, true) }
        if (list.isNotEmpty()) return list
        return GhatFallback.placeMatches(q, exclude)
    }

    suspend fun ghatSuggestions(q: String, exclude: String? = null, type: String? = null): List<String> =
        ghatPlaceSuggestions(q, exclude, type).map { it.name }

    suspend fun citySuggestions(q: String = "", type: String? = "hotel"): List<String> {
        val list = runCatching {
            val res = api.getJson(
                "trip/ghat-suggestions",
                mapOf(
                    "query" to q.trim().takeIf { it.isNotEmpty() },
                    "type" to type,
                ),
            )
            val arr = res.a("data") ?: res.o("data").a("items") ?: res.o("data").a("ghats")
            parseSuggestions(arr).distinct()
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) return list

        val fallback = if (q.isBlank()) emptyList()
        else runCatching { parseSuggestions(api.getJson("city/suggestion", mapOf("q" to q)).a("data")).distinct() }.getOrDefault(emptyList())
        if (fallback.isNotEmpty()) return fallback
        return if (type == "hotel") listOf("Dhaka", "Chittagong", "Barisal") else GhatFallback.matches(q, null)
    }

    private fun parseSuggestions(arr: JsonArray?): List<String> = arr?.mapNotNull { e ->
        when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> listOf("name", "city", "title", "ghat_name", "place_name", "label", "trip_from").firstNotNullOfOrNull { e.s(it) }
            else -> null
        }
    }?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** Paribahan trips need the search row's `book_token` to load seats. */
    suspend fun tripDetails(tripId: String, bookToken: String? = null): JsonObject {
        val token = bookToken?.takeIf { it.isNotBlank() }
        val d = api.get("trip/$tripId", mapOf("book_token" to token))
            .requireSuccess(AppCtx.str(R.string.err_load_trip)).o("data") ?: JsonObject(emptyMap())
        if (token == null || d.s("book_token") != null) return d
        return JsonObject(d + ("book_token" to JsonPrimitive(token)))
    }

    suspend fun lockItem(
        tripId: String,
        type: String,
        itemId: Int,
        socketId: String?,
        supplier: SupplierLock? = null,
    ): JsonObject {
        val key = UUID.randomUUID().toString()
        val headers = buildMap {
            put("X-Idempotency-Key", key)
            if (supplier != null) put("Idempotency-Key", key)
            if (!socketId.isNullOrEmpty()) put("X-Socket-Id", socketId)
        }
        val r = if (supplier != null) {
            val body = jsonOf(
                "trip_id" to tripId,
                "item_id" to itemId,
                "type" to type,
                "platform" to "android",
                "book_token" to supplier.bookToken,
                "seat_ids" to supplier.seatIds,
                "seat_lbls" to supplier.seatLabels,
                "ticket_prices" to supplier.ticketPrices,
                "passenger_name" to supplier.passengerName,
                "passenger_mobile" to supplier.passengerMobile,
                "boarding_point_id" to supplier.boardingPointId,
                "boarding_point_name" to supplier.boardingPointName,
            )
            api.postJson("cart/add", JsonObject(body.filterValues { it !is JsonNull }), headers)
        } else {
            api.postForm("cart/add", mapOf("trip_id" to tripId, "item_id" to "$itemId", "type" to type), headers)
        }
        if (r.success) return r
        val code = r.i("http_status") ?: 0
        throw ApiException(
            (r.message ?: if (code in listOf(409, 410, 423)) AppCtx.str(R.string.err_item_unavailable) else AppCtx.str(R.string.err_lock_item)) + " ($code)",
            code,
        )
    }

    /** Supplier holds need `supplier` + `booking_id` so the partner booking is cancelled too. */
    suspend fun releaseLock(item: CartItem): Boolean =
        releaseLock(item.lockId, item.itemId, item.meta?.s("supplier"), item.meta?.s("booking_id"))

    suspend fun releaseLock(lockId: String, itemId: Int?, supplier: String? = null, bookingId: String? = null): Boolean {
        val body = jsonOf(
            "lock_id" to lockId,
            "item_id" to itemId?.toString(),
            "supplier" to supplier,
            "booking_id" to bookingId,
        )
        val m = api.postJson("cart/unlock", JsonObject(body.filterValues { it !is JsonNull }))
        if (m.success) return true
        val code = m.s("code") ?: m.s("status")
        val msg = m.message.orEmpty().lowercase()
        return code == "404" || code == "409" || listOf("already", "expired", "not found").any { msg.contains(it) }
    }

    suspend fun cart(): JsonObject = api.get("cart")
}

class BookingRepository(private val api: ApiClient) {
    /**
     * amount = subtotal + Σtotal_charge + Σtotal_vat; for_self as 0/1 (master checkout).
     * Local inventory → booking/confirm; Paribahan supplier holds → order/confirm.
     */
    suspend fun confirm(
        items: List<CartItem>,
        passengerName: String?,
        passengerMobile: String?,
        forSelf: Boolean = passengerName.isNullOrBlank(),
        boarding: BoardingOption? = null,
    ): Pair<Int, JsonObject> {
        val amount = items.sumOf { it.lineTotal }
        val isSupplier = items.any {
            val supplier = it.meta?.s("supplier").orEmpty()
            supplier == "paribahan" || !it.meta?.s("book_token").isNullOrBlank()
        }
        val payload = jsonOf(
            "items" to items.map {
                val bookingId = it.meta?.i("booking_id")
                    ?: it.lockId.toIntOrNull()
                buildMap<String, Any?> {
                    put("item_id", it.itemId)
                    put("type", it.type)
                    put("price", it.price)
                    put("lock_id", it.lockId)
                    put("meta", it.meta)
                    put("for_self", if (forSelf) 1 else 0)
                    put(
                        "passengers",
                        listOf(
                            mapOf(
                                "name" to passengerName.orEmpty(),
                                "mobile" to passengerMobile.orEmpty(),
                            ),
                        ),
                    )
                    if (bookingId != null && bookingId > 0) put("booking_id", bookingId)
                    it.meta?.s("supplier")?.let { s -> put("supplier", s) }
                    if (boarding != null) {
                        put("boardingPoint", mapOf("id" to boarding.id, "name" to boarding.name))
                    }
                }
            },
            "amount" to amount,
            "platform" to "android",
            "channel" to "android",
            "service_type" to "transport",
            "customer_name" to passengerName,
            "customer_mobile" to passengerMobile,
        )
        val path = if (isSupplier) "order/confirm" else "booking/confirm"
        val r = api.postJson(path, payload).requireSuccess(AppCtx.str(R.string.err_booking_failed))
        val bm = r.o("booking") ?: r.o("data")
        val id = bm.i("id") ?: r.i("booking_id", "order_id", "id")
            ?: throw ApiException(AppCtx.str(R.string.err_booking_id_missing))
        return id to r
    }

    /** Prefer `GET my/bookings/android?page=` then fall back to `my/bookings` (master). */
    suspend fun myBookings(page: Int = 1): Pair<List<Booking>, Int> {
        val primary = runCatching { api.get("my/bookings/android", mapOf("page" to page)) }.getOrNull()
        val code = primary?.i("http_status") ?: 0
        if (primary != null && (primary.success || code in 200..299) && code != 404) {
            return parseBookingsPage(primary)
        }
        val fallback = api.get("my/bookings", mapOf("page" to page)).requireSuccess(AppCtx.str(R.string.err_load_bookings))
        return parseBookingsPage(fallback)
    }

    private fun parseBookingsPage(r: JsonObject): Pair<List<Booking>, Int> {
        val b = r.o("bookings") ?: r.o("data")
        return b.a("data").objects().map(Booking::from) to (b.i("last_page") ?: 1)
    }

    suspend fun bookingDetails(id: Int): JsonObject =
        api.get("my/booking/android/$id").requireSuccess(AppCtx.str(R.string.err_load_booking))

    suspend fun cancelItem(itemId: Int): JsonObject =
        api.postJson("booking/item/$itemId/cancel", JsonObject(emptyMap())).requireSuccess(AppCtx.str(R.string.err_cancel_failed))
}

class PaymentRepository(private val api: ApiClient) {
    suspend fun gateways(bookingId: Int): List<Gateway> {
        val r = api.get("gateway", mapOf("booking_id" to bookingId)).requireSuccess(AppCtx.str(R.string.err_load_gateways))
        val raw = r["gateways"] ?: r["data"] ?: r
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> raw.a("gateways") ?: raw.a("data")
            else -> null
        }
        return arr.objects().mapNotNull { g ->
            val id = g.i("id") ?: return@mapNotNull null
            val name = g.str("name").ifEmpty { return@mapNotNull null }
            Gateway(id, name, MediaUrl.resolve(g.s("icon")), g.o("meta").d("fee") ?: g.d("fee"))
        }
    }

    suspend fun make(bookingId: Int, gatewayId: Int, amount: Double): Pair<String, Int> {
        val r = api.postJson(
            "payment/make",
            jsonOf("order_id" to bookingId, "booking_id" to bookingId, "gateway_id" to gatewayId, "amount" to amount),
        ).requireSuccess(AppCtx.str(R.string.err_payment_init))
        val data = r.o("data")
        val url = listOf(
            "paymentURL", "paymentUrl", "payment_url", "redirectURL", "redirectUrl",
            "redirect_url", "checkout_url", "url", "bkashURL", "bkashUrl",
        ).firstNotNullOfOrNull { r.s(it) ?: data.s(it) } ?: throw ApiException(AppCtx.str(R.string.err_no_payment_url))
        return url to (data.i("booking_id") ?: bookingId)
    }

    suspend fun verify(bookingId: Int): JsonObject =
        api.get("payment/verify", mapOf("booking_id" to bookingId))
}

class HotelRepository(private val api: ApiClient) {
    suspend fun search(
        city: String,
        checkIn: String,
        checkOut: String,
        adults: Int = 2,
        kind: String = "hotel",
    ): List<Hotel> {
        val r = api.getJson(
            "hotel/search",
            mapOf("city" to city, "check_in" to checkIn, "check_out" to checkOut, "adults" to adults, "type" to kind),
        )
        val d = r["data"]
        val list = if (d is JsonArray) d.objects()
        else (d.obj().a("hotels") ?: d.obj().a("data") ?: d.obj().a("items")).objects()
        return list.mapNotNull(Hotel::from)
    }

    suspend fun details(id: Int): JsonObject = api.getJson("hotel/$id").o("data") ?: JsonObject(emptyMap())

    suspend fun rooms(id: Int, checkIn: String, checkOut: String, adults: Int = 2): List<JsonObject> =
        api.getJson("hotel/$id/rooms", mapOf("check_in" to checkIn, "check_out" to checkOut, "adults" to adults))
            .requireSuccess().a("data").objects()

    suspend fun quote(body: JsonObject) = api.postJson("hotel/quote", body).requireSuccess(AppCtx.str(R.string.err_quote_failed))

    /**
     * Master hold body: `{lines:[{room_type_id, quantity}], check_in, check_out, adults, children}`
     * Idempotency-Key `hc{hotelId}r{rooms}t{µs}` ≤ 64 chars.
     */
    suspend fun hold(
        hotelId: Int,
        lines: List<Pair<Int, Int>>,
        checkIn: String,
        checkOut: String,
        adults: Int,
        children: Int = 0,
    ): JsonObject {
        val rooms = lines.sumOf { it.second }
        val key = "hc${hotelId}r${rooms}t${System.currentTimeMillis() * 1000}".take(64)
        val primaryRt = lines.firstOrNull { it.first > 0 }?.first ?: 0
        val body = jsonOf(
            "room_type_id" to primaryRt,
            "lines" to lines.map { (rt, qty) -> mapOf("room_type_id" to rt, "quantity" to qty) },
            "check_in" to checkIn,
            "check_out" to checkOut,
            "adults" to adults,
            "children" to children,
        )
        return api.postJson("hotel/hold", body, mapOf("Idempotency-Key" to key)).requireSuccess(AppCtx.str(R.string.err_hold_failed))
    }

    /** Prefer `POST hotel/hold/release {hold_id}`, fall back to DELETE. */
    suspend fun releaseHold(holdId: Int): JsonObject {
        val post = runCatching {
            api.postJson("hotel/hold/release", jsonOf("hold_id" to holdId))
        }.getOrNull()
        if (post != null && (post.success || (post.i("http_status") ?: 500) in 200..299)) return post
        return api.delete("hotel/hold/$holdId")
    }

    suspend fun confirm(holdId: Int) =
        api.postJson("hotel/booking/confirm", jsonOf("hold_id" to holdId)).requireSuccess(AppCtx.str(R.string.err_confirm_failed))

    suspend fun review(id: Int, rating: Double, text: String) =
        api.postJson("hotel/$id/reviews", jsonOf("rating" to rating, "text" to text))

    suspend fun favourites(): List<Hotel> =
        api.getJson("my/favourite/hotels").a("data").objects().mapNotNull { (it.o("hotel") ?: it).let(Hotel::from) }

    suspend fun addFavourite(id: Int) = api.postJson("my/favourite/hotels", jsonOf("hotel_id" to id))
    suspend fun removeFavourite(id: Int) = api.delete("my/favourite/hotels/$id")
}

class ProfileRepository(private val api: ApiClient, private val session: SessionStore) {
    suspend fun profile(): JsonObject {
        val r = api.get("my/profile").requireSuccess()
        val u = r.o("data") ?: r.o("user") ?: r
        session.saveUser(u)
        return u
    }

    suspend fun update(
        name: String,
        email: String,
        phone: String?,
        avatarPart: okhttp3.MultipartBody.Part? = null,
    ): JsonObject {
        val fields = buildMap {
            put("name", name)
            put("email", email)
            phone?.let {
                put("mobile", it)
                put("phone", it)
            }
        }
        var r = api.putMultipart(
            "my/update-profile",
            fields,
            avatarPart,
            method = "POST",
        )
        if (!r.success && avatarPart != null) {
            val up = api.putMultipart(
                "my/profile/upload/procedural",
                emptyMap(),
                avatarPart,
                method = "POST",
            )
            if (up.success) r = up
        }
        r.requireSuccess(AppCtx.str(R.string.err_update_failed))
        val current = session.user.value?.toMutableMap() ?: mutableMapOf()
        fields.forEach { (k, v) -> current[k] = JsonPrimitive(v) }
        val updated = r.o("data") ?: r.o("user")
        updated?.forEach { (k, v) -> current[k] = v }
        val avatar = listOf("avatar", "photo", "avatar_url")
            .firstNotNullOfOrNull { r.s(it) ?: updated.s(it) }
        if (!avatar.isNullOrBlank()) {
            current["avatar"] = JsonPrimitive(avatar)
            current["photo"] = JsonPrimitive(avatar)
            current["avatar_url"] = JsonPrimitive(avatar)
        }
        session.saveUser(JsonObject(current))
        return r
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String): JsonObject =
        api.postJson(
            "my/password/change",
            jsonOf(
                "current_password" to currentPassword,
                "password" to newPassword,
                "confirm_password" to confirmPassword,
                "password_confirmation" to confirmPassword,
            ),
        ).requireSuccess(AppCtx.str(R.string.err_update_failed))
}

/** Master ghat fallback (expanded). */
object GhatFallback {
    private val ghats = listOf(
        "Dhaka (Sadar Ghat)", "Barishal", "Bhola", "Patuakhali", "Chandpur", "Hatiya", "Madaripur",
        "Jhalokati", "Pirojpur", "Barguna", "Chattogram", "Cox's Bazar", "Sylhet", "Khulna",
        "Rajshahi", "Mawa", "Teknaf", "Saint Martin", "Aricha", "Daulatdia", "Paturia", "Shimulia",
        "Kawkhali", "Nalchity", "Laharhat", "Beduria", "Ilisha", "Monpura", "Char Fasson",
        "Kalapara", "Kuakata", "Morrelganj", "Mongla", "Bagerhat", "Satkhira", "Barguna Launch Ghat",
        "Mirersarai", "Sandwip", "Companiganj", "Noakhali", "Lakshmipur", "Rangamati", "Kaptai",
        "Bandarban", "Khagrachari", "Sunamganj", "Netrokona", "Kishoreganj", "Bhairab", "Narayanganj",
        "Munshiganj", "Gazipur",
    )
    fun matches(q: String, exclude: String?) =
        ghats.filter { (q.isBlank() || it.contains(q.trim(), true)) && !it.equals(exclude, true) }

    fun placeMatches(q: String, exclude: String?): List<GhatPlace> =
        ghats.mapIndexedNotNull { idx, name ->
            if ((q.isBlank() || name.contains(q.trim(), true)) && !name.equals(exclude, true)) {
                GhatPlace(idx + 1, name)
            } else null
        }
}
