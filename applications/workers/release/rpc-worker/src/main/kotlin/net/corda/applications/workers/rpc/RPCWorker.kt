package net.corda.applications.workers.rpc

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getAdditionalConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for handling RPC requests. */
@Suppress("Unused")
@Component(service = [Application::class])
class RPCWorker @Activate constructor(
    @Reference(service = RPCProcessor::class)
    private val processor: RPCProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("RPC worker starting.")

        val params = getParams(args, RPCWorkerParams())
        if (printHelpOrVersion(params.defaultParams, RPCWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val config = getAdditionalConfig(params.defaultParams, smartConfigFactory)
        processor.start(params.defaultParams.instanceId, params.defaultParams.topicPrefix, config)
    }

    override fun shutdown() {
        logger.info("RPC worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the RPC worker are added here. */
private class RPCWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}