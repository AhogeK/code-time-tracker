package com.ahogek.codetimetracker.statistics

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.Duration

/**
 * A custom Gson TypeAdapter for the Duration class.
 * This adapter serializes a Duration object into the total number of seconds,
 * which is a format suitable for ues in JavaScript charting libraries.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-08 00:58:03
 */
class DurationAdapter : TypeAdapter<Duration>() {

    override fun write(out: JsonWriter, value: Duration?) {
        if (value == null) out.nullValue()
        else out.value(value.seconds) // Write the total number of seconds of the duration
    }

    override fun read(`in`: JsonReader?): Duration? {
        // Deserialization is not needed for this ues case
        return null
    }
}