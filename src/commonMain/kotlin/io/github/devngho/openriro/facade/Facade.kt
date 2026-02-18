package io.github.devngho.openriro.facade

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.BoardKindMismatchException
import io.github.devngho.openriro.common.Cate
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.Menu
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.endpoints.Board
import io.github.devngho.openriro.endpoints.BoardItem
import io.github.devngho.openriro.endpoints.BoardMsg
import io.github.devngho.openriro.endpoints.BoardMsgItem
import io.github.devngho.openriro.endpoints.MenuList
import io.github.devngho.openriro.endpoints.Portfolio
import io.github.devngho.openriro.endpoints.PortfolioItem
import io.github.devngho.openriro.endpoints.PortfolioList
import io.github.devngho.openriro.endpoints.Score
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmName

@OptIn(InternalApi::class)
class OpenRiroClient(
    @property:InternalApi
    val api: OpenRiroAPI,
    val boardCacheStrategy: CacheStrategy = CacheStrategy.BOARD,
    val boardMsgCacheStrategy: CacheStrategy = CacheStrategy.BOARD_MSG,
    val portfolioCacheStrategy: CacheStrategy = CacheStrategy.PORTFOLIO,
    val portfolioListCacheStrategy: CacheStrategy = CacheStrategy.PORTFOLIO_LIST
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

    private var menuCache: Result<List<WithClient<Menu>>>? = null
    private val materializedMenuCache: MutableMap<DBId, WithClient<Menu.Board>> = mutableMapOf()
    private val menuMutex = Mutex()
    private val materializedMenuMutex = Mutex()
    private val pagedCache = mutableMapOf<Any, Paged<*>>()
    private val pagedMutex = Mutex()

    suspend fun menu(): Result<List<WithClient<Menu>>> =
        menuCache ?: menuMutex.withLock {
            menuCache ?: MenuList.execute(api, Unit).map { it.map { m -> WithClient(m) } }.also { menuCache = it }
        }

    suspend fun invalidateMenu() {
        menuMutex.withLock {
            menuCache = null
        }
        materializedMenuMutex.withLock {
            materializedMenuCache.clear()
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal suspend fun <T> getOrCreatePaged(key: Any, factory: suspend () -> Paged<T>): Paged<T> {
        pagedCache[key]?.let { return it as Paged<T> }
        return pagedMutex.withLock {
            pagedCache[key] as? Paged<T> ?: factory().also { pagedCache[key] = it }
        }
    }

    fun invalidatePaged(key: Any) {
        pagedCache.remove(key)
    }

    fun invalidateAllPaged() {
        pagedCache.clear()
    }

    suspend fun labeled(label: String): Result<WithClient<Menu>> =
        menu().map { it.first { m -> m.value.label == label } }

    @JvmName("labeledTyped")
    suspend inline fun <reified T : Menu> labeled(label: String): Result<WithClient<T>> = when (T::class) {
        Menu.Board.Normal::class,
        Menu.Board.Score::class -> {
            menu().map { list -> WithClient(list.first { it.value is Menu.Board && it.value.label == label }.value as Menu.Board) }.mapCatching {
                val materialized = it.materialize().getOrThrow()

                if (materialized.value is T) {
                    @Suppress("UNCHECKED_CAST")
                    materialized as WithClient<T>
                } else {
                    throw IllegalArgumentException("Menu with label \"$label\" is not of type ${T::class}.")
                }
            }
        }
        else -> menu().map { list -> WithClient(list.first { it.value is T && it.value.label == label }.value as T) }
    }

    suspend fun of(dbId: DBId): Result<WithClient<Menu>> =
        menu().map { it.first { m -> m.value.dbId == dbId } }

    @JvmName("ofTyped")
    suspend inline fun <reified T : Menu> of(dbId: DBId): Result<WithClient<T>> = when (T::class) {
        Menu.Board.Normal::class,
        Menu.Board.Score::class -> {
            menu().map { list -> WithClient(list.first { it.value is Menu.Board && it.value.dbId == dbId }.value as Menu.Board) }.mapCatching {
                val materialized = it.materialize().getOrThrow()

                if (materialized.value is T) {
                    @Suppress("UNCHECKED_CAST")
                    materialized as WithClient<T>
                } else {
                    throw IllegalArgumentException("Menu with dbId \"${dbId.value}\" is not of type ${T::class}.")
                }
            }
        }
        else -> menu().map { list -> WithClient(list.first { it.value is T && it.value.dbId == dbId }.value as T) }
    }

    companion object {
        @JvmName("listMenu")
        @Suppress("UNCHECKED_CAST")
        suspend fun <T: Menu> WithClient<T>.list(cacheStrategy: CacheStrategy? = null):  Result<Paged<T>> = when (this.value) {
            is Menu.Board.Unknown -> (this as WithClient<Menu.Board.Unknown>).materialize().getOrThrow().list(cacheStrategy ?: this.client.boardCacheStrategy) as Result<Paged<T>>
            is Menu.Board.Score -> (this as WithClient<Menu.Board.Score>).list(cacheStrategy ?: this.client.boardCacheStrategy) as Result<Paged<T>>
            is Menu.Board.Normal -> (this as WithClient<Menu.Board.Normal>).list(cacheStrategy ?: this.client.boardCacheStrategy) as Result<Paged<T>>
            is Menu.Portfolio -> (this as WithClient<Menu.Portfolio>).list(cacheStrategy ?: this.client.portfolioCacheStrategy) as Result<Paged<T>>
            is Menu.BoardMsg -> (this as WithClient<Menu.BoardMsg>).list(cacheStrategy ?: this.client.boardMsgCacheStrategy) as Result<Paged<T>>
        }

        suspend fun <T: Menu.Board> WithClient<T>.materialize(): Result<WithClient<Menu.Board>> = runCatching {
            @Suppress("UNCHECKED_CAST")
            if (this.value is Menu.Board.Normal || this.value is Menu.Board.Score) return@runCatching this as WithClient<Menu.Board>

            // check menu cache
            @Suppress("UNCHECKED_CAST")
            this.client.materializedMenuCache[this.value.dbId]?.let { return@runCatching it }

            // try calling board
            val res = Board.execute(this.client.api, Board.BoardRequest(db = this.value.dbId))

            when (res.exceptionOrNull()) {
                null -> Menu.Board.Normal(label = this.value.label, dbId = this.value.dbId)
                is BoardKindMismatchException -> Menu.Board.Score(label = this.value.label, dbId = this.value.dbId)
                else -> throw res.exceptionOrNull()!!
            }.let {
                this.client.WithClient(it)
            }.also {
                this.client.materializedMenuMutex.withLock {
                    this.client.materializedMenuCache[this.value.dbId] = it
                }
            }
        }

        @JvmName("listBoardMsg")
        suspend fun WithClient<Menu.BoardMsg>.list(cacheStrategy: CacheStrategy = this.client.boardMsgCacheStrategy): Result<Paged<WithClient<BoardMsg.BoardMsgItem>>> = runCatching {
            this.client.getOrCreatePaged(this.value.dbId to "boardMsg") {
                val res = BoardMsg.execute(this.client.api, BoardMsg.BoardMsgRequest(db = this.value.dbId)).getOrThrow()

                val initialPage = res.list.map { this.client.WithClient(it) }

                PagedImpl(initialPage, res.totalCount, res.list.count(), cacheStrategy) { page ->
                    val r = BoardMsg.execute(this.client.api, BoardMsg.BoardMsgRequest(db = this.value.dbId, page = page + 1)).getOrThrow()
                    PagedImpl.FetchResult(r.list.map { this.client.WithClient(it) }, r.totalCount)
                }
            }
        }

        @JvmName("listBoard")
        suspend fun WithClient<Menu.Board.Normal>.list(cacheStrategy: CacheStrategy = this.client.boardCacheStrategy): Result<Paged<WithClient<Board.BoardItem>>> = runCatching {
            this.client.getOrCreatePaged(this.value.dbId to "board") {
                val res = Board.execute(this.client.api, Board.BoardRequest(db = this.value.dbId)).getOrThrow()

                val initialPage = res.list.map { this.client.WithClient(it) }

                PagedImpl(initialPage, res.totalCount, res.list.count(), cacheStrategy) { page ->
                    val r = Board.execute(this.client.api, Board.BoardRequest(db = this.value.dbId, page = page + 1)).getOrThrow()
                    PagedImpl.FetchResult(r.list.map { this.client.WithClient(it) }, r.totalCount)
                }
            }
        }

        @JvmName("listScore")
        suspend fun WithClient<Menu.Board.Score>.list(cacheStrategy: CacheStrategy = this.client.boardCacheStrategy): Result<Paged<WithClient<Score.ScoreOptions>>> = runCatching {
            this.client.getOrCreatePaged(this.value.dbId to "board") {
                val res = Score.execute(this.client.api, Score.ScoreRequest(db = this.value.dbId)).getOrThrow()

                val initialPage = res.scoreOptions.map { this.client.WithClient(it) }

                PagedImpl(initialPage, initialPage.count(), initialPage.count(), cacheStrategy) { page ->
                    if (page == 0) {
                        val r = Score.execute(this.client.api, Score.ScoreRequest(db = this.value.dbId)).getOrThrow()
                        PagedImpl.FetchResult(r.scoreOptions.map { this.client.WithClient(it) }, r.scoreOptions.count())
                    } else {
                        PagedImpl.FetchResult(emptyList(), res.scoreOptions.count())
                    }
                }
            }
        }

        @JvmName("listPortfolio")
        suspend fun WithClient<Menu.Portfolio>.list(cacheStrategy: CacheStrategy = this.client.portfolioCacheStrategy): Result<Paged<WithClient<Portfolio.PortfolioItem>>> = runCatching {
            this.client.getOrCreatePaged(this.value.dbId to "portfolio") {
                val res = Portfolio.execute(this.client.api, Portfolio.PortfolioRequest(db = this.value.dbId)).getOrThrow()

                val initialPage = res.list.map { this.client.WithClient(it) }

                PagedImpl(initialPage, res.totalCount, res.list.count(), cacheStrategy) { page ->
                    val r = Portfolio.execute(this.client.api, Portfolio.PortfolioRequest(db = this.value.dbId, page = page + 1)).getOrThrow()
                    PagedImpl.FetchResult(r.list.map { this.client.WithClient(it) }, r.totalCount)
                }
            }
        }

        @JvmName("listPortfolioList")
        suspend fun WithClient<Portfolio.PortfolioItem>.list(cacheStrategy: CacheStrategy = this.client.portfolioListCacheStrategy): Result<Paged<WithClient<PortfolioList.PortfolioListItem>>> = runCatching {
            this.client.getOrCreatePaged(Triple(this.value.dbId, this.value.cate, "portfolioList")) {
                val res = PortfolioList.execute(this.client.api, PortfolioList.PortfolioListRequest(db = this.value.dbId, cate = this.value.cate)).getOrThrow()

                val initialPage = res.list.map { this.client.WithClient(it) }

                PagedImpl(initialPage, res.totalCount, res.list.count(), cacheStrategy) { page ->
                    val r = PortfolioList.execute(this.client.api, PortfolioList.PortfolioListRequest(db = this.value.dbId, page = page + 1, cate = this.value.cate)).getOrThrow()
                    PagedImpl.FetchResult(r.list.map { this.client.WithClient(it) }, r.totalCount)
                }
            }
        }

        @JvmName("getBoard")
        suspend fun WithClient<Menu.Board.Normal>.get(uid: Uid) = runCatching {
            BoardItem.execute(this.client.api, BoardItem.BoardItemRequest(db = this.value.dbId, uid = uid)).getOrThrow()
        }

        @JvmName("getScore")
        suspend fun WithClient<Menu.Board.Score>.get(uid: Uid) = runCatching {
            Score.execute(this.client.api, Score.ScoreRequest(db = this.value.dbId, uid = uid)).getOrThrow()
        }

        @JvmName("getPortfolioItem")
        suspend fun WithClient<Menu.Portfolio>.get(cate: Cate, uid: Uid) = runCatching {
            PortfolioItem.execute(this.client.api, PortfolioItem.PortfolioItemRequest(db = this.value.dbId, cate = cate, uid = uid)).getOrThrow()
        }

        @JvmName("getPortfolio")
        suspend fun WithClient<Menu.Portfolio>.get(cate: Cate) = runCatching {
            PortfolioList.execute(this.client.api, PortfolioList.PortfolioListRequest(db = this.value.dbId, cate = cate)).getOrThrow()
        }

        @JvmName("getBoardMsg")
        suspend fun WithClient<Menu.BoardMsg>.get(uid: Uid) = runCatching {
            BoardMsgItem.execute(this.client.api, BoardMsgItem.BoardMsgItemRequest(db = this.value.dbId, uid = uid)).getOrThrow()
        }

        @JvmName("getBoardItem")
        suspend fun WithClient<Board.BoardItem>.get(): Result<BoardItem.BoardItemResponse> = runCatching {
            BoardItem.execute(this.client.api, BoardItem.BoardItemRequest(db = this.value.dbId, uid = this.value.uid)).getOrThrow()
        }

        @JvmName("getBoardMsgItem")
        suspend fun WithClient<BoardMsg.BoardMsgItem>.get(): Result<BoardMsgItem.BoardMsgItemResponse> = runCatching {
            BoardMsgItem.execute(this.client.api, BoardMsgItem.BoardMsgItemRequest(db = this.value.dbId, uid = this.value.uid.getOrThrow())).getOrThrow()
        }

        @JvmName("getPortfolioItem")
        suspend fun WithClient<PortfolioList.PortfolioListItem>.get(): Result<PortfolioItem.PortfolioItemResponse> = runCatching {
            PortfolioItem.execute(this.client.api, PortfolioItem.PortfolioItemRequest(db = this.value.dbId, cate = this.value.cate, uid = this.value.uid)).getOrThrow()
        }

        @JvmName("getScoreOption")
        suspend fun WithClient<Score.ScoreOptions>.get(): Result<Score.ScoreResponse> = runCatching {
            Score.execute(this.client.api, Score.ScoreRequest(db = this.value.dbId, uid = this.value.uid)).getOrThrow()
        }
    }
}