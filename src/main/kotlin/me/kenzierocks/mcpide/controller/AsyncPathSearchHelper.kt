package me.kenzierocks.mcpide.controller

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.nio.file.Files
import java.nio.file.Path

data class SearchResult(
    val path: Path,
    val line: Int,
    val columns: IntRange
)

abstract class AsyncPathSearchHelper(
    private val root: Path
) : SearchHelper<SearchResult>(),
    CoroutineScope by CoroutineScope(Dispatchers.Default + CoroutineName("AsyncPathSearch")) {
    override fun search(term: String): Flow<SearchResult>? {
        val regex = term.toRegex(RegexOption.LITERAL)
        return flow<SearchResult> {
            Files.walk(root).use { paths ->
                paths.iterator().asFlow()
                    .filter { Files.isRegularFile(it) }
                    .map { file ->
                        Files.newBufferedReader(file).useLines { lines ->
                            lines.withIndex().flatMap { (i, line) ->
                                regex.findAll(line).map { SearchResult(file, i, it.range) }
                            }.asFlow()
                        }
                    }
                    .flowOn(Dispatchers.IO)
                    .buffer()
                    .collect { emitAll(it) }
            }
        }
    }

    override suspend fun moveToResult(result: SearchResult) {
        // does nothing, there's no jumping to do.
    }
}