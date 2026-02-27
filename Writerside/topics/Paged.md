# Paged

`Paged`는 목록 데이터를 인덱스 기반으로 조회할 수 있는 인터페이스이며 [Flow](https://kotlinlang.org/docs/flow.html)를 구현합니다.

`menu.list()`가 반환하는 값이 `Paged`이며, 필요한 항목만 가져오거나(`get`), 미리 로딩하거나(`preload`), 전체를 `Flow`로 순회할 수 있습니다. 또한, 불러온 페이지는 캐싱되므로 성능을 향상시킬 수 있습니다.

## 성능 팁

- 많은 양의 데이터를 조회할 경우, `preloadAll`을 사용해 미리 로딩하는 것이 좋습니다. 이는 여러 페이지를 병렬로 불러와 전체 로딩 시간을 줄일 수 있습니다. `toList`를 사용하더라도 수동으로 `preloadAll`을 하는 것이 더 효율적입니다.
- `get`을 사용할 때는 범위를 사용해서 호출하는 것이 좋습니다. 예를 들어, `get(0..<100)`는 첫 100개의 항목을 담은 페이지를 병렬적으로 불러옵니다.

## get / preload

`get`은 범위를 벗어나면 `null`을 반환합니다.

```kotlin
val first = paging.get(0)
val maybeOutOfRange = paging.get(999999)

println(first)
println(maybeOutOfRange)
```

여러 개를 한 번에 가져올 수도 있습니다. 이는 병렬적으로 처리됩니다.

```kotlin
val firstFive = paging.get(0..4)
println(firstFive)
```

전체 목록을 가져오려면 `totalCount`와 같이 사용하세요.

```kotlin
val all = paging.get(0..<paging.totalCount)
println(all)
```

미리 로딩하려면 `preload` 또는 `preloadAll`을 사용합니다. 이 또한 페이지가 아닌 인덱스 단위임에 유의하세요.

```kotlin
paging.preload(0)
paging.preload(0..4)
paging.preloadAll()
```

## Flow로 사용하기

`Paged`는 `Flow`이기도 합니다. 따라서 `toList`나 `collect`와 같은 함수를 사용해 편리하게 전체 항목을 순회할 수 있습니다. 자동으로 다음 페이지를 `preload`하므로 직접 `get`하는 것보다 효율적일 수 있습니다.

```kotlin
paging.collect { item ->
    println(item)
}

val list = paging.toList()
```

또는 `first`와 같은 함수를 사용해 조건에 맞는 항목을 찾을 수도 있습니다.

```kotlin
val firstMatching = paging.first { it.someProperty == someValue }
```

자세한 내용은 [Flow](https://kotlinlang.org/docs/flow.html) 문서를 참조하세요.

## 캐시 무효화

`Paged`는 기본적으로 `OpenRiroClient`의 캐시 정책을 사용하지만, 필요에 따라 캐시를 무효화할 수도 있습니다. 예를 들어, 게시글 목록이 변경된 후에 다시 불러오고 싶다면 `invalidate` 함수를 호출하세요.

```kotlin
paging.invalidate()
```
