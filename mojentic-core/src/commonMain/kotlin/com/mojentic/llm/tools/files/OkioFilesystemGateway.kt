package com.mojentic.llm.tools.files

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * [FilesystemGateway] implementation backed by [okio.FileSystem].
 *
 * Defaults to `FileSystem.SYSTEM`; tests can swap in `FakeFileSystem`.
 *
 * @property fileSystem the underlying okio file system.
 * @property basePath the sandbox root. Every operation is constrained to this
 *   subtree; attempts to escape (e.g. `../foo`) raise [SandboxEscapeException].
 */
public class OkioFilesystemGateway(
    private val fileSystem: FileSystem,
    basePath: String,
) : FilesystemGateway {

    private val root: Path = basePath.toPath().normalized()
    private val rootString: String = root.toString()

    override suspend fun ls(path: String): List<String> {
        val resolved = resolve(path)
        return fileSystem.list(resolved).map { relativise(it) }
    }

    override suspend fun listAllFiles(path: String): List<String> {
        val resolved = resolve(path)
        return fileSystem
            .listRecursively(resolved)
            .filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }
            .map { relativise(it) }
            .toList()
    }

    override suspend fun findFilesByGlob(path: String, pattern: String): List<String> {
        val resolved = resolve(path)
        val regex = globToRegex(pattern)
        return fileSystem
            .listRecursively(resolved)
            .filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }
            .map { it to relativiseFrom(it, resolved) }
            .filter { (_, rel) -> regex.matches(rel) }
            .map { (full, _) -> relativise(full) }
            .toList()
    }

    override suspend fun findFilesContaining(path: String, pattern: String): List<String> {
        val resolved = resolve(path)
        val regex = Regex(pattern)
        val matches = mutableListOf<String>()
        for (candidate in fileSystem.listRecursively(resolved)) {
            if (fileSystem.metadataOrNull(candidate)?.isRegularFile != true) continue
            val content = try {
                fileSystem.read(candidate) { readUtf8() }
            } catch (_: IOException) {
                continue
            }
            if (regex.containsMatchIn(content)) {
                matches += relativise(candidate)
            }
        }
        return matches
    }

    override suspend fun findLinesMatching(
        path: String,
        fileName: String,
        pattern: String,
    ): List<LineMatch> {
        val resolved = resolve(path).resolveSafely(fileName)
        val regex = Regex(pattern)
        val text = fileSystem.read(resolved) { readUtf8() }
        return buildList {
            text.lineSequence().forEachIndexed { index, line ->
                if (regex.containsMatchIn(line)) {
                    add(LineMatch(lineNumber = index + 1, content = line))
                }
            }
        }
    }

    override suspend fun read(path: String, fileName: String): String {
        val target = resolve(path).resolveSafely(fileName)
        return fileSystem.read(target) { readUtf8() }
    }

    override suspend fun write(path: String, fileName: String, content: String) {
        val target = resolve(path).resolveSafely(fileName)
        target.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(target) { writeUtf8(content) }
    }

    override suspend fun createDirectory(path: String) {
        fileSystem.createDirectories(resolve(path))
    }

    private fun resolve(relative: String): Path {
        val candidate = if (relative.isEmpty() || relative == ".") {
            root
        } else {
            root.resolve(relative.toPath()).normalized()
        }
        ensureInsideSandbox(candidate)
        return candidate
    }

    private fun Path.resolveSafely(child: String): Path {
        val target = resolve(child.toPath()).normalized()
        ensureInsideSandbox(target)
        return target
    }

    private fun ensureInsideSandbox(candidate: Path) {
        val candidateString = candidate.toString()
        val escapes = candidateString != rootString &&
            !candidateString.startsWith(rootString + Path.DIRECTORY_SEPARATOR) &&
            !candidateString.startsWith("$rootString/")
        if (escapes) {
            throw SandboxEscapeException("Path '$candidate' attempts to escape sandbox '$root'")
        }
    }

    private fun relativise(path: Path): String = relativiseFrom(path, root)

    private fun relativiseFrom(path: Path, from: Path): String {
        val full = path.toString()
        val base = from.toString()
        return when {
            full == base -> ""
            full.startsWith("$base/") -> full.substring(base.length + 1)
            full.startsWith(base + Path.DIRECTORY_SEPARATOR) -> full.substring(base.length + 1)
            else -> full
        }
    }
}

/** Thrown when a sandboxed path attempts to escape its base directory. */
public class SandboxEscapeException(message: String) : RuntimeException(message)
