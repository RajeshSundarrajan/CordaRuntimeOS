package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record

interface MessageHandler {
    fun invoke(msg: Any): Record<*, *>
}