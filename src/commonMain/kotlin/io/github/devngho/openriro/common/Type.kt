package io.github.devngho.openriro.common

import kotlin.jvm.JvmInline

/**
 * 게시판 DB 번호
 */
@JvmInline
value class DBId(val value: Int)

/**
 * 게시글/항목 고유 번호
 */
@JvmInline
value class Uid(val value: Int)

/**
 * 카테고리(과제방) 번호
 */
@JvmInline
value class Cate(val value: Int)