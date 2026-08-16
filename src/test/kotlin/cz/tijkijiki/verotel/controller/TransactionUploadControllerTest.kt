package cz.tijkijiki.verotel.controller

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import javax.sql.DataSource

/**
 * Nahrání dávky transakcí:
 *
 *     curl -X POST --data-binary @transactions-2023-01-11.csv http://localhost:5000/transactions
 *
 *
 */
@MicronautTest(transactional = false)
class TransactionUploadControllerTest {

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
    fun `nahraje vypis a ulozi kazde pole spravne`() {
        val response = upload(csvFixture())

        assertEquals(200, response.statusCode())
        assertEquals(7, countTransactions())
        assertEquals(
            listOf("10000001", "10000002", "10000003", "10000004", "10000005", "10000006", "10000007"),
            queryList("SELECT reference FROM transactions ORDER BY reference") { it.getString(1) }
        )

        assertAmount("20000", amountOf("10000001"))
        assertEquals("CZK", currencyOf("10000001"))
        assertEquals(Instant.parse("2023-01-11T03:00:01Z"), occurredAt("10000001"), "čas se ukládá jako okamžik v UTC")
        assertNull(descriptionOf("10000001"), "prázdný popis se ukládá jako null")

        assertAmount("-12000", amountOf("10000006"))
        assertEquals(Instant.parse("2023-01-11T17:00:00Z"), occurredAt("10000006"))
        assertEquals("Servis Škoda Praha", descriptionOf("10000006"), "diakritika v popisu se nesmí poškodit")
        assertEquals("Lekárna Hradčanská", descriptionOf("10000002"))
        assertEquals("Šenkýrna", descriptionOf("10000004"))
    }

    @Test
    fun `castku ulozi v celem rozsahu sloupce`() {
        val vRozsahu = """
            reference,timestamp,amount,currency,description
            10000020,2023-01-14T08:00:00Z,-1234.56,CZK,Nákup
            10000021,2023-01-14T09:00:00Z,0.05,CZK,Úrok
            10000022,2023-01-14T10:00:00Z,-99.999,BHD,Bahrajnský dinár
            10000023,2023-01-14T11:00:00Z,999999999999999.9999,CZK,Na hraně sloupce
        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertEquals(200, upload(vRozsahu).statusCode())
        assertAmount("-1234.56", amountOf("10000020"))
        assertAmount("0.05", amountOf("10000021"))
        assertAmount("-99.999", amountOf("10000022"))
        assertAmount("999999999999999.9999", amountOf("10000023"))
        assertEquals(4, countTransactions())
    }

    @Test
    fun `rozumi dialektum CSV`() {
        // raw string tu nejde použít, obsah končí trojicí uvozovek
        val davkaSCarkou = (
            "reference,timestamp,amount,currency,description\n" +
                "10000030,2023-01-15T08:00:00Z,-50,CZK,\"Lekárna, Hradčanská\"\n" +
                "10000031,2023-01-15T09:00:00Z,-80,CZK,\"Kavárna \"\"U Pepy\"\"\"\n" +
                "10000032,2023-01-15T10:00:00Z,-90,CZK,\"Servis Škoda\nfaktura 2023/11\"\n"
            ).toByteArray(Charsets.UTF_8)

        assertEquals(200, upload(davkaSCarkou).statusCode())
        assertEquals("Lekárna, Hradčanská", descriptionOf("10000030"), "čárka v uvozovkách nesmí rozdělit sloupec")
        assertEquals("Kavárna \"U Pepy\"", descriptionOf("10000031"))
        assertEquals("Servis Škoda\nfaktura 2023/11", descriptionOf("10000032"), "uvozovkový konec řádku patří do popisu")
        assertEquals(3, countTransactions(), "víceřádkový popis nesmí vzniknout jako další transakce")
    }

    @Test
    fun `prijme telo i bez hlavicky content-type jako holy curl`() {
        // curl -X POST --data-binary @soubor.csv posílá application/x-www-form-urlencoded
        val response = upload(csvFixture(), contentType = "application/x-www-form-urlencoded")

        assertEquals(200, response.statusCode())
        assertEquals(7, countTransactions())
    }

    @Test
    fun `kodovani se ridi deklaraci klienta`() {
        val bezDeklarace = upload(csvFixtureIn(WINDOWS_1250))

        assertEquals(400, bezDeklarace.statusCode(), "vadné bajty se nesmí tiše nahradit U+FFFD")
        assertEquals(0, countTransactions())

        val nesmyslnyNazev = upload(csvFixture(), contentType = "text/csv; charset=takove-kodovani-neni")

        assertEquals(400, nesmyslnyNazev.statusCode(), "nesmyslný charset je chyba klienta, ne serveru")
        assertEquals(0, countTransactions())

        val sDeklaraci = upload(csvFixtureIn(WINDOWS_1250), contentType = "text/csv; charset=windows-1250")

        assertEquals(200, sDeklaraci.statusCode())
        assertEquals("Lekárna Hradčanská", descriptionOf("10000002"), "deklarované kódování se má respektovat")
    }

    @Test
    fun `davku s jiz existujici referenci odmitne a nic z ni neulozi`() {
        upload(csvFixture())

        val druhaDavka = """
            reference,timestamp,amount,currency,description
            10000008,2023-01-12T08:00:00Z,-50,CZK,Trafika
            10000003,2023-01-12T09:00:00Z,-999,CZK,Lidl znovu
            10000009,2023-01-12T10:00:00Z,-70,CZK,Kavárna
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val response = upload(druhaDavka)

        assertEquals(409, response.statusCode())
        assertEquals(7, countTransactions())
        assertEquals(0, countReferences("10000008", "10000009"), "z odmítnuté dávky se nesmí uložit ani nová transakce")
        assertAmount("-1337", amountOf("10000003")) // původní transakce zůstala nedotčená
    }

