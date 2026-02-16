package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

/**
 * /board_msg.php에 대응합니다.
 */
object BoardMsg: Request<BoardMsg.BoardMsgRequest, BoardMsg.BoardMsgResponse> {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val format = LocalDate.Format {
        byUnicodePattern("yyyy-MM-dd")
    }

    data class BoardMsgRequest(
        val db: DBId,
        val page: Int = 1
    )

    data class BoardMsgResponse(
        val totalCount: Int,
        val page: Int,
        val list: List<BoardMsgItem>
    )

    data class BoardMsgItem(
        val id: String,
        val uid: Uid,
        val kind: BoardMsgKind,
        val target: String,
        val title: String,
        val hasAttachments: Boolean,
        val author: String,
        val reads: Int,
        val date: LocalDate
    )

    @Suppress("NonAsciiCharacters", "Unused")
    enum class BoardMsgKind(val value: String) {
        알림("알림"),
        대기("대기"),
        제출("제출"),
        마감("마감"),
    }

    override suspend fun execute(client: OpenRiroAPI, request: BoardMsgRequest): Result<BoardMsgResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/board_msg.php?db=${request.db.value}&page=${request.page}")
            .also { client.auth(it) }.bodyAsText()

        val total: Int
        val items = mutableListOf<BoardMsgItem>()

        html(page) {
            total = selectFirst(".paging_total > .number > span")!!.text().toInt()

            select(".rd_board > table").forEach {
                it.select("tr").drop(1).forEach { tr ->
                    val tds = tr.select("td")
                    if (tds.size != 8) return@forEach

                    items += BoardMsgItem(
                        id = tds[0].text(),
                        uid = Uid(tds[3].selectFirst("a")!!.attr("href").substringAfter("bL(").substringBefore(")").split(",")[1].toInt()),
                        kind = BoardMsgKind.entries.find { f -> f.value == tds[1].text() } ?: throw Exception("알 수 없는 종류: ${tds[1].text()}"),
                        target = tds[2].text(),
                        title = tds[3].text(),
                        hasAttachments = tds[4].text() != "-",
                        author = tds[5].text(),
                        reads = tds[6].text().toIntOrNull() ?: 0,
                        date = LocalDate.parse(tds[7].text(), format)
                    )
                }
            }
        }

        BoardMsgResponse(
            totalCount = total,
            page = request.page,
            list = items
        )
    }
}