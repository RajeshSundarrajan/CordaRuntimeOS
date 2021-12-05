package net.corda.applications.examples.testclients

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.applications.common.ConfigHelper
import net.corda.applications.common.ConfigHelper.Companion.SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH
import net.corda.components.examples.publisher.RunChaosTestStringsPub
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.*

@Component
class ChaosTestStringsPubApp @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        // const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting publisher...")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val instanceId = parameters.instanceId?.toInt()
            var publisher: RunChaosTestStringsPub? = null

            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<ChaosTestStringsPubApp>() { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            publisher!!.start()
                        }
                        is StopEvent -> {
                            publisher?.stop()
                            shutdownOSGiFramework()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }

            publisher = RunChaosTestStringsPub(
                lifeCycleCoordinator!!,
                publisherFactory,
                instanceId,
                parameters.numberOfRecords.toInt(),
                parameters.numberOfKeys.toInt(),
                getBootstrapConfig(getKafkaPropertiesFromFile(parameters.kafkaProperties), instanceId),
                parameters.msgPrefix,
                parameters.msgDelayMs,
                parameters.logPubMsgs
            )

            lifeCycleCoordinator!!.start()
            consoleLogger.info("Finished publishing")
        }
    }

    override fun shutdown() {
        consoleLogger.info("Stopping publisher")
        log.info("Stopping application")
        lifeCycleCoordinator?.stop()
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    /*
    // Old
    private fun getBootstrapConfig(kafkaConnectionProperties: Properties?): SmartConfig {
        val bootstrapServer = getConfigValue(kafkaConnectionProperties, SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH)
        return smartConfigFactory.create(ConfigFactory.empty())
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, "")))
    }
     */

    // New version wrapping ConfigHelper
    // Looking to remove kafkaConnectionProperties
    // kafkaConnectionProperties currently provides:
    // TOPIC_PREFIX - covered by ConfigHelper.Companion.getBootstrapConfig() provides
    // SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH - covered by ConfigHelper.Companion.getBootstrapConfig() provides
    private fun getBootstrapConfig(kafkaConnectionProperties: Properties?, instanceId: Int?): SmartConfig {
        val bootstrapServer = getConfigValue(kafkaConnectionProperties, SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH)
        return smartConfigFactory.create(ConfigHelper.Companion.getBootstrapConfig(instanceId))
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, "")))
    }

    /*
    private fun getConfigValue(kafkaConnectionProperties: Properties?, path: String, default: String? = null): String {
        var configValue = System.getProperty(path)
        if (configValue == null && kafkaConnectionProperties != null) {
            configValue = kafkaConnectionProperties[path].toString()
        }

        if (configValue == null) {
            if (default != null) {
                return default
            }
            log.error(
                "No $path property found! " +
                    "Pass property in via --kafka properties file or via -D$path"
            )
            shutdown()
        }
        return configValue
    }
     */
    
    private fun getConfigValue(kafkaConnectionProperties: Properties?, path: String, default: String? = null): String {
        val configValue: String = ConfigHelper.Companion.getConfigValue(path, default)
        if 
        return configValue
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
            log.error(
                "No $path property found! " +
                        "Pass property in via --kafka properties file or via -D$path"
            )
            shutdown()
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

    @CommandLine.Option(
        names = ["--msgPrefix"],
        description = ["Message prefix string"]
    )
    var msgPrefix = "ChaosTestMessage"

    @CommandLine.Option(
        names = ["--msgDelayMs"],
        description = ["Optional delay between publishing messages in ms"]
    )
    var msgDelayMs: Long = 0

    @CommandLine.Option(
        names = ["--logPubMsgs"],
        description = ["Added published messages to INFO logging level. This could be very verbose!"]
    )
    var logPubMsgs: Boolean = false

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    var kafkaProperties: File? = null
}
