package com.durpalla.booking.data.repo

import com.durpalla.booking.core.a
import com.durpalla.booking.core.objects
import com.durpalla.booking.core.s
import com.durpalla.booking.core.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class BoardingOption(
    val id: String,
    val name: String,
    val time: String? = null,
) {
    val label: String
        get() = if (time.isNullOrBlank()) name else "$name · ${formatBoardingTime(time)}"
}

fun formatBoardingTime(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    val match = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?(?:\s*(AM|PM))?$""", RegexOption.IGNORE_CASE)
        .matchEntire(value) ?: return value
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2]
    val meridiem = match.groupValues.getOrNull(3)?.uppercase()
    if (!meridiem.isNullOrBlank()) return "$hour:$minute $meridiem"
    val suffix = if (hour >= 12) "PM" else "AM"
    hour = hour % 12
    if (hour == 0) hour = 12
    return "$hour:$minute $suffix"
}

/** Clock label from "06:30[:00]", "6:30 PM" or ISO datetimes (Paribahan `leaving_at`). */
fun displayClock(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    val clock = formatBoardingTime(value)
    if (clock != value || Regex("""^\d{1,2}:\d{2}\s*(AM|PM)$""", RegexOption.IGNORE_CASE).matches(value)) return clock
    if (!Regex("""[T\-/]""").containsMatchIn(value)) return value
    val time = runCatching { java.time.OffsetDateTime.parse(value).toLocalTime() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(value.replace(" ", "T")).toLocalTime() }.getOrNull()
        ?: return value
    return formatBoardingTime("%02d:%02d".format(time.hour, time.minute))
}

/** Prefer supplier boarding_points; fall back to start/via stoppages. */
fun boardingOptionsFromTrip(d: JsonObject?): List<BoardingOption> {
    if (d == null) return emptyList()
    val fromPoints = boardingOptionsFromPoints(d.a("boarding_points"))
    if (fromPoints.isNotEmpty()) return fromPoints
    return boardingOptionsFromStoppages(d.a("stoppages"))
}

fun boardingOptionsFromPoints(raw: JsonArray?): List<BoardingOption> {
    val out = mutableListOf<BoardingOption>()
    val seen = mutableSetOf<String>()
    for (p in raw.objects()) {
        val id = p.s("id")
            ?: p.s("boarding_point_id")
            ?: p.s("unique_boarding_point_id")
            ?: p.s("reporting_branch_id")
            ?: continue
        if (!seen.add(id)) continue
        val name = p.str(
            "name",
            "unique_boarding_point_name",
            "counter_name",
            "boarding_point_name",
        ).trim()
        if (name.isEmpty()) continue
        val time = p.s("time") ?: p.s("schedule_time") ?: p.s("departure_time")
        out += BoardingOption(id, name, time)
    }
    return out
}

fun boardingOptionsFromStoppages(raw: JsonArray?): List<BoardingOption> {
    val out = mutableListOf<BoardingOption>()
    val seen = mutableSetOf<String>()
    for (s in raw.objects()) {
        val type = s.str("type").lowercase()
        if (type == "end") continue
        val name = s.str("name").trim()
        if (name.isEmpty()) continue
        val id = s.s("id") ?: name
        if (!seen.add(id)) continue
        out += BoardingOption(id, name, s.s("time") ?: s.s("departure_time"))
    }
    return out
}

fun boardingOptionsFromMeta(meta: JsonObject?): List<BoardingOption> {
    if (meta == null) return emptyList()
    val fromPoints = boardingOptionsFromPoints(meta.a("boarding_points"))
    if (fromPoints.isNotEmpty()) return fromPoints
    return boardingOptionsFromStoppages(meta.a("stoppages"))
}

fun findBoardingOption(options: List<BoardingOption>, selectedId: String?): BoardingOption? {
    if (selectedId.isNullOrBlank()) return null
    return options.firstOrNull { it.id == selectedId }
}

/** Paribahan `cart/add` JSON extras (sent only when the trip carries a `book_token`). */
data class SupplierLock(
    val bookToken: String,
    val seatIds: List<String>,
    val seatLabels: List<String>,
    val ticketPrices: List<Double>,
    val passengerName: String?,
    val passengerMobile: String?,
    val boardingPointId: String?,
    val boardingPointName: String?,
)

/** Merge boarding selection into cart/lock meta so checkout can restore it. */
fun JsonObject.withBoarding(
    trip: JsonObject?,
    selected: BoardingOption?,
): JsonObject = buildJsonObject {
    this@withBoarding.forEach { (k, v) -> put(k, v) }
    trip?.a("boarding_points")?.let { put("boarding_points", it) }
    trip?.a("stoppages")?.let { put("stoppages", it) }
    selected?.let {
        put("boarding_point_id", JsonPrimitive(it.id))
        put("boarding_point_name", JsonPrimitive(it.name))
        put("boarding_point_label", JsonPrimitive(it.label))
    }
}
