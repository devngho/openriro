package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.Attachment
import io.github.devngho.openriro.common.Attachment.File
import io.github.devngho.openriro.common.Cate
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
 * /portfolio.php?action=view에 대응합니다.
 */
object PortfolioItem: Request<PortfolioItem.PortfolioItemRequest, PortfolioItem.PortfolioItemResponse> {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val format = LocalDateTime.Format {
        byUnicodePattern("yyyy-MM-dd HH:mm:ss")
    }

    data class PortfolioItemRequest(
        val db: DBId,
        val cate: Cate,
        val uid: Uid
    )

    data class PortfolioItemResponse(
        val title: String,
        val author: String,
        /**
         * 본인이 제출했으면 true, 아니면 false
         */
        val isSubmitted: Boolean,
        val commentCount: Int,
        val letterCount: Int,
        val reads: Int,
        val summitedAt: LocalDateTime,
        val lastModifiedAt: LocalDateTime,
        /**
         * 제출 항목의 HTML 본문
         */
        val body: String,
        val attachments: List<Attachment>
    )

    override suspend fun execute(client: OpenRiroAPI, request: PortfolioItemRequest): Result<PortfolioItemResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/portfolio.php?action=view&db=${request.db.value}&cate=${request.cate.value}&uid=${request.uid.value}")
            .also { client.auth(it) }.bodyAsText()

        if (page.startsWith("<script>")) {
            val errorMsg = page.substringAfter("alert(\"").substringBefore("\");")

            throw RequestFailedException(errorMsg)
        }

        html(page) {
            val title = selectFirst(".tit")!!.text()
            val author = selectFirst(".who")!!.text()
            val isSubmitted = selectFirst(".icon_chk_my") != null

            val meta = selectFirst("ul.clearfix")!!.text().split(" ")
            val reads = meta[2].toInt()
            val letterCount = meta[5].removeSuffix("자").replace(",", "").toInt()
            val commentCount = meta[8].removeSuffix("건").toInt()

            val dateText = selectFirst(".date")!!.text().removeSuffix(")").split(" ", "\n")
            val summitedAt = LocalDateTime.parse("${dateText[0]} ${dateText[1]}", format)
            val lastModifiedAt = LocalDateTime.parse("${dateText[4]} ${dateText[5]}", format)

            val body = selectFirst("#rText")!!.html()

            val attachments = selectFirst(".attched_file")?.select(".btn_file")?.map {
                val spans = it.select("span")
                val name = spans[0].text()
                val lastModifiedAt = LocalDateTime.parse(spans[0].attr("data-tooltip").substringAfter("최종 수정 시간 : ").substringBefore(" / "), format)
                val size = spans[1].text().substringAfter("[").substringBefore("]")
                val file = it.attr("href").substringAfter("bL(").substringBefore(")").split(",")

                Attachment(
                    name = name,
                    file = File.Portfolio(
                        db = request.db,
                        cate = request.cate,
                        uid = Uid(file[1].toIntOrNull()!!),
                        fileNumber = file[2].toIntOrNull()!!,
                        fileCode = file[3].trim('\''),
                        size = size,
                        lastModifiedAt = lastModifiedAt,
                    )
                )
            } ?: emptyList()

            PortfolioItemResponse(
                title = title,
                author = author,
                isSubmitted = isSubmitted,
                reads = reads,
                letterCount = letterCount,
                commentCount = commentCount,
                summitedAt = summitedAt,
                lastModifiedAt = lastModifiedAt,
                body = body,
                attachments = attachments
            )
        }
    }
}
