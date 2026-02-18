package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI
import io.github.devngho.openriro.common.DBId
import io.github.devngho.openriro.common.Menu
import io.github.devngho.openriro.util.html
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * /index.php에서 모든 메뉴를 가져옵니다.
 */
object MenuList: Request<Unit, List<Menu>> {
    override suspend fun execute(client: OpenRiroAPI, request: Unit): Result<List<Menu>> = client.retry {
        val page = client.httpClient
            .get("${client.config.baseUrl}/index.php")
            .also { client.auth(it) }.bodyAsText()

        val items = mutableListOf<Menu>()

        html(page) {
            select(".depth2_list > li").forEach {
                val span = it.selectFirst("span") ?: return@forEach
                val action = span.attr("onclick").substringAfter("menuAction('").substringBefore("', '');").split("?")
                val name = span.text()
                val menu = runCatching { when (action[0]) {
                    "/board.php" -> Menu.Board.Unknown(label=name, dbId = DBId(action[1].substringAfter("db=").toInt()))
                    "/board_msg.php" -> Menu.BoardMsg(label=name, dbId = DBId(action[1].substringAfter("db=").toInt()))
                    "/portfolio.php" -> Menu.Portfolio(label=name, dbId = DBId(action[1].substringAfter("db=").toInt()))
                    else -> null
                } }.getOrNull() ?: return@forEach

                items.add(menu)
            }
        }

        items.toList()
    }
}
