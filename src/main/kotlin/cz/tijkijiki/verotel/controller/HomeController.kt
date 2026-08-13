package cz.tijkijiki.verotel.controller

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.views.View

@Controller
class HomeController {

    @Get(produces = [MediaType.TEXT_HTML])
    @View("home")
    fun index(): Map<String, String> {
        return mapOf("message" to "Hello Verotel")
    }
}
