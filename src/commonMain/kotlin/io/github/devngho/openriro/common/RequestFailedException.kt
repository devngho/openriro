package io.github.devngho.openriro.common

class RequestFailedException(val msg: String): Exception("요청이 실패했습니다: $msg")