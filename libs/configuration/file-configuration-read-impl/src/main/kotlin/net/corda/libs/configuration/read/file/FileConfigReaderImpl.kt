package net.corda.libs.configuration.read.file

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileConfigReaderImpl(
    private val configurationRepository: ConfigRepository,
    private val bootstrapConfig: Config
) : ConfigReader {

    companion object {
        private val log = contextLogger()

        @VisibleForTesting
        const val CONFIG_FILE_NAME = "config.file"
    }

    @Volatile
    private var stopped = false

    private val lock = ReentrantLock()

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            storeFileConfig()
            stopped = false
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                stopped = true
            }
        }
    }

    override fun registerCallback(configListener: ConfigListener): AutoCloseable {
        val sub = ConfigListenerSubscription()
        val configs = configurationRepository.getConfigurations()
        configListener.onUpdate(configs.keys, configs)
        return sub
    }

    private fun storeFileConfig() {
        val config = parseConfigFile()
        for (packageKey in config.root().keys) {
            val packageConfig = config.getConfig(packageKey)
            for (componentKey in packageConfig.root().keys) {
                configurationRepository.updateConfiguration("$packageKey.$componentKey", packageConfig.getConfig(componentKey))
            }
        }
    }

    private fun parseConfigFile(): Config {
        return try {
            val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
            val configFilePath = bootstrapConfig.getString(CONFIG_FILE_NAME)
            ConfigFactory.parseURL(File(configFilePath).toURI().toURL(), parseOptions).resolve()
        } catch (e: ConfigException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        } catch (e: IOException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        }
    }

    private class ConfigListenerSubscription : AutoCloseable {
        override fun close() {
            // do nothing
        }
    }
}

