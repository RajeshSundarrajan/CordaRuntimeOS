package net.corda.introspiciere.server

import io.javalin.http.InternalServerErrorResponse

internal fun <R> wrapException(action: () -> R): R {
    try {
        return action()
    } catch (t: Throwable) {
        throw InternalServerErrorResponse(details = mapOf("Exception" to t.stackTraceToString()))
    }
}