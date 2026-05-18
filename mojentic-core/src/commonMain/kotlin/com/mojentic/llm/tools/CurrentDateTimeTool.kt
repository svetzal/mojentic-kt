@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.mojentic.llm.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

private val DESCRIPTOR = ToolDescriptor(
    name = "get_current_datetime",
    description =
    "Get the current date and time in the system time zone. Useful when " +
        "the assistant needs to know the wall-clock now.",
    parameters = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", JsonObject(emptyMap()))
        put("required", kotlinx.serialization.json.JsonArray(emptyList()))
    },
)

/**
 * Returns the current date / time in the host time zone.
 *
 * Output is a small JSON object: `{ "iso_datetime": "...", "epoch_millis": N,
 * "timezone": "..." }`.
 *
 * @param clock Clock source. Override in tests for determinism.
 * @param timeZone Time zone for the formatted output. Defaults to the system TZ.
 */
public class CurrentDateTimeTool(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : LlmTool {
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override suspend fun execute(arguments: JsonObject): String {
        val now = clock.now()
        val local = now.toLocalDateTime(timeZone)
        return buildJsonObject {
            put("iso_datetime", JsonPrimitive(local.toString()))
            put("epoch_millis", JsonPrimitive(now.toEpochMilliseconds()))
            put("timezone", JsonPrimitive(timeZone.id))
        }.toString()
    }
}
