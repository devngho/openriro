package io.github.devngho.openriro.common

sealed interface Menu {
    sealed interface Board: Menu {
        data class Unknown(override val label: String, override val dbId: DBId): Board
        data class Score(override val label: String, override val dbId: DBId): Board
        data class Normal(override val label: String, override val dbId: DBId): Board
    }
    data class BoardMsg(override val label: String, override val dbId: DBId): Menu
    data class Portfolio(override val label: String, override val dbId: DBId): Menu

    val label: String
    val dbId: DBId
}