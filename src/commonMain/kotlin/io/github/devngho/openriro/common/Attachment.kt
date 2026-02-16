package io.github.devngho.openriro.common

import kotlinx.datetime.LocalDateTime

data class Attachment(
    val name: String,
    val file: File
) {
    sealed interface File {
        data class Portfolio(
            val db: DBId,
            val cate: Cate,
            val uid: Uid,
            val size: String,
            val lastModifiedAt: LocalDateTime,
            val fileNumber: Int,
            val fileCode: String
        ): File {
            override val downloadUrl: String = "/portfolio.php?action=down&db=${db.value}&cate=${cate.value}&uid=${uid.value}&file_num=$fileNumber&file_code=$fileCode"
        }

        data class Board(
            val db: DBId,
            val uid: Uid,
            val size: String,
            val fileNumber: Int,
            val fileCode: String
        ): File {
            override val downloadUrl: String = "/board.php?action=down&db=${db.value}&uid=${uid.value}&file_num=$fileNumber&file_code=$fileCode"
        }

        data class BoardMsg(
            val db: DBId,
            val uid: Uid,
            val filePath: String
        ): File {
            override val downloadUrl: String = "/board_msg.php?action=down&db=${db.value}&uid=${uid.value}&downfile=$filePath"
        }

        val downloadUrl: String
    }
}