package net.corda.applications.examples.publisher

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.components.examples.publisher.RunPublisher
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.FrameworkService
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.Properties

@Component
class DemoPublisher @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory) {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    fun activate(ctx : BundleContext) {
        val frameworkService = ctx.getService(ctx.getServiceReference(FrameworkService::class.java))

        consoleLogger.info("Starting publisher...")

        val parameters = CliParameters()
        @Suppress("SpreadOperator")
        CommandLine(parameters).parseArgs(*frameworkService.getArgs())

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            ctx.getBundle(0).stop()
        } else {
            val instanceId = parameters.instanceId?.toInt()
            var publisher: RunPublisher? = null

            lifeCycleCoordinator = coordinatorFactory.createCoordinator<DemoPublisher>(
            ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                log.info("LifecycleEvent received: $event")
                when (event) {
                    is StartEvent -> {
                        publisher!!.start()
                    }
                    is StopEvent -> {
                        publisher?.stop()
                        ctx.getBundle(0).stop()
                    }
                    else -> {
                        log.error("$event unexpected!")
                    }
                }
            }

            publisher = RunPublisher(
                lifeCycleCoordinator!!,
                publisherFactory,
                instanceId,
                parameters.numberOfRecords.toInt(),
                parameters.numberOfKeys.toInt(),
                getBootstrapConfig(getKafkaPropertiesFromFile(parameters.kafkaProperties))
            )

            lifeCycleCoordinator!!.start()
            consoleLogger.info("Finished publishing")
        }
    }

    fun deactivate() {
        consoleLogger.info("Stopping publisher")
        log.info("Stopping application")
        lifeCycleCoordinator?.stop()
    }

    private fun getBootstrapConfig(kafkaConnectionProperties: Properties?): Config {
        val bootstrapServer = getConfigValue(kafkaConnectionProperties, KAFKA_BOOTSTRAP_SERVER)
        return ConfigFactory.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, "")))
    }

    private fun getConfigValue(kafkaConnectionProperties: Properties?, path: String, default: String? = null): String {
        var configValue = System.getProperty(path)
        if (configValue == null && kafkaConnectionProperties != null) {
            configValue = kafkaConnectionProperties[path].toString()
        }

        if (configValue == null) {
            if (default != null) {
                return default
            }
            throw IllegalArgumentException("No $path property found! " +
                    "Pass property in via --kafka properties file or via -D$path")
        }
        return configValue
    }

    private fun getKafkaPropertiesFromFile(kafkaPropertiesFile: File?): Properties? {
        if (kafkaPropertiesFile == null) {
            return null
        }

        val kafkaConnectionProperties = Properties()
        kafkaConnectionProperties.load(FileInputStream(kafkaPropertiesFile))
        return kafkaConnectionProperties
    }

}

class CliParameters {
    @CommandLine.Option(
        names = ["--instanceId"],
        description = ["InstanceId for a transactional publisher, leave blank to use async publisher"]
    )
    var instanceId: String? = null

    @CommandLine.Option(names = ["--numberOfRecords"], description = ["Number of records to send per key."])
    lateinit var numberOfRecords: String

    @CommandLine.Option(
        names = ["--numberOfKeys"],
        description = ["Number of keys to use. total records sent = numberOfKeys * numberOfRecords"]
    )
    lateinit var numberOfKeys: String

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    var kafkaProperties: File? = null
}
