package com.mojentic.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

/**
 * Walks a kotlinx.serialization [SerialDescriptor] and produces a JSON Schema
 * (`draft 2020-12`-ish subset) suitable for handing to LLM provider APIs.
 *
 * Supported descriptors:
 * - Primitives → `type: "string|number|integer|boolean"`
 * - Classes / objects → `type: "object"` with `properties` and `required`
 * - Lists → `type: "array"` with `items`
 * - Maps → `type: "object"` with `additionalProperties`
 * - Enums → `type: "string"` with `enum`
 *
 * Sealed and open polymorphic descriptors fall back to `{}` (any). Phase 1
 * does not need them; revisit when adding richer structured-output use cases.
 */
@OptIn(ExperimentalSerializationApi::class)
public object JsonSchemaGenerator {
    public inline fun <reified T> schemaFor(): JsonObject = schemaFor(serializer<T>().descriptor)

    public fun schemaFor(descriptor: SerialDescriptor): JsonObject = build(descriptor)

    @Suppress("ReturnCount")
    private fun build(descriptor: SerialDescriptor): JsonObject = when (val kind = descriptor.kind) {
        is PrimitiveKind -> primitive(kind)
        SerialKind.ENUM -> enumSchema(descriptor)
        StructureKind.LIST -> listSchema(descriptor)
        StructureKind.MAP -> mapSchema(descriptor)
        StructureKind.CLASS, StructureKind.OBJECT -> objectSchema(descriptor)
        is PolymorphicKind, SerialKind.CONTEXTUAL -> JsonObject(emptyMap())
    }

    private fun primitive(kind: PrimitiveKind): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(primitiveTypeFor(kind)))
    }

    private fun primitiveTypeFor(kind: PrimitiveKind): String = when (kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> "string"
        PrimitiveKind.BOOLEAN -> "boolean"
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG,
        -> "integer"
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> "number"
    }

    private fun enumSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put(
            "enum",
            JsonArray((0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }),
        )
    }

    private fun listSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("items", build(descriptor.getElementDescriptor(0)))
    }

    private fun mapSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", build(descriptor.getElementDescriptor(1)))
    }

    private fun objectSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        val properties = buildJsonObject {
            for (i in 0 until descriptor.elementsCount) {
                put(descriptor.getElementName(i), build(descriptor.getElementDescriptor(i)))
            }
        }
        put("properties", properties)
        val required = buildJsonArray {
            for (i in 0 until descriptor.elementsCount) {
                if (!descriptor.isElementOptional(i)) {
                    add(JsonPrimitive(descriptor.getElementName(i)))
                }
            }
        }
        put("required", required)
    }
}
