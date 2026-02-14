package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.AuthConfig
import io.github.devngho.openriro.client.OpenRiroClient
import io.github.devngho.openriro.client.RequestConfig
import io.github.devngho.openriro.client.UserType
import io.github.devngho.openriro.common.InternalApi
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.Url
import java.io.File
import kotlin.collections.emptyList

class OpenRiroClientTest : DescribeSpec({
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
        it("should execute board_msg request") {
            val boardMsgRequest = BoardMsg.BoardMsgRequest(db = "1901", page = 1)
            val result = BoardMsg().execute(client, boardMsgRequest)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }
    }

    describe("Board") {
        it("should execute board request") {
            val boardRequest = Board.BoardRequest(db = "1003", page = 1)
            val result = Board().execute(client, boardRequest)

            result shouldBeSuccess {
                it.totalCount shouldBeGreaterThan 0
                it.page shouldBe 1
                it.list.shouldNotBeEmpty()
                println(it.list)
            }
        }
    }
})