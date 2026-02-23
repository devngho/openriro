# Portfolio

`Portfolio`는 보고서 등 `/portfolio.php`로 접근할 수 있는 메뉴를 나타냅니다. `Portfolio`의 차이점은, 아래에 다시 여러 과제방이 존재한다는 점입니다.

## 과제방 목록 조회

먼저 과제방 목록을 조회해봅시다.

```kotlin
val menu = client.labeled<Menu.Portfolio>("교과활동").getOrThrow()
val paging = menu.list().getOrThrow()

val category = paging.get(0)?.value
println(category)
```
```Kotlin
WithClient(value=PortfolioItem(dbId=DBId(value=1551), id=2806, kind=마감, title=2025년 1학년 영어 - 2학기 영어 심화탐구 보고서, cate=Cate(value=107301), isSubmitted=true, hasAttachments=true, author=***, summitCount=344, timeRange=0000-10-13T08:30..0000-10-17T16:40))
```

**`timeRange`를 사용할 때 주의하세요.** 목록 화면에서는 연도를 확인할 수 없기 때문에 연도를 `0000`으로 사용했습니다. 상세 조회에서는 실제 연도가 포함된 `timeRange`를 얻을 수 있습니다.

## 과제 제출 조회

과제방 항목에서 `list()`를 호출하면 제출물 목록을 조회할 수 있습니다.

```kotlin
val categoryItem = paging.get(0) ?: return
val submissions = categoryItem.list().getOrThrow()

println(submissions.get(0))
```
```Kotlin
WithClient(value=PortfolioListItem(dbId=DBId(value=1551), cate=Cate(value=107301), id=344, isPrivate=true, title=***, uid=Uid(value=281920), isSubmitted=false, hasAttachments=true, author=***, summitedAt=2025-10-17T16:34:41, lastModifiedAt=2025-10-17T16:39:29, reads=6))
```

이제 각 제출의 세부 정보를 조회해봅시다.

```Kotlin
submissions.get(0)?.get()?.getOrThrow()
```
```Kotlin
PortfolioItemResponse(title=***, author=***, isSubmitted=true, commentCount=0, letterCount=1, reads=5, summitedAt=2025-10-17T14:01:05, lastModifiedAt=2025-10-17T14:01:05, body=.
<br>
<br>, attachments=[Attachment(name=***, file=Portfolio(db=DBId(value=1551), cate=Cate(value=107301), uid=Uid(value=281727), size=0.13M, lastModifiedAt=2025-10-17T14:01:05, fileNumber=0, fileCode=***))])
```

한편, 읽은 권한이 없는 제출물에 접근하려고 하면 `RequestFailedException`이 발생합니다.

```Kotlin
submissions.get(1)?.get()?.getOrThrow()
```
```javastacktrace
io.github.devngho.openriro.common.RequestFailedException: 요청이 실패했습니다: 본인(모둠)만 조회할 수 있습니다.
	at io.github.devngho.openriro.endpoints.PortfolioItem$execute$2.invokeSuspend(PortfolioItem.kt:59)
	...
```

## 연도 필터

엔드포인트를 직접 호출할 때는 `PortfolioRequest.year`를 사용할 수 있습니다.

- `year = 1`: 전체 연도
- 그 외 값: 특정 연도만 조회

```kotlin
val response = Portfolio.execute(
    api,
    Portfolio.PortfolioRequest(db = menu.value.dbId, page = 1, year = 1)
).getOrThrow()
```