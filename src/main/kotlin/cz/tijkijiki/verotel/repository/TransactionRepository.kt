package cz.tijkijiki.verotel.repository

import cz.tijkijiki.verotel.domain.Transaction
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TransactionRepository : CrudRepository<Transaction, String> {

    /** Řazení dělá databáze — `ORDER BY occurred_at DESC` si Micronaut Data odvodí z názvu. */
    fun findAllOrderByOccurredAtDesc(): List<Transaction>

    /**
     * Vloží transakci a řádek, který podle reference už v databázi je, nechá být.
     *
     * Vrací referenci jen tehdy, když se opravdu vložila — `RETURNING` je jediný způsob,
     * jak to zjistit, protože `DO NOTHING` počet zasažených řádků nerozliší.
     */
    @Query(
        """
        INSERT INTO transactions (reference, occurred_at, amount, currency, description)
        VALUES (:reference, :occurredAt, :amount, :currency, :description)
        ON CONFLICT (reference) DO NOTHING
        RETURNING reference
        """
    )
    fun insertIgnoringConflict(transaction: Transaction): String?
}
