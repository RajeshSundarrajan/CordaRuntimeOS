package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AuthenticatedMessage

abstract class AuthenticatedMessageHandler : MessageHandler {
    override fun invoke(msg: Any): Record<*, *> {
        if (msg is AuthenticatedMessage) {
            return invokeAutheticatedMessage(msg)
        } else {
            throw UnsupportedOperationException(
                "Handler does not support message type. Only AuthenticatedMessage is allowed."
            )
        }
    }

    abstract fun invokeAutheticatedMessage(msg: AuthenticatedMessage): Record<*, *>
}

