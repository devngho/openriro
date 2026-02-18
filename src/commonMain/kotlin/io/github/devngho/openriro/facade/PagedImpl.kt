package io.github.devngho.openriro.facade

import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

class PagedImpl<T>(
    initialPage: List<T>,
    private val initialTotalCount: Int,
    initialPageSize: Int,
    private val cacheStrategy: CacheStrategy,
    private val fetchPage: suspend (page: Int) -> FetchResult<T>?
) : Paged<T>() {

    data class FetchResult<T>(val items: List<T>, val totalCount: Int)

    private val cache = mutableMapOf<Int, List<T>>()
    private val pageMutexes = mutableMapOf<Int, Mutex>()
    private val mapMutex = Mutex()
    override val totalCount: Int get() = knownTotalCount
    private var knownTotalCount = initialTotalCount
    private var pageSize = initialPageSize
    // 만약 (initialPageSize == initialTotalCount)라면 페이지가 하나뿐이므로 pageSize가 확정된 것으로 간주할 수 없음
    private var pageSizeConfirmed = initialPageSize != initialTotalCount
    private var lastValidated = TimeSource.Monotonic.markNow()

    init {
        cache[0] = initialPage
    }

    private val totalPages: Int get() = if (pageSize == 0) 1 else knownTotalCount / pageSize + if (knownTotalCount % pageSize == 0) 0 else 1

    private suspend fun mutexFor(page: Int): Mutex = mapMutex.withLock {
        pageMutexes.getOrPut(page) { Mutex() }
    }

    private suspend fun confirmPageSize() {
        if (pageSizeConfirmed) return

        val result = fetchPage(1) ?: return
        if (result.items.isNotEmpty()) {
            pageSize = result.items.count()
            pageSizeConfirmed = true
            cache[1] = result.items

            if (result.totalCount != knownTotalCount) {
                knownTotalCount = result.totalCount
            }
        }
    }

    private suspend fun checkRevalidation() {
        val elapsed = lastValidated.elapsedNow()

        if (cacheStrategy.hardLimit > kotlin.time.Duration.ZERO && elapsed >= cacheStrategy.hardLimit) {
            invalidate()
            return
        }

        if (cacheStrategy.softLimit > kotlin.time.Duration.ZERO && elapsed >= cacheStrategy.softLimit) {
            // 첫 페이지를 조회, totalCount와 아이템 내용을 확인하여 변경 여부 판단
            val probeResult = fetchPage(0) ?: return
            val probeItems = probeResult.items
            val cached = cache[0]

            if (probeResult.totalCount != knownTotalCount || cached == null || (cached != probeItems)) {
                knownTotalCount = probeResult.totalCount

                if (!pageSizeConfirmed && probeResult.items.count() != knownTotalCount) {
                    pageSize = probeResult.items.count()
                    pageSizeConfirmed = true
                }

                mapMutex.withLock {
                    cache.clear()
                    pageMutexes.clear()
                }
                cache[0] = probeItems
            }

            lastValidated = TimeSource.Monotonic.markNow()
        }
    }

    private suspend fun fetchAndCache(page: Int): List<T>? {
        val result = fetchPage(page) ?: return null
        val items = result.items

        if (!pageSizeConfirmed && page > 0 && result.items.isNotEmpty()) {
            pageSize = result.items.count()
            pageSizeConfirmed = true
        }

        if (result.totalCount != knownTotalCount) {
            knownTotalCount = result.totalCount
            mapMutex.withLock {
                cache.clear()
                pageMutexes.clear()
            }
        }

        cache[page] = items
        return items
    }

    override suspend fun get(index: Int): T? {
        if (!pageSizeConfirmed && index >= pageSize) confirmPageSize()
        if (cacheStrategy != CacheStrategy.NONE) checkRevalidation()

        val page = index / pageSize
        val pageIndex = index % pageSize

        if (cacheStrategy == CacheStrategy.NONE) {
            return fetchAndCache(page)?.getOrNull(pageIndex)
        }

        cache[page]?.let { return it.getOrNull(pageIndex) }

        return mutexFor(page).withLock {
            cache[page]?.getOrNull(pageIndex) ?: fetchAndCache(page)?.getOrNull(pageIndex)
        }
    }

    override suspend fun preload(index: Int) {
        if (!pageSizeConfirmed && index >= pageSize) confirmPageSize()

        val page = index / pageSize

        if (cacheStrategy == CacheStrategy.NONE) return
        if (cache.containsKey(page)) return

        mutexFor(page).withLock {
            if (!cache.containsKey(page)) {
                fetchAndCache(page)
            }
        }
    }

    override fun asFlow() = channelFlow {
        for (i in 0 until totalCount) {
//            if (i + 1 < totalPages) launch { preload((i + 1) * pageSize) }
            if (totalPages > 1 && i + 1 < totalPages) launch { preload((i + 1) * pageSize) }

            send(get(i) ?: return@channelFlow)
        }
    }

    override suspend fun invalidate() {
        mapMutex.withLock {
            cache.clear()
            pageMutexes.clear()
        }
        lastValidated = TimeSource.Monotonic.markNow()
    }
}