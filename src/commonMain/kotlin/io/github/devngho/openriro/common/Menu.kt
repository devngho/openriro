package io.github.devngho.openriro.common

sealed interface Menu {
    data class Board(override val label: String, override val dbId: DBId): Menu
    data class BoardMsg(override val label: String, override val dbId: DBId): Menu
    data class Portfolio(override val label: String, override val dbId: DBId): Menu

    val label: String
    val dbId: DBId
}