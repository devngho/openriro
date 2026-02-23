# Board

`Board`는 공지사항과 성적 등 `/board.php`로 접근할 수 있는 메뉴를 나타냅니다.

## 종류

`Board`는 `Board.Normal`과 `Board.Score`, `Board.Unknown`으로 나뉩니다. `Board.Normal`은 공지사항과 같이 일반적인 게시판을 나타내며, `Board.Score`는 성적 게시판을 나타냅니다. `Board.Unknown`은 아직 어떤 메뉴인지 파악되지 않은 게시판을 나타냅니다.

```Kotlin
val menu1 = client.labeled<Menu.Board.Normal>("공지사항").getOrThrow()
val menu2 = client.labeled<Menu.Board.Score>("성적조회").getOrThrow()
val menu3 = client.labeled<Menu.Board.Unknown>("성적조회").getOrThrow()
```

`list` 함수를 사용해 게시글 목록을 가져올 수 있지만, `Board.Unknown`의 경우에는 예외입니다. `Board.Unknown`을 사용하려면 반드시 구체화해야 합니다.

### 구체화하기

`labeled` 함수를 사용하면 `Board`의 타입을 명시할 수 있으므로 걱정하지 않아도 됩니다. 하지만 `menu` 함수나 `MenuList` 엔드포인트로 직접 메뉴 목록을 가져왔을 경우에는 `Board`의 타입이 `Unknown`이 됩니다. 이는 URL로 두 타입을 구분할 수 없기 때문입니다. 따라서 Board를 **구체화**해야 합니다. `materialize` 함수를 사용하세요.

```Kotlin
val menu = client.menu().getOrThrow().first { it.name == "공지사항" }
val board = menu.materialize<Menu.Board.Normal>().getOrThrow()
```

한번 `materialize`하면 그 인스턴스를 캐싱하므로 여러 번 사용해도 됩니다.

만약 `OpenRiroClient`를 사용하지 않고 직접 엔드포인트를 호출하는 경우라면, 직접 `Board` 엔드포인트를 호출해 보세요. 만약 `Board.Normal`이라면 요청이 성공할 것이고, `Board.Score`라면 `BoardKindMismatchException`이 발생할 것입니다.

## 게시글 목록 가져오기

`Board.Normal`의 게시글 목록을 가져와봅시다.

```Kotlin
val menu = client.labeled<Menu.Board.Normal>("공지사항").getOrThrow()
val paging = menu.list().getOrThrow()
println(paging.get(0)) // 첫 페이지의 첫 번째 게시글을 불러옵니다.
```

```kotlin
WithClient(value=BoardItem(dbId=DBId(value=1003), id=1870, uid=Uid(value=3235), kind=알림, title=입학식 행사 주차 안내, hasAttachments=true, author=교무부, reads=392, date=2026-02-20))
```

더 자세한 사용 방법은 [`Paged`](Paged.md)를 참고하세요.

## 성적 조회하기

`Board.Score`의 목록을 가져와봅시다.

```Kotlin
val menu = client.labeled<Menu.Board.Score>("성적조회").getOrThrow()
val paging = menu.list().getOrThrow()
println(paging.get(0..<paging.totalCount).map { it!!.value.name }) // 전체 게시글을 불러옵니다.
```
```
[2025년 1학년 2학기 종합성적, 2025년 1학년 2학기 기말고사, 2025년 1학년 10월 모의고사, ...]
```

이렇게, 성적의 경우 각각 시험이 조회됩니다. 구체적인 정보를 얻으려면 `get` 함수를 사용합니다.

```Kotlin
val item = paging.get(0)!!
val detail = item.get().getOrThrow()
```
```Kotlin
ScoreResponse(scoreOptions=[/*...*/], selectedOption=/*...*/, scores=[WithStanding(name=전과목 평균, score=***, standing=null, tiedStanding=null, candidates=null, percentile=null, grade=null), /*...*/, WithStanding(name=공통국어2(4), score=***, standing=***, tiedStanding=null, candidates=***, percentile=***, grade=***)], detailedScores=[ScoreDetailedItem(name=공통국어2(4), details=[ScoreDetailItem(name=1회고사(25%), score=***), ScoreDetailItem(name=2회고사(25%), score=***), /* ... */], weightedScore=***, rawScore=***, achievement=***, grade=***, standing=***, tiedStanding=null, candidates=***), /*...*/])
```

여기서 `scores`가 각 과목별 성적이고, `detailedScores`는 상세내역입니다. `detailedScores`는 없을 수도 있습니다. `WithStanding`은 내신처럼 석차가 제공되는 경우에 사용됩니다. 한편, 모의고사 등의 경우에는 대신 표준 점수가 제공되므로 `WithStandardScore`이 사용됩니다.