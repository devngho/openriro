package io.github.devngho.openriro.util

import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import org.intellij.lang.annotations.Language
import java.nio.charset.Charset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <T> html(
    @Language("HTML") html: String,
    init: Doc.() -> T,
): T {
    contract {
        // it guarantees that [init] is called exactly once, so we can assign uninitialized variables in it
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }

    return htmlDocument(html).init()
}