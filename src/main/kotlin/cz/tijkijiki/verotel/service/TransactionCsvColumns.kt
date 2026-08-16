package cz.tijkijiki.verotel.service

/**
 * Sloupce výpisu v pořadí, ve kterém je čekáme v hlavičce.
 */
internal object CsvColumns {

    const val REFERENCE = "reference"
    const val TIMESTAMP = "timestamp"
    const val AMOUNT = "amount"
    const val CURRENCY = "currency"
    const val DESCRIPTION = "description"

    val ALL = listOf(REFERENCE, TIMESTAMP, AMOUNT, CURRENCY, DESCRIPTION)
}
