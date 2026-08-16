package cz.tijkijiki.verotel.error

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/** Stav i zprávu si nese sama výjimka, tady se jen převedou na odpověď. */
@Produces
@Singleton
@Requires(classes = [ExceptionHandler::class])
class VerotelExceptionHandler : ExceptionHandler<VerotelException, HttpResponse<ErrorResponse>> {

    override fun handle(request: HttpRequest<*>, exception: VerotelException): HttpResponse<ErrorResponse> =
        HttpResponse.status<ErrorResponse>(exception.status)
            .body(ErrorResponse(exception.message, exception.details))
}
