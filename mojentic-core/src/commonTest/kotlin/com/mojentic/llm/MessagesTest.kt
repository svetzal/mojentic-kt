package com.mojentic.llm

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessagesTest {
    @Test
    fun systemFactoryBuildsSystemMessage() {
        val message = LlmMessage.system("you are concise")

        assertEquals(MessageRole.System, message.role)
        assertEquals("you are concise", message.content)
        assertNull(message.toolCalls)
    }

    @Test
    fun userFactoryDefaultsToUserRole() {
        val message = LlmMessage.user("hello")

        assertEquals(MessageRole.User, message.role)
        assertEquals("hello", message.content)
    }

    @Test
    fun assistantFactoryCarriesToolCalls() {
        val call = LlmToolCall(name = "do", arguments = JsonObject(emptyMap()))

        val message = LlmMessage.assistant(toolCalls = listOf(call))

        assertEquals(MessageRole.Assistant, message.role)
        assertNull(message.content)
        assertEquals(listOf(call), message.toolCalls)
    }

    @Test
    fun toolFactoryPairsResultWithCall() {
        val call = LlmToolCall(id = "abc", name = "echo", arguments = JsonObject(emptyMap()))

        val message = LlmMessage.tool(content = "{\"ok\":true}", toolCall = call)

        assertEquals(MessageRole.Tool, message.role)
        assertEquals("{\"ok\":true}", message.content)
        assertEquals(listOf(call), message.toolCalls)
    }

    @Test
    fun messageRoleWireValueIsLowercase() {
        assertEquals("system", MessageRole.System.wireValue)
        assertEquals("user", MessageRole.User.wireValue)
        assertEquals("assistant", MessageRole.Assistant.wireValue)
        assertEquals("tool", MessageRole.Tool.wireValue)
    }

    @Test
    fun multimodalUserMessageCarriesParts() {
        val parts = listOf<MessageContent>(
            TextContent("describe this"),
            ImageContent(data = "AAA=", mimeType = "image/png"),
        )

        val message = LlmMessage.user(parts)

        assertEquals(MessageRole.User, message.role)
        assertTrue(message.contentParts == parts)
    }
}
