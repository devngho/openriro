package io.github.devngho.openriro.endpoints

import com.fleeksoft.ksoup.nodes.Element
import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.client.json
import io.github.devngho.openriro.common.Attachment
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.RequestFailedException
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * /board_msg.php/action=view에 대응합니다.
 */
object BoardMsgItem : Request<BoardMsgItem.BoardMsgItemRequest, BoardMsgItem.BoardMsgItemResponse> {
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
        data object All : BoardMsgItemTarget {
            override val kind: String = "전체"
        }

        data class Students(val classes: List<String>) : BoardMsgItemTarget {
            override val kind: String = "재학생"
        }

        val kind: String
    }

    data class BoardMsgForm(
        val period: ClosedRange<LocalDateTime>,
        val applicants: Int,
        val name: String,
        val id: String,
        val questions: List<BoardMsgFormQuestion<*>>,
        val isSubmitEnabled: Boolean = false,
        val submit: suspend ((questions: List<BoardMsgFormQuestion<*>>, isSigned: Boolean) -> Result<Unit>),
        val delete: suspend () -> Result<Unit>
    ) {
        @OptIn(ExperimentalContracts::class)
        suspend fun submit(block: SubmitDSL.() -> Unit): Result<Unit> {
            contract {
                callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
            }
            if (!isSubmitEnabled) throw IllegalStateException("이 양식은 제출할 수 없습니다.")

            val dsl = SubmitDSL(this)
            dsl.block()
            return dsl.submit()
        }

        // create submit DSL
        data class SubmitDSL(
            val form: BoardMsgForm,
            var isSigned: Boolean = false,
            val answers: MutableList<BoardMsgFormQuestion<*>> = mutableListOf()
        ) {
            fun sign() {
                isSigned = true
            }

            @Suppress("UNCHECKED_CAST")
            inline fun <reified T : BoardMsgFormAnswer> option(idx: Int): BoardMsgFormQuestion<T> {
                // get question by idx
                val question = form.questions.getOrNull(idx) ?: throw IllegalArgumentException("존재하지 않는 질문 번호: $idx")

                if (question.answer !is T) throw IllegalArgumentException("질문 ${question.number}번은 ${T::class.simpleName} 질문이 아닙니다.")
                return question as BoardMsgFormQuestion<T>
            }

            fun BoardMsgFormQuestion<BoardMsgFormAnswer.Radio>.set(idx: Int) {
                if (idx < 0 || idx >= answer.options.size) throw IllegalArgumentException("질문 ${number}번의 선택지 번호는 0부터 ${answer.options.size - 1}까지입니다.")

                val newOptions = answer.options.mapIndexed { i, option -> option.copy(selected = i == idx) }
                val newAnswer = BoardMsgFormAnswer.Radio(options = newOptions)
                val newQuestion = copy(answer = newAnswer)

                answers.add(newQuestion)
            }

            fun BoardMsgFormQuestion<BoardMsgFormAnswer.Radio>.set(value: String) {
                val idx = answer.options.indexOfFirst { it.value == value }
                if (idx == -1) throw IllegalArgumentException("질문 ${number}번에 '${value}' 선택지는 존재하지 않습니다.")

                set(idx)
            }

            fun BoardMsgFormQuestion<BoardMsgFormAnswer.Checkbox>.set(vararg indexes: Int) {
                val newOptions = answer.options.mapIndexed { i, option -> option.copy(selected = indexes.contains(i)) }
                val newAnswer = BoardMsgFormAnswer.Checkbox(options = newOptions)
                val newQuestion = copy(answer = newAnswer)

                answers.add(newQuestion)
            }

            fun BoardMsgFormQuestion<BoardMsgFormAnswer.Checkbox>.set(vararg values: String) {
                val indexes =
                    answer.options.mapIndexedNotNull { i, option -> if (values.contains(option.value)) i else null }
                set(*indexes.toIntArray())
            }

            @JvmName("setText")
            fun BoardMsgFormQuestion<BoardMsgFormAnswer.Text>.set(value: String) {
                val newAnswer = BoardMsgFormAnswer.Text(value = value)
                val newQuestion = copy(answer = newAnswer)

                answers.add(newQuestion)
            }

            suspend fun submit() = form.submit(answers, isSigned)
        }
    }

    @Serializable
    data class BoardMsgFormResponse(
        val status: Boolean,
        val msg: String
    )

    data class BoardMsgFormQuestion<T : BoardMsgFormAnswer>(
        val number: Int,
        val label: String,
        val required: Boolean,
        val answer: T
    )

    sealed interface BoardMsgFormAnswer {
        data class Radio(val options: List<BoardMsgFormOption>) : BoardMsgFormAnswer
        data class Checkbox(val options: List<BoardMsgFormOption>) : BoardMsgFormAnswer
        data class Text(val value: String) : BoardMsgFormAnswer
    }

    data class BoardMsgFormOption(
        val value: String,
        val selected: Boolean
    )

    override suspend fun execute(client: OpenRiroAPI, request: BoardMsgItemRequest): Result<BoardMsgItemResponse> =
        client.retry {
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

                val target =
                    selectFirst(".view_table > tbody:nth-child(1) > tr:nth-child(2) > td:nth-child(2)")!!.text().let {
                        when (it) {
                            "전체" -> BoardMsgItemTarget.All
                            "재학생" -> BoardMsgItemTarget.Students(
                                selectFirst(".view_table > tbody:nth-child(1) > tr:nth-child(3) > td:nth-child(2)")!!.text()
                                    .split(",").map { s -> s.trim() }
                            )

                            else -> throw Exception("알 수 없는 대상: $it")
                        }
                    }

                val body = selectFirst(".ck-content")!!.html()

                val form = selectFirst(".view_input")?.let { formEl ->
                    parseForm(client, request, formEl)
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

    internal fun parseForm(client: OpenRiroAPI, req: BoardMsgItemRequest, formEl: Element): BoardMsgForm {
        val metaTds = formEl.select("table > tbody > tr:nth-child(1) > td")

        val periodParts = metaTds[0].text().trim().split(" ~ ")
        fun parsePeriod(raw: String) =
            LocalDateTime.parse("0000-${raw.replace("시", ":").replace("분", ":").replace("초", "")}", format)

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

        return BoardMsgForm(
            period = period,
            applicants = count,
            name = name,
            id = id,
            questions = questions,
            isSubmitEnabled = isSubmitEnabled(formEl),
            submit = { questions, isSigned ->
                client.retry {
                    val res = client.httpClient.post("${client.config.baseUrl}/board_msg.php") {
                        contentType(ContentType.MultiPart.FormData)
                        setBody(FormDataContent(Parameters.build {
                            append("db", req.db.value.toString())
                            append("action", "answer_ajax")
                            append("uid", req.uid.value.toString())
                            append("burl", "")
                            append("check_sign", if (isSigned) "Y" else "N")

                            questions.forEachIndexed { idx, question ->
                                when (val answer = question.answer) {
                                    is BoardMsgFormAnswer.Text -> append("user_input[$idx]", answer.value)
                                    is BoardMsgFormAnswer.Checkbox -> answer.options.forEachIndexed { optionIdx, option ->
                                        if (option.selected) {
                                            append("user_input[$idx-$optionIdx]", option.value)
                                        }
                                    }

                                    is BoardMsgFormAnswer.Radio -> answer.options.forEach { option ->
                                        if (option.selected) {
                                            append("user_input[$idx]", option.value)
                                        }
                                    }
                                }
                            }
                        }))
                    }.also {
                        client.auth(it)
                    }

                    val body = json.decodeFromString<BoardMsgFormResponse>(res.bodyAsText())
                    if (!body.status) {
                        throw RequestFailedException(body.msg)
                    }
                }
            },
            delete = {
                client.retry {
                    val res = client.httpClient.post("${client.config.baseUrl}/board_msg.php") {
                        contentType(ContentType.MultiPart.FormData)
                        setBody(FormDataContent(Parameters.build {
                            append("db", req.db.value.toString())
                            append("action", "remove_ajax")
                            append("uid", req.uid.value.toString())
                            append("pw", client.auth.pw)
                        }))
                    }.also {
                        client.auth(it)
                    }

                    val body = json.decodeFromString<BoardMsgFormResponse>(res.bodyAsText())
                    if (!body.status) {
                        throw RequestFailedException(body.msg)
                    }
                }
            }
        )
    }

    private fun isSubmitEnabled(formEl: Element): Boolean {
        val isClosedPeriod = formEl.select("b").any { element ->
            element.text().contains("신청 기간이 아닙니다")
        }
        if (isClosedPeriod) return false

        val submitActionElement = formEl
            .select("#btn_click button, #btn_click input[type=button], #btn_click input[type=submit], #btn_click a")
            .firstOrNull { element ->
                val onclick = element.attr("onclick")
                val href = element.attr("href")
                val text = element.text().trim()

                onclick.contains("apply_chk") || href.contains("apply_chk") || text.contains("제출하기")
            }
            ?: return false

        if (submitActionElement.hasAttr("disabled")) return false
        if (submitActionElement.attr("aria-disabled").equals("true", ignoreCase = true)) return false

        val classes = submitActionElement.attr("class").split(" ").map { it.trim() }
        if (classes.any { className -> className.equals("disabled", ignoreCase = true) }) return false

        return true
    }
}
