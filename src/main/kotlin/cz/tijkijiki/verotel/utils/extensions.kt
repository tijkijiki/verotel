package cz.tijkijiki.verotel.utils

import cz.tijkijiki.verotel.error.VerotelException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun requireOrBadRequest(condition: Boolean, message: () -> String) {
    contract { returns() implies condition }

    if (!condition) {
        throw VerotelException.badRequest(message())
    }
}