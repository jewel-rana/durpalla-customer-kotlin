package com.durpalla.booking.core

import com.durpalla.booking.data.repo.Booking
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Flutter `lib/helpers/booking_cancel_rules.dart` (master).
 * Cancel shows only if not a hotel stay, item/booking cancellable, booking succeeded,
 * and departure is still ahead.
 */
object BookingCancelRules {
    private val successWords = listOf("complete", "completed", "success", "confirmed", "paid", "successful")
    private val failWords = listOf("cancel", "fail", "reject", "refund", "incomplete")

    fun canCancelItem(booking: Booking, item: JsonObject): Boolean {
        if (isHotelStay(booking, item)) return false
        if (!item.b("cancellable") && !booking.cancellable) return false
        if (!itemStatusOk(item)) return false
        if (!bookingSucceeded(booking)) return false
        return departureAhead(item)
    }

    fun isHotelStay(booking: Booking, item: JsonObject = booking.firstItem ?: JsonObject(emptyMap())): Boolean {
        if (booking.raw.b("is_hotel_stay") || booking.raw.b("isHotelStay")) return true
        val t = item.str("item_type", "type", "service_type", "booking_type").lowercase(Locale.US)
        return t.contains("hotel") || t.contains("resort") || t.contains("homestay") ||
            item.s("hotel_name") != null || item.s("room_type_title") != null
    }

    private fun itemStatusOk(item: JsonObject): Boolean {
        val s = item.s("status") ?: return true
        return s == "true" || s == "1" || s.equals("active", true)
    }

    private fun bookingSucceeded(b: Booking): Boolean {
        val blob = listOf(b.paymentStatus, b.status).joinToString(" ").lowercase(Locale.US)
        if (failWords.any { blob.contains(it) }) return false
        return successWords.any { blob.contains(it) }
    }

    private fun departureAhead(item: JsonObject): Boolean {
        val leaving = item.s("leaving_time") ?: item.s("leaving_at")
        if (!leaving.isNullOrBlank()) {
            parseDateTime(leaving)?.let { return it.isAfter(LocalDateTime.now()) }
        }
        val date = item.s("schedule_date") ?: item.s("trip_date")
        val timeFmt = item.s("leaving_time_formatted") ?: item.s("leaving_time_formated")
        if (!date.isNullOrBlank()) {
            val d = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return true
            val t = parseTime(timeFmt) ?: LocalTime.MIDNIGHT
            return LocalDateTime.of(d, t).isAfter(LocalDateTime.now())
        }
        return true
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        val formats = listOf(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        )
        for (f in formats) runCatching { return LocalDateTime.parse(raw.trim(), f) }
        return null
    }

    private fun parseTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        val formats = listOf(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
        )
        for (f in formats) runCatching { return LocalTime.parse(raw.trim(), f) }
        return null
    }
}
