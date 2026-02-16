package io.github.devngho.openriro

import io.github.devngho.openriro.client.AuthConfig
import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.client.RequestConfig
import io.github.devngho.openriro.client.UserType
import io.github.devngho.openriro.common.Menu
import io.github.devngho.openriro.endpoints.PortfolioList
import io.github.devngho.openriro.facade.OpenRiroClient
import io.github.devngho.openriro.facade.OpenRiroClient.Companion.list
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File

class FacadeTest: FunSpec({
    val fileText = File("auth.txt").readLines()
    val id = fileText[0]
    val pw = fileText[1]
    val baseUrl = fileText[2]

    val client = OpenRiroClient(
        OpenRiroAPI(
            AuthConfig(UserType.STUDENT_OR_TEACHER, id, pw),
            RequestConfig(baseUrl)
        )
    )

    test("can fetch menu") {
        val res = client.menu()
        println(res)
    }

    context("a board") {
        val menu = client.labeled<Menu.Board>("공지사항").getOrThrow()
        val paging = menu.list().getOrThrow()

        test("can access notice") {
            paging.page(0) shouldNotBe null
            paging.page(0)!! shouldHaveSize menu.value.pageSize
        }

        test("can preload notice") {
            paging.preloadPage(1)

            withTimeout(10) {
                paging.page(1) shouldNotBe null
                paging.page(1)!! shouldHaveSize menu.value.pageSize
            }

            paging.preloadPage(2..10)

            withTimeout(10) {
                paging.page(2..10) shouldNotBe null
                paging.page(2..10)!! shouldHaveSize menu.value.pageSize * 9
            }
        }

        test("can get range of notice") {
            paging.get(200..<300) shouldNotBe null
            paging.get(200..<300) shouldHaveSize 100
        }

        test("can preload range of notice") {
            paging.preload(300..<400)

            withTimeout(10) {
                paging.get(300..<400) shouldNotBe null
                paging.get(300..<400) shouldHaveSize 100
            }
        }
    }

    context("a board msg") {
        val menu = client.labeled<Menu.BoardMsg>("가정통신문(설문조사용)").getOrThrow()
        val paging = menu.list().getOrThrow()

        test("can access notice") {
            paging.page(0) shouldNotBe null
            paging.page(0)!! shouldHaveSize menu.value.pageSize
        }

        test("can preload notice") {
            paging.preloadPage(1)

            withTimeout(10) {
                paging.page(1) shouldNotBe null
                paging.page(1)!! shouldHaveSize menu.value.pageSize
            }

            paging.preloadPage(2..10)

            withTimeout(10) {
                paging.page(2..10) shouldNotBe null
                paging.page(2..10)!! shouldHaveSize menu.value.pageSize * 9
            }
        }

        test("can get range of notice") {
            paging.get(200..<300) shouldNotBe null
            paging.get(200..<300) shouldHaveSize 100
        }

        test("can preload range of notice") {
            paging.preload(300..<400)

            withTimeout(10) {
                paging.get(300..<400) shouldNotBe null
                paging.get(300..<400) shouldHaveSize 100
            }
        }
    }

    context("a portfolio") {
        val menu = client.labeled<Menu.Portfolio>("교과활동").getOrThrow()
        val paging = menu.list().getOrThrow()

        test("can access portfolio") {
            paging.page(0) shouldNotBe null
            paging.page(0)!! shouldHaveSize menu.value.pageSize
        }

        test("can preload portfolio") {
            paging.preloadPage(1)

            withTimeout(10) {
                paging.page(1) shouldNotBe null
                paging.page(1)!! shouldHaveSize menu.value.pageSize
            }

            paging.preloadPage(2..10)

            withTimeout(10) {
                paging.page(2..10) shouldNotBe null
                paging.page(2..10)!! shouldHaveSize menu.value.pageSize * 9
            }
        }

        test("can get range of portfolio") {
            paging.get(200..<300) shouldNotBe null
            paging.get(200..<300) shouldHaveSize 100
        }

        test("can preload range of portfolio") {
            paging.preload(300..<400)

            withTimeout(10) {
                paging.get(300..<400) shouldNotBe null
                paging.get(300..<400) shouldHaveSize 100
            }
        }
    }

    context("a portfolio list") {
        val menu = client.labeled<Menu.Portfolio>("교과활동").getOrThrow().list().getOrThrow().asFlow().filterNotNull().first { (it, _) ->
            it.isSubmitted && it.summitCount >= 300
        }
        val paging = menu.list().getOrThrow()

        paging shouldNotBe null

        test("can access portfolio") {
            paging.page(0) shouldNotBe null
            paging.page(0)!! shouldHaveSize PortfolioList.PAGE_SIZE
        }

        test("can preload portfolio") {
            paging.preloadPage(1)

            withTimeout(10) {
                paging.page(1) shouldNotBe null
                paging.page(1)!! shouldHaveSize PortfolioList.PAGE_SIZE
            }

            paging.preloadPage(2..5)

            withTimeout(10) {
                paging.page(2..5) shouldNotBe null
                paging.page(2..5)!! shouldHaveSize PortfolioList.PAGE_SIZE * 4
            }
        }

        test("can get range of portfolio") {
            paging.get(200..<300) shouldNotBe null
            paging.get(200..<300) shouldHaveSize 100
        }

        test("can preload range of portfolio") {
            paging.preload(300..<400)

            withTimeout(10) {
                paging.get(300..<400) shouldNotBe null
                paging.get(300..<400) shouldHaveSize 100
            }
        }
    }
})