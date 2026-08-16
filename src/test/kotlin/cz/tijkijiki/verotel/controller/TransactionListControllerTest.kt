package cz.tijkijiki.verotel.controller

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import javax.sql.DataSource

/**
 * Výpis transakcí na `GET /transactions`.
 *
 */
@MicronautTest(transactional = false)
class TransactionListControllerTest {

    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var injectedDataSource: DataSource

    /** Injektovaný DataSource je transakční proxy, testy sahají do DB mimo transakci. */
    private val dataSource: DataSource
        get() = DelegatingDataSource.unwrapDataSource(injectedDataSource)

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun clearTransactions() {
        execute("DELETE FROM transactions")
    }

    @Test
    fun `radi od nejnovejsi a zvyrazni nejvetsi prijem`() {
        // největší příjem záměrně není nejnovější, aby se pořadí a zvýraznění nedaly zaměnit
        insert("900001", "2023-04-01T06:00:00Z", "100", "Úrok")
        insert("900002", "2023-03-15T14:30:00Z", "45000", "Výplata za březen")
        insert("900003", "2023-02-02T08:15:00Z", "-1200", "Nájem")
        insert("900004", "2023-01-05T11:00:00Z", "3000", "Vratka daně")

        val rows = rowsOf(page())

        assertEquals(
            listOf("Úrok", "Výplata za březen", "Nájem", "Vratka daně"),
            rows.map { it.description },
            "seznam má jít od nejnovější transakce po nejstarší"
        )
        assertEquals(
            listOf(false, true, false, false),
            rows.map { it.highlighted },
            "zvýraznit se má právě největší příjem, ne nejnovější řádek"
        )
    }

    @Test
    fun `u zapronych vydaju nezvyrazni nic`() {
        // největší z výdajů je pořád ztráta — zvýraznit se nesmí nic
        insert("900005", "2023-05-01T10:00:00Z", "-50", "Trafika")
        insert("900006", "2023-05-02T10:00:00Z", "-900", "Servis")

        val rows = rowsOf(page())

        assertEquals(2, rows.size)
        assertTrue(rows.none { it.highlighted }, "u zapornych výdajů nemá být zvýrazněný žádný řádek")
    }

    @Test
    fun `prazdny seznam se vyrenderuje bez chyby`() {
        assertEquals(emptyList<Row>(), rowsOf(page()), "bez transakcí nemá tabulka mít žádný řádek")
    }

    private fun page(): String {
        val request = HttpRequest.newBuilder(URI.create("${server.url}/transactions"))
            .header("Accept", "text/html")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        assertEquals(200, response.statusCode())
        return response.body()
    }

    /** Řádky tabulky tak, jak je vidí prohlížeč — bez hlavičky, která má `th` místo `td`. */
    private fun rowsOf(html: String): List<Row> =
        Jsoup.parse(html)
            .select("table tr")
            .mapNotNull { tr ->
                val cells = tr.select("td")
                if (cells.isEmpty()) return@mapNotNull null // hlavička
                require(cells.size >= 3) {
                    "neočekávaná struktura řádku, čekaly se aspoň 3 buňky: ${tr.html()}"
                }
                Row(
                    description = cells[2].text().trim(),
                    highlighted = tr.hasClass(HIGHLIGHT_CLASS)
                )
            }

    private fun insert(reference: String, occurredAt: String, amount: String, description: String) =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO transactions (reference, occurred_at, amount, currency, description)
                VALUES (?, ?, ?, 'CZK', ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, reference)
                statement.setObject(2, OffsetDateTime.parse(occurredAt))
                statement.setBigDecimal(3, BigDecimal(amount))
                statement.setString(4, description)
                statement.executeUpdate()
            }
        }

    private fun execute(sql: String) =
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }

    private data class Row(
        val description: String,
        val highlighted: Boolean
    )

    private companion object {
        const val HIGHLIGHT_CLASS = "largest-income"
    }
}