package net.corda.membership.impl.persistence.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.impl.persistence.MembershipPersistenceException

class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistMemberInfo>(persistenceHandlerServices) {

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> = cordaAvroSerializationFactory.createAvroSerializer {
        logger.error("Failed to serialize key value pair list.")
    }

    private fun serializeContext(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo): Any? {
        if (request.members.isNotEmpty()) {
            logger.info("Persisting member information.")
            transaction(context.holdingIdentityId) { em ->
                request.members.forEach {
                    val memberInfo = memberInfoFactory.createFromAvro(it)
                    val entity = MemberInfoEntity(
                        memberInfo.groupId,
                        memberInfo.name.toString(),
                        memberInfo.status,
                        clock.instant(),
                        serializeContext(it.memberContext),
                        serializeContext(it.mgmContext),
                        memberInfo.serial
                    )
                    em.persist(entity)
                }
                em.flush()
            }
        }
        return null
    }
}