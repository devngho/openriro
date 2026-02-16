package io.github.devngho.openriro.common

sealed interface Menu {
    data class Board(override val label: String, override val dbId: DBId): Menu {
        override val pageSize: Int = 10
    }
    data class BoardMsg(override val label: String, override val dbId: DBId): Menu {
        override val pageSize: Int = 20
    }
    data class Portfolio(override val label: String, override val dbId: DBId): Menu {
        override val pageSize: Int = 20
    }

    val label: String
    val dbId: DBId
    val pageSize: Int
}