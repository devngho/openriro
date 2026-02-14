package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroClient
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * /board.php에 대응합니다.
 */
class Board: Request<Board.BoardRequest, Board.BoardResponse> {
    data class BoardRequest(
        val db: String,
        val page: Int = 1
    )

    data class BoardResponse(
        val totalCount: Int,
        val page: Int,
        val list: List<BoardItem>
    )

    data class BoardItem(
        val id: String,
        val kind: BoardMsgKind,
        val title: String,
        val hasAttachments: Boolean,
        val author: String,
        val reads: Int,
        val date: String
    )

    @Suppress("NonAsciiCharacters", "Unused")
    enum class BoardMsgKind(val value: String) {
        알림("알림"),
        대기("대기"),
        제출("제출"),
        마감("마감"),
    }

    override suspend fun execute(client: OpenRiroClient, request: BoardRequest): Result<BoardResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/board.php?db=${request.db}&page=${request.page}")
            .also { client.auth(it) }.bodyAsText()

        val total: Int
        val items = mutableListOf<BoardItem>()

        html(page) {
            total = findFirst(".paging_total > .number > span").text.toInt()

            findFirst(".rd_board > table") {
                findAll("tr").drop(1).forEach { tr ->
                    val tds = tr.findAll("td")
                    if (tds.size != 7) return@forEach

                    items += BoardItem(
                        id = tds[0].text,
                        kind = BoardMsgKind.entries.find { it.value == tds[1].text } ?: throw Exception("알 수 없는 종류: ${tds[1].text}"),
                        title = tds[2].text,
                        hasAttachments = tds[3].text != "-",
                        author = tds[4].text,
                        reads = tds[5].text.toIntOrNull() ?: 0,
                        date = tds[6].text
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