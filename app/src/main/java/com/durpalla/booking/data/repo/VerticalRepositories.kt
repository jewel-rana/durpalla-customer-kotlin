package com.durpalla.booking.data.repo

import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.net.requireSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Master tour APIs. */
class TourRepository(private val api: ApiClient) {
    suspend fun search(destination: String?, departDate: String): List<Experience> {
        val q = buildMap<String, Any?> {
            destination?.takeIf { it.isNotBlank() }?.let { put("destination", it) }
            put("depart_date", departDate)
        }
        val r = api.getJson("tour/search", q)
        return parseExperiences(r, Service.TOUR)
    }

    suspend fun details(id: Int): JsonObject =
        api.getJson("tour/$id").o("data") ?: JsonObject(emptyMap())

    suspend fun quote(body: JsonObject) = api.postJson("tour/quote", body).requireSuccess(AppCtx.str(R.string.err_quote_failed))

    suspend fun hold(departureId: Int, places: Int, guestName: String, guestMobile: String): JsonObject =
        api.postJson(
            "tour/hold",
            jsonOf(
                "departure_id" to departureId,
                "places" to places,
                "guest" to mapOf("name" to guestName, "mobile" to guestMobile),
            ),
            mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
        ).requireSuccess(AppCtx.str(R.string.err_hold_failed))

    suspend fun confirm(holdId: Int, customerName: String, customerMobile: String): JsonObject =
        api.postJson(
            "tour/booking/confirm",
            jsonOf(
                "hold_id" to holdId,
                "platform" to "android",
                "customer_name" to customerName,
                "customer_mobile" to customerMobile,
            ),
        ).requireSuccess(AppCtx.str(R.string.err_confirm_failed))
}

/** Master entertainment APIs. */
class EntertainmentRepository(private val api: ApiClient) {
    suspend fun homeTop(limit: Int = 10): List<Experience> =
        parseExperiences(api.getJson("entertainment/home/top", mapOf("limit" to limit)), Service.ENTERTAINMENT)

    suspend fun search(query: String = "", date: String? = null): List<Experience> {
        val q = buildMap<String, Any?> {
            if (query.isNotBlank()) put("q", query)
            date?.let { put("visit_date", it) }
        }
        return parseExperiences(api.getJson("entertainment/search", q), Service.ENTERTAINMENT)
    }

    suspend fun details(id: Int): JsonObject =
        api.getJson("entertainment/$id").o("data") ?: JsonObject(emptyMap())

    suspend fun reviews(id: Int): List<JsonObject> =
        api.getJson("entertainment/$id/reviews").a("data").objects()

    suspend fun postReview(id: Int, rating: Double, text: String) =
        api.postJson("entertainment/$id/reviews", jsonOf("rating" to rating, "text" to text))

    suspend fun quote(body: JsonObject) = api.postJson("entertainment/quote", body).requireSuccess(AppCtx.str(R.string.err_quote_failed))

    suspend fun hold(
        venueId: Int,
        visitDate: String,
        lines: List<Map<String, Any?>>,
        guestName: String,
        guestMobile: String,
        slotTemplateId: Int? = null,
    ): JsonObject = api.postJson(
        "entertainment/hold",
        jsonOf(
            "venue_id" to venueId,
            "visit_date" to visitDate,
            "slot_template_id" to slotTemplateId,
            "lines" to lines,
            "guest" to mapOf("name" to guestName, "mobile" to guestMobile),
        ),
        mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
    ).requireSuccess(AppCtx.str(R.string.err_hold_failed))

    suspend fun confirm(holdId: Int, guestName: String, guestMobile: String): JsonObject =
        api.postJson(
            "entertainment/booking/confirm",
            jsonOf(
                "hold_id" to holdId,
                "platform" to "mobile",
                "guest" to mapOf("name" to guestName, "mobile" to guestMobile),
                "payment" to mapOf("mode" to "none"),
            ),
        ).requireSuccess(AppCtx.str(R.string.err_confirm_failed))
}

/** Master boat-rental APIs including custom trip + bids. */
class BoatRentalRepository(private val api: ApiClient) {
    suspend fun homeTop(limit: Int = 10): List<Experience> =
        parseExperiences(api.getJson("boat-rental/home/top", mapOf("limit" to limit)), Service.BOAT)

    suspend fun search(q: String?, startsAt: String, endsAt: String): List<Experience> {
        val query = buildMap<String, Any?> {
            q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
            put("starts_at", startsAt)
            put("ends_at", endsAt)
        }
        return parseExperiences(api.getJson("boat-rental/search", query), Service.BOAT)
    }

    suspend fun details(id: Int): JsonObject =
        api.getJson("boat-rental/$id").o("data") ?: JsonObject(emptyMap())

    suspend fun quote(body: JsonObject) = api.postJson("boat-rental/quote", body).requireSuccess(AppCtx.str(R.string.err_quote_failed))

