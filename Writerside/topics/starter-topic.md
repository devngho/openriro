# 시작하기

`openriro`는 [리로스쿨](https://riroschool.kr/)의 기능을 `Kotlin`에서 사용할 수 있도록 하는 스크래핑 wrapper 라이브러리입니다. `openriro`를 사용하면 리로스쿨에서
지원하지 않는 기능을 직접 구현해 사용하거나, 다음과 같은 작업을 할 수 있습니다.

- 특정한 유저의 게시글 목록을 가져오기
- 자신의 제출물과 첨부 파일을 다운로드하기
- 가정통신문을 자동으로 제출하기
- 이 외에도 대부분의 작업을 수행할 수 있습니다.

## 일러두기

> 해당 라이브러리는 리로스쿨의 공식 API가 아니며, 리로스쿨과는 무관한 제3자에 의해 개발된 라이브러리입니다. 따라서, 라이브러리 사용으로 인해 발생하는 문제에 대해서는 **책임지지 않습니다.** 충분한 주의를
> 기울여 사용하시기 바랍니다.
> {style=warning}

> API가 수시로 변경될 수 있습니다.

> 스크래핑 특성상, 사이트 업데이트에 따라 라이브러리가 작동하지 않을 수 있습니다. 또한, 학교마다 다른 설정으로 인해 일부 학교에서는 제대로 작동하지 않을 수 있습니다. 적극적인 참여를 환영합니다.

## 설치하기

`openriro`는 Maven Central에 배포되어 있으므로, 다음과 같이 의존성을 추가하여 사용할 수 있습니다. 최신 버전은 [GitHub](https://github.com/devngho/openriro)
에서 확인하세요.

```kotlin
implementation("io.github.devngho:openriro:[VERSION]")
```

## 사용하기

### 클라이언트 만들기

`openriro`를 사용하려면 먼저 `OpenRiroAPI` 객체를 만들어야 합니다.

`OpenRiroAPI`
: {type=medium} endpoints를 호출하기 위해서 필요한 객체로, 설정과 인증 정보를 담고 있습니다.

```kotlin
val api = OpenRiroAPI(
    AuthConfig(UserType.STUDENT_OR_TEACHER, id, pw),
    RequestConfig(baseUrl)
)
```

`id`와 `pw`는 리로스쿨 계정의 아이디와 비밀번호를 그대로 사용하면 됩니다. `baseUrl`은 `https://[school].riroschool.kr` 형태로, 학교에 따라 다릅니다.

`OpenRiroAPI` 객체를 만들었다면, 이제 `OpenRiroClient` 객체를 만들어봅시다. `OpenRiroClient`는 `OpenRiroAPI`를 보다 사용하기 쉽도록 한 객체로, 직접 엔드포인트를 호출하지 않아도 원하는 메뉴에 접근할 수 있도록 도와줍니다. 또한 캐싱 등의 기능을 제공합니다.

```kotlin
val client = OpenRiroClient(api)
```

### 공지사항 조회하기

> 아래 출력은 예시이며, 실제 결과와 다를 수 있습니다.

먼저 간단하게 **공지사항** 메뉴에 접근해봅시다. 그 전에 리로스쿨의 메뉴가 어떻게 구성되어 있는지 살펴봅시다.

/board.php (`Board.Normal`)
: {type=wide} 공지사항 등이 있는 게시판 메뉴입니다. `대상`이 없고, 설문조사 등이 없는 메뉴입니다.

/board_msg.php (`BoardMsg`)
: {type=wide} 가정통신문 등이 있는 게시판 메뉴입니다. `대상`이 있고, 설문조사가 가능한 메뉴입니다.

/portfolio.php (`Portfolio`)
: {type=wide} 보고서 등이 있는 메뉴입니다. 하위로 여러 과제가 있고, 각 과제마다 여러 제출물이 있는 메뉴입니다.

공지사항은 `Board.Normal` 메뉴에 있습니다. 메뉴를 가져오려면 `OpenRiroClient.labeled` 함수를 사용하면 됩니다.

```kotlin
val menu = client.labeled<Menu.Board.Normal>("공지사항").getOrThrow()
```

> `openriro`는 대부분의 함수에 `Result` 타입이 적용되어 있으며, 기본적으로 3회까지 재시도합니다.

이제 `list` 함수를 사용해 게시글 목록을 가져올 수 있습니다.

```kotlin
val paging = menu.list().getOrThrow()
```

> `openriro`의 목록은 `Paged` 객체를 사용하며, 인덱스 단위로 `get`하거나 `preload`할 수도 있고 `asFlow`를 사용해 [Flow](https://kotlinlang.org/docs/flow.html)로 변환할 수도 있습니다. 자세한 사용법은 [`Paged`](Paged.md)를 참고하세요.

먼저 첫 게시글을 가져와봅시다.

```kotlin
println(paging.get(0))
```
```kotlin
WithClient(value=BoardItem(dbId=DBId(value=1003), id=1870, uid=Uid(value=3235), kind=알림, title=입학식 행사 주차 안내, hasAttachments=true, author=교무부, reads=392, date=2026-02-20))
```

기본적으로 캐싱이 적용되어 있기 때문에, 같은 게시글을 다시 가져오면 캐시에서 불러옵니다.

```kotlin
println(measureTimedValue { paging.get(0) })
```
```kotlin
TimedValue(value=/* ... */, duration=25.9us)
```
