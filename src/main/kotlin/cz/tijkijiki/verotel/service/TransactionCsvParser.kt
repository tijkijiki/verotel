package cz.tijkijiki.verotel.service

import cz.tijkijiki.verotel.domain.Transaction
import cz.tijkijiki.verotel.error.VerotelException
import cz.tijkijiki.verotel.utils.requireOrBadRequest
import jakarta.inject.Singleton
import org.apache.commons.csv.CSVFormat
import java.io.UncheckedIOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Převádí nahraný CSV výpis na transakce.
 *
 * Řeší soubor jako celek — kódování, hlavičku a duplicity uvnitř dávky. Jednotlivé
 * řádky rozebírá [TransactionRowParser].
 *
 */
@Singleton
class TransactionCsvParser(private val rowParser: TransactionRowParser) {

    fun parse(csv: ByteArray, charset: Charset = Charsets.UTF_8): List<Transaction> {
        val text = decode(csv, charset).removePrefix(UTF8_BOM)
        val header = text.lineSequence().firstOrNull { it.isNotBlank() }
                ?: throw VerotelException.badRequest("Soubor je prázdný.")
        requireOrBadRequest(columnNames(header) == CsvColumns.ALL) {
            "Neočekávaná hlavička souboru: '$header'. Očekáváno: ${CsvColumns.ALL.joinToString(DELIMITER.toString())}"
        }

        val records = try {
            CSV_FORMAT.parse(text.reader()).records
        } catch (e: UncheckedIOException) {
            throw VerotelException.badRequest("Soubor se nepodařilo přečíst jako CSV: ${e.message}")
        }

        requireOrBadRequest(records.isNotEmpty()) { "Soubor neobsahuje žádnou transakci." }

        val transactions = records.map { rowParser.parseRow(it) }
        rejectDuplicateReferences(transactions)
        return transactions
    }


    private fun decode(csv: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            decoder.decode(ByteBuffer.wrap(csv)).toString()
        } catch (e: CharacterCodingException) {
            throw VerotelException.badRequest(
                "Soubor není v kódování ${charset.name()}. Pošlete ho v UTF-8, " +
                    "nebo kódování uveďte v hlavičce, například: Content-Type: text/csv; charset=windows-1250"
            )
        }
    }

    private fun columnNames(header: String): List<String> =
        try {
            HEADER_FORMAT.parse(header.reader()).records
                .firstOrNull()
                ?.map { it.trim().lowercase() }
                ?: emptyList()
        } catch (e: UncheckedIOException) {
            emptyList()
        }


    private fun rejectDuplicateReferences(transactions: List<Transaction>) {
        val duplicates = transactions.groupingBy { it.reference }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()

        if (duplicates.isNotEmpty()) {
            throw VerotelException.conflict(
                "Soubor obsahuje tutéž referenci víckrát, dávka nebyla nahrána.",
                duplicates
            )
        }
    }

    private companion object {
        const val UTF8_BOM = "﻿"

        /** Jediný podporovaný oddělovač — soubor s jiným neprojde kontrolou hlavičky. */
        const val DELIMITER = ','

        /** Formát dat: hlavička se přeskakuje, sloupce se berou podle pozice. */
        val CSV_FORMAT: CSVFormat = CSVFormat.DEFAULT.builder()
            .setDelimiter(DELIMITER)
            .setHeader(*CsvColumns.ALL.toTypedArray())
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setIgnoreEmptyLines(true)
            .setTrim(false)
            .get()

        /** Pro přečtení samotné hlavičky, tedy bez pojmenovaných sloupců. */
        val HEADER_FORMAT: CSVFormat = CSVFormat.DEFAULT.builder()
            .setDelimiter(DELIMITER)
            .get()
    }
}
