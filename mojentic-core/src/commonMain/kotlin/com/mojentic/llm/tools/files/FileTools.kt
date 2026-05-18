package com.mojentic.llm.tools.files

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private fun string(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
}

private fun objectSchema(
    requiredFields: List<String>,
    properties: Map<String, JsonObject>,
): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put(
        "properties",
        buildJsonObject {
            properties.forEach { (name, schema) -> put(name, schema) }
        },
    )
    put("required", JsonArray(requiredFields.map { JsonPrimitive(it) }))
}

private fun requireString(args: JsonObject, key: String, tool: String): String =
    (args[key] as? JsonPrimitive)?.content
        ?: error("$tool: missing '$key' argument")

private fun optionalString(args: JsonObject, key: String): String? =
    (args[key] as? JsonPrimitive)?.content

private fun splitPath(full: String): Pair<String, String> {
    val idx = full.lastIndexOf('/')
    return if (idx < 0) "" to full else full.substring(0, idx) to full.substring(idx + 1)
}

private fun stringsArray(items: List<String>): String =
    buildJsonArray { items.forEach { add(JsonPrimitive(it)) } }.toString()

/** List files (non-recursive) in a sandbox-relative directory. */
public class ListFilesTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "list_files",
        description = "List files in the specified directory (non-recursive), optionally filtered by extension. " +
            "Use this when you need to see what files are available in a specific directory without including " +
            "subdirectories.",
        parameters = objectSchema(
            requiredFields = listOf("path"),
            properties = mapOf(
                "path" to string(
                    "The path relative to the sandbox root to list files from. For example, '.' for the root " +
                        "directory, 'src' for the src directory, or 'docs/images' for a nested directory.",
                ),
                "extension" to string(
                    "The file extension to filter by (e.g., '.py', '.txt', '.md'). If not provided, all files " +
                        "will be listed.",
                ),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        val extension = optionalString(arguments, "extension")
        return runCatching {
            val entries = fs.ls(path)
            val filtered = extension?.let { ext -> entries.filter { it.endsWith(ext) } } ?: entries
            stringsArray(filtered)
        }.getOrElse { errorPayload(it, "listing files in '$path'") }
    }
}

/** Read the entire UTF-8 text contents of a file. */
public class ReadFileTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "read_file",
        description = "Read the entire content of a file as a string. Use this when you need to access or " +
            "analyze the complete contents of a file.",
        parameters = objectSchema(
            requiredFields = listOf("path"),
            properties = mapOf(
                "path" to string(
                    "The full relative path including the filename of the file to read.",
                ),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        return runCatching {
            val (dir, file) = splitPath(path)
            fs.read(dir, file)
        }.getOrElse { errorPayload(it, "reading file '$path'") }
    }
}

/** Overwrite a file in the sandbox with new UTF-8 content. */
public class WriteFileTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "write_file",
        description = "Write content to a file, completely overwriting any existing content. Use this when you " +
            "want to replace the entire contents of a file with new content.",
        parameters = objectSchema(
            requiredFields = listOf("path", "content"),
            properties = mapOf(
                "path" to string("The full relative path including the filename where the file should be written."),
                "content" to string("The content to write to the file. This will completely replace any existing content."),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        val content = requireString(arguments, "content", name)
        return runCatching {
            val (dir, file) = splitPath(path)
            fs.write(dir, file, content)
            "Successfully wrote to $path"
        }.getOrElse { errorPayload(it, "writing to file '$path'") }
    }
}

/** Recursively list every regular file under a directory. */
public class ListAllFilesTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "list_all_files",
        description = "List all files recursively in the specified directory, including files in subdirectories. " +
            "Use this when you need a complete inventory of all files in a directory and its subdirectories.",
        parameters = objectSchema(
            requiredFields = listOf("path"),
            properties = mapOf(
                "path" to string(
                    "The path relative to the sandbox root to list files from recursively.",
                ),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        return runCatching { stringsArray(fs.listAllFiles(path)) }
            .getOrElse { errorPayload(it, "listing files recursively in '$path'") }
    }
}

