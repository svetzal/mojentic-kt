package com.mojentic.llm.tools.files

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileToolsTest {
    private lateinit var fs: FakeFileSystem
    private lateinit var gateway: OkioFilesystemGateway

    @BeforeTest
    fun setUp() {
        fs = FakeFileSystem()
        fs.createDirectories("/sandbox".toPath())
        gateway = OkioFilesystemGateway(fs, "/sandbox")
    }

    @Test
    fun fileToolsForReturnsExpectedToolNames() {
        val names = fileToolsFor(gateway).map { it.name }.toSet()
        assertEquals(
            setOf(
                "list_files",
                "read_file",
                "write_file",
                "list_all_files",
                "find_files_by_glob",
                "find_files_containing",
                "find_lines_matching",
                "create_directory",
            ),
            names,
        )
    }

    @Test
    fun writeFileToolWritesContent() = runTest {
        val tool = WriteFileTool(gateway)
        val result = tool.execute(
            buildJsonObject {
                put("path", JsonPrimitive("greetings.txt"))
                put("content", JsonPrimitive("hi"))
            },
        )
        assertEquals("Successfully wrote to greetings.txt", result)
        assertEquals("hi", gateway.read("", "greetings.txt"))
    }

    @Test
    fun readFileToolReturnsContent() = runTest {
        gateway.write("", "data.txt", "payload")
        val result = ReadFileTool(gateway).execute(
            buildJsonObject { put("path", JsonPrimitive("data.txt")) },
        )
        assertEquals("payload", result)
    }

    @Test
    fun listFilesToolFiltersByExtension() = runTest {
        gateway.write("", "a.kt", "")
        gateway.write("", "b.txt", "")
        gateway.write("", "c.kt", "")
        val result = ListFilesTool(gateway).execute(
            buildJsonObject {
                put("path", JsonPrimitive(""))
                put("extension", JsonPrimitive(".kt"))
            },
        )
        val parsed = Json.parseToJsonElement(result) as JsonArray
        val items = parsed.map { (it as JsonPrimitive).content }.sorted()
        assertEquals(listOf("a.kt", "c.kt"), items)
    }

    @Test
    fun findFilesByGlobToolReturnsMatches() = runTest {
        gateway.write("src", "main.kt", "")
        gateway.write("src", "main.txt", "")
        val result = FindFilesByGlobTool(gateway).execute(
            buildJsonObject {
                put("path", JsonPrimitive(""))
                put("pattern", JsonPrimitive("**.kt"))
            },
        )
        val parsed = Json.parseToJsonElement(result) as JsonArray
        assertEquals(listOf("src/main.kt"), parsed.map { (it as JsonPrimitive).content })
    }

    @Test
    fun findLinesMatchingToolEmitsJsonObjects() = runTest {
        gateway.write("", "log.txt", "ok\nbad\nok\n")
        val result = FindLinesMatchingTool(gateway).execute(
            buildJsonObject {
                put("path", JsonPrimitive("log.txt"))
                put("pattern", JsonPrimitive("bad"))
            },
        )
        val parsed = Json.parseToJsonElement(result) as JsonArray
        assertEquals(1, parsed.size)
        val item = parsed[0] as JsonObject
        assertEquals("2", (item["line_number"] as JsonPrimitive).content)
        assertEquals("bad", (item["content"] as JsonPrimitive).content)
    }

    @Test
    fun createDirectoryToolCreatesPath() = runTest {
        val result = CreateDirectoryTool(gateway).execute(
            buildJsonObject { put("path", JsonPrimitive("nested/dir")) },
        )
        assertEquals("Successfully created directory 'nested/dir'", result)
        assertTrue(fs.exists("/sandbox/nested/dir".toPath()))
    }

    @Test
    fun listAllFilesToolReturnsEverything() = runTest {
        gateway.write("", "a.txt", "")
        gateway.write("nest", "b.txt", "")
        val result = ListAllFilesTool(gateway).execute(
            buildJsonObject { put("path", JsonPrimitive("")) },
        )
        val parsed = Json.parseToJsonElement(result) as JsonArray
        val items = parsed.map { (it as JsonPrimitive).content }.sorted()
        assertEquals(listOf("a.txt", "nest/b.txt"), items)
    }

    @Test
    fun toolsReturnErrorStringOnSandboxEscape() = runTest {
        val result = ReadFileTool(gateway).execute(
            buildJsonObject { put("path", JsonPrimitive("../secret.txt")) },
        )
        assertTrue(result.startsWith("Error reading file"))
    }
}
