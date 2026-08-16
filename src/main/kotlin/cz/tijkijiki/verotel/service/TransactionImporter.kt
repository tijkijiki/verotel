package cz.tijkijiki.verotel.service

import cz.tijkijiki.verotel.domain.Transaction
import cz.tijkijiki.verotel.error.VerotelException
import cz.tijkijiki.verotel.repository.TransactionRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional


@Singleton
open class TransactionImporter(private val repository: TransactionRepository) {

    @Transactional
    open fun importBatch(transactions: List<Transaction>): Int {
        val inserted = transactions.mapNotNull { repository.insertIgnoringConflict(it) }.toSet()
        val alreadyPresent = transactions.map { it.reference }.filterNot { it in inserted }

        if (alreadyPresent.isNotEmpty()) {
            throw VerotelException.conflict(
                "Některé transakce už v databázi jsou, dávka nebyla nahrána.",
                alreadyPresent.sorted()
            )
        }

        return inserted.size
    }
}
