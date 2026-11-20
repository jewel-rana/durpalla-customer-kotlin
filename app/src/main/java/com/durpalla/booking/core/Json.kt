package com.durpalla.booking.core

import kotlinx.serialization.json.*

/** Loose JSON helpers that mirror the Flutter app's `Map<String, dynamic>` handling. */
val LooseJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

typealias JObj = JsonObject

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonObject?.o(key: String): JsonObject? = this?.get(key) as? JsonObject
fun JsonObject?.a(key: String): JsonArray? = this?.get(key) as? JsonArray

/** String value of a key (numbers/bools stringified, null/objects -> null). */
fun JsonObject?.s(key: String): String? {
    val e = this?.get(key) ?: return null
    if (e is JsonNull) return null
    val p = e as? JsonPrimitive ?: return null
    return p.contentOrNull?.takeIf { it.isNotBlank() }
}
fun JsonObject?.str(vararg keys: String, default: String = ""): String {
    for (k in keys) s(k)?.let { return it }
    return default
}
fun JsonObject?.i(vararg keys: String): Int? {
    for (k in keys) { val v = s(k) ?: continue; v.toIntOrNull()?.let { return it }; v.toDoubleOrNull()?.let { return it.toInt() } }
    return null
}
fun JsonObject?.d(vararg keys: String): Double? {
    for (k in keys) s(k)?.toDoubleOrNull()?.let { return it }
    return null
}
fun JsonObject?.b(key: String): Boolean {
    val v = s(key) ?: return false
    return v == "true" || v == "1"
}
fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject } ?: emptyList()

/** `success` flag as Flutter computes it: explicit success or status==true. */
val JsonObject.success: Boolean get() = s("success")?.let { it == "true" } ?: (s("status") == "true")
val JsonObject.message: String? get() = s("message")

fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) -> put(k, v.toJsonElement()) }
}

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject { this@toJsonElement.forEach { (k, v) -> put(k.toString(), v.toJsonElement()) } }
    is Iterable<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
    else -> JsonPrimitive(toString())
}

class ApiException(message: String, val code: Int = 0, val unauthorized: Boolean = false) : Exception(message)
