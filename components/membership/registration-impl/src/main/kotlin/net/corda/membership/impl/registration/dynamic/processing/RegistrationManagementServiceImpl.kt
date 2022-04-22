package net.corda.membership.impl.registration.dynamic.processing

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
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
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.RegistrationManagementService
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMANDS
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [RegistrationManagementService::class])
class RegistrationManagementServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider
) : RegistrationManagementService {

    companion object {
        val logger = contextLogger()

        const val CONSUMER_GROUP = "membership_registration_processor"
        const val CLIENT_ID = "membership_registration_processor"
    }

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<RegistrationManagementService>(::handleEvent)

    private var registrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    private var subscription: StateAndEventSubscription<String, RegistrationState, RegistrationCommand>? = null

    private var databaseSender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>? = null

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
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Processing start event.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
                coordinator.updateStatus(LifecycleStatus.DOWN, "Received stop event.")
                registrationHandle?.close()
                configHandle?.close()
                subscription?.close()
                databaseSender?.close()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Dependency services are UP. Registering to receive configuration.")
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(MESSAGING_CONFIG, BOOT_CONFIG)
                    )
                } else {
                    logger.info("Setting deactive state due to receiving registration status ${event.status}")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    subscription?.close()
                    databaseSender?.close()
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.toMessagingConfig()
                databaseSender?.close()
                databaseSender = publisherFactory.createRPCSender(
                    RPCConfig(
                        groupName = CONSUMER_GROUP,
                        clientName = CLIENT_ID,
                        requestTopic = Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                        requestType = MembershipPersistenceRequest::class.java,
                        responseType = MembershipPersistenceResponse::class.java
                    ),
                    event.config.toMessagingConfig()
                ).also { it.start() }
                subscription?.close()
                subscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(
                        CONSUMER_GROUP,
                        REGISTRATION_COMMANDS
                    ),
                    RegistrationProcessor(
                        layeredPropertyMapFactory,
                        memberInfoFactory,
                        membershipGroupReaderProvider,
                        databaseSender!!
                    ),
                    messagingConfig
                ).also { it.start() }
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

}

