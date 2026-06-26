package dev.phonecode.provider.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** One lenient parser shared by both stream mappers - no per-stream allocation, one consistent config. */
internal val streamJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Null-safe typed accessors over [JsonObject] - every lookup tolerates absent/wrong-typed fields. */
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.longOf(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.intOf(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
