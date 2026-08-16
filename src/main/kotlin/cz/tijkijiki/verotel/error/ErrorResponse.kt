package cz.tijkijiki.verotel.error

import io.micronaut.core.annotation.Introspected

/** Tělo chybové odpovědi. */
@Introspected
data class ErrorResponse(val message: String, val details: List<String> = emptyList())
