package com.durpalla.booking.data.repo

import com.durpalla.booking.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Master CartProvider: sync `GET cart` at startup (empty server keeps local),
 * charge/VAT, expiry countdown → cart/unlock, retainHoldsAfterBooking.
 */
class CartStore(
    private val trips: TripRepository,
    private val scope: CoroutineScope,
    private val defaultHoldMinutes: Long = 15,
) {
    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    private val expiryJobs = mutableMapOf<String, Job>()
    private var retainAfterBooking = false

    fun set(list: List<CartItem>) {
        _items.value = list
        rescheduleAll()
    }

    fun add(item: CartItem) {
        _items.value = _items.value + item
        scheduleExpiry(item)
    }

    fun remove(item: CartItem) {
        _items.value = _items.value - item
        expiryJobs.remove(item.lockId)?.cancel()
    }

    fun clearLocal() {
        expiryJobs.values.forEach { it.cancel() }
        expiryJobs.clear()
        _items.value = emptyList()
    }

    fun retainHoldsAfterBooking() {
        retainAfterBooking = true
        expiryJobs.values.forEach { it.cancel() }
        expiryJobs.clear()
        _items.value = _items.value.map { it.copy(retainHold = true) }
    }

    suspend fun syncFromServer() {
        val r = runCatching { trips.cart() }.getOrNull() ?: return
        val data = r["data"] ?: r["cart"] ?: r["items"]
        val rows = when (data) {
            is JsonArray -> data.objects()
            is JsonObject -> (data.a("items") ?: data.a("data") ?: data.a("cart")).objects()
            else -> emptyList()
        }
        if (rows.isEmpty()) return // master: empty server keeps local
        val parsed = rows.mapNotNull(CartItem::fromServer)
        if (parsed.isEmpty()) return
        // GET /cart omits client trip meta (boarding, book_token). Merge from local.
        val prevByLock = _items.value.associateBy { it.lockId }
        val merged = parsed.map { item ->
            val prev = prevByLock[item.lockId] ?: return@map item
            val prevMeta = prev.meta
            val serverMeta = item.meta
            val meta = buildJsonObject {
                prevMeta?.forEach { (k, v) -> put(k, v) }
                serverMeta?.forEach { (k, v) -> put(k, v) }
                listOf(
                    "boarding_point_id",
                    "boarding_point_name",
                    "boarding_point_label",
                    "boarding_points",
                    "book_token",
                    "booking_id",
                    "stoppages",
                    "supplier",
                    "trip_id",
                ).forEach { key ->
                    val keep = serverMeta?.get(key) ?: prevMeta?.get(key)
                    if (keep != null) put(key, keep)
                }
            }
            item.copy(meta = meta, forSelf = prev.forSelf, tripId = item.tripId.ifBlank { prev.tripId })
        }
        set(merged)
    }

    private fun rescheduleAll() {
        expiryJobs.values.forEach { it.cancel() }
        expiryJobs.clear()
        if (retainAfterBooking) return
        _items.value.forEach { scheduleExpiry(it) }
    }

    private fun scheduleExpiry(item: CartItem) {
        if (retainAfterBooking || item.retainHold) return
        val expires = parseExpires(item.expiresAt)
            ?: Instant.now().plus(defaultHoldMinutes, ChronoUnit.MINUTES)
        val delayMs = (expires.toEpochMilli() - Instant.now().toEpochMilli()).coerceAtLeast(0)
        expiryJobs[item.lockId]?.cancel()
        expiryJobs[item.lockId] = scope.launch {
            delay(delayMs)
            expireAndRelease(item)
        }
    }

    suspend fun expireAndRelease(item: CartItem) {
        if (retainAfterBooking || item.retainHold) return
        runCatching { trips.releaseLock(item) }
        _items.value = _items.value.filterNot { it.lockId == item.lockId }
        expiryJobs.remove(item.lockId)
    }

    private fun parseExpires(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { Instant.parse(raw.replace(" ", "T") + "Z") }.getOrNull()
    }

    fun amountWithCharges(): Double = _items.value.sumOf { it.lineTotal }
    fun subtotal(): Double = _items.value.sumOf { it.price }
    fun totalCharge(): Double = _items.value.sumOf { it.totalCharge }
    fun totalVat(): Double = _items.value.sumOf { it.totalVat }
}