/** Find files whose relative paths match a glob pattern. */
public class FindFilesByGlobTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "find_files_by_glob",
        description = "Find files matching a glob pattern in the specified directory. Use this when you need to " +
            "locate files with specific patterns in their names or paths (e.g. '*.kt' or '**/*.json').",
        parameters = objectSchema(
            requiredFields = listOf("path", "pattern"),
            properties = mapOf(
                "path" to string("The path relative to the sandbox root to search for files from."),
                "pattern" to string(
                    "The glob pattern to match files against. '*' matches a single path segment, '**' matches " +
                        "any depth.",
                ),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        val pattern = requireString(arguments, "pattern", name)
        return runCatching { stringsArray(fs.findFilesByGlob(path, pattern)) }
            .getOrElse { errorPayload(it, "finding files with pattern '$pattern' in '$path'") }
    }
}

/** Find files whose contents match a regular expression. */
public class FindFilesContainingTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "find_files_containing",
        description = "Find files containing text matching a regex pattern in the specified directory. Use this " +
            "when you need to search for specific content across multiple files.",
        parameters = objectSchema(
            requiredFields = listOf("path", "pattern"),
            properties = mapOf(
                "path" to string("The path relative to the sandbox root to search in."),
                "pattern" to string("The regex pattern to search for in files (Kotlin regex syntax)."),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        val pattern = requireString(arguments, "pattern", name)
        return runCatching { stringsArray(fs.findFilesContaining(path, pattern)) }
            .getOrElse { errorPayload(it, "finding files containing pattern '$pattern' in '$path'") }
    }
}

/** Find every line in a file whose content matches a regex. */
public class FindLinesMatchingTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "find_lines_matching",
        description = "Find all lines in a file matching a regex pattern, returning both line numbers and content. " +
            "Use this when you need to locate specific patterns within a single file.",
        parameters = objectSchema(
            requiredFields = listOf("path", "pattern"),
            properties = mapOf(
                "path" to string("The full relative path including the filename of the file to search."),
                "pattern" to string("The regex pattern to match lines against (Kotlin regex syntax)."),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        val pattern = requireString(arguments, "pattern", name)
        return runCatching {
            val (dir, file) = splitPath(path)
            val matches = fs.findLinesMatching(dir, file, pattern)
            buildJsonArray {
                matches.forEach { match ->
                    add(
                        buildJsonObject {
                            put("line_number", JsonPrimitive(match.lineNumber))
                            put("content", JsonPrimitive(match.content))
                        },
                    )
                }
            }.toString()
        }.getOrElse { errorPayload(it, "finding lines matching pattern '$pattern' in file '$path'") }
    }
}

/** Create a directory (and missing parents) inside the sandbox. */
public class CreateDirectoryTool(private val fs: FilesystemGateway) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "create_directory",
        description = "Create a new directory at the specified path. If the directory already exists, this " +
            "operation will succeed without error. Use this when you need to create a directory structure " +
            "before writing files to it.",
        parameters = objectSchema(
            requiredFields = listOf("path"),
            properties = mapOf(
                "path" to string("The relative path where the directory should be created."),
            ),
        ),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val path = requireString(arguments, "path", name)
        return runCatching {
            fs.createDirectory(path)
            "Successfully created directory '$path'"
        }.getOrElse { errorPayload(it, "creating directory '$path'") }
    }
}

private fun errorPayload(t: Throwable, context: String): String {
    val msg = t.message ?: t::class.simpleName ?: "unknown error"
    return "Error $context: $msg"
}

/**
 * Factory for the full set of file tools bound to a single [FilesystemGateway].
 *
 * Drop the returned list straight into [com.mojentic.llm.LlmBroker] alongside
 * any other tools you want to expose.
 */
public fun fileToolsFor(fs: FilesystemGateway): List<LlmTool> = listOf(
    ListFilesTool(fs),
    ReadFileTool(fs),
    WriteFileTool(fs),
    ListAllFilesTool(fs),
    FindFilesByGlobTool(fs),
    FindFilesContainingTool(fs),
    FindLinesMatchingTool(fs),
    CreateDirectoryTool(fs),
)
