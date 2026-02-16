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
 * /board_msg.php/action=view에 대응합니다.
 */
object BoardMsgItem: Request<BoardMsgItem.BoardMsgItemRequest, BoardMsgItem.BoardMsgItemResponse> {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val format = LocalDateTime.Format {
        byUnicodePattern("yyyy-MM-dd HH:mm:ss")
    }

    data class BoardMsgItemRequest(
        val db: DBId,
        val uid: Uid
    )

    data class BoardMsgItemResponse(
        val title: String,
        val target: BoardMsgItemTarget,
        val attachments: List<Attachment>,
        val author: String,
        val reads: Int,
        val writtenAt: LocalDateTime,
        /** 공지사항 본문 HTML */
        val body: String,
        /** 입력 양식 (설문조사 등). 양식이 없는 게시글은 null */
        val form: BoardMsgForm? = null
    )

    sealed interface BoardMsgItemTarget {
        data object All: BoardMsgItemTarget {
            override val kind: String = "전체"
        }

        data class Students(val classes: List<String>): BoardMsgItemTarget {
            override val kind: String = "재학생"
        }

        val kind: String
    }

    data class BoardMsgForm(
        val period: ClosedRange<LocalDateTime>,
        val applicants: Int,
        val name: String,
        val id: String,
        val questions: List<BoardMsgFormQuestion>
    )

    data class BoardMsgFormQuestion(
        val number: Int,
        val label: String,
        val required: Boolean,
        val answer: BoardMsgFormAnswer
    )

    sealed interface BoardMsgFormAnswer {
        data class Radio(val options: List<BoardMsgFormOption>): BoardMsgFormAnswer
        data class Checkbox(val options: List<BoardMsgFormOption>): BoardMsgFormAnswer
        data class Text(val value: String): BoardMsgFormAnswer
    }

    data class BoardMsgFormOption(
        val value: String,
        val selected: Boolean
    )

    override suspend fun execute(client: OpenRiroAPI, request: BoardMsgItemRequest): Result<BoardMsgItemResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/board_msg.php?action=view&db=${request.db.value}&uid=${request.uid.value}")
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
            val reads = meta[1].text().removePrefix("조회").removeSuffix("|").trim().toInt()
            val author = meta[3].text().removePrefix("글쓴이").removeSuffix("|").trim()

            val target = selectFirst(".view_table > tbody:nth-child(1) > tr:nth-child(2) > td:nth-child(2)")!!.text().let { when(it) {
                "전체" -> BoardMsgItemTarget.All
                "재학생" -> BoardMsgItemTarget.Students(
                    selectFirst(".view_table > tbody:nth-child(1) > tr:nth-child(3) > td:nth-child(2)")!!.text().split(",").map { s -> s.trim() }
                )
                else -> throw Exception("알 수 없는 대상: $it")
            } }

            val body = selectFirst(".ck-content")!!.html()

            val form = selectFirst(".view_input")?.let { formEl ->
                val metaTds = formEl.select("table > tbody > tr:nth-child(1) > td")

                val periodParts = metaTds[0].text().trim().split(" ~ ")
                fun parsePeriod(raw: String) = LocalDateTime.parse("0000-${raw.replace("시", ":").replace("분", ":").replace("초", "")}", format)
                val period = parsePeriod(periodParts[0])..parsePeriod(periodParts[1])
                val count = metaTds[1].text().replace("명", "").trim().toInt()

                val applicantTd = formEl.select("table > tbody > tr")[1].selectFirst("td")!!.text().trim()
                val name = applicantTd.substringAfter("이름 :").substringBefore("학번").trim()
                val id = applicantTd.substringAfter("학번 :").trim()

                val questionDivs = formEl.select("td > div").filter { it.selectFirst("b")?.text()?.toIntOrNull() != null }

                val questions = questionDivs.mapIndexed { index, questionDiv ->
                    val labelEl = questionDiv.selectFirst("div > div > div")
                    val label = labelEl?.text()?.trim() ?: ""
                    val required = labelEl?.selectFirst("span")?.text()?.contains("＊") == true

                    val checkboxes = questionDiv.select("input[type=checkbox]")
                    val radios = questionDiv.select("input[type=radio]")
                    val textarea = questionDiv.selectFirst("textarea")

                    val answer = when {
                        textarea != null -> BoardMsgFormAnswer.Text(value = textarea.text())
                        checkboxes.isNotEmpty() -> BoardMsgFormAnswer.Checkbox(options = checkboxes.map { input ->
                            BoardMsgFormOption(value = input.attr("value"), selected = input.hasAttr("checked"))
                        })
                        else -> BoardMsgFormAnswer.Radio(options = radios.map { input ->
                            BoardMsgFormOption(value = input.attr("value"), selected = input.hasAttr("checked"))
                        })
                    }

                    BoardMsgFormQuestion(
                        number = index + 1,
                        label = label,
                        required = required,
                        answer = answer
                    )
                }

                BoardMsgForm(
                    period = period,
                    applicants = count,
                    name = name,
                    id = id,
                    questions = questions
                )
            }

            BoardMsgItemResponse(
                title = title,
                author = author,
                reads = reads,
                writtenAt = writtenAt,
                target = target,
                attachments = select("div.flex_box_contents").map {
                    val a = it.selectFirst("a")!!

                    val name = a.childNodes.last().nodeValue().trim()
                    val path = a.attr("href").substringAfter("&downfile=").substringBefore("&uid=")

                    Attachment(
                        name = name,
                        file = Attachment.File.BoardMsg(
                            db = request.db,
                            uid = request.uid,
                            filePath = path
                        )
                    )
                },
                body = body,
                form = form
            )
        }
    }
}