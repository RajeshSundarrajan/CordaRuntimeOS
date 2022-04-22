package net.corda.membership.impl.persistence.db.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.impl.persistence.db.MembershipDatabasePersistenceRPCProcessor
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import java.time.Instant

class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistMemberInfo>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo): Any? {
        if (request.members.isNotEmpty()) {
            logger.info("Persisting member information.")
            transaction(context) { em ->
                request.members.forEach {
                    val memberInfo = memberInfoFactory.createFromAvro(it)
                    val entity = MemberInfoEntity(
                        memberInfo.groupId,
                        memberInfo.name.toString(),
                        memberInfo.status,
                        Instant.now(),
                        it.memberContext.toByteBuffer().array(),
                        it.mgmContext.toByteBuffer().array(),
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