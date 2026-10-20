package com.durpalla.booking.data.repo

import com.durpalla.booking.core.*
import kotlinx.serialization.json.JsonObject

enum class Service(val apiType: String?) { LAUNCH("launch"), BUS("bus"), BOAT("boat"), HOTEL("hotel"), TOUR(null), ENTERTAINMENT(null) }

/** Row from `GET search` (fields used by lib/pages/search_result_page.dart). */
data class TripSummary(
    val tripId: Int, val serviceType: String, val vehicleName: String, val routeName: String,
    val leavingAt: String, val from: String, val to: String, val fare: Double?, val seatAvailable: Int,
    val cabinAvailable: Int, val totalSeats: Int, val isAc: Boolean, val subLine: String, val photo: String?, val raw: JsonObject,
) {
    companion object {
        fun from(m: JsonObject, type: String): TripSummary? {
            val id = m.i("trip_id", "id") ?: return null
            val stops = m.a("stoppages").objects()
            return TripSummary(
                tripId = id, serviceType = m.str("service_type", default = type),
                vehicleName = m.str("vehicle_name"), routeName = m.str("route_name"),
                leavingAt = m.str("leaving_at", "leaving_time_formated", "leaving_time"),
                from = stops.firstOrNull().str("name", default = m.str("trip_from")),
                to = stops.lastOrNull().str("name", default = m.str("trip_to")),
                fare = m.d("fare", "min_fare", "price", "lowest_fare"),
                seatAvailable = m.i("seat_available") ?: 0, cabinAvailable = m.i("cabin_available") ?: 0,
                totalSeats = m.i("total_seats") ?: 0, isAc = m.b("is_ac"),
                subLine = m.str("vehicle_no", "trip_code", "schedule_code"),
                photo = m.s("vehicle_photo"), raw = m,
            )
        }
    }
}

/** One cabin/seat/sofa cell from `GET trip/{id}` → `{cabins|seats|sofas}.{first_floor..}.{row: [items]}`. */
data class SeatItem(
    val itemId: Int?, val label: String, val fare: Double, val kind: String, val cabinType: String?,
    val cls: String, val status: Int?, val bookingId: String?, val raw: JsonObject,
) {
    val isEmptySlot get() = cabinType == "empty" || (itemId == null && fare == 0.0)
    val isBooked get() = !isEmptySlot && (bookingId != null || listOf("disable", "booked", "sold", "unavailable").any { cls.lowercase().contains(it) })
    val isTempLocked get() = !isEmptySlot && !isBooked && status == 0
}

data class CartItem(val itemId: Int, val type: String, val price: Double, val lockId: String, val label: String,
                    val tripId: Int, val vehicleName: String, val routeName: String, val expiresAt: String?, val meta: JsonObject?)

data class Booking(
    val id: Int, val pnr: String, val bookingDate: String, val paymentStatus: String, val status: String,
    val totalPayable: Double, val cancellable: Boolean, val downloadable: Boolean,
    val items: List<JsonObject>, val transactionId: String?, val raw: JsonObject,
) {
    val firstItem get() = items.firstOrNull()
    companion object {
        fun from(m: JsonObject) = Booking(
            id = m.i("id") ?: 0, pnr = m.str("pnr"), bookingDate = m.str("booking_date_formated", "booking_date"),
            paymentStatus = m.str("payment_status"), status = m.str("status"),
            totalPayable = m.d("total_payable", "total_amount") ?: 0.0, cancellable = m.b("cancellable"),
            downloadable = m.b("downloadable"), items = m.a("items").objects(), transactionId = m.s("transaction_id"), raw = m,
        )
    }
}

data class Gateway(val id: Int, val name: String, val icon: String?)

data class Hotel(val id: Int, val name: String, val city: String, val photo: String?, val stars: Int, val rating: Double,
                 val pricePerNight: Double?, val currency: String, val amenities: List<String>, val raw: JsonObject) {
    companion object {
        fun from(m: JsonObject): Hotel? {
            val id = m.i("hotel_id", "id") ?: return null
            return Hotel(id, m.str("name", "hotel_name"), m.str("city", "location"), m.s("photo") ?: m.s("cover"),
                m.i("stars") ?: 0, m.d("rating") ?: 0.0, m.d("price_per_night", "min_price", "price"), m.str("currency", default = "BDT"),
                m.a("amenities")?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList(), m)
        }
    }
}

data class Offer(val id: Int, val title: String, val subtitle: String, val image: String?, val code: String?)

/** Tour / Entertainment item — no backend yet (see MockExperienceRepository). */
data class Experience(val id: Int, val service: Service, val title: String, val location: String, val duration: String,
                      val price: Double, val rating: Double, val photoRes: Int, val highlights: List<String>, val date: String)
