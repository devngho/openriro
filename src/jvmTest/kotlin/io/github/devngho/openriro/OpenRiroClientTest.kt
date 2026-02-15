package io.github.devngho.openriro

import io.github.devngho.openriro.client.AuthConfig
import io.github.devngho.openriro.client.OpenRiroClient
import io.github.devngho.openriro.client.RequestConfig
import io.github.devngho.openriro.client.UserType
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.RequestFailedException
import io.github.devngho.openriro.endpoints.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import java.io.File


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

    val client = OpenRiroClient(
        AuthConfig(UserType.STUDENT_OR_TEACHER, id, pw),
        RequestConfig(baseUrl)
    )

    describe("OpenRiroClient") {
        xit("should login") {
            client.login(force = true).shouldBeSuccess()
            @OptIn(InternalApi::class)
            client.cookies.get(Url(client.config.baseUrl)).find { it.name == "cookie_token" } shouldNotBe null
        }
    }

    describe("Login") {
        xit("should execute login request") @OptIn(InternalApi::class) {
            val loginRequest = Login()
            val result = loginRequest.execute(client, client.auth)

            result shouldBeSuccess {
                it.code shouldBe "000"
                it.msg shouldBe "로그인에 성공했습니다."
            }
        }
    }

    describe("BoardMsg") {
        it("should execute request") {
            val request = BoardMsg.BoardMsgRequest(db = 1901, page = 1)
            val result = BoardMsg().execute(client, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }
    }

    describe("Board") {
        it("should execute request") {
            val request = Board.BoardRequest(db = 1003, page = 1)
            val result = Board().execute(client, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }
    }

    describe("Portfolio") {
        it("should execute request") {
            val request = Portfolio.PortfolioRequest(db = 1553, page = 1)
            val result = Portfolio().execute(client, request)

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
            val request = PortfolioList.PortfolioListRequest(db = 1553, page = 1, cate = 107627)
            val result = PortfolioList().execute(client, request)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }

        it("should fail incorrect request") {
            val request = PortfolioList.PortfolioListRequest(db = 1556, page = 1, cate = 107655)
            val result = PortfolioList().execute(client, request)

            result shouldBeFailure {
                it.message shouldBe RequestFailedException("이 과제방에 제출한 보고서가 없습니다.").message
            }
        }
    }

    describe("PortfolioItem") {
        it("should execute request") {
            val request = PortfolioItem.PortfolioItemRequest(db = 1551, cate = 106770, uid = 259050)
            val result = PortfolioItem().execute(client, request)

            result shouldBeSuccess {
                it.isSubmitted shouldBe true
                println(it)
            }
        }

        it("should fail incorrect request") {
            val request = PortfolioItem.PortfolioItemRequest(db = 1551, cate = 106770, uid = 259186)
            val result = PortfolioItem().execute(client, request)

            result shouldBeFailure {
                it.message shouldBe RequestFailedException("본인(모둠)만 조회할 수 있습니다.").message
            }
        }
    }

    describe("Score") {
        it("should execute request - 내신") {
            val request = Score.ScoreRequest(db = 1010, uid = 360)
            val result = Score().execute(client, request)

            result shouldBeSuccess {
                println(it.prettyPrint())
            }
        }

        it("should execute request - 모평") {
            val request = Score.ScoreRequest(db = 1010, uid = 335)
            val result = Score().execute(client, request)

            result shouldBeSuccess {
                println(it.prettyPrint())
            }
        }
    }
})
