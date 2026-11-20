package com.durpalla.booking.data.repo

import com.durpalla.booking.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class Service(val apiType: String?, val kind: String) {
    LAUNCH("launch", "transport"),
    BUS("bus", "transport"),
    TRAIN("train", "transport"),
    FLIGHT("flight", "transport"),
    BOAT(null, "boat"), // boat-rental API, not search?type=boat
    HOTEL("hotel", "hotel"),
    RESORT("resort", "hotel"),
    HOMESTAY("homestay", "hotel"),
    TOUR("tour", "tour"),
    ENTERTAINMENT("entertainment", "entertainment"),
}

/** Row from `GET search` / boat-rental / tour / entertainment lists. */
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
                vehicleName = m.str("vehicle_name", "name", "title"),
                routeName = m.str("route_name", "destination"),
                leavingAt = m.str("leaving_at", "leaving_time_formated", "leaving_time", "starts_at", "depart_date"),
                from = stops.firstOrNull().str("name", default = m.str("trip_from", "from")),
                to = stops.lastOrNull().str("name", default = m.str("trip_to", "to")),
                fare = m.d("fare", "min_fare", "price", "lowest_fare", "price_from"),
                seatAvailable = m.i("seat_available") ?: 0, cabinAvailable = m.i("cabin_available") ?: 0,
                totalSeats = m.i("total_seats") ?: 0, isAc = m.b("is_ac"),
                subLine = m.str("vehicle_no", "trip_code", "schedule_code"),
                photo = MediaUrl.resolve(m.s("vehicle_photo") ?: m.s("photo") ?: m.s("cover") ?: m.s("image")),
                raw = m,
            )
        }
    }
}

data class SeatItem(
    val itemId: Int?, val label: String, val fare: Double, val kind: String, val cabinType: String?,
    val cls: String, val status: Int?, val bookingId: String?, val raw: JsonObject,
) {
    val isEmptySlot get() = cabinType == "empty" || (itemId == null && fare == 0.0)
    val isBooked get() = !isEmptySlot && (bookingId != null || listOf("disable", "booked", "sold", "unavailable").any { cls.lowercase().contains(it) })
    val isTempLocked get() = !isEmptySlot && !isBooked && status == 0
}

/** Master CartItem: total_charge, total_vat, expires_at, for_self, passengers, meta. */
data class CartItem(
    val itemId: Int,
    val type: String,
    val price: Double,
    val lockId: String,
    val label: String,
    val tripId: Int,
    val vehicleName: String,
    val routeName: String,
    val expiresAt: String?,
    val meta: JsonObject?,
    val totalCharge: Double = 0.0,
    val totalVat: Double = 0.0,
    val forSelf: Boolean = true,
    val retainHold: Boolean = false,
) {
    val lineTotal: Double get() = price + totalCharge + totalVat

    companion object {
        fun fromServer(m: JsonObject): CartItem? {
            val itemId = m.i("item_id", "id") ?: return null
            val meta = m.o("meta") ?: JsonObject(emptyMap())
            val charge = m.d("total_charge") ?: meta.d("total_charge") ?: 0.0
            val vat = m.d("total_vat") ?: meta.d("total_vat") ?: 0.0
            val forSelfRaw = m.s("for_self") ?: meta.s("for_self")
            val forSelf = forSelfRaw == null || forSelfRaw == "1" || forSelfRaw.equals("true", true)
            return CartItem(
                itemId = itemId,
                type = m.str("type", "item_type", default = "cabin"),
                price = m.d("price", "fare") ?: 0.0,
                lockId = m.str("lock_id", "hold_id"),
                label = m.str("cabin_no", "label", default = meta.str("cabin_no")),
                tripId = m.i("trip_id") ?: meta.i("trip_id") ?: 0,
                vehicleName = m.str("vehicle_name", default = meta.str("vehicle_name")),
                routeName = m.str("route_name", default = meta.str("route_name")),
                expiresAt = m.s("expires_at") ?: meta.s("expires_at"),
                meta = meta,
                totalCharge = charge,
                totalVat = vat,
                forSelf = forSelf,
            )
        }
    }
}

data class Booking(
    val id: Int, val pnr: String, val bookingDate: String, val paymentStatus: String, val status: String,
    val totalPayable: Double, val cancellable: Boolean, val downloadable: Boolean,
    val items: List<JsonObject>, val transactionId: String?, val invoiceUrl: String?,
    val isHotelStay: Boolean, val raw: JsonObject,
) {
    val firstItem get() = items.firstOrNull()
    companion object {
        fun from(m: JsonObject) = Booking(
            id = m.i("id") ?: 0,
            pnr = m.str("pnr"),
            bookingDate = m.str("booking_date_formated", "booking_date"),
            paymentStatus = m.str("payment_status"),
            status = m.str("status"),
            totalPayable = m.d("total_payable", "total_amount") ?: 0.0,
            cancellable = m.b("cancellable"),
            downloadable = m.b("downloadable"),
            items = m.a("items").objects(),
            transactionId = m.s("transaction_id"),
            invoiceUrl = MediaUrl.resolve(m.s("invoice_url")),
            isHotelStay = m.b("is_hotel_stay") || m.b("isHotelStay"),
            raw = m,
        )
    }
}

data class Gateway(val id: Int, val name: String, val icon: String?, val fee: Double? = null)

data class Hotel(
    val id: Int, val name: String, val city: String, val photo: String?, val stars: Int, val rating: Double,
    val pricePerNight: Double?, val currency: String, val amenities: List<String>, val raw: JsonObject,
) {
    companion object {
        fun from(m: JsonObject): Hotel? {
            val id = m.i("hotel_id", "id") ?: return null
            return Hotel(
                id, m.str("name", "hotel_name"), m.str("city", "location"),
                MediaUrl.resolve(m.s("photo") ?: m.s("cover")),
                m.i("stars") ?: 0, m.d("rating") ?: 0.0,
                m.d("price_per_night", "min_price", "price"),
                m.str("currency", default = "BDT"),
                m.a("amenities")?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
                m,
            )
        }
    }
}

data class Offer(val id: Int, val title: String, val subtitle: String, val image: String?, val code: String?)

/** Tour / entertainment / boat listing card from real APIs. */
data class Experience(
    val id: Int,
    val service: Service,
    val title: String,
    val location: String,
    val duration: String,
    val price: Double,
    val rating: Double,
    val photoUrl: String?,
    val photoRes: Int,
    val highlights: List<String>,
    val date: String,
    val raw: JsonObject = JsonObject(emptyMap()),
)
