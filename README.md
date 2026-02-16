[//]: # (// @formatter:off)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.devngho/openriro/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.devngho/openriro)
[![javadoc](https://javadoc.io/badge2/io.github.devngho/openriro/javadoc.svg)](https://javadoc.io/doc/io.github.devngho/openriro)
[![License MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://raw.githubusercontent.com/devngho/openriro/master/LICENSE)
# openriro

리로스쿨 스크래핑 wrapper 라이브러리입니다.

## 주의사항

> API가 수시로 변경될 수 있습니다.

> 스크래핑 특성상, 사이트 업데이트에 따라 라이브러리가 작동하지 않을 수 있습니다. 또한, 학교마다 다른 설정으로 인해 일부 학교에서는 제대로 작동하지 않을 수 있습니다. 적극적인 참여를 환영합니다.

## 사용하기

```kotlin
implementation("com.github.devngho:openriro:[VERSION]")
```

## 예시

```kotlin
val api = OpenRiroAPI(
    AuthConfig(UserType.STUDENT_OR_TEACHER, id, pw),
    RequestConfig(baseUrl) // https://[school].riroschool.kr
)

val client = OpenRiroClient(api) // facade

val menu1 = client.labeled<Menu.Board>("공지사항").getOrThrow()
val paging1 = menu1.list().getOrThrow()
println(paging1.get(0)) // 첫 페이지의 첫 번째 게시글을 불러옵니다.
// 각 페이지 단위로 불러오거나(.page(0)), 전체에서 인덱스로 불러올 수 있습니다(.get(0))

val menu2 = client.labeled<Menu.Portfolio>("교과활동").getOrThrow()
val category = menu2.list().getOrThrow().asFlow().filterNotNull().first { (it, _) ->
    // asFlow를 사용해 list를 순회하는 등의 작업을 할 수 있습니다.
    it.isSubmitted && it.summitCount >= 300
}
val paging2 = category.list().getOrThrow()
println(paging2.get(0)) // 교과활동 중 자신이 제출했고 제출자가 300명 이상인 가장 최근 과제의 첫 제출을 불러옵니다.

// 또는 수동으로 각각 endpoint를 호출할 수도 있습니다.

val request = BoardMsg.BoardMsgRequest(db = DBId(1901), page = 1)
val result = BoardMsg.execute(api, request)

println(it.list)
```

## 오픈 소스 라이선스
이 라이브러리는 MIT License를 사용합니다. 자세한 내용은 LICENSE 파일을 참조하세요.

다음은 사용하는 라이브러리의 라이선스입니다.
### Kotlin
- [Jetbrains/Kotlin](https://github.com/JetBrains/kotlin) - [Apache License Version 2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) - [Apache License Version 2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)
- [ktorio/ktor](https://github.com/ktorio/ktor) - [Apache License Version 2.0](https://github.com/ktorio/ktor/blob/main/LICENSE)
### Other
- [kotest](https://github.com/kotest/kotest) - [Apache License Version 2.0](https://github.com/kotest/kotest/blob/master/LICENSE)
- [Ksoup](https://github.com/fleeksoft/ksoup) - [MIT License](https://github.com/fleeksoft/ksoup/blob/master/LICENSE)