    @Test
    fun `duplicitu uvnitr souboru odmitne a pojmenuje ji`() {
        val davka = """
            reference,timestamp,amount,currency,description
            10000010,2023-01-13T08:00:00Z,-50,CZK,Trafika
            10000010,2023-01-13T09:00:00Z,-60,CZK,Trafika podruhé
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val response = upload(davka)

        assertEquals(409, response.statusCode())
        assertEquals(0, countTransactions())
        assertTrue(
            response.body().contains("Soubor obsahuje tutéž referenci víckrát"),
            "duplicita v souboru nesmí tvrdit, že transakci už máme v databázi; hláška byla: ${response.body()}"
        )
        assertTrue(
            response.body().contains("10000010"),
            "chyba má vyjmenovat duplicitní reference, byla: ${response.body()}"
        )
    }

    @Test
    fun `odmitne soubor s vadnou hlavickou`() {
        // data se mapují podle pozice, prohozený sloupec by uložil popis jako měnu
        val prohozeneSloupce = """
            reference,timestamp,amount,description,currency
            10000014,2023-01-13T08:00:00Z,-50,Trafika,CZK
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val chybejiciSloupec = """
            reference,timestamp,amount,currency
            10000015,2023-01-13T08:00:00Z,-50,CZK
        """.trimIndent().toByteArray(Charsets.UTF_8)

        listOf(prohozeneSloupce, chybejiciSloupec).forEach { davka ->
            val response = upload(davka)

            assertEquals(400, response.statusCode(), "vadná hlavička je chyba klienta")
            assertTrue(
                response.body().contains("hlavička"),
                "chyba má ukázat na hlavičku, byla: ${response.body()}"
            )
        }

        assertEquals(0, countTransactions())
    }

