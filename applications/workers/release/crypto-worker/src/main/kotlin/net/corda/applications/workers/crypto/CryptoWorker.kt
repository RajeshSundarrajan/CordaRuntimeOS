package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.crypto.CryptoDependenciesProcessor
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine
import picocli.CommandLine.Mixin

/** The worker for interacting with the key material. */
@Suppress("Unused")
@Component(service = [Application::class])
class CryptoWorker @Activate constructor(
    @Reference(service = CryptoProcessor::class)
    private val processor: CryptoProcessor,
    @Reference(service = CryptoDependenciesProcessor::class)
    private val dependenciesProcessor: CryptoDependenciesProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor] and [dependenciesProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Crypto worker starting.")
        JavaSerialisationFilter.install()

        val params = getParams(args, CryptoWorkerParams())
        if (printHelpOrVersion(params.defaultParams, CryptoWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val databaseConfig = PathAndConfig(DB_CONFIG, params.databaseParams)
        val config = getBootstrapConfig(params.defaultParams, listOf(databaseConfig))

        dependenciesProcessor.start(config)
        processor.start(config)
    }

    override fun shutdown() {
        logger.info("Crypto worker stopping.")
        processor.stop()
        dependenciesProcessor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the crypto worker are added here. */
private class CryptoWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-d", "--databaseParams"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()
}