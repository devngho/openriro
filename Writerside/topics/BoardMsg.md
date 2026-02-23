# BoardMsg

`BoardMsg`는 가정통신문처럼 `/board_msg.php`로 접근하는 메뉴를 나타냅니다. `Board`와 달리 `대상` 정보가 있으며, 게시글에 설문 양식이 포함될 수 있습니다.

## 게시글 목록 가져오기

먼저 `Menu.BoardMsg`를 가져온 뒤 `list`를 호출해 목록을 조회해봅시다.

```Kotlin
val menu = client.labeled<Menu.BoardMsg>("가정통신문(설문조사용)").getOrThrow()
val paging = menu.list().getOrThrow()
println(paging.get(0))
```

```Kotlin
WithClient(value=BoardMsgItem(dbId=DBId(value=1901), id=584, uid=Success(Uid(value=1960)), kind=제출, target=재학생, title=***, hasAttachments=false, author=***, reads=1017, date=2026-02-23))
```

여기서 `uid` 타입이 `Result<Uid>`라는 점을 주의하세요. 권한이 없는 경우 링크에서 `uid`를 얻을 수 없기 때문입니다.

## 상세 조회하기

목록 항목에서 바로 `get()`을 호출하면 상세를 가져올 수 있습니다.

```Kotlin
val detail = paging.get(0)?.get()?.getOrThrow() ?: return
println(detail)
```
```Kotlin
BoardMsgItemResponse(title=***, target=Students(classes=[101, 102, /* ... */]), attachments=[], author=***, reads=1019, writtenAt=2026-02-23T09:05:04, body=/* ... */, form=BoardMsgForm(period=0000-02-23T12:00..0000-02-27T06:00, applicants=***, name=***, id=***, questions=[BoardMsgFormQuestion(number=1, label=외박 설문조사, required=false, answer=Radio(options=[BoardMsgFormOption(value=***, selected=false), BoardMsgFormOption(value=***, selected=true), BoardMsgFormOption(value=***, selected=false)])), BoardMsgFormQuestion(number=2, label=***, required=false, answer=Radio(options=[BoardMsgFormOption(value=신청, selected=false), BoardMsgFormOption(value=미신청, selected=true)])), BoardMsgFormQuestion(number=3, label=***, required=false, answer=Radio(options=[BoardMsgFormOption(value=신청, selected=false), BoardMsgFormOption(value=미신청, selected=true)]))], isSubmitEnabled=true)
```

위는 `form`이 주어진 경우입니다.

## 양식

양식의 질문은 다음과 같은 유형이 있습니다.

`Radio`
: {type=narrow} 일반적인 라디오 버튼으로, 하나의 옵션을 선택할 수 있습니다.

`Checkbox`
: {type=narrow} 일반적인 체크박스로, 여러 옵션을 선택할 수 있습니다.

`Text`
: {type=narrow} 텍스트 입력란입니다.


### 제출하기

양식이 주어진 게시글에 답변을 제출하려면 `submit` 함수를 사용하세요.

```Kotlin
it.form.submit {
    // 옵션 번호로 응답
    option<BoardMsgItem.BoardMsgFormAnswer.Radio>(0).set(1)
    // 옵션 값에 따라 응답
    option<BoardMsgItem.BoardMsgFormAnswer.Radio>(1).set("미신청")
    option<BoardMsgItem.BoardMsgFormAnswer.Radio>(2).set("미신청")
    
    // option<BoardMsgItem.BoardMsgFormAnswer.Text>(3).set("텍스트 응답")
    
    // 여러 옵션을 선택할 수 있는 체크박스의 경우, set 함수에 선택한 옵션 번호들을 전달하면 됩니다.
    // option<BoardMsgItem.BoardMsgFormAnswer.Checkbox>(4).set(1, 2)
    // 또는 옵션 값에 따라 선택할 수도 있습니다.
    // option<BoardMsgItem.BoardMsgFormAnswer.Checkbox>(4).set("옵션1", "옵션3")
    
    // 명시적으로 서명해야 합니다.
    sign()
}.getOrThrow()
```

`option` 함수로 질문 번호에 해당하는 양식 항목에 접근할 수 있습니다. 올바르지 않은 유형으로 접근하려고 하면 에러가 발생합니다. 답변을 설정한 뒤에는 반드시 `sign()` 함수를 호출해야 합니다.

> 서명은 기존 서명을 사용하며, 변경하려면 직접 웹사이트에서 변경해야 합니다.

`Radio`의 경우에는 `set` 함수에 선택한 옵션 번호(0부터)나 옵션 값(문자열)을 전달하면 됩니다. `Checkbox`의 경우에는 `set` 함수에 선택한 옵션 번호들을 전달하거나, 옵션 값에 따라 선택할 수도 있습니다. `Text`의 경우에는 `set` 함수에 텍스트 응답을 전달하면 됩니다.

반대로 답변을 삭제하려면 `delete` 함수를 사용하세요.

```Kotlin
it.form.delete().getOrThrow()
```
