package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.Attachment
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.RequestFailedException
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

/**
 * /board.php/action=view에 대응합니다.
 */
object BoardItem: Request<BoardItem.BoardItemRequest, BoardItem.BoardItemResponse> {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val format = LocalDateTime.Format {
        byUnicodePattern("yyyy.MM.dd '['HH:mm:ss']'") // why??
    }

    data class BoardItemRequest(
        val db: DBId,
        val uid: Uid
    )

    data class BoardItemResponse(
        val title: String,
        val attachments: List<Attachment>,
        val author: String,
        val reads: Int,
        val writtenAt: LocalDateTime,
        /** 공지사항 본문 HTML */
        val body: String,
    )

    override suspend fun execute(client: OpenRiroAPI, request: BoardItemRequest): Result<BoardItemResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/board.php?action=view&db=${request.db.value}&uid=${request.uid.value}")
            .also { client.auth(it) }.bodyAsText()

        if (page.startsWith("<script>")) {
            val errorMsg = page.substringAfter("alert(\"").substringBefore("\");")

            throw RequestFailedException(errorMsg)
        }

        html(page) {
            val header = select(".view_header > div")
            val title = header[0].text()

            val meta = header[1].childElementsList()
            val writtenAt = LocalDateTime.parse(meta[0].text().removePrefix("등록일").trim(), format)
            val reads = meta[1].text().removePrefix("조회").removeSuffix("|").trim().replace(",", "").toInt()
            val author = meta[2].text().removePrefix("글쓴이").removeSuffix("|").trim()

            val body = selectFirst(".ck-content")!!.html()

            BoardItemResponse(
                title = title,
                author = author,
                reads = reads,
                writtenAt = writtenAt,
                attachments = select("div.flex_box_contents").map {
                    val a = it.selectFirst("a")!!

                    val name = it.selectFirst("font")!!.attr("data-tooltip")
                    val size = it.select("span").last()!!.text().substringAfter("[").substringBefore("]")
                    val file = a.attr("href").substringAfter("bL(").substringBefore(")").split(",")

                    Attachment(
                        name = name,
                        file = Attachment.File.Board(
                            db = request.db,
                            uid = Uid(file[1].toInt()),
                            fileNumber = file[2].toInt(),
                            fileCode = file[3].trim('\''),
                            size = size
                        )
                    )
                },
                body = body
            )
        }
    }
}