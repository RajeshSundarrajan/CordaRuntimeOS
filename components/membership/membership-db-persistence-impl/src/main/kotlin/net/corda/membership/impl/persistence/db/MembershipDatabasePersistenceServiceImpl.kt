package net.corda.membership.impl.persistence.db

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.MemberInfoFactory
import net.corda.membership.persistence.db.MembershipDatabasePersistenceService
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [MembershipDatabasePersistenceService::class])
class MembershipDatabasePersistenceServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory
) : MembershipDatabasePersistenceService {

    private companion object {
        val logger = contextLogger()

        const val GROUP_NAME = "membership.db.persistence"
        const val CLIENT_NAME = "membership.db.persistence"
    }

    private val coordinator = coordinatorFactory.createCoordinator<MembershipDatabasePersistenceService>(::handleEvent)
    private var rpcSubscription: RPCSubscription<MembershipPersistenceRequest, MembershipPersistenceResponse>? = null

    private var registrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting component.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping component.")
        coordinator.stop()
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> {
                logger.info("Handling start event.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<DbConnectionManager>()
                    )
                )
            }
            is StopEvent -> {
                logger.info("Handling stop event.")
                coordinator.updateStatus(
                    LifecycleStatus.DOWN,
                    "Component received stop event."
                )
                registrationHandle?.close()
                configHandle?.close()
                rpcSubscription?.close()
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Handling registration changed event.")
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                )
            }
            is ConfigChangedEvent -> {
                logger.info("Handling config changed event.")
                rpcSubscription?.close()
                rpcSubscription = subscriptionFactory.createRPCSubscription(
                    rpcConfig = RPCConfig(
                        groupName = GROUP_NAME,
                        clientName = CLIENT_NAME,
                        requestTopic = Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                        requestType = MembershipPersistenceRequest::class.java,
                        responseType = MembershipPersistenceResponse::class.java
                    ),
                    responderProcessor = MembershipDatabasePersistenceRPCProcessor(
                        dbConnectionManager,
                        jpaEntitiesRegistry,
                        memberInfoFactory
                    ),
                    messagingConfig = event.config.toMessagingConfig()
                ).also {
                    it.start()
                }
                coordinator.updateStatus(
                    LifecycleStatus.UP,
                    "Received config and started RPC topic subscription."
                )
            }
        }
    }
}
