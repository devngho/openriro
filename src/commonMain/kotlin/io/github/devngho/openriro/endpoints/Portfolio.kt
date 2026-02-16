package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.Cate
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.LocalDateTime

/**
 * /portfolio.php에 대응합니다.
 */
object Portfolio: Request<Portfolio.PortfolioRequest, Portfolio.PortfolioResponse> {
    data class PortfolioRequest(
        val db: DBId,
        val page: Int = 1,
        /**
         * 1이면 전체 연도를 가져오고, 이외의 경우 특정 년도만 가져옵니다.
         */
        val year: Int = 1,
    )

    data class PortfolioResponse(
        val totalCount: Int,
        val page: Int,
        val list: List<PortfolioItem>
    )

    data class PortfolioItem(
        val dbId: DBId,
        val id: String,
        val kind: PortfolioKind,
        val title: String,
        val cate: Cate,
        /**
         * 본인이 제출했으면 true, 아니면 false
         */
        val isSubmitted: Boolean,
        val hasAttachments: Boolean,
        val author: String,
        val summitCount: Int,
        /**
         * 제출 가능 시간 범위. 연도를 알 수 없으므로 0으로 설정됩니다.
         */
        val timeRange: ClosedRange<LocalDateTime>,
    )

    @Suppress("NonAsciiCharacters", "Unused")
    enum class PortfolioKind(val value: String) {
        투표("투표"),
        대기("대기"),
        제출("제출"),
        마감("마감"),
    }

    override suspend fun execute(client: OpenRiroAPI, request: PortfolioRequest): Result<PortfolioResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/portfolio.php?db=${request.db.value}&page=${request.page}&t_year=${request.year}")
            .also { client.auth(it) }.bodyAsText()

        val total: Int
        val items = mutableListOf<PortfolioItem>()

        html(page) {
            total = selectFirst(".paging_total > .number > span")!!.text().toInt()

            select(".rd_board > table").forEach {
                it.select("tr").drop(1).forEach { tr ->
                    val tds = tr.select("td")
                    if (tds.size != 9) return@forEach

                    items += PortfolioItem(
                        dbId = request.db,
                        id = tds[0].text(),
                        kind = PortfolioKind.entries.find { f -> f.value == tds[1].text() } ?: throw Exception("알 수 없는 종류: ${tds[1].text()}"),
                        title = tds[2].text(),
                        isSubmitted = tds[3].select("span").isNotEmpty(),
                        summitCount = tds[4].text().removeSuffix("명").toIntOrNull() ?: 0,
                        author = tds[5].text(),
                        hasAttachments = tds[6].text() != "-",
                        timeRange = run {
                            val times = tds[7].text().split(" ")
                            if (times.size != 4) throw Exception("시간 범위 형식이 올바르지 않습니다: ${tds[6].text()}")

                            val start = LocalDateTime.parse("0000-"+times[0] + "T" + times[1])
                            val end = LocalDateTime.parse("0000-"+times[2] + "T" + times[3])

                            start..end
                        },
                        cate = Cate(tds[2].select("a")[1].attr("href").substringAfter("cate=").toIntOrNull() ?: 0)
                    )
                }
            }
        }

        PortfolioResponse(
            totalCount = total,
            page = request.page,
            list = items
        )
    }
}
