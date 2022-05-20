package net.corda.membership.impl.persistence.db

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MembershipEntities
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.persistence.db.MembershipDatabasePersistenceService
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.use
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.crypto.sha256Bytes
import net.corda.v5.membership.GROUP_ID
import net.corda.v5.membership.MEMBER_STATUS_ACTIVE
import net.corda.v5.membership.PARTY_NAME
import net.corda.v5.membership.PLATFORM_VERSION
import net.corda.v5.membership.PROTOCOL_VERSION
import net.corda.v5.membership.SERIAL
import net.corda.v5.membership.SOFTWARE_VERSION
import net.corda.v5.membership.STATUS
import net.corda.v5.membership.URL_KEY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.util.UUID.randomUUID
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipPersistenceTest {
    companion object {
        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var membershipDatabasePersistenceService: MembershipDatabasePersistenceService

        @InjectService(timeout = 5000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 5000)
        lateinit var dbConnectionManager: DbConnectionManager

        @InjectService(timeout = 5000)
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        val logger = contextLogger()

        private const val BOOT_CONFIG_STRING = """
            $INSTANCE_ID = 1
            $BUS_TYPE = INMEMORY
        """

        private const val MEMBER_CONTEXT_KEY = "key"
        private const val MEMBER_CONTEXT_VALUE = "value"

        private val groupId = randomUUID().toString()
        private val x500Name = MemberX500Name.parse("O=Alice, C=GB, L=London")
        private val viewOwningHoldingIdentity = HoldingIdentity(x500Name.toString(), groupId)
        private val holdingIdentityId: String =
            randomUUID().toString().toByteArray().sha256Bytes().toHexString().take(12)

        private val vnodeDbInfo = TestDbInfo(
            name = "vnode_vault_$holdingIdentityId",
            schemaName = DbSchema.VNODE
        )
        private val clusterDbInfo = TestDbInfo.createConfig()

        private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        private val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))
        private val dbConfig = smartConfigFactory.create(clusterDbInfo.config)

        private lateinit var vnodeEmf: EntityManagerFactory
        private lateinit var rpcSender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>
        private lateinit var cordaAvroSerializer: CordaAvroSerializer<KeyValuePairList>
        private lateinit var cordaAvroDeserializer: CordaAvroDeserializer<KeyValuePairList>

        private val testClock: Clock = UTCClock()


        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MembershipPersistenceTest> { e, c ->
                when (e) {
                    is StartEvent -> {
                        logger.info("Starting test coordinator")
                        c.followStatusChangesByName(
                            setOf(
                                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                                LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                                LifecycleCoordinatorName.forComponent<MembershipDatabasePersistenceService>(),
                            )
                        )
                    }
                    is RegistrationStatusChangeEvent -> {
                        logger.info("Test coordinator is ${e.status}")
                        c.updateStatus(e.status)
                    }
                    else -> {
                        logger.info("Received and ignored event $e.")
                    }
                }
            }
            coordinator.start()
            cordaAvroSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            cordaAvroDeserializer =
                cordaAvroSerializationFactory.createAvroDeserializer({ }, KeyValuePairList::class.java)
            val dbInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            vnodeEmf = dbInstaller.setupDatabase(vnodeDbInfo, "vnode-vault", MembershipEntities.classes)
            dbInstaller.setupClusterDatabase(clusterDbInfo, "config", ConfigurationEntities.classes).close()

            entitiesRegistry.register(CordaDb.Vault.persistenceUnitName, MembershipEntities.classes)

            configurationReadService.startAndWait()
            dbConnectionManager.startAndWait()
            membershipDatabasePersistenceService.startAndWait()

            configurationReadService.bootstrapConfig(bootConfig)
            dbConnectionManager.bootstrap(dbConfig)

            rpcSender = publisherFactory.createRPCSender(
                RPCConfig(
                    "membership_persistence_test",
                    "membership_persistence_test_client",
                    Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                    MembershipPersistenceRequest::class.java,
                    MembershipPersistenceResponse::class.java
                ),
                messagingConfig = bootConfig
            ).also {
                it.start()
            }

            eventually {
                logger.info("Waiting for required services to start...")
                assertEquals(LifecycleStatus.UP, coordinator.status)
                logger.info("Required services started.")
            }
            dbConnectionManager.putConnection(
                name = vnodeDbInfo.name,
                privilege = DbPrivilege.DML,
                config = vnodeDbInfo.config,
                description = null,
                updateActor = "sa"
            )
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            if (::vnodeEmf.isInitialized) {
                vnodeEmf.close()
            }
        }

        private fun Lifecycle.startAndWait() {
            start()
            eventually(5.seconds) {
                assertTrue(isRunning)
            }
        }
    }

    @Test
    fun `registration requests can persist over RPC topic`() {
        val registrationId = randomUUID().toString()
        val status = RegistrationStatus.NEW
        val rpcRequest = MembershipPersistenceRequest(
            MembershipRequestContext(
                testClock.instant(),
                randomUUID().toString(),
                holdingIdentityId
            ),
            PersistRegistrationRequest(
                status,
                MembershipRegistrationRequest(
                    registrationId,
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(MEMBER_CONTEXT_KEY, MEMBER_CONTEXT_VALUE)
                                )
                            )
                        )
                    ) ?: fail("Failed to serialize KeyValuePairList"),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    )
                )
            )
        )
        val rpcResponse = assertDoesNotThrow {
            rpcSender.sendRequest(rpcRequest).getOrThrow(5.seconds)
        }
        assertThat(rpcResponse.context.holdingIdentityId).isEqualTo(rpcRequest.context.holdingIdentityId)
        assertThat(rpcResponse.context.requestId).isEqualTo(rpcRequest.context.requestId)
        assertThat(rpcResponse.context.requestTimestamp).isEqualTo(rpcRequest.context.requestTimestamp)
        assertThat(rpcResponse.context.responseTimestamp).isAfter(rpcRequest.context.requestTimestamp)
        assertThat(rpcResponse.success).isTrue
        assertThat(rpcResponse.payload).isNull()

        val persistedEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityId).isEqualTo(holdingIdentityId)
        assertThat(persistedEntity.status).isEqualTo(status.toString())
        assertThat(persistedEntity.created)
            .isAfterOrEqualTo(rpcRequest.context.requestTimestamp)
            .isBeforeOrEqualTo(rpcResponse.context.responseTimestamp)
        assertThat(persistedEntity.lastModified)
            .isAfterOrEqualTo(rpcRequest.context.requestTimestamp)
            .isBeforeOrEqualTo(rpcResponse.context.responseTimestamp)

        val persistedMemberContext = persistedEntity.context.deserializeContextAsMap()
        with(persistedMemberContext.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(MEMBER_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(MEMBER_CONTEXT_VALUE)
        }
    }

    @Test
    fun `member infos can persist over RPC topic`() {
        val groupId = randomUUID().toString()
        val memberx500Name = MemberX500Name.parse("O=Alice, C=GB, L=London")
        val endpointUrl = "http://localhost:8080"
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberx500Name.toString()),
                KeyValuePair(PLATFORM_VERSION, "11"),
                KeyValuePair(SERIAL, "1"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0")
            )
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE)
            )
        )

        val rpcRequest = MembershipPersistenceRequest(
            MembershipRequestContext(
                testClock.instant(),
                randomUUID().toString(),
                holdingIdentityId
            ),
            PersistMemberInfo(
                listOf(
                    PersistentMemberInfo(
                        viewOwningHoldingIdentity,
                        memberContext,
                        mgmContext
                    )
                )
            )
        )
        val rpcResponse = assertDoesNotThrow {
            rpcSender.sendRequest(rpcRequest).getOrThrow(5.seconds)
        }
        assertThat(rpcResponse.context.holdingIdentityId).isEqualTo(rpcRequest.context.holdingIdentityId)
        assertThat(rpcResponse.context.requestId).isEqualTo(rpcRequest.context.requestId)
        assertThat(rpcResponse.context.requestTimestamp).isEqualTo(rpcRequest.context.requestTimestamp)
        assertThat(rpcResponse.context.responseTimestamp).isAfterOrEqualTo(rpcRequest.context.requestTimestamp)
        assertThat(rpcResponse.success).isTrue
        assertThat(rpcResponse.payload).isNull()

        val persistedEntity = vnodeEmf.use {
            it.find(
                MemberInfoEntity::class.java, MemberInfoEntityPrimaryKey(
                    groupId, memberx500Name.toString()
                )
            )
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.groupId).isEqualTo(groupId)
        assertThat(persistedEntity.memberX500Name).isEqualTo(memberx500Name.toString())
        assertThat(persistedEntity.modifiedTime)
            .isAfterOrEqualTo(rpcRequest.context.requestTimestamp)
            .isBeforeOrEqualTo(rpcResponse.context.responseTimestamp)
        assertThat(persistedEntity.serialNumber).isEqualTo(1)
        assertThat(persistedEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)

        fun contextIsEqual(actual: String?, expected: String) = assertThat(actual).isNotNull.isEqualTo(expected)

        val persistedMgmContext = persistedEntity.mgmContext.deserializeContextAsMap()
        contextIsEqual(persistedMgmContext[STATUS], MEMBER_STATUS_ACTIVE)

        val persistedMemberContext = persistedEntity.memberContext.deserializeContextAsMap()
        with(persistedMemberContext) {
            contextIsEqual(get(String.format(URL_KEY, "0")), endpointUrl)
            contextIsEqual(get(String.format(PROTOCOL_VERSION, "0")), "1")
            contextIsEqual(get(GROUP_ID), groupId)
            contextIsEqual(get(PARTY_NAME), memberx500Name.toString())
            contextIsEqual(get(PLATFORM_VERSION), "11")
            contextIsEqual(get(SERIAL), "1")
            contextIsEqual(get(SOFTWARE_VERSION), "5.0.0")
        }
    }

    fun ByteArray.deserializeContextAsMap(): Map<String, String> =
        cordaAvroDeserializer.deserialize(this)
            ?.items
            ?.associate { it.key to it.value } ?: fail("Failed to deserialize context.")

}