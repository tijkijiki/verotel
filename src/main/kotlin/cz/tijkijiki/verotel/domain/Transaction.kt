package cz.tijkijiki.verotel.domain

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.math.BigDecimal
import java.time.Instant

/**
 * Jedna bankovní transakce z nahraného výpisu.
 *
 * [reference] je business klíč z výpisu — podle něj se pozná, že už transakci máme.
 */
@MappedEntity("transactions")
data class Transaction(
    @field:Id val reference: String,
    val occurredAt: Instant,
    val amount: BigDecimal,
    val currency: String,
    val description: String?
)
