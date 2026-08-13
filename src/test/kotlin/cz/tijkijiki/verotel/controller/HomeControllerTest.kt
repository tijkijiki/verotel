package cz.tijkijiki.verotel.controller

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest
class HomeControllerTest {

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `uvodni stranka obsahuje default pozdrav`() {
        val response = httpClient.toBlocking().retrieve(HttpRequest.GET<Any>("/"))

        assertTrue(response.contains("Hello World"))
    }
}
