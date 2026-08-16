package cz.tijkijiki.verotel.error

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Poslední záchyt: bez něj se text neošetřené výjimky propíše rovnou do těla odpovědi
 * a klient dostane interní detail implementace.
 *
 * Micronaut vybírá handler podle nejužšího typu výjimky, takže [VerotelExceptionHandler]
 * má dál přednost — sem propadne jen to nečekané, včetně chyb databáze, které
 * [cz.tijkijiki.verotel.service.TransactionImporter] nepřeložil na [VerotelException].
 */
@Produces
@Singleton
@Requires(classes = [ExceptionHandler::class])
class UnexpectedExceptionHandler : ExceptionHandler<Throwable, HttpResponse<ErrorResponse>> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(request: HttpRequest<*>, exception: Throwable): HttpResponse<ErrorResponse> {
        log.error("Neošetřená chyba při zpracování {} {}", request.method, request.path, exception)
        return HttpResponse.serverError(ErrorResponse("Požadavek se nepodařilo zpracovat."))
    }
}
