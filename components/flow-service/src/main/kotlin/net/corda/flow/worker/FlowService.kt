package net.corda.flow.worker

import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.manager.FlowManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
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
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This component is a sketch of how the flow service might be structured using the configuration service and the flow
 * libraries to put together a component that reacts to config changes. It should be read as not a finished component,
 * but rather a suggestion of how to put together the pieces to create components.
 */
@Component(service = [FlowService::class])
class FlowService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = FlowManager::class)
    private val flowManager: FlowManager
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()

        private const val MESSAGING_KEY = "MESSAGING"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowService>(::eventHandler)

    private var registration: RegistrationHandle? = null

    private var configHandle: AutoCloseable? = null

    private var executor: FlowExecutor? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting flow runner component." }
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.close()
                }
            }
            is NewConfigurationReceived -> {
                executor?.stop()
                val newExecutor = FlowExecutor(coordinatorFactory, event.config, subscriptionFactory, flowManager)
                newExecutor.start()
                executor = newExecutor
            }
            is StopEvent -> {
                logger.debug { "Stopping flow runner component." }
                registration?.close()
                registration = null
            }
        }
    }

    @Suppress("TooGenericExceptionThrown")
    private fun onConfigChange(keys: Set<String>, config: Map<String, SmartConfig>) {
        if (MESSAGING_KEY in keys) {
            val newConfig = config[MESSAGING_KEY] ?: throw Exception("Configuration missing from map")
            coordinator.postEvent(NewConfigurationReceived(newConfig))
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}