    suspend fun hold(
        boatId: Int,
        rentalMode: String,
        startsAt: String,
        endsAt: String,
        guests: Int,
        guestName: String,
        guestMobile: String,
        packageId: Int? = null,
    ): JsonObject = api.postJson(
        "boat-rental/hold",
        jsonOf(
            "boat_id" to boatId,
            "rental_mode" to rentalMode,
            "starts_at" to startsAt,
            "ends_at" to endsAt,
            "guests" to guests,
            "package_id" to packageId,
            "guest" to mapOf("name" to guestName, "mobile" to guestMobile),
        ),
        mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
    ).requireSuccess(AppCtx.str(R.string.err_hold_failed))

    suspend fun confirm(holdId: Int, customerName: String, customerMobile: String): JsonObject =
        api.postJson(
            "boat-rental/booking/confirm",
            jsonOf(
                "hold_id" to holdId,
                "platform" to "android",
                "customer_name" to customerName,
                "customer_mobile" to customerMobile,
            ),
        ).requireSuccess(AppCtx.str(R.string.err_confirm_failed))

    suspend fun stoppages(): List<JsonObject> =
        api.getJson("boat-rental/stoppages").a("data").objects()

    suspend fun listTripRequests(): List<JsonObject> =
        api.getJson("boat-rental/trip-requests").a("data").objects()

    suspend fun createTripRequest(
        stoppageIds: List<Int>,
        startsAt: String,
        endsAt: String,
        guests: Int,
        notes: String? = null,
    ): JsonObject = api.postJson(
        "boat-rental/trip-requests",
        jsonOf(
            "stoppage_ids" to stoppageIds,
            "starts_at" to startsAt,
            "ends_at" to endsAt,
            "guests" to guests,
            "notes" to notes,
        ),
    ).requireSuccess(AppCtx.str(R.string.err_trip_request_failed))

    suspend fun tripRequest(id: Int): JsonObject =
        api.getJson("boat-rental/trip-requests/$id").o("data") ?: JsonObject(emptyMap())

    suspend fun cancelTripRequest(id: Int) =
        api.postJson("boat-rental/trip-requests/$id/cancel", JsonObject(emptyMap()))

    suspend fun bids(tripRequestId: Int): List<JsonObject> =
        api.getJson("boat-rental/trip-requests/$tripRequestId/bids").a("data").objects()

    suspend fun acceptBid(bidId: Int): JsonObject =
        api.postJson(
            "boat-rental/bids/$bidId/accept",
            JsonObject(emptyMap()),
            mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
        ).requireSuccess(AppCtx.str(R.string.err_accept_bid_failed))
}

/** Unified list/details for tour + entertainment (replaces MockExperienceRepository). */
class ApiExperienceRepository(
    private val tours: TourRepository,
    private val entertainment: EntertainmentRepository,
) : ExperienceRepository {
    override suspend fun list(service: Service, query: String): List<Experience> = when (service) {
        Service.TOUR -> tours.search(query.ifBlank { null }, java.time.LocalDate.now().toString())
        Service.ENTERTAINMENT -> entertainment.search(query)
        else -> emptyList()
    }

    override suspend fun details(id: Int): Experience? {
        val tour = runCatching { tours.details(id) }.getOrNull()
        if (tour != null && !tour.isEmpty()) return experienceFromJson(tour, Service.TOUR)
        val ent = runCatching { entertainment.details(id) }.getOrNull() ?: return null
        return experienceFromJson(ent, Service.ENTERTAINMENT)
    }
}

interface ExperienceRepository {
    suspend fun list(service: Service, query: String = ""): List<Experience>
    suspend fun details(id: Int): Experience?
}

internal fun parseExperiences(r: JsonObject, service: Service): List<Experience> {
    val data = r["data"]
    val list = when (data) {
        is JsonArray -> data.objects()
        is JsonObject -> (data.a("items") ?: data.a("results") ?: data.a("data") ?: data.a("tours") ?: data.a("venues") ?: data.a("boats")).objects()
        else -> emptyList()
    }
    return list.mapNotNull { experienceFromJson(it, service) }
}

internal fun experienceFromJson(m: JsonObject, service: Service): Experience? {
    val id = m.i("id", "tour_id", "venue_id", "boat_id") ?: return null
    val title = m.str("title", "name", "vehicle_name")
    if (title.isBlank()) return null
    val photo = MediaUrl.resolve(m.s("photo") ?: m.s("cover") ?: m.s("image") ?: m.s("thumbnail"))
    val highlights = m.a("highlights")?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?: m.a("amenities")?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?: emptyList()
    val fallbackRes = when (service) {
        Service.ENTERTAINMENT -> R.drawable.photo_concert
        Service.BOAT -> R.drawable.photo_trawler
        else -> R.drawable.photo_sajek
    }
    return Experience(
        id = id,
        service = service,
        title = title,
        location = m.str("location", "city", "destination", "place"),
        duration = m.str("duration", "duration_text", "rental_mode"),
        price = m.d("price", "price_from", "min_price", "fare") ?: 0.0,
        rating = m.d("rating", "stars") ?: 0.0,
        photoUrl = photo,
        photoRes = fallbackRes,
        highlights = highlights,
        date = m.str("date", "depart_date", "visit_date", "starts_at", "schedule"),
        raw = m,
    )
}
