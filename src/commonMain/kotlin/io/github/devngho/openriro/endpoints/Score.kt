package io.github.devngho.openriro.endpoints

import com.fleeksoft.ksoup.nodes.Element
import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.BoardKindMismatchException
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.Menu
import io.github.devngho.openriro.common.RequestFailedException
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * /board.php?action=score에 대응합니다.
 */
object Score: Request<Score.ScoreRequest, Score.ScoreResponse> {
    data class ScoreRequest(
        val db: DBId,
        val uid: Uid? = null,
    )

    data class ScoreResponse(
        val scoreOptions: List<ScoreOptions>,
        val selectedOption: ScoreOptions,
        val scores: List<ScoreItem>,
        val detailedScores: List<ScoreDetailedItem>?
    )

    data class ScoreOptions(
        val name: String,
        val dbId: DBId,
        val uid: Uid
    )

    sealed interface ScoreItem {
        data class WithStandardScore(
            val name: String,
            val score: Double?,
            val standing: Int?,
            /** 동석차 수 */
            val tiedStanding: Int?,
            /** 응시자 수 */
            val candidates: Int?,
            /** 백분위 */
            val percentile: Double?,
            /** 표준 점수 */
            val standardScore: Int?,
            val grade: Double?
        ): ScoreItem

        data class WithStanding(
            val name: String,
            val score: Double?,
            val standing: Int?,
            /** 동석차 수 */
            val tiedStanding: Int?,
            /** 응시자 수 */
            val candidates: Int?,
            /** 백분위 */
            val percentile: Double?,
            val grade: Int?
        ): ScoreItem
    }

    data class ScoreDetailedItem(
        val name: String,
        val details: List<ScoreDetailItem>,
        val weightedScore: Double?,
        val rawScore: Int?,
        val achievement: String?,
        val grade: Int?,
        val standing: Int?,
        /** 동석차 수 */
        val tiedStanding: Int?,
        /** 응시자 수 */
        val candidates: Int?
    )

    data class ScoreDetailItem(
        val name: String,
        val score: Double?
    )

