package net.corda.membership.impl.persistence.db

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.MemberInfoFactory
import net.corda.membership.impl.persistence.db.handler.PersistMemberInfoHandler
import net.corda.membership.impl.persistence.db.handler.PersistRegistrationRequestHandler
import net.corda.membership.impl.persistence.db.handler.PersistenceHandler
import net.corda.membership.impl.persistence.db.handler.PersistenceHandlerServices
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.v5.base.util.contextLogger
import java.lang.reflect.Constructor
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class MembershipDatabasePersistenceRPCProcessor(
    dbConnectionManager: DbConnectionManager,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    memberInfoFactory: MemberInfoFactory
) : RPCResponderProcessor<MembershipPersistenceRequest, MembershipPersistenceResponse> {

    private companion object {
        val logger = contextLogger()
    }

    private val handlers: Map<Class<*>, Class<out PersistenceHandler<out Any>>> = mapOf(
        PersistRegistrationRequest::class.java to PersistRegistrationRequestHandler::class.java,
        PersistMemberInfo::class.java to PersistMemberInfoHandler::class.java,
    )
    private val constructors = ConcurrentHashMap<Class<*>, Constructor<*>>()
    private val persistenceHandlerServices = PersistenceHandlerServices(
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
    )

    override fun onNext(
        request: MembershipPersistenceRequest,
        respFuture: CompletableFuture<MembershipPersistenceResponse>
    ) {
        logger.info("Processor received new RPC persistence request. Selecting handler.")
        val requestClass = request.request::class.java
        val (responsePayload, success) = try {
            Pair(getHandler(requestClass).invoke(request.context, request.request), true)
        } catch (e: Exception) {
            logger.warn("Exception thrown while processing membership persistence request: ${e.message}")
            Pair(null, false)
        }
        respFuture.complete(
            MembershipPersistenceResponse(
                buildResponseContext(request.context),
                success,
                responsePayload
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(requestClass: Class<*>): PersistenceHandler<Any> {
        return constructors.computeIfAbsent(requestClass) {
            val type = handlers[requestClass] ?: throw MembershipPersistenceException(
                "No handler has been registered to handle the persistence request received." +
                        "Request received: [$requestClass]"
            )
            type.constructors.first {
                it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(PersistenceHandlerServices::class.java)
            }.apply { isAccessible = true }
        }.newInstance(persistenceHandlerServices) as PersistenceHandler<Any>
    }

    private fun buildResponseContext(requestContext: MembershipRequestContext): MembershipResponseContext {
        return with(requestContext) {
            MembershipResponseContext(requestTimestamp, requestId, Instant.now(), holdingIdentityId)
        }
    }
}
