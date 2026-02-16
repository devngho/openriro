package io.github.devngho.openriro.facade

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.Menu
import io.github.devngho.openriro.endpoints.Board
import io.github.devngho.openriro.endpoints.BoardMsg
import io.github.devngho.openriro.endpoints.MenuList
import io.github.devngho.openriro.endpoints.Portfolio
import io.github.devngho.openriro.endpoints.PortfolioList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmName

@OptIn(InternalApi::class)
class OpenRiroClient(
    @property:InternalApi
    val api: OpenRiroAPI
) {
    inner class WithClient<T>(val value: T) {
        val client = this@OpenRiroClient
        operator fun component1() = value
        operator fun component2() = client

        override fun toString(): String {
            return "WithClient(value=$value)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WithClient<*>) return false

            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int {
            return value?.hashCode() ?: 0
        }
    }

    inner class Paged<T>(val pages: Array<List<WithClient<T>>?>, val pageSize: Int, private val fetchPage: suspend (page: Int) -> List<T>?) {
        val client = this@OpenRiroClient
        private val pageMutexes = List(pages.size) { Mutex() }
        private val fetchPageWithClient = suspend { page: Int ->
            fetchPage(page)?.map { WithClient(it) }
        }

        suspend fun page(page: Int) = pages.getOrNull(page)
            ?: pageMutexes.getOrNull(page)
                ?.withLock { pages.getOrNull(page) ?: fetchPageWithClient(page).also { if (pages.size > page) pages[page] = it } }

        suspend fun page(range: IntRange) = coroutineScope {
            range.map { page ->
                async { page(page) }
            }.awaitAll()
        }.let {
            if (it.any { a -> a == null }) null else it.filterNotNull().flatten().map { v -> v.value }
        }

        suspend fun get(index: Int): WithClient<T>? {
            val page = index / pageSize
            val pageIndex = index % pageSize
            return page(page)?.getOrNull(pageIndex)
        }

        suspend fun get(range: IntRange): List<WithClient<T>?> = coroutineScope {
            range.map { index ->
                async { get(index) }
            }.awaitAll()
        }

        suspend fun preloadPage(page: Int) {
            if (pages.getOrNull(page) == null) {
                pageMutexes.getOrNull(page)?.withLock {
                    if (pages.getOrNull(page) == null) {
                        fetchPageWithClient(page)?.let { if (pages.size > page) pages[page] = it }
                    }
                }
            }
        }

        suspend fun preloadPage(range: IntRange) = coroutineScope {
            range.map { page ->
                async { preloadPage(page) }
            }.awaitAll()
        }

        suspend fun preload(index: Int) = preloadPage(index / pageSize)

        suspend fun preload(range: IntRange) = coroutineScope {
            range.map { index ->
                async { preload(index) }
            }.awaitAll()
        }

        fun asFlow() = channelFlow {
            for (page in pages.indices) {
                if (page + 1 < pages.count()) launch { preload(page+1) } // it slightly improves the performance (about 10%)

                page(page)?.forEach { send(it) }
            }
        }
    }

    private var menuCache: Result<List<WithClient<Menu>>>? = null
    private val menuMutex = Mutex()

    suspend fun menu(): Result<List<WithClient<Menu>>> =
        menuCache ?: menuMutex.withLock {
            menuCache ?: MenuList.execute(api, Unit).map { it.map { m -> WithClient(m) } }.also { menuCache = it }
        }

    fun invalidateMenu() {
        menuCache = null
    }

    suspend fun labeled(label: String): Result<WithClient<Menu>> =
        menu().map { it.first { m -> m.value.label == label } }

    @JvmName("labeledTyped")
    suspend inline fun <reified T : Menu> labeled(label: String): Result<WithClient<T>> =
        menu().map { list -> list.first { it.value is T && it.value.label == label }.let { WithClient(it.value as T) } }

    companion object {
        private fun calculateTotalPages(totalCount: Int, pageSize: Int) =
            totalCount / pageSize + if (totalCount % pageSize == 0) 0 else 1

        @JvmName("listMenu")
        @Suppress("UNCHECKED_CAST")
        suspend fun <T: Menu> WithClient<T>.list():  Result<Paged<T>> = when (this.value) {
            is Menu.Board -> (this as WithClient<Menu.Board>).list() as Result<Paged<T>>
            is Menu.Portfolio -> (this as WithClient<Menu.Portfolio>).list() as Result<Paged<T>>
            is Menu.BoardMsg -> (this as WithClient<Menu.BoardMsg>).list() as Result<Paged<T>>
        }

        @JvmName("listBoardMsg")
        suspend fun WithClient<Menu.BoardMsg>.list(): Result<Paged<BoardMsg.BoardMsgItem>> = runCatching {
            val res = BoardMsg.execute(this.client.api, BoardMsg.BoardMsgRequest(db = this.value.dbId)).getOrThrow()

            val pages = Array<List<WithClient<BoardMsg.BoardMsgItem>>?>(calculateTotalPages(res.totalCount, this.value.pageSize)) { null }

            pages[0] = res.list.map { this.client.WithClient(it) }

            this.client.Paged(pages, this.value.pageSize) { page ->
                BoardMsg.execute(this.client.api, BoardMsg.BoardMsgRequest(db = this.value.dbId, page = page + 1)).getOrThrow().list
            }
        }

        @JvmName("listBoard")
        suspend fun WithClient<Menu.Board>.list(): Result<Paged<Board.BoardItem>> = runCatching {
            val res = Board.execute(this.client.api, Board.BoardRequest(db = this.value.dbId)).getOrThrow()

            val pages = Array<List<WithClient<Board.BoardItem>>?>(calculateTotalPages(res.totalCount, this.value.pageSize)) { null }

            pages[0] = res.list.map { this.client.WithClient(it) }

            this.client.Paged(pages, this.value.pageSize) { page ->
                Board.execute(this.client.api, Board.BoardRequest(db = this.value.dbId, page = page + 1)).getOrThrow().list
            }
        }

        @JvmName("listPortfolio")
        suspend fun WithClient<Menu.Portfolio>.list(): Result<Paged<Portfolio.PortfolioItem>> = runCatching {
            val res = Portfolio.execute(this.client.api, Portfolio.PortfolioRequest(db = this.value.dbId)).getOrThrow()

            val pages = Array<List<WithClient<Portfolio.PortfolioItem>>?>(calculateTotalPages(res.totalCount, this.value.pageSize)) { null }

            pages[0] = res.list.map { this.client.WithClient(it) }

            this.client.Paged(pages, this.value.pageSize) { page ->
                Portfolio.execute(this.client.api, Portfolio.PortfolioRequest(db = this.value.dbId, page = page + 1))
                    .getOrThrow().list
            }
        }

        @JvmName("listPortfolioList")
        suspend fun WithClient<Portfolio.PortfolioItem>.list(): Result<Paged<PortfolioList.PortfolioListItem>> = runCatching {
            val res = PortfolioList.execute(this.client.api, PortfolioList.PortfolioListRequest(db = this.value.dbId, cate = this.value.cate)).getOrThrow()

            val pages = Array<List<WithClient<PortfolioList.PortfolioListItem>>?>(calculateTotalPages(res.totalCount, PortfolioList.PAGE_SIZE)) { null }

            pages[0] = res.list.map { this.client.WithClient(it) }

            this.client.Paged(pages, PortfolioList.PAGE_SIZE) { page ->
                PortfolioList.execute(this.client.api, PortfolioList.PortfolioListRequest(db = this.value.dbId, page = page + 1, cate = this.value.cate))
                    .getOrThrow().list
            }
        }
    }
}