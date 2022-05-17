package net.corda.membership.impl.p2p

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.impl.p2p.MembershipP2PProcessor.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMANDS
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipP2PIntegrationTest {

    companion object {
        @InjectService(timeout = 5000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var subscriptionFactory: SubscriptionFactory

        @InjectService(timeout = 5000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 5000)
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000)
        lateinit var membershipP2PReadService: MembershipP2PReadService

        @InjectService
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        val logger = contextLogger()

        private const val BOOT_CONFIG_STRING = """
            ${MessagingConfig.Boot.INSTANCE_ID} = 1
            ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
        """
        private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        private val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))

        private lateinit var p2pSender: Publisher
        private lateinit var registrationRequestSerializer: CordaAvroSerializer<MembershipRegistrationRequest>
        private lateinit var keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList>
        private lateinit var keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList>

        const val MEMBER_CONTEXT_KEY = "key"
        const val MEMBER_CONTEXT_VALUE = "value"

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MembershipP2PIntegrationTest> { e, c ->
                when (e) {
                    is StartEvent -> {
                        logger.info("Starting test coordinator")
                        c.followStatusChangesByName(
                            setOf(
                                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                                LifecycleCoordinatorName.forComponent<MembershipP2PReadService>()
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

            registrationRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            keyValuePairListSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            keyValuePairListDeserializer =
                cordaAvroSerializationFactory.createAvroDeserializer({}, KeyValuePairList::class.java)

            configurationReadService.startAndWait()
            membershipP2PReadService.startAndWait()
            configurationReadService.bootstrapConfig(bootConfig)

            p2pSender = publisherFactory.createPublisher(
                PublisherConfig("membership_p2p_test_sender"),
                messagingConfig = bootConfig
            ).also {
                it.start()
            }

            eventually {
                logger.info("Waiting for required services to start...")
                Assertions.assertEquals(LifecycleStatus.UP, coordinator.status)
                logger.info("Required services started.")
            }
        }

        private fun Lifecycle.startAndWait() {
            start()
            eventually(5.seconds) {
                Assertions.assertTrue(isRunning)
            }
        }
    }

    @Test
    fun `membership p2p service reads registration requests from the p2p topic and puts them on a membership topic for further processing`() {
        val groupId = UUID.randomUUID().toString()
        val source = MemberX500Name.parse("O=Alice,C=GB,L=London").toString()
        val sourceHoldingIdentity = net.corda.virtualnode.HoldingIdentity(source, groupId)
        val destination = MemberX500Name.parse("O=MGM,C=GB,L=London").toString()
        val registrationId = UUID.randomUUID().toString()
        val fakeKey = "fakeKey"
        val fakeSig = "fakeSig"
        val countDownLatch = CountDownLatch(1)
        var result: Pair<RegistrationState?, Record<String, RegistrationCommand>>? = null

        // Set up subscription to gather results of processing p2p message
        val registrationRequestSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("membership_p2p_test_receiver", REGISTRATION_COMMANDS),
            getTestProcessor { s, e ->
                result = Pair(s, e)
                countDownLatch.countDown()
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(
                    MEMBER_CONTEXT_KEY,
                    MEMBER_CONTEXT_VALUE
                )
            )
        )
        val fakeSigWithKey = CryptoSignatureWithKey(
            ByteBuffer.wrap(fakeKey.encodeToByteArray()),
            ByteBuffer.wrap(fakeSig.encodeToByteArray()),
        )
        val messageHeader = UnauthenticatedMessageHeader(
            HoldingIdentity(destination, groupId),
            HoldingIdentity(source, groupId),
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val message = MembershipRegistrationRequest(
            registrationId,
            ByteBuffer.wrap(keyValuePairListSerializer.serialize(memberContext)),
            fakeSigWithKey
        )

        // Publish P2P message requesting registration
        val sendFuture = p2pSender.publish(
            listOf(
                buildUnauthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(registrationRequestSerializer.serialize(message))
                )
            )
        )

        // Wait for latch to countdown so we know when processing has completed and results have been collected
        try {
            countDownLatch.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            fail("P2P message was not processed in an expected amount of time.")
        } finally {
            registrationRequestSubscription.close()
            p2pSender.close()
        }

        // Assert Results
        assertThat(sendFuture.size).isEqualTo(1)
        assertThat(sendFuture.single().isDone).isTrue
        assertThat(result).isNotNull
        assertThat(result?.first).isNull()
        assertThat(result?.second).isNotNull
        with(result!!.second) {
            assertThat(topic).isEqualTo(REGISTRATION_COMMANDS)
            assertThat(key).isEqualTo(sourceHoldingIdentity.id)
            assertThat(value).isNotNull
            assertThat(value!!).isInstanceOf(RegistrationCommand::class.java)
            assertThat(value!!.command).isNotNull
            assertThat(value!!.command).isInstanceOf(StartRegistration::class.java)
            with(value!!.command as StartRegistration) {
                assertThat(this.destination.x500Name).isEqualTo(destination)
                assertThat(this.destination.groupId).isEqualTo(groupId)
                assertThat(this.source.x500Name).isEqualTo(source)
                assertThat(this.source.groupId).isEqualTo(groupId)
                assertThat(this.memberRegistrationRequest).isNotNull
                with(memberRegistrationRequest) {
                    assertThat(this.registrationId).isEqualTo(registrationId)
                    val deserializedContext = keyValuePairListDeserializer.deserialize(this.memberContext.array())
                    assertThat(deserializedContext).isNotNull
                    assertThat(deserializedContext).isEqualTo(memberContext)
                    assertThat(deserializedContext!!.items.size).isEqualTo(1)
                    assertThat(deserializedContext.items.single().key).isEqualTo(MEMBER_CONTEXT_KEY)
                    assertThat(deserializedContext.items.single().value).isEqualTo(MEMBER_CONTEXT_VALUE)
                    assertThat(this.memberSignature).isEqualTo(fakeSigWithKey)
                    assertThat(this.memberSignature.publicKey.array().decodeToString()).isEqualTo(fakeKey)
                    assertThat(this.memberSignature.bytes.array().decodeToString()).isEqualTo(fakeSig)
                }
            }
        }
    }

    private fun buildUnauthenticatedP2PRequest(
        messageHeader: UnauthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, AppMessage> {
        return Record(
            Schemas.P2P.P2P_IN_TOPIC,
            UUID.randomUUID().toString(),
            AppMessage(
                UnauthenticatedMessage(
                    messageHeader,
                    payload
                )
            )
        )
    }

    private fun getTestProcessor(resultCollector: (RegistrationState?, Record<String, RegistrationCommand>) -> Unit): StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {
        class TestProcessor : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {
            override fun onNext(
                state: RegistrationState?,
                event: Record<String, RegistrationCommand>
            ): StateAndEventProcessor.Response<RegistrationState> {
                resultCollector(state, event)
                return StateAndEventProcessor.Response(null, emptyList())
            }

            override val keyClass = String::class.java
            override val stateValueClass = RegistrationState::class.java
            override val eventValueClass = RegistrationCommand::class.java
        }
        return TestProcessor()
    }
}