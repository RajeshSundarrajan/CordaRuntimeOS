package net.corda.applications.workers.db.test

import java.io.InputStream
import net.corda.applications.workers.db.DBWorker
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.osgi.api.Shutdown
import net.corda.processors.db.DBProcessor
import net.corda.schema.configuration.BootConfig.BOOT_CPK_WRITE_INTERVAL
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.schema.configuration.BootConfig.BOOT_PERMISSION_SUMMARY_INTERVAL
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPK_WRITE_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS
import net.corda.v5.base.versioning.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.osgi.framework.Bundle

/**
 * Tests handling of command-line arguments for the [DBWorker].
 *
 * Since the behaviour is almost identical across workers, we do not have equivalent tests for the other worker types.
 */
class ConfigTests {

    @Test
    @Suppress("MaxLineLength")
    fun `instance ID, topic prefix, workspace dir, temp dir, messaging params, database params and additional params are passed through to the processor`() {
        val processor = DummyProcessor()
        val dbWorker = DBWorker(processor, DummyShutdown(), DummyHealthMonitor(), DummyValidatorFactory())
        val args = arrayOf(
            FLAG_INSTANCE_ID, VAL_INSTANCE_ID,
            FLAG_TOPIC_PREFIX, VALUE_TOPIC_PREFIX,
            FLAG_MSG_PARAM, "$MSG_KEY_ONE=$MSG_VAL_ONE",
            FLAG_DB_PARAM, "$DB_KEY_ONE=$DB_VAL_ONE"
        )

        dbWorker.startup(args)
        val config = processor.config!!

        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            WORKSPACE_DIR,
            TEMP_DIR,
            MSG_KEY_ONE,
            "$BOOT_DB_PARAMS.$DB_KEY_ONE",
            BOOT_PERMISSION_SUMMARY_INTERVAL,
            BOOT_CPK_WRITE_INTERVAL,
        )
        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)

        assertEquals(VAL_INSTANCE_ID.toInt(), config.getAnyRef(INSTANCE_ID))
        assertEquals(VALUE_TOPIC_PREFIX, config.getAnyRef(TOPIC_PREFIX))
        assertEquals(MSG_VAL_ONE, config.getAnyRef(MSG_KEY_ONE))
        assertEquals(DB_VAL_ONE, config.getAnyRef("$BOOT_DB_PARAMS.$DB_KEY_ONE"))

        assertEquals(
            ConfigDefaults.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS_DEFAULT,
            config.getLong(BOOT_PERMISSION_SUMMARY_INTERVAL)
        )
        assertEquals(
            ConfigDefaults.RECONCILIATION_CPK_WRITE_INTERVAL_MS_DEFAULT,
            config.getLong(BOOT_CPK_WRITE_INTERVAL)
        )
    }

    @Test
    fun `reconciliation params are passed through to the processor`() {
        val processor = DummyProcessor()
        val dbWorker = DBWorker(processor, DummyShutdown(), DummyHealthMonitor(), DummyValidatorFactory())
        val args = arrayOf(
            FLAG_RECONCILIATION_TASKS_PARAM,
            "$RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS=1234",
            FLAG_RECONCILIATION_TASKS_PARAM,
            "$RECONCILIATION_CPK_WRITE_INTERVAL_MS=5678"
        )
        dbWorker.startup(args)
        val config = processor.config!!

        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            WORKSPACE_DIR,
            TEMP_DIR,
            BOOT_PERMISSION_SUMMARY_INTERVAL,
            BOOT_CPK_WRITE_INTERVAL,
        )

        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)

        assertEquals(
            1234L,
            config.getLong(BOOT_PERMISSION_SUMMARY_INTERVAL)
        )
        assertEquals(
            5678L,
            config.getLong(BOOT_CPK_WRITE_INTERVAL)
        )
    }

    @Test
    fun `other params are not passed through to the processor`() {
        val processor = DummyProcessor()
        val dbWorker = DBWorker(processor, DummyShutdown(), DummyHealthMonitor(), DummyValidatorFactory())
        val args = arrayOf(
            FLAG_DISABLE_MONITOR,
            FLAG_MONITOR_PORT, "9999"
        )
        dbWorker.startup(args)
        val config = processor.config!!

        // Instance ID and topic prefix are always present, with default values if none are provided.
        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            WORKSPACE_DIR,
            TEMP_DIR,
            BOOT_PERMISSION_SUMMARY_INTERVAL,
            BOOT_CPK_WRITE_INTERVAL,
        )
        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)
    }

    @Test
    fun `defaults are provided for instance Id, topic prefix, workspace dir, temp dir and reconciliation`() {
        val processor = DummyProcessor()
        val dbWorker = DBWorker(processor, DummyShutdown(), DummyHealthMonitor(), DummyValidatorFactory())
        val args = arrayOf<String>()
        dbWorker.startup(args)
        val config = processor.config!!

        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            WORKSPACE_DIR,
            TEMP_DIR,
            BOOT_PERMISSION_SUMMARY_INTERVAL,
            BOOT_CPK_WRITE_INTERVAL,
        )
        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)

        // The default for instance ID is randomly generated, so its value can't be tested for.
        assertEquals(DEFAULT_TOPIC_PREFIX, config.getAnyRef(TOPIC_PREFIX))
    }

    @Test
    fun `multiple messaging params can be provided`() {
        val processor = DummyProcessor()
        val dbWorker = DBWorker(processor, DummyShutdown(), DummyHealthMonitor(), DummyValidatorFactory())
        val args = arrayOf(
            FLAG_MSG_PARAM, "$MSG_KEY_ONE=$MSG_VAL_ONE",
            FLAG_MSG_PARAM, "$MSG_KEY_TWO=$MSG_VAL_TWO"
        )
        dbWorker.startup(args)
        val config = processor.config!!

        assertEquals(MSG_VAL_ONE, config.getAnyRef(MSG_KEY_ONE))
        assertEquals(MSG_VAL_TWO, config.getAnyRef(MSG_KEY_TWO))
    }

    @Test
    fun `multiple database params can be provided`() {
        val processor = DummyProcessor()
        val dbWorker = DBWorker(processor, DummyShutdown(), DummyHealthMonitor(), DummyValidatorFactory())
        val args = arrayOf(
            FLAG_DB_PARAM, "$DB_KEY_ONE=$DB_VAL_ONE",
            FLAG_DB_PARAM, "$DB_KEY_TWO=$DB_VAL_TWO"
        )
        dbWorker.startup(args)
        val config = processor.config!!

        assertEquals(DB_VAL_ONE, config.getAnyRef("${BOOT_DB_PARAMS}.$DB_KEY_ONE"))
        assertEquals(DB_VAL_TWO, config.getAnyRef("${BOOT_DB_PARAMS}.$DB_KEY_TWO"))
    }

    /** A [DBProcessor] that stores the passed-in config in [config] for inspection. */
    private class DummyProcessor : DBProcessor {
        var config: SmartConfig? = null

        override fun start(bootConfig: SmartConfig) {
            this.config = bootConfig
        }

        override fun stop() = throw NotImplementedError()
    }

    /** A no-op [Shutdown]. */
    private class DummyShutdown : Shutdown {
        override fun shutdown(bundle: Bundle) = Unit
    }

    /** A no-op [HealthMonitor]. */
    private class DummyHealthMonitor : HealthMonitor {
        override fun listen(port: Int) = Unit
        override fun stop() = throw NotImplementedError()
    }

    private class DummyValidatorFactory : ConfigurationValidatorFactory {
        override fun createConfigValidator(): ConfigurationValidator = DummyConfigurationValidator()
    }

    private class DummyConfigurationValidator : ConfigurationValidator {
        override fun validate(key: String, version: Version, config: SmartConfig, applyDefaults: Boolean): SmartConfig =
            SmartConfigImpl.empty()

        override fun validateConfig(key: String, config: SmartConfig, schemaInput: InputStream) = Unit
    }
}