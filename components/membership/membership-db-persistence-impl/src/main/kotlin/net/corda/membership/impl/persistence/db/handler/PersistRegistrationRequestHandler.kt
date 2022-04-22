package net.corda.membership.impl.persistence.db.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.RegistrationRequestEntity
import java.time.Instant

class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest): Any? {
        logger.info("Persisting registration request with ID [${request.registrationRequest.registrationId}].")
        transaction(context) { em ->
            val now = Instant.now()
            em.persist(
                RegistrationRequestEntity(
                    request.registrationRequest.registrationId,
                    context.holdingIdentityId,
                    request.status.toString(),
                    now,
                    now,
                    request.registrationRequest.memberContext.array()
                )
            )
            em.flush()
        }
        return null
    }
}
