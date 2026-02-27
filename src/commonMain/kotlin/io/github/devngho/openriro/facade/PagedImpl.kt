package io.github.devngho.openriro.facade

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
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
    private val revalidationMutex = Mutex()
    override val size: Int get() = knownTotalCount
    private var knownTotalCount = initialTotalCount
    private var pageSize = initialPageSize
    // 만약 (initialPageSize == initialTotalCount)라면 페이지가 하나뿐이므로 pageSize가 확정된 것으로 간주할 수 없음
    private var pageSizeConfirmed = initialPageSize != initialTotalCount
    private var lastValidated = TimeSource.Monotonic.markNow()

    init {
        require(initialPageSize >= 0) { "initialPageSize must be >= 0" }
        require(initialTotalCount >= 0) { "initialTotalCount must be >= 0" }
        require(initialTotalCount == 0 || initialPageSize > 0) {
            "initialPageSize must be > 0 when initialTotalCount > 0"
        }
    }

    init {
        cache[0] = initialPage
    }

    private val totalPages: Int get() = if (pageSize == 0) 1 else knownTotalCount / pageSize + if (knownTotalCount % pageSize == 0) 0 else 1

    private suspend fun mutexFor(page: Int): Mutex = mapMutex.withLock {
        pageMutexes.getOrPut(page) { Mutex() }
    }

    private suspend fun maybeUpdateTotalCount(newTotalCount: Int) {
        if (newTotalCount != knownTotalCount) {
            knownTotalCount = newTotalCount
            mapMutex.withLock {
                cache.clear()
            }
        }
    }

    private suspend fun confirmPageSize() {
        if (pageSizeConfirmed) return

        val result = fetchPage(0) ?: return
        if (result.totalCount != result.items.count()) {
            pageSize = result.items.count()
            pageSizeConfirmed = true

            maybeUpdateTotalCount(result.totalCount)
            mapMutex.withLock {
                cache[0] = result.items
            }
        }
    }

    private suspend fun checkRevalidation() {
        revalidationMutex.withLock {
            val elapsed = lastValidated.elapsedNow()
            val shouldHardInvalidate =
                cacheStrategy.hardLimit == Duration.ZERO ||
                    (cacheStrategy.hardLimit > Duration.ZERO && elapsed >= cacheStrategy.hardLimit)

            if (shouldHardInvalidate) {
                invalidate()
                return@withLock
            }

            val shouldSoftProbe =
                cacheStrategy.softLimit == Duration.ZERO ||
                    (cacheStrategy.softLimit > Duration.ZERO && elapsed >= cacheStrategy.softLimit)

            if (shouldSoftProbe) {
                // 첫 페이지를 조회, totalCount와 아이템 내용을 확인하여 변경 여부 판단
                val probeResult = fetchPage(0) ?: return@withLock
                val probeItems = probeResult.items
                val cached = mapMutex.withLock { cache[0] }

                if (probeResult.totalCount != knownTotalCount || cached == null || (cached != probeItems)) {
                    knownTotalCount = probeResult.totalCount

                    if (!pageSizeConfirmed && probeResult.items.count() != knownTotalCount) {
                        pageSize = probeResult.items.count()
                        pageSizeConfirmed = true
                    }

                    mapMutex.withLock {
                        cache.clear()
                        cache[0] = probeItems
                    }
                }

                lastValidated = TimeSource.Monotonic.markNow()
            }
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
            }
        }

        if (page >= totalPages) {
            // although the total count might have changed, if the requested page is out of range, we can just return null without caching
            mapMutex.withLock {
                cache[page] = emptyList() // cache empty list to avoid repeated fetches for out-of-range pages
            }

            return null
        }

        if (cacheStrategy != CacheStrategy.NONE) {
            mapMutex.withLock {
                cache[page] = items
            }
        }

        return items
    }

    override suspend fun get(index: Int): T? {
        if (index < 0) return null
        if (knownTotalCount == 0) return null

        if (!pageSizeConfirmed && index >= pageSize) confirmPageSize()
        if (cacheStrategy != CacheStrategy.NONE) checkRevalidation()
        if (pageSize <= 0) return null

        val page = index / pageSize
        val pageIndex = index % pageSize

        if (cacheStrategy == CacheStrategy.NONE) {
            return fetchAndCache(page)?.getOrNull(pageIndex)
        }

        mapMutex.withLock {
            cache[page]?.let { return it.getOrNull(pageIndex) }
        }

        return mutexFor(page).withLock {
            mapMutex.withLock {
                cache[page]?.getOrNull(pageIndex)
            } ?: fetchAndCache(page)?.getOrNull(pageIndex)
        }
    }

    override suspend fun preload(index: Int) {
        if (index < 0) return
        if (knownTotalCount == 0) return

        if (!pageSizeConfirmed && index >= pageSize) confirmPageSize()
        if (pageSize <= 0) return

        val page = index / pageSize

        if (cacheStrategy == CacheStrategy.NONE) return
        if (mapMutex.withLock { cache.containsKey(page) }) return

        mutexFor(page).withLock {
            if (!mapMutex.withLock { cache.containsKey(page) }) {
                fetchAndCache(page)
            }
        }
    }

    override suspend fun collect(collector: FlowCollector<T>) = coroutineScope {
        for (i in 0 until size) {
//            if (i + 1 < totalPages) launch { preload((i + 1) * pageSize) }
            // preloading next page improves performance by about 10%
            if (totalPages > 1 && i + 1 < totalPages) launch { preload((i + 1) * pageSize) }

            collector.emit(get(i) ?: return@coroutineScope)
        }
    }

    override suspend fun invalidate() {
        mapMutex.withLock {
            cache.clear()
        }
        lastValidated = TimeSource.Monotonic.markNow()
    }
}