package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import net.corda.p2p.app.UnauthenticatedMessage

abstract class UnauthenticatedMessageHandler : MessageHandler {
    override fun invoke(msg: Any): Record<*, *> {
        if(msg is UnauthenticatedMessage) {
            return invokeUnautheticatedMessage(msg)
        }
        else {
            throw UnsupportedOperationException(
                "Handler does not support message type. Only UnauthenticatedMessage is allowed."
            )
        }
    }

    abstract fun invokeUnautheticatedMessage(msg: UnauthenticatedMessage): Record<*, *>
}