    private fun parseStandings(text: String): Triple<Int?, Int?, Int?> {
        val parts = text.split("(", ")", "/")
        return when (parts.size) {
            1 if parts[0] == "**" -> Triple(null, null, null)
            2 -> Triple(parts[0].toIntOrNull(), null, parts[1].toIntOrNull())
            4 -> Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[3].toIntOrNull())
            else -> Triple(null, null, null)
        }
    }

    private fun parseDetailedScores(table: Element, out: MutableList<ScoreDetailedItem>) {
        var currentName: String? = null
        var detailItems = mutableListOf<ScoreDetailItem>()
        var weightedScore: Double? = null
        var rawScore: Int? = null
        var achievement: String? = null
        var grade: Int? = null
        var standing: Int? = null
        var tiedStanding: Int? = null
        var candidates: Int? = null

        fun flush() {
            val name = currentName ?: return
            if (detailItems.isEmpty() && listOfNotNull(weightedScore, rawScore, achievement, grade, standing, tiedStanding, candidates).isEmpty()) return
            out += ScoreDetailedItem(name, detailItems, weightedScore, rawScore, achievement, grade, standing, tiedStanding, candidates)
        }

        fun reset(name: String) {
            currentName = name
            detailItems = mutableListOf()
            weightedScore = null; rawScore = null; achievement = null
            grade = null; standing = null; tiedStanding = null; candidates = null
        }

        table.select("tr").forEach { tr ->
            if (tr.select("th").isNotEmpty()) return@forEach
            val tds = tr.select("td")
            if (tds.isEmpty()) return@forEach

            when (tds.size) {
                3 -> {
                    flush()
                    reset(tds[0].text())
                    detailItems += ScoreDetailItem(tds[1].text(), tds[2].text().toDoubleOrNull())
                }
                2 -> {
                    if (currentName == null) return@forEach
                    val label = tds[0].text()
                    val value = tds[1].text()
                    when (label) {
                        "합계" -> weightedScore = value.toDoubleOrNull()
                        "원점수" -> rawScore = value.toIntOrNull()
                        "성취도" -> achievement = value.takeIf { it.isNotBlank() }
                        "석차등급" -> grade = value.toIntOrNull()
                        "석차(동점자)/수강자" -> parseStandings(value).let { (s, t, c) ->
                            standing = s; tiedStanding = t; candidates = c
                        }
                        else -> detailItems += ScoreDetailItem(label, value.toDoubleOrNull())
                    }
                }
            }
        }

        flush()
    }

    @OptIn(InternalApi::class)
    override suspend fun execute(client: OpenRiroAPI, request: ScoreRequest): Result<ScoreResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/board.php?action=score&db=${request.db.value}&uid=${request.uid?.value}")
            .also { client.auth(it) }.bodyAsText()

        val items = mutableListOf<ScoreItem>()
        val detailedItems = mutableListOf<ScoreDetailedItem>()
        val options = mutableListOf<ScoreOptions>()
        var selectedOption: ScoreOptions? = null

        if (page.startsWith("<script>")) {
            val errorMsg = page.substringAfter("alert(\"").substringBefore("\");")

            if (errorMsg == "비정상적인 접속입니다.") {
                // try checking board
                val res = Board.execute(client, Board.BoardRequest(request.db, 1))

                if (res.isSuccess) throw BoardKindMismatchException(Menu.Board.Score::class, Menu.Board.Normal::class)
            }

            throw RequestFailedException(errorMsg)
        }

        html(page) {
            if (selectFirst(".check_password_box") != null) {
                // perform auth
                val form = selectFirst("form") ?: throw IllegalStateException("비밀번호 입력 폼이 없습니다.")

                client.httpClient.post("${client.config.baseUrl}/board.php") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(FormDataContent(Parameters.build {
                        // all the form's input
                        form.select("input").forEach {
                            val name = it.attr("name")
                            val value = it.attr("value")

                            append(name, value)
                        }

                        // password
                        append("pw", client.auth.pw)
                    }))
                }

                return@html execute(client, request)
            }

            selectFirst(".rd_select")!!.select("option").forEach {
                val option = ScoreOptions(
                    name = it.text(),
                    dbId = request.db,
                    uid = Uid(it.attr("value").let { v ->
                        v.ifBlank { return@forEach }
                    }.toInt())
                )

                if (it.hasAttr("selected")) {
                    selectedOption = option
                }

                options += option
            }

            val tables = select(".table_box")

            tables[0].let {
                val headers = it.selectFirst("tr")
                val isContainingStandardScore = headers?.select("td")?.any { a -> a.text() == "표준" } ?: false

                it.select("tr").drop(1).forEach { tr ->
                    val tds = tr.select("td")
                    if (tds.size != 5) return@forEach

                    if (isContainingStandardScore) {
                        items += ScoreItem.WithStandardScore(
                            name = tds[0].text(),
                            score = tds[1].text().toDoubleOrNull(),
                            standing = null,
                            percentile = tds[3].text().toDoubleOrNull(),
                            grade = tds[4].text().toDoubleOrNull(),
                            tiedStanding = null,
                            candidates = null,
                            standardScore = tds[2].text().toIntOrNull()
                        )
                        return@forEach
                    }

                    val (standing, tiedStanding, candidates) = parseStandings(tds[2].text())

                    items += ScoreItem.WithStanding(
                        name = tds[0].text(),
                        score = tds[1].text().toDoubleOrNull(),
                        standing = standing,
                        percentile = tds[3].text().toDoubleOrNull(),
                        grade = tds[4].text().toIntOrNull(),
                        tiedStanding = tiedStanding,
                        candidates = candidates
                    )
                }
            }

            tables.getOrNull(1)?.let { parseDetailedScores(it, detailedItems) }
        }

        ScoreResponse(
            scoreOptions = options,
            selectedOption = selectedOption!!,
            scores = items,
            detailedScores = detailedItems.takeIf { it.isNotEmpty() }
        )
    }
}
