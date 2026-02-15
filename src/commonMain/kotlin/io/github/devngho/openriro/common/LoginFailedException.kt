package io.github.devngho.openriro.common

class LoginFailedException(val msg: String): Exception("로그인에 실패했습니다: $msg")