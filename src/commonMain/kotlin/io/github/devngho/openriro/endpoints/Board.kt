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
 * /board.php에 대응합니다.
 */
object Board: Request<Board.BoardRequest, Board.BoardResponse> {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val format = LocalDate.Format {
        byUnicodePattern("yyyy.MM.dd")
    }

    data class BoardRequest(
        val db: DBId,
        val page: Int = 1
    )

    data class BoardResponse(
        val totalCount: Int,
        val page: Int,
        val list: List<BoardItem>
    )

    data class BoardItem(
        val dbId: DBId,
        val id: String,
        val uid: Uid,
        val kind: BoardMsgKind,
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

    override suspend fun execute(client: OpenRiroAPI, request: BoardRequest): Result<BoardResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/board.php?db=${request.db.value}&page=${request.page}")
            .also { client.auth(it) }.bodyAsText()

        val total: Int
        val items = mutableListOf<BoardItem>()

        html(page) {
            total = selectFirst(".paging_total > .number > span")!!.text().toInt()

            select(".rd_board > table").forEach {
                it.select("tr").drop(1).forEach { tr ->
                    val tds = tr.select("td")
                    if (tds.size != 7) return@forEach

                    items += BoardItem(
                        dbId = request.db,
                        id = tds[0].text(),
                        uid = Uid(tds[2].selectFirst("a")!!.attr("href").substringAfter("bL(").substringBefore(")").split(",")[1].toInt()),
                        kind = BoardMsgKind.entries.find { it.value == tds[1].text() } ?: throw Exception("알 수 없는 종류: ${tds[1].text()}"),
                        title = tds[2].text(),
                        hasAttachments = tds[3].text() != "-",
                        author = tds[4].text(),
                        reads = tds[5].text().toIntOrNull() ?: 0,
                        date = LocalDate.parse(tds[6].text(), format)
                    )
                }
            }
        }

        BoardResponse(
            totalCount = total,
            page = request.page,
            list = items
        )
    }
}
