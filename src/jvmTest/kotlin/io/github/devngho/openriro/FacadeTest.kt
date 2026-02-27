package io.github.devngho.openriro

import io.github.devngho.openriro.client.AuthConfig
import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.client.RequestConfig
import io.github.devngho.openriro.client.UserType
import io.github.devngho.openriro.common.Menu
import io.github.devngho.openriro.facade.OpenRiroClient
import io.github.devngho.openriro.facade.OpenRiroClient.Companion.get
import io.github.devngho.openriro.facade.OpenRiroClient.Companion.list
import io.github.devngho.openriro.facade.OpenRiroClient.Companion.timetable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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
        val menu = client.labeled<Menu.Board.Normal>("공지사항").getOrThrow()
        val paging = menu.list().getOrThrow()

        test("can access notice") {
            paging.get(0) shouldNotBe null
            println(paging.get(1))
        }

        test("can access detail of notice") {
            val page = paging.get(0) ?: return@test
            val detail = page.get().getOrThrow()

            detail.title shouldNotBe null
        }

        test("can preload notice") {
            paging.preload(20)

            withTimeout(10) {
                paging.get(20) shouldNotBe null
            }

            paging.preload(40..<200)

            withTimeout(10) {
                paging.get(40..<200) shouldNotBe null
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
            paging.get(0) shouldNotBe null
        }

        test("can access detail of notice") {
            val page = paging.get(0) ?: return@test
            val detail = page.get().getOrThrow()

            detail.title shouldNotBe null
        }

        test("can preload notice") {
            paging.preload(20)

            withTimeout(10) {
                paging.get(20) shouldNotBe null
            }

            paging.preload(40..<200)

            withTimeout(10) {
                paging.get(40..<200) shouldNotBe null
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
            paging.get(0) shouldNotBe null
        }

        test("can preload portfolio") {
            paging.preload(20)

            withTimeout(10) {
                paging.get(20) shouldNotBe null
            }

            paging.preload(40..<200)

            withTimeout(10) {
                paging.get(40..<200) shouldNotBe null
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
        val menu = client.labeled<Menu.Portfolio>("교과활동").getOrThrow().list().getOrThrow().filterNotNull().first { (it, _) ->
            it.isSubmitted && it.summitCount >= 300
        }
        val paging = menu.list().getOrThrow()

        paging shouldNotBe null

        test("can access portfolio") {
            paging.get(0) shouldNotBe null
        }

        test("can preload portfolio") {
            paging.preload(20)

            withTimeout(10) {
                paging.get(20) shouldNotBe null
            }

            paging.preload(40..<100)

            withTimeout(10) {
                paging.get(40..<100) shouldNotBe null
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

        test("can create as flow") {
            withTimeout(10) {
                paging.first() shouldNotBe null
            }

            paging.toList() shouldHaveSize paging.size
        }
    }

    context("a score") {
        val menu = client.labeled<Menu.Board.Score>("성적조회").getOrThrow()

        test("can access score") {
            val paging = menu.list().getOrThrow()
            println(paging.get(0..<paging.size))
            paging.get(0) shouldNotBe null
        }

        test("can access detail of score") {
            val paging = menu.list().getOrThrow()
            val page = paging.get(0) ?: return@test
            val detail = page.get().getOrThrow()

            detail.scores[0] shouldNotBe null
        }
    }

    context("timetables") {
        val timetable = client.timetable().getOrThrow()

        test("can access timetable") {
            println(timetable.get(0..<timetable.size))
            timetable.get(0) shouldNotBe null
        }

        test("can access detail of timetable") {
            val page = timetable.get(0) ?: return@test
            val detail = page.get().getOrThrow()

            detail.table.shouldNotBeNull()

            detail.table.forEach {
                println(it.cols.joinToString("\t") { c -> "${c.name}(${c.teacher}, ${c.room}, ${c.seat})" })
            }
        }
    }
})