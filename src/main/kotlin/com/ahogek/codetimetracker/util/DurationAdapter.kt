package com.ahogek.codetimetracker.util

import com.google.gson.*
import java.lang.reflect.Type
import java.time.Duration

/**
 * Gson TypeAdapter for Duration serialization and deserialization
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 13:57:59
 */
class DurationAdapter : JsonSerializer<Duration>, JsonDeserializer<Duration> {

    override fun serialize(
        src: Duration?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.toSeconds())
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Duration {
        val seconds = json?.asLong
            ?: throw JsonParseException("Duration cannot be null")
        return Duration.ofSeconds(seconds)
    }
}