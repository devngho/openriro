package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroClient
import io.github.devngho.openriro.common.RequestFailedException
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

/**
 * /portfolio.php?action=list에 대응합니다.
 */
class PortfolioList: Request<PortfolioList.PortfolioListRequest, PortfolioList.PortfolioListResponse> {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val format = LocalDateTime.Format {
        byUnicodePattern("yyyy-MM-dd HH:mm:ss")
    }

    data class PortfolioListRequest(
        val db: Int,
        val page: Int,
        val cate: Int
    )

    data class PortfolioListResponse(
        val totalCount: Int,
        val page: Int,
        val list: List<PortfolioListItem>
    )

    data class PortfolioListItem(
        val id: String,
        val isPrivate: Boolean,
        val title: String,
        val uid: Int,
        /**
         * 본인이 제출했으면 true, 아니면 false
         */
        val isSubmitted: Boolean,
        val hasAttachments: Boolean,
        val author: String,
        val summitedAt: LocalDateTime,
        val lastModifiedAt: LocalDateTime,
        val reads: Int
    )

    override suspend fun execute(client: OpenRiroClient, request: PortfolioListRequest): Result<PortfolioListResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/portfolio.php?action=list&db=${request.db}&cate=${request.cate}")
            .also { client.auth(it) }.bodyAsText()

        if (page.startsWith("<script>")) {
            val errorMsg = page.substringAfter("alert(\"").substringBefore("\");")

            throw RequestFailedException(errorMsg)
        }

        val total: Int
        val items = mutableListOf<PortfolioListItem>()

        html(page) {
            total = selectFirst(".paging_total > .number > span")!!.text().toInt()

            select(".rd_board > table").forEach {
                it.select("tr").drop(1).forEach { tr ->
                    val tds = tr.select("td")
                    if (tds.size != 6) return@forEach

                    val dateTooltip = tds[4].selectFirst("span")?.attr("data-tooltip")?.removeSuffix(")")?.split(" ", "\n") ?: return@forEach

                    items += PortfolioListItem(
                        id = tds[0].text(),
                        title = tds[1].text(),
                        isSubmitted = tds[1].select(".my").isNotEmpty(),
                        isPrivate = tds[1].select(".lock_black").isNotEmpty(),
                        author = tds[3].select("span").attr("data-tooltip"),
                        hasAttachments = tds[2].select("span").isNotEmpty(),
                        summitedAt = LocalDateTime.parse("${dateTooltip[0]} ${dateTooltip[1]}", format),
                        lastModifiedAt = LocalDateTime.parse("${dateTooltip[4]} ${dateTooltip[5]}", format),
                        reads = tds[5].text().toIntOrNull() ?: 0,
                        uid = tds[1].selectFirst("a")?.attr("href")?.substringAfter("bL(")?.split(",")[1]?.toIntOrNull() ?: 0
                    )
                }
            }
        }

        PortfolioListResponse(
            totalCount = total,
            page = request.page,
            list = items
        )
    }
}
