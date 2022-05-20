package net.corda.membership.impl.persistence.db.handler

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.membership.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

interface PersistenceHandler<REQUEST> {
    fun invoke(context: MembershipRequestContext, request: REQUEST): Any?
}

abstract class BasePersistenceHandler<REQUEST>(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : PersistenceHandler<REQUEST> {

    companion object {
        val logger = contextLogger()
    }

    private val dbConnectionManager get() = persistenceHandlerServices.dbConnectionManager
    private val jpaEntitiesRegistry get() = persistenceHandlerServices.jpaEntitiesRegistry
    val cordaAvroSerializationFactory get() = persistenceHandlerServices.cordaAvroSerializationFactory
    val memberInfoFactory get() = persistenceHandlerServices.memberInfoFactory

    fun <R> transaction(context: MembershipRequestContext, block: (EntityManager) -> R) {
        getEntityManagerFactory(context).transaction(block)
    }
    private fun getEntityManagerFactory(context: MembershipRequestContext): EntityManagerFactory {
        return dbConnectionManager.getOrCreateEntityManagerFactory(
            "vnode_vault_${context.holdingIdentityId}", // TEMP!!!!!! SHOULD BE A BETTER WAY TO GET THIS NAME
            DbPrivilege.DML,
            jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw java.lang.IllegalStateException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                )
        )
    }
}

data class PersistenceHandlerServices(
    val dbConnectionManager: DbConnectionManager,
    val jpaEntitiesRegistry: JpaEntitiesRegistry,
    val memberInfoFactory: MemberInfoFactory,
    val cordaAvroSerializationFactory: CordaAvroSerializationFactory
)