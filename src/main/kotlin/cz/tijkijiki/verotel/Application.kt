package cz.tijkijiki.verotel

import io.micronaut.runtime.Micronaut.build

fun main(args: Array<String>) {
    build()
        .args(*args)
        .packages("cz.tijkijiki.verotel")
        .banner(false)
        .start()
}