    @Test
    fun `vadny radek odmitne, ukaze na nej a nic neulozi`() {
        val vadneDavky = mapOf(
            "rozbité datum" to ("10000012,tohle-neni-datum,-60,CZK,Rozbity radek" to "Záznam 2"),
            "chybějící reference" to (",2023-01-13T09:00:00Z,-60,CZK,Bez reference" to "referenci"),
            "neplatná měna" to ("10000012,2023-01-13T09:00:00Z,-60,CZKK,Divná měna" to "měny"),
            "chybějící sloupec na řádku" to ("10000012,2023-01-13T09:00:00Z,-60,CZK" to "sloupců")
        )

        vadneDavky.forEach { (popis, radekAOcekavanyText) ->
            val (vadnyRadek, ocekavanyText) = radekAOcekavanyText
            val davka = """
                reference,timestamp,amount,currency,description
                10000011,2023-01-13T08:00:00Z,-50,CZK,Trafika
                $vadnyRadek
            """.trimIndent().toByteArray(Charsets.UTF_8)

            val response = upload(davka)

            assertEquals(400, response.statusCode(), "$popis má skončit chybou klienta")
            assertTrue(
                response.body().contains(ocekavanyText),
                "$popis má dát hlášku s '$ocekavanyText', byla: ${response.body()}"
            )
            assertEquals(0, countTransactions(), "$popis nesmí uložit ani řádky před ním")
        }

        val jenHlavicka = "reference,timestamp,amount,currency,description\n".toByteArray(Charsets.UTF_8)
        val response = upload(jenHlavicka)

        assertEquals(400, response.statusCode())
        assertTrue(
            response.body().contains("neobsahuje žádnou transakci"),
            "soubor bez transakcí má být pojmenovaný, hláška byla: ${response.body()}"
        )
    }

    private fun upload(csv: ByteArray, contentType: String = "text/csv"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("${server.url}/transactions"))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofByteArray(csv))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
    }

    private fun csvFixture(): ByteArray =
        javaClass.getResourceAsStream("/transactions-2023-01-11.csv")!!.readBytes()

    /** Tentýž výpis překódovaný — takhle vypadá export z Excelu na Windows. */
    private fun csvFixtureIn(charset: Charset): ByteArray =
        String(csvFixture(), Charsets.UTF_8).toByteArray(charset)

    private fun descriptionOf(reference: String): String? =
        queryOne("SELECT description FROM transactions WHERE reference = '$reference'") { it.getString(1) }

    private fun currencyOf(reference: String): String? =
        queryOne("SELECT currency FROM transactions WHERE reference = '$reference'") { it.getString(1) }

    private fun amountOf(reference: String): BigDecimal =
        queryOne("SELECT amount FROM transactions WHERE reference = '$reference'") { it.getBigDecimal(1) }!!

    /** BigDecimal.equals rozlišuje scale (20000 != 20000.0000), porovnáváme proto hodnotu. */
    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "očekávána částka $expected, uloženo $actual")

    private fun occurredAt(reference: String): Instant =
        queryOne("SELECT occurred_at FROM transactions WHERE reference = '$reference'") {
            it.getObject(1, OffsetDateTime::class.java).toInstant()
        }!!

    private fun countTransactions(): Int =
        queryOne("SELECT count(*) FROM transactions") { it.getInt(1) }!!

    private fun countReferences(vararg references: String): Int =
        queryOne(
            "SELECT count(*) FROM transactions WHERE reference IN (${references.joinToString(",") { "'$it'" }})"
        ) { it.getInt(1) }!!

    private fun <T> queryOne(sql: String, mapper: (ResultSet) -> T): T? =
        queryList(sql, mapper).firstOrNull()

    private fun <T> queryList(sql: String, mapper: (ResultSet) -> T): List<T> =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    buildList { while (rs.next()) add(mapper(rs)) }
                }
            }
        }

    private fun execute(sql: String) =
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }

    private companion object {
        val WINDOWS_1250: Charset = Charset.forName("windows-1250")
    }
}
