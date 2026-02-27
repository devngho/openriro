package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.Cate
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.Uid
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * /lecture.php?action=timetable에 대응합니다.
 */
object Timetable: Request<Timetable.TimetableRequest, Timetable.TimetableResponse> {
    data class TimetableRequest(
        val db: DBId? = null,
        val cate: Cate? = null,
        val uid: Uid? = null,
    )

    data class TimetableResponse(
        val timetableLectures: List<TimetableLecture>,
        val selectedLecture: TimetableLecture?,
        val table: List<TimetableRow>?,
    )

    data class TimetableLecture(
        val name: String,
        val dbId: DBId,
        val uid: Uid,
        val cate: Cate
    )

    data class TimetableRow(
        /** 교시 */
        val time: Int,
        val cols: List<TimetableCol>
    )

    data class TimetableCol(
        /** 교시 */
        val time: Int,
        /** 요일 */
        val day: String,
        val name: String,
        val teacher: String,
        val room: String,
        val seat: String
    )

    @OptIn(InternalApi::class)
    override suspend fun execute(client: OpenRiroAPI, request: TimetableRequest): Result<TimetableResponse> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/lecture.php?action=timetable&db=${request.db?.value ?: ""}&cate=${request.cate?.value ?: ""}&uid=${request.uid?.value ?: ""}&show_name=1&show_room=1&show_seat=1")
            .also { client.auth(it) }.bodyAsText()

        val options = mutableListOf<TimetableLecture>()
        var selectedOption: TimetableLecture? = null
        val items = mutableListOf<TimetableRow>()

        html(page) {
            selectFirst(".rd_select")!!.select("option").forEach {
                val value = it.attr("value")
                if (value.isBlank()) return@forEach

                val l = value.split("&")
                val dbId = l.find { f -> f.startsWith("db=") }?.substringAfter("db=")?.toIntOrNull() ?: return@forEach
                val uid = l.find { f -> f.startsWith("uid=") }?.substringAfter("uid=")?.toIntOrNull() ?: return@forEach
                val cate = l.find { f -> f.startsWith("cate=") }?.substringAfter("cate=")?.toIntOrNull() ?: return@forEach

                val option = TimetableLecture(
                    name = it.text(),
                    dbId = DBId(dbId),
                    uid = Uid(uid),
                    cate = Cate(cate)
                )

                if (it.hasAttr("selected")) {
                    selectedOption = option
                }

                options += option
            }

            val tables = select(".table_box")

            tables.getOrNull(0)?.let {
                val headers = it.selectFirst("tr")!!.select("th").map { m -> m.text() }

                it.select("tr").drop(1).map { tr ->
                    val tds = tr.select("td")
                    val time = tds[0].text().toIntOrNull() ?: return@map null

                    val cols = tds.drop(1).mapIndexed { i, td ->
                        val day = headers.getOrNull(i)!!
                        val nodes = td.childNodes()
                        val name = nodes.getOrNull(0)?.childNodes()!![0].nodeValue().trim()
                        val teacher = nodes.getOrNull(1)?.toString()?.trim() ?: ""
                        val room = nodes.getOrNull(2)?.childNodes()!![0].nodeValue().trim()
                        val seat = nodes.getOrNull(3)?.childNodes()!![0].nodeValue().trim()

                        TimetableCol(time, day, name, teacher, room, seat)
                    }

                    TimetableRow(time, cols)
                }.filterNotNull().forEach { f -> items += f }
            }
        }

        TimetableResponse(
            timetableLectures = options,
            selectedLecture = selectedOption,
            table = items.ifEmpty { null }
        )
    }
}
