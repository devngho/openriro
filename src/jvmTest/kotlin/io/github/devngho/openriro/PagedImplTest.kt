package io.github.devngho.openriro

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.facade.CacheStrategy
import io.github.devngho.openriro.facade.OpenRiroClient
import io.github.devngho.openriro.facade.PagedImpl
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

class PagedImplTest : DescribeSpec({

    fun makeData(totalCount: Int, pageSize: Int): List<List<String>> {
        return (0 until totalCount).chunked(pageSize).mapIndexed { pageIndex, chunk ->
            List(chunk.size) { i -> "item-${pageIndex * pageSize + i}" }
        }
    }

    describe("basic index-based access") {
        val pages = makeData(50, 10)

        val paged = PagedImpl(pages[0], 50, 10, CacheStrategy.NONE) { page ->
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 50) }
        }

        it("gets first item from cached initial page") {
            val item = paged.get(0)
            item shouldNotBe null
            item shouldBe "item-0"
        }

        it("gets item from second page") {
            val item = paged.get(10)
            item shouldNotBe null
            item shouldBe "item-10"
        }

        it("gets last item") {
            val item = paged.get(49)
            item shouldNotBe null
            item shouldBe "item-49"
        }

        it("returns null for out-of-bounds index") {
            paged.get(50).shouldBeNull()
        }

        it("gets a range of items") {
            val items = paged.get(0..<10)
            items shouldHaveSize 10
            items shouldBe (0..<10).map { "item-$it" }
        }
    }

    describe("CacheStrategy.NONE fetches every time") {
        val fetchCount = AtomicInteger(0)
        val pages = makeData(30, 10)

        val paged = PagedImpl(pages[0], 30, 10, CacheStrategy.NONE) { page ->
            fetchCount.incrementAndGet()
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 30) }
        }

        it("fetches on every get, even for same page") {
            paged.get(10)
            paged.get(10)
            fetchCount.get() shouldBe 2
        }
    }

    describe("caching strategy caches pages") {
        val fetchCount = AtomicInteger(0)
        val pages = makeData(30, 10)

        val strategy = CacheStrategy(
            softLimit = 500.milliseconds,
            hardLimit = 1000.milliseconds
        )

        val paged = PagedImpl(pages[0], 30, 10, strategy) { page ->
            fetchCount.incrementAndGet()
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 30) }
        }

        it("uses cache for repeated access to the same page") {
            paged.get(10)
            val countAfterFirst = fetchCount.get()
            paged.get(11)
            fetchCount.get() shouldBe countAfterFirst
        }

        it("uses cached initial page without fetch") {
            val before = fetchCount.get()
            paged.get(0)
            fetchCount.get() shouldBe before
        }
    }

    describe("soft limit revalidation") {
        val fetchCount = AtomicInteger(0)
        val pages = makeData(30, 10)

        val strategy = CacheStrategy(
            softLimit = 50.milliseconds,
            hardLimit = 10000.milliseconds
        )

        val paged = PagedImpl(pages[0], 30, 10, strategy) { page ->
            fetchCount.incrementAndGet()
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 30) }
        }

        it("probes first page after soft limit elapses") {
            paged.get(0)
            val before = fetchCount.get()

            delay(100)

            paged.get(0)
            fetchCount.get() shouldBe before + 1
        }
    }

    describe("soft limit detects content change and invalidates") {
        var serverData = makeData(30, 10).map { it.toMutableList() }.toMutableList()

        val strategy = CacheStrategy(
            softLimit = 50.milliseconds,
            hardLimit = 10000.milliseconds
        )

        val paged = PagedImpl(serverData[0].toList(), 30, 10, strategy) { page ->
            serverData.getOrNull(page)?.let { PagedImpl.FetchResult(it.toList(), 30) }
        }

        it("returns updated data after server-side change") {
            paged.get(0) shouldBe "item-0"

            serverData[0][0] = "changed-0"

            delay(100)

            paged.get(0) shouldBe "changed-0"
        }
    }

    describe("hard limit invalidates regardless of content") {
        val fetchCount = AtomicInteger(0)
        val pages = makeData(30, 10)

        val strategy = CacheStrategy(
            softLimit = 50.milliseconds,
            hardLimit = 80.milliseconds
        )

        val paged = PagedImpl(pages[0], 30, 10, strategy) { page ->
            fetchCount.incrementAndGet()
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 30) }
        }

        it("invalidates cache after hard limit even if content is same") {
            paged.get(10)
            val after = fetchCount.get()

            delay(100)

            paged.get(10)
            fetchCount.get() shouldBe after + 1
        }
    }

    describe("totalCount change triggers invalidation") {
        var currentTotal = 30

        val paged = PagedImpl(makeData(30, 10)[0], 30, 10, CacheStrategy.NONE) { page ->
            val data = makeData(currentTotal, 10)
            data.getOrNull(page)?.let { PagedImpl.FetchResult(it, currentTotal) }
        }

        it("adapts when totalCount changes") {
            paged.get(29) shouldNotBe null

            currentTotal = 50
            paged.get(49) shouldNotBe null
        }
    }

    describe("single-page pageSize confirmation") {
        it("confirms pageSize when accessing beyond initial page") {
            val page0 = (0..<5).map { "item-$it" }
            val page1 = (5..<10).map { "item-$it" }
            val page2 = (10..<15).map { "item-$it" }

            val paged = PagedImpl(page0, 5, 5, CacheStrategy.NONE) { page ->
                when (page) {
                    0 -> PagedImpl.FetchResult(page0, 15)
                    1 -> PagedImpl.FetchResult(page1, 15)
                    2 -> PagedImpl.FetchResult(page2, 15)
                    else -> null
                }
            }

            paged.get(0) shouldBe "item-0"
            paged.get(4) shouldBe "item-4"
            paged.get(7) shouldBe "item-7"
            paged.get(14) shouldBe "item-14"
        }
    }

    describe("preload") {
        val fetchCount = AtomicInteger(0)
        val pages = makeData(30, 10)

        val strategy = CacheStrategy(
            softLimit = 10000.milliseconds,
            hardLimit = 10000.milliseconds
        )

        val paged = PagedImpl(pages[0], 30, 10, strategy) { page ->
            fetchCount.incrementAndGet()
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 30) }
        }

        it("preloads page and serves from cache") {
            paged.preload(10)
            val countAfterPreload = fetchCount.get()

            val item = paged.get(10)
            item shouldNotBe null
            item shouldBe "item-10"
            fetchCount.get() shouldBe countAfterPreload
        }

        it("skips preload for CacheStrategy.NONE") {
            val nonCached = PagedImpl(pages[0], 30, 10, CacheStrategy.NONE) { page ->
                fetchCount.incrementAndGet()
                pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 30) }
            }
            val before = fetchCount.get()
            nonCached.preload(10)
            fetchCount.get() shouldBe before
        }
    }

    describe("asFlow") {
        val pages = makeData(25, 10)

        val strategy = CacheStrategy(
            softLimit = 10000.milliseconds,
            hardLimit = 10000.milliseconds
        )

        val paged = PagedImpl(pages[0], 25, 10, strategy) { page ->
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 25) }
        }

        it("emits all items in order") {
            val items = paged.asFlow().toList()
            items shouldHaveSize 25
            items shouldBe (0..<25).map { "item-$it" }
        }
    }

    describe("invalidate") {
        val fetchCount = AtomicInteger(0)
        val pages = makeData(20, 10)

        val strategy = CacheStrategy(
            softLimit = 10000.milliseconds,
            hardLimit = 10000.milliseconds
        )

        val paged = PagedImpl(pages[0], 20, 10, strategy) { page ->
            fetchCount.incrementAndGet()
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 20) }
        }

        it("clears cache and re-fetches after invalidate") {
            paged.get(10)
            val countBefore = fetchCount.get()

            paged.invalidate()

            paged.get(10)
            fetchCount.get() shouldBe countBefore + 1
        }
    }

    describe("concurrent access") {
        val pages = makeData(100, 10)

        val strategy = CacheStrategy(
            softLimit = 10000.milliseconds,
            hardLimit = 10000.milliseconds
        )

        val paged = PagedImpl(pages[0], 100, 10, strategy) { page ->
            delay(10)
            pages.getOrNull(page)?.let { PagedImpl.FetchResult(it, 100) }
        }

        it("handles concurrent gets to different pages") {
            val results = (0..<10).map { page ->
                async { paged.get(page * 10) }
            }.awaitAll()

            results shouldHaveSize 10
            results.forEachIndexed { i, item ->
                item shouldNotBe null
                item shouldBe "item-${i * 10}"
            }
        }

        it("keeps throughput high with many elements and slow page fetch") {
            val totalCount = 10000
            val pageSize = 50
            val pageCount = totalCount / pageSize
            val heavyPages = makeData(totalCount, pageSize)
            val fetchCount = AtomicInteger(0)

            val heavyPaged = PagedImpl(heavyPages[0], totalCount, pageSize, CacheStrategy.NO_REVALIDATION) { page ->
                fetchCount.incrementAndGet()
                delay(100)
                heavyPages.getOrNull(page)?.let { PagedImpl.FetchResult(it, totalCount) }
            }

            val firstWave = TimeSource.Monotonic.markNow()
            val firstResults = (0 until pageCount).map { page ->
                async { heavyPaged.get(page * pageSize) }
            }.awaitAll()
            val firstElapsed = firstWave.elapsedNow()

            firstResults.shouldHaveSize(pageCount)
            firstResults.forEachIndexed { page, item ->
                item shouldBe "item-${page * pageSize}"
            }
            fetchCount.get() shouldBe pageCount - 1
            firstElapsed shouldBeLessThan 600.milliseconds

            heavyPaged.invalidate()

            val secondWave = TimeSource.Monotonic.markNow()
            val secondResults = (0 until pageCount).map { page ->
                async { heavyPaged.get(page * pageSize) }
            }.awaitAll()
            val secondElapsed = secondWave.elapsedNow()

            secondResults.shouldHaveSize(pageCount)
            secondResults.forEachIndexed { page, item ->
                item shouldBe "item-${page * pageSize}"
            }
            fetchCount.get() shouldBe 200+pageCount - 1
            secondElapsed shouldBeLessThan 120.milliseconds

            heavyPaged.invalidate()

            val w3 = TimeSource.Monotonic.markNow()
            val res = heavyPaged.asFlow().toList()
            val w3Elapsed = w3.elapsedNow()

            res.shouldHaveSize(totalCount)
            res.forEachIndexed { i, item ->
                item shouldBe "item-$i"
            }
            fetchCount.get() shouldBe 400+pageCount - 1
            w3Elapsed shouldBeLessThan 400.milliseconds

            heavyPaged.invalidate()

            heavyPaged.preloadAll()
            val w4 = TimeSource.Monotonic.markNow()
            val res2 = heavyPaged.asFlow().toList()
            val w4Elapsed = w4.elapsedNow()

            res2.shouldHaveSize(totalCount)
            res2.forEachIndexed { i, item ->
                item shouldBe "item-$i"
            }
            fetchCount.get() shouldBe 600+pageCount - 1
            w4Elapsed shouldBeLessThan 10.milliseconds
        }
    }
    describe("OpenRiroClient paged instance cache") {
        val client = OpenRiroClient(mockk<OpenRiroAPI>(relaxed = true))

        it("returns same Paged instance for the same key") {
            val factoryCount = AtomicInteger(0)
            val key = "test-key"

            val paged1 = client.getOrCreatePaged(key) {
                factoryCount.incrementAndGet()
                PagedImpl(listOf("a"), 1, 1, CacheStrategy.NONE) { null }
            }

            val paged2 = client.getOrCreatePaged(key) {
                factoryCount.incrementAndGet()
                PagedImpl(listOf("b"), 1, 1, CacheStrategy.NONE) { null }
            }

            paged1 shouldBeSameInstanceAs paged2
            factoryCount.get() shouldBe 1
        }

        it("returns different Paged instances for different keys") {
            val paged1 = client.getOrCreatePaged("key-1") {
                PagedImpl(listOf("a"), 1, 1, CacheStrategy.NO_REVALIDATION) { null }
            }

            val paged2 = client.getOrCreatePaged("key-2") {
                PagedImpl(listOf("b"), 1, 1, CacheStrategy.NO_REVALIDATION) { null }
            }

            paged1.get(0) shouldBe "a"
            paged2.get(0) shouldBe "b"
        }

        it("creates new instance after invalidatePaged") {
            val key = "invalidate-test"

            val paged1 = client.getOrCreatePaged(key) {
                PagedImpl(listOf("first"), 1, 1, CacheStrategy.NO_REVALIDATION) { null }
            }

            client.invalidatePaged(key)

            val paged2 = client.getOrCreatePaged(key) {
                PagedImpl(listOf("second"), 1, 1, CacheStrategy.NO_REVALIDATION) { null }
            }

            paged1.get(0) shouldBe "first"
            paged2.get(0) shouldBe "second"
        }

        it("creates new instances after invalidateAllPaged") {
            val client2 = OpenRiroClient(mockk<OpenRiroAPI>(relaxed = true))

            val paged1 = client2.getOrCreatePaged("a") {
                PagedImpl(listOf("x"), 1, 1, CacheStrategy.NONE) { null }
            }

            val paged2 = client2.getOrCreatePaged("b") {
                PagedImpl(listOf("y"), 1, 1, CacheStrategy.NONE) { null }
            }

            client2.invalidateAllPaged()

            val paged3 = client2.getOrCreatePaged("a") {
                PagedImpl(listOf("x2"), 1, 1, CacheStrategy.NO_REVALIDATION) { null }
            }

            val paged4 = client2.getOrCreatePaged("b") {
                PagedImpl(listOf("y2"), 1, 1, CacheStrategy.NO_REVALIDATION) { null }
            }

            paged3.get(0) shouldBe "x2"
            paged4.get(0) shouldBe "y2"
        }

        it("handles concurrent getOrCreatePaged for the same key") {
            val client3 = OpenRiroClient(mockk<OpenRiroAPI>(relaxed = true))
            val factoryCount = AtomicInteger(0)
            val key = "concurrent-key"

            val results = (0..<10).map {
                async {
                    client3.getOrCreatePaged(key) {
                        factoryCount.incrementAndGet()
                        delay(10)
                        PagedImpl(listOf("concurrent"), 1, 1, CacheStrategy.NONE) { null }
                    }
                }
            }.awaitAll()

            results.forEach { it shouldBeSameInstanceAs results[0] }
            factoryCount.get() shouldBe 1
        }

        it("sustains high-throughput cache hits under concurrency") {
            val client4 = OpenRiroClient(mockk<OpenRiroAPI>(relaxed = true))
            val factoryCount = AtomicInteger(0)
            val key = "throughput-key"

            val baseline = client4.getOrCreatePaged(key) {
                factoryCount.incrementAndGet()
                PagedImpl(listOf("hot"), 1, 1, CacheStrategy.NO_REVALIDATION) {
                    delay(100)
                    null
                }
            }

            withTimeout(5_000) {
                (0 until 50).map {
                    async {
                        repeat(2000) {
                            val cached = client4.getOrCreatePaged(key) {
                                factoryCount.incrementAndGet()
                                PagedImpl(listOf("cold"), 1, 1, CacheStrategy.NO_REVALIDATION) {
                                    delay(100)
                                    null
                                }
                            }
                            cached shouldBeSameInstanceAs baseline
                        }
                    }
                }.awaitAll()
            }

            factoryCount.get() shouldBe 1
        }
    }
})
