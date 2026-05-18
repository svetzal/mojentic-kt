package com.mojentic.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data class Person(val name: String, val age: Int, val nickname: String? = null, val hobbies: List<String> = emptyList())

@Serializable
private enum class Mood { HAPPY, SAD }

@Serializable
private data class Tagged(val mood: Mood)

class JsonSchemaGeneratorTest {
    @Test
    fun objectSchemaListsPropertiesAndRequired() {
        val schema = JsonSchemaGenerator.schemaFor<Person>()

        assertEquals("object", (schema["type"] as JsonPrimitive).content)
        val properties = schema["properties"] as JsonObject
        assertTrue(properties.containsKey("name"))
        assertTrue(properties.containsKey("age"))
        assertTrue(properties.containsKey("nickname"))
        assertTrue(properties.containsKey("hobbies"))
        val required = (schema["required"] as JsonArray).map { (it as JsonPrimitive).content }
        assertEquals(setOf("name", "age"), required.toSet())
    }

    @Test
    fun primitivePropertyMapsToJsonType() {
        val schema = JsonSchemaGenerator.schemaFor<Person>()
        val properties = schema["properties"] as JsonObject

        assertEquals("string", ((properties["name"] as JsonObject)["type"] as JsonPrimitive).content)
        assertEquals("integer", ((properties["age"] as JsonObject)["type"] as JsonPrimitive).content)
    }

    @Test
    fun listPropertyDescribesItems() {
        val schema = JsonSchemaGenerator.schemaFor<Person>()
        val properties = schema["properties"] as JsonObject
        val hobbies = properties["hobbies"] as JsonObject

        assertEquals("array", (hobbies["type"] as JsonPrimitive).content)
        val items = hobbies["items"] as JsonObject
        assertEquals("string", (items["type"] as JsonPrimitive).content)
    }

    @Test
    fun enumIsSerializedAsStringEnumeration() {
        val schema = JsonSchemaGenerator.schemaFor<Tagged>()
        val properties = schema["properties"] as JsonObject
        val mood = properties["mood"] as JsonObject

        assertEquals("string", (mood["type"] as JsonPrimitive).content)
        val enumValues = (mood["enum"] as JsonArray).map { (it as JsonPrimitive).content }
        assertEquals(listOf("HAPPY", "SAD"), enumValues)
        assertNotNull(schema["required"])
    }
}
