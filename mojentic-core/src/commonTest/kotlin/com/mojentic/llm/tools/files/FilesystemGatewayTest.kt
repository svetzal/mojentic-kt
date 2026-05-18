package com.mojentic.llm.tools.files

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FilesystemGatewayTest {
    private lateinit var fs: FakeFileSystem
    private lateinit var gateway: OkioFilesystemGateway
    private val basePath = "/sandbox"

    @BeforeTest
    fun setUp() {
        fs = FakeFileSystem()
        fs.createDirectories(basePath.toPath())
        gateway = OkioFilesystemGateway(fs, basePath)
    }

    @AfterTest
    fun tearDown() {
        fs.checkNoOpenFiles()
    }

    @Test
    fun writeAndReadRoundTrips() = runTest {
        gateway.write("", "hello.txt", "Hi there!")
        assertEquals("Hi there!", gateway.read("", "hello.txt"))
    }

    @Test
    fun writeCreatesIntermediateDirectories() = runTest {
        gateway.write("docs/inner", "note.md", "content")
        assertEquals("content", gateway.read("docs/inner", "note.md"))
    }

    @Test
    fun lsReturnsRelativePaths() = runTest {
        gateway.write("", "a.txt", "a")
        gateway.write("", "b.txt", "b")
        val entries = gateway.ls("").sorted()
        assertEquals(listOf("a.txt", "b.txt"), entries)
    }

    @Test
    fun listAllFilesIsRecursive() = runTest {
        gateway.write("", "root.txt", "1")
        gateway.write("sub", "leaf.txt", "2")
        gateway.write("sub/deeper", "deep.txt", "3")
        val files = gateway.listAllFiles("").sorted()
        assertEquals(listOf("root.txt", "sub/deeper/deep.txt", "sub/leaf.txt"), files)
    }

    @Test
    fun findFilesByGlobMatchesExtensions() = runTest {
        gateway.write("", "a.kt", "kt")
        gateway.write("src", "main.kt", "main")
        gateway.write("src", "main.txt", "ignored")
        val matches = gateway.findFilesByGlob("", "**.kt").sorted()
        assertEquals(listOf("a.kt", "src/main.kt"), matches)
    }

    @Test
    fun findFilesContainingMatchesRegex() = runTest {
        gateway.write("", "a.txt", "hello world")
        gateway.write("", "b.txt", "nothing here")
        gateway.write("", "c.txt", "world peace")
        val matches = gateway.findFilesContaining("", "world").sorted()
        assertEquals(listOf("a.txt", "c.txt"), matches)
    }

    @Test
    fun findLinesMatchingReturnsLineNumbers() = runTest {
        gateway.write("", "log.txt", "ok\nerror: boom\nok\nerror: bang\n")
        val matches = gateway.findLinesMatching("", "log.txt", "error")
        assertEquals(2, matches.size)
        assertEquals(2, matches[0].lineNumber)
        assertEquals("error: boom", matches[0].content)
        assertEquals(4, matches[1].lineNumber)
    }

    @Test
    fun createDirectoryCreatesNestedPath() = runTest {
        gateway.createDirectory("a/b/c")
        assertTrue(fs.exists("/sandbox/a/b/c".toPath()))
    }

    @Test
    fun escapingPathThrows() = runTest {
        assertFailsWith<SandboxEscapeException> { gateway.ls("../outside") }
    }

    @Test
    fun absoluteEscapeThrows() = runTest {
        assertFailsWith<SandboxEscapeException> { gateway.read("..", "secret.txt") }
    }
}
