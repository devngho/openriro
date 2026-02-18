package io.github.devngho.openriro.facade

import kotlin.time.Duration

/**
 * Paged 캐시 정책을 정의합니다.
 *
 * @property softLimit 이 시간이 경과하면, 첫 페이지를 probe하여 내용물이 변경되었는지 확인합니다.
 *                     변경이 감지되면 전체 캐시를 invalidate합니다.
 *                     [Duration.ZERO]이면 매번 probe합니다.
 * @property hardLimit 이 시간이 경과하면, 무조건 전체 캐시를 invalidate합니다.
 *                     [Duration.ZERO]이면 매번 invalidate합니다.
 *                     [Duration.INFINITE]이면 hard invalidation을 하지 않습니다.
 */
data class CacheStrategy(
    val softLimit: Duration,
    val hardLimit: Duration
) {
    init {
        require(hardLimit >= softLimit) { "hardLimit must be >= softLimit" }
    }

    companion object {
        /**
         * 캐시를 사용하지 않습니다. 매번 서버에서 데이터를 가져옵니다.
         */
        val NONE = CacheStrategy(Duration.ZERO, Duration.ZERO)

        /**
         * 캐시를 무효화하지 않습니다.
         */
        val NO_REVALIDATION = CacheStrategy(Duration.INFINITE, Duration.INFINITE)

        /**
         * 게시판(공지사항 등) 기본 캐시 전략입니다.
         */
        val BOARD = CacheStrategy(Duration.parse("1h"), Duration.parse("24h"))

        /**
         * 가정통신문 기본 캐시 전략입니다.
         */
        val BOARD_MSG = CacheStrategy(Duration.parse("1h"), Duration.parse("24h"))

        /**
         * 포트폴리오(과제 카테고리) 기본 캐시 전략입니다.
         */
        val PORTFOLIO = CacheStrategy(Duration.parse("1h"), Duration.parse("24h"))

        /**
         * 포트폴리오 제출 목록 기본 캐시 전략입니다.
         */
        val PORTFOLIO_LIST = CacheStrategy(Duration.parse("30m"), Duration.parse("12h"))
    }
}
