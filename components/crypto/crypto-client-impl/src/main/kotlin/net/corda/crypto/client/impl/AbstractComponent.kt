package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractComponent<RESOURCE: AutoCloseable>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    coordinatorName: LifecycleCoordinatorName,
    private val configurationReadService: ConfigurationReadService
) : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(coordinatorName, ::eventHandler)

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    var resources: RESOURCE? = null
        private set

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.stop()
    }

    protected open fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is StartEvent -> {
                logger.info("Registering for configuration updates.")
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(MESSAGING_CONFIG, BOOT_CONFIG)
                )
            }
            is StopEvent -> {
                configHandle?.close()
                configHandle = null
                deleteResources()
            }
            is ConfigChangedEvent -> {
                createResources(event)
                logger.info("Setting status UP.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                logger.warn("Unexpected event $event!")
            }
        }
    }

    private fun createResources(event: ConfigChangedEvent) {
        logger.info("Creating resources")
        val tmp = resources
        resources = allocateResources(event)
        tmp?.close()
    }

    private fun deleteResources() {
        logger.info("Closing resources")
        val tmp = resources
        resources = null
        tmp?.close()
    }

    protected abstract fun allocateResources(event: ConfigChangedEvent): RESOURCE
}