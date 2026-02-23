package io.github.devngho.openriro

import io.github.devngho.openriro.client.AuthConfig
import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.client.RequestConfig
import io.github.devngho.openriro.client.UserType
import io.github.devngho.openriro.common.BoardKindMismatchException
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.RequestFailedException
import io.github.devngho.openriro.common.Cate
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.endpoints.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File


@OptIn(InternalApi::class)
class OpenRiroClientTest : DescribeSpec({
    fun Any.prettyPrint(): String {

        var indentLevel = 0
        val indentWidth = 4

        fun padding() = "".padStart(indentLevel * indentWidth)

        val toString = toString()

        val stringBuilder = StringBuilder(toString.length)

        var i = 0
        while (i < toString.length) {
            when (val char = toString[i]) {
                '(', '[', '{' -> {
                    indentLevel++
                    stringBuilder.appendLine(char).append(padding())
                }
                ')', ']', '}' -> {
                    indentLevel--
                    stringBuilder.appendLine().append(padding()).append(char)
                }
                ',' -> {
                    stringBuilder.appendLine(char).append(padding())
                    // ignore space after comma as we have added a newline
                    val nextChar = toString.getOrElse(i + 1) { char }
                    if (nextChar == ' ') i++
                }
                else -> {
                    stringBuilder.append(char)
                }
            }
            i++
        }

        return stringBuilder.toString()
    }

    val fileText = File("auth.txt").readLines()
    val id = fileText[0]
    val pw = fileText[1]
    val baseUrl = fileText[2]

    val api = OpenRiroAPI(
        AuthConfig(UserType.STUDENT_OR_TEACHER, id, pw),
        RequestConfig(baseUrl)
    )

    describe("OpenRiroClient") {
        xit("should login") {
            api.login(force = true).shouldBeSuccess()
            @OptIn(InternalApi::class)
            api.cookies.get(Url(api.config.baseUrl)).find { it.name == "cookie_token" } shouldNotBe null
        }
    }

    describe("Login") {
        xit("should execute login request") @OptIn(InternalApi::class) {
            val result = Login.execute(api, api.auth)

            result shouldBeSuccess {
                it.code shouldBe "000"
                it.msg shouldBe "로그인에 성공했습니다."
            }
        }
    }

    describe("MenuList") {
        it("should execute request") {
            val result = MenuList.execute(api, Unit)

            result shouldBeSuccess {
                it.shouldNotBeEmpty()
                println(it.map { v -> v.prettyPrint() })
            }
        }
    }

    describe("BoardMsg") {
        it("should execute request") {
            val request = BoardMsg.BoardMsgRequest(db = DBId(1901), page = 1)
            val result = BoardMsg.execute(api, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }
    }

    describe("BoardMsgItem") {
        it("should execute request") {
            val request = BoardMsgItem.BoardMsgItemRequest(db = DBId(1901), uid = Uid(1958))
            val result = BoardMsgItem.execute(api, request)

            result shouldBeSuccess {
                it.target shouldBe BoardMsgItem.BoardMsgItemTarget.All
                it.attachments shouldHaveSize 2

                println(it)
            }
        }

        it("should execute request with a form") {
            val request = BoardMsgItem.BoardMsgItemRequest(db = DBId(1901), uid = Uid(1939))
            val result = BoardMsgItem.execute(api, request)

            result.exceptionOrNull()?.printStackTrace()

            result shouldBeSuccess {
                it.target shouldBe BoardMsgItem.BoardMsgItemTarget.Students(
                    (1..12).map { v -> "1${v.toString().padStart(2, '0')}" } + (1..12).map { v -> "2${v.toString().padStart(2, '0')}" }
                )
                it.form shouldNotBe null
                it.form!!.let { form ->
                    form.questions shouldHaveSize 3
                    form.questions[0].answer.shouldBeInstanceOf<BoardMsgItem.BoardMsgFormAnswer.Radio>()
                    (form.questions[0].answer as BoardMsgItem.BoardMsgFormAnswer.Radio).options[0].selected shouldBe true
                    (form.questions[0].answer as BoardMsgItem.BoardMsgFormAnswer.Radio).options[1].selected shouldBe false
                    form.questions[1].answer.shouldBeInstanceOf<BoardMsgItem.BoardMsgFormAnswer.Radio>()
                    (form.questions[1].answer as BoardMsgItem.BoardMsgFormAnswer.Radio).options[0].selected shouldBe false
                    (form.questions[1].answer as BoardMsgItem.BoardMsgFormAnswer.Radio).options[1].selected shouldBe true
                    form.questions[2].answer.shouldBeInstanceOf<BoardMsgItem.BoardMsgFormAnswer.Radio>()
                    (form.questions[2].answer as BoardMsgItem.BoardMsgFormAnswer.Radio).options[0].selected shouldBe false
                    (form.questions[2].answer as BoardMsgItem.BoardMsgFormAnswer.Radio).options[1].selected shouldBe true

                    form.isSubmitEnabled shouldBe false
                }

                println(it)
            }
        }

        xit("should execute request with a form and submit it") {
            val request = BoardMsgItem.BoardMsgItemRequest(db = DBId(1901), uid = Uid(1960))
            val result = BoardMsgItem.execute(api, request)

            result.exceptionOrNull()?.printStackTrace()

            result shouldBeSuccess {
                it.target shouldBe BoardMsgItem.BoardMsgItemTarget.Students(
                    (1..12).map { v -> "1${v.toString().padStart(2, '0')}" } + (1..12).map { v -> "2${v.toString().padStart(2, '0')}" } + (1..12).map { v -> "3${v.toString().padStart(2, '0')}" }
                )
                it.form shouldNotBe null
                it.form!!.isSubmitEnabled shouldBe true
                runBlocking {
                    it.form.submit {
                        option<BoardMsgItem.BoardMsgFormAnswer.Radio>(0).set(1)
                        option<BoardMsgItem.BoardMsgFormAnswer.Radio>(1).set(1)
                        option<BoardMsgItem.BoardMsgFormAnswer.Radio>(2).set(1)

                        sign()
                    }.getOrThrow()

                    it.form.delete().getOrThrow()
                }

                println(it)
            }
        }
    }

    describe("Board") {
        it("should execute request") {
            val request = Board.BoardRequest(db = DBId(1003), page = 1)
            val result = Board.execute(api, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }

        it("should fail incorrect request") {
            val request = Board.BoardRequest(db = DBId(1010), page = 1)
            val result = Board.execute(api, request)

            result shouldBeFailure {
                it.shouldBeInstanceOf<BoardKindMismatchException>()
            }
        }
    }

    describe("BoardItem") {
        it("should execute request") {
            val request = BoardItem.BoardItemRequest(db = DBId(1003), uid = Uid(3234))
            val result = BoardItem.execute(api, request)

            result shouldBeSuccess {
                it.attachments shouldHaveSize 1

                println(it)
            }
        }
    }

    describe("Portfolio") {
        it("should execute request") {
            val request = Portfolio.PortfolioRequest(db = DBId(1553), page = 1)
            val result = Portfolio.execute(api, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }
    }

    describe("PortfolioList") {
        it("should execute request") {
            val request = PortfolioList.PortfolioListRequest(db = DBId(1553), page = 1, cate = Cate(107627))
            val result = PortfolioList.execute(api, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }

        it("should fail incorrect request") {
            val request = PortfolioList.PortfolioListRequest(db = DBId(1556), page = 1, cate = Cate(107457))
            val result = PortfolioList.execute(api, request)

            result shouldBeFailure {
                it.message shouldBe RequestFailedException("이 과제방에 제출한 보고서가 없습니다.").message
            }
        }
    }

    describe("PortfolioItem") {
        it("should execute request") {
            val request = PortfolioItem.PortfolioItemRequest(db = DBId(1551), cate = Cate(106770), uid = Uid(259050))
            val result = PortfolioItem.execute(api, request)

            result shouldBeSuccess {
                it.isSubmitted shouldBe true
//                println(it)
            }
        }

        it("should execute request with attachments") {
            val request = PortfolioItem.PortfolioItemRequest(db = DBId(1502), cate = Cate(107254), uid = Uid(281167))
            val result = PortfolioItem.execute(api, request)

            result shouldBeSuccess {
                it.isSubmitted shouldBe true
                it.attachments shouldHaveSize 1
                it.attachments[0].file.downloadUrl shouldBe "/portfolio.php?action=down&db=1502&cate=107254&uid=281167&file_num=0&file_code=e95e42b8d7a22258005b73e2301e3f53"
                println(it)
            }
        }

        it("should fail incorrect request") {
            val request = PortfolioItem.PortfolioItemRequest(db = DBId(1551), cate = Cate(106770), uid = Uid(259186))
            val result = PortfolioItem.execute(api, request)

            result shouldBeFailure {
                it.message shouldBe RequestFailedException("본인(모둠)만 조회할 수 있습니다.").message
            }
        }
    }

    describe("Score") {
        it("should execute request - 내신") {
            val request = Score.ScoreRequest(db = DBId(1010), uid = Uid(360))
            val result = Score.execute(api, request)

            result shouldBeSuccess {
                it.scores.forEach { s ->
                    when (s) {
                        is Score.ScoreItem.WithStandardScore -> println("과목: ${s.name}, 점수: ${s.score}, 표준점수: ${s.standardScore}, 백분위: ${s.percentile}")
                        is Score.ScoreItem.WithStanding -> println("과목: ${s.name}, 점수: ${s.score}, 석차: ${s.standing}, 동석차: ${s.tiedStanding}, 전체인원: ${s.candidates}, 백분위: ${s.percentile}")
                    }
                }

                it.detailedScores?.forEach { s ->
                    println("과목: ${s.name}, 합: ${s.weightedScore}")
                    s.details.forEach { d ->
                        println("  ${d.name}: ${d.score}")
                    }
                }
            }
        }

        it("should execute request - 모평") {
            val request = Score.ScoreRequest(db = DBId(1010), uid = Uid(335))
            val result = Score.execute(api, request)

            result shouldBeSuccess {
                it.scores.forEach { s ->
                    when (s) {
                        is Score.ScoreItem.WithStandardScore -> println("과목: ${s.name}, 점수: ${s.score}, 표준점수: ${s.standardScore}, 백분위: ${s.percentile}")
                        is Score.ScoreItem.WithStanding -> println("과목: ${s.name}, 점수: ${s.score}, 석차: ${s.standing}, 동석차: ${s.tiedStanding}, 전체인원: ${s.candidates}, 백분위: ${s.percentile}")
                    }
                }
            }
        }

        it("should fail incorrect request") {
            val request = Score.ScoreRequest(db = DBId(1003))
            val result = Score.execute(api, request)

            result shouldBeFailure {
                it.printStackTrace()
                it.shouldBeInstanceOf<BoardKindMismatchException>()
            }
        }
    }
})
