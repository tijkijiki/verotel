package cz.tijkijiki.verotel.controller

import cz.tijkijiki.verotel.domain.Transaction
import cz.tijkijiki.verotel.error.VerotelException
import cz.tijkijiki.verotel.repository.TransactionRepository
import cz.tijkijiki.verotel.service.TransactionCsvParser
import cz.tijkijiki.verotel.service.TransactionImporter
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.views.View
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Nahrání výpisu transakcí:
 *
 *     curl -X POST --data-binary @transactions-2023-01-11.csv http://localhost:5000/transactions
 */
@Controller("/transactions")
class TransactionController(
    private val parser: TransactionCsvParser,
    private val importer: TransactionImporter,
    private val repository: TransactionRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Výpis transakcí od nejnovější; seřazené je vrací už databáze. */
    @Get(produces = [MediaType.TEXT_HTML])
    @View("transactions")
    fun list(): Map<String, Any> {
        val transactions = repository.findAllOrderByOccurredAtDesc().map { TransactionRow.of(it) }

        return mapOf(
            "transactions" to transactions,
            "highlightIndex" to indexOfLargestIncome(transactions)
        )
    }

    /**
     * Pořadí řádku, který se má zvýraznit, nebo -1, když není co zvýraznit.
     *
     * Hledá se největší příjem, takže záporné maximum se ignoruje — u samých výdajů
     * by jinak zvýraznění padlo na nejmenší z nich a tvrdilo o něm, že je to příjem.
     */
    private fun indexOfLargestIncome(rows: List<TransactionRow>): Int {
        val largest = rows.indices.maxByOrNull { rows[it].amount } ?: return -1

        return if (rows[largest].amount > BigDecimal.ZERO) largest else -1
    }

    @Post(consumes = [MediaType.ALL])
    @Produces(MediaType.APPLICATION_JSON)
    fun upload(request: HttpRequest<*>, @Body csv: ByteArray): HttpResponse<ImportSummary> {
        val declaredCharset = try {
            request.contentType.flatMap { it.charset }.orElse(Charsets.UTF_8)
        } catch (e: Exception) {
            log.warn("Nepoužitelné kódování v hlavičce Content-Type: ${request.headers.get("Content-Type")}", e)
            throw VerotelException.badRequest("Nezname kodovani")
        }
        val transactions = parser.parse(csv, declaredCharset)
        val imported = importer.importBatch(transactions)
        return HttpResponse.ok(ImportSummary(imported))
    }

    @Introspected
    data class ImportSummary(val imported: Int)

    /**
     * Řádek tabulky. Datum a částka se formátují tady, aby šablona jen vypisovala
     * hotové řetězce a nemusela na to mít vlastní nástroje.
     */
    @Introspected
    data class TransactionRow(
        val occurredAt: String,
        val occurredAtIso: String,
        val amount: BigDecimal,
        val formattedAmount: String,
        val description: String
    ) {
        companion object {

            /**
             * HTTP neumí časovou zónu klienta sdělit, takže server formátuje pražský čas
             * a prohlížeč si ho podle [occurredAtIso] přepíše na svou zónu. Bez JavaScriptu
             * zůstane vidět pražský — proto je fallback tady, ne v šabloně.
             */
            private val ZONE = ZoneId.of("Europe/Prague")
            private val DATE_FORMAT = DateTimeFormatter.ofPattern("d. M. yyyy H:mm").withZone(ZONE)

            fun of(transaction: Transaction) = TransactionRow(
                occurredAt = DATE_FORMAT.format(transaction.occurredAt),
                occurredAtIso = transaction.occurredAt.toString(),
                amount = transaction.amount,
                formattedAmount = "${formatAmount(transaction.amount)} ${transaction.currency}",
                description = transaction.description ?: ""
            )

            /**
             * Vždy aspoň haléře, ale až čtyři místa, aby se u měn s tisícinami (BHD, KWD)
             * nic nezaokrouhlilo. Sloupec je `numeric(19,4)`, takže syrová hodnota by
             * jinak ukazovala `45000.0000`.
             *
             * Formátovač se vyrábí pokaždé znovu — `DecimalFormat` není thread-safe
             * a controller je singleton.
             */
            private fun formatAmount(amount: BigDecimal): String =
                DecimalFormat("#,##0.00##", DecimalFormatSymbols(Locale.forLanguageTag("cs")))
                    .format(amount)
        }
    }
}
