# Timetable

`Timetable`은 특정한 출석부의 시간표를 나타냅니다.

## 시간표 목록 가져오기

시간표는 클라이언트에 `OpenRiroClient.timetable()`을 직접 사용해 가져올 수 있습니다.

```kotlin
val timetables = client.timetable().getOrThrow()
println(timetables)
```

## 시간표 가져오기

가져온 시간표 목록에서 특정한 시간표를 가져오려면 `get` 함수를 사용하세요.

```kotlin
val timetable = timetables.get(0)?.get().getOrThrow()

timetable.table.forEach {
    println(it.cols.joinToString("\t") { c -> "${c.name}(${c.teacher}, ${c.room}, ${c.seat})" })
}
```