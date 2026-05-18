package com.mojentic.llm.tools.files

/**
 * One line that matched a regex search inside a file.
 */
public data class LineMatch(val lineNumber: Int, val content: String)

/**
 * Sandboxed multiplatform file-system gateway.
 *
 * All paths are interpreted relative to the gateway's base path. Implementations
 * MUST reject attempts to escape the sandbox via `..` or absolute paths.
 *
 * Methods are `suspend` so I/O can be dispatched off the calling coroutine
 * where appropriate.
 */
public interface FilesystemGateway {
    /** Non-recursive listing of [path] (paths returned relative to the base). */
    public suspend fun ls(path: String): List<String>

    /** Recursive listing of every file under [path] (relative paths). */
    public suspend fun listAllFiles(path: String): List<String>

    /** Find every file under [path] whose relative path matches the glob [pattern]. */
    public suspend fun findFilesByGlob(path: String, pattern: String): List<String>

    /** Find every text file under [path] whose contents match the regex [pattern]. */
    public suspend fun findFilesContaining(path: String, pattern: String): List<String>

    /** Find every line in [path]/[fileName] whose content matches [pattern]. */
    public suspend fun findLinesMatching(path: String, fileName: String, pattern: String): List<LineMatch>

    /** Read the full UTF-8 text contents of [path]/[fileName]. */
    public suspend fun read(path: String, fileName: String): String

    /** Replace the contents of [path]/[fileName] with [content] (UTF-8). */
    public suspend fun write(path: String, fileName: String, content: String)

    /** Create [path] (and any missing parents) inside the sandbox. */
    public suspend fun createDirectory(path: String)
}
