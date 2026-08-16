package cz.tijkijiki.verotel.repository

import cz.tijkijiki.verotel.domain.Transaction
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@JdbcRepository(dialect = Dialect.POSTGRES)
abstract class TransactionRepository(private val jdbc: JdbcOperations) : CrudRepository<Transaction, String> {

    /** Řazení dělá databáze — `ORDER BY occurred_at DESC` si Micronaut Data odvodí z názvu. */
    abstract fun findAllOrderByOccurredAtDesc(): List<Transaction>

    /**
     * Vloží dávku jedním dotazem a řádky, které už v databázi jsou, přeskočí.
     *
     * Vrací reference těch, které se opravdu vložily — `RETURNING` je jediný způsob,
     * jak to zjistit, protože `DO NOTHING` počet zasažených řádků nerozliší.
     *
     * Vkládá po [MAX_ROWS_PER_STATEMENT] řádcích: každý řádek zabere pět parametrů
     * a Postgres jich v jednom dotazu unese 65535.
     */
    open fun insertIgnoringConflicts(transactions: List<Transaction>): List<String> =
        transactions.chunked(MAX_ROWS_PER_STATEMENT).flatMap { insertChunk(it) }

    private fun insertChunk(chunk: List<Transaction>): List<String> {
        val sql = """
            INSERT INTO transactions (reference, occurred_at, amount, currency, description)
            VALUES ${chunk.joinToString(", ") { "(?, ?, ?, ?, ?)" }}
            ON CONFLICT (reference) DO NOTHING
            RETURNING reference
        """.trimIndent()

        return jdbc.prepareStatement(sql) { statement ->
            var parameter = 1

            chunk.forEach { transaction ->
                statement.setString(parameter++, transaction.reference)
                // OffsetDateTime v UTC, ne Timestamp — ten by se přepočítal podle zóny serveru
                statement.setObject(parameter++, OffsetDateTime.ofInstant(transaction.occurredAt, ZoneOffset.UTC))
                statement.setBigDecimal(parameter++, transaction.amount)
                statement.setString(parameter++, transaction.currency)
                statement.setString(parameter++, transaction.description)
            }

            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(rows.getString(1))
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ROWS_PER_STATEMENT = 1000
    }
}
