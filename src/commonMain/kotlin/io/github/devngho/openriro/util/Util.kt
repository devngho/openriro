package io.github.devngho.openriro.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
suspend fun <T> html(
    html: String,
    init: suspend Document.() -> T
): T {
    contract {
        // it guarantees that [init] is called exactly once, so we can assign uninitialized variables in it
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }

    return Ksoup.parse(html).init()
}