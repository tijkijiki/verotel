package cz.tijkijiki.verotel.error

import io.micronaut.http.HttpStatus

/**
 * Chyba aplikace, kterou má vidět klient. Nese si stav odpovědi i zprávu,
 * takže překlad na HTTP je jediné přemapování v [VerotelExceptionHandler].
 *
 * [details] je volitelný výčet toho, čeho se chyba týká — třeba referencí transakcí,
 * kvůli kterým se dávka nenahrála.
 */
open class VerotelException(
    val status: HttpStatus,
    override val message: String,
    val details: List<String> = emptyList()
) : RuntimeException(message) {

    companion object {

        fun badRequest(message: String, details: List<String> = emptyList()): VerotelException =
            VerotelException(HttpStatus.BAD_REQUEST, message, details)

        fun conflict(message: String, details: List<String> = emptyList()): VerotelException =
            VerotelException(HttpStatus.CONFLICT, message, details)
    }
}
