package cz.tijkijiki.verotel.service

import cz.tijkijiki.verotel.domain.Transaction
import cz.tijkijiki.verotel.error.VerotelException
import cz.tijkijiki.verotel.utils.requireOrBadRequest
import jakarta.inject.Singleton
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Rozebírá jeden záznam výpisu na [Transaction].
 */
@Singleton
class TransactionRowParser {

    fun parseRow(row: CSVRecord): Transaction {
        val number = row.recordNumber // hlavička se přeskakuje, první transakce je záznam 1

        requireOrBadRequest(row.isConsistent) {
            "Záznam $number má ${row.size()} sloupců místo ${CsvColumns.ALL.size}."
        }

        return Transaction(
            reference = parseReference(row.get(CsvColumns.REFERENCE), number),
            occurredAt = parseTimestamp(row.get(CsvColumns.TIMESTAMP), number),
            amount = parseAmount(row.get(CsvColumns.AMOUNT), number),
            currency = parseCurrency(row.get(CsvColumns.CURRENCY), number),
            description = parseDescription(row.get(CsvColumns.DESCRIPTION), number)
        )
    }

    private fun parseReference(value: String, number: Long): String {
        val reference = value.trim()
        requireOrBadRequest(reference.isNotEmpty()) { "Záznam $number nemá referenci." }
        return reference
    }


    private fun parseTimestamp(value: String, number: Long): Instant =
        try {
            Instant.parse(value.trim())
        } catch (e: DateTimeParseException) {
            throw VerotelException.badRequest(
                "Záznam $number má neplatný čas '$value', očekáván formát 2023-01-11T03:00:01Z."
            )
        }


    //TODO validace castky (19,4)
    private fun parseAmount(value: String, number: Long): BigDecimal =
        try {
            BigDecimal(value.trim())
        } catch (e: NumberFormatException) {
            throw VerotelException.badRequest("Záznam $number má neplatnou částku '$value'.")
        }

    private fun parseCurrency(value: String, number: Long): String =
        value.trim().uppercase().takeIf { it.length == CURRENCY_CODE_LENGTH }
            ?: throw VerotelException.badRequest(
                "Záznam $number má neplatný kód měny '$value', očekáván tříznakový kód."
            )

    private fun parseDescription(value: String, number: Long): String? =
        value.trim().ifBlank { null }


    private companion object {
        const val CURRENCY_CODE_LENGTH = 3
    }
}
