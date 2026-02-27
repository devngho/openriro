package io.github.devngho.openriro.facade

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

abstract class Paged<T>: Flow<T> {
    abstract val size: Int

    abstract suspend fun get(index: Int): T?

    suspend fun get(range: IntRange): List<T?> = coroutineScope {
        range.map { index ->
            async { get(index) }
        }.awaitAll()
    }

    abstract suspend fun preload(index: Int)

    suspend fun preload(range: IntRange) = coroutineScope {
        range.map { index ->
            async { preload(index) }
        }.awaitAll()
    }

    suspend fun preloadAll() = coroutineScope {
        (0 until size).map { index ->
            async { preload(index) }
        }.awaitAll()
    }

    abstract suspend fun invalidate()
}