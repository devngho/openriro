package io.github.devngho.openriro.common

import kotlin.reflect.KClass

@InternalApi
class BoardKindMismatchException(val expected: KClass<out Menu.Board>, val actual: KClass<out Menu.Board>): Exception("보드 종류가 일치하지 않습니다. 예상: ${expected.simpleName}, 실제: ${actual.simpleName}")