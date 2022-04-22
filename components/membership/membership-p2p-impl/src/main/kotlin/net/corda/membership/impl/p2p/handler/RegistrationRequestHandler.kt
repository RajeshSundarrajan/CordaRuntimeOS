package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.StartRegistration
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMANDS
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda

class RegistrationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : UnauthenticatedMessageHandler() {
    companion object {
        val logger = contextLogger()
    }

    override fun invokeUnautheticatedMessage(msg: UnauthenticatedMessage): Record<String, StartRegistration> {
        logger.info("Received registration request. Issuing StartRegistration command.")
        return Record(
            REGISTRATION_COMMANDS,
            msg.header.source.toCorda().id,
            StartRegistration(
                msg.header.destination,
                msg.header.source,
                avroSchemaRegistry.deserialize(msg.payload)
            )
        )
    }
}