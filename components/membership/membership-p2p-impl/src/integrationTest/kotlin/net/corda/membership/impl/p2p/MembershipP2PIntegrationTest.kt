package net.corda.membership.impl.p2p

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
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
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.util.*

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

        val logger = contextLogger()

        private const val BOOT_CONFIG_STRING = """
            ${MessagingConfig.Boot.INSTANCE_ID} = 1
            ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
        """
        private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        private val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))

        private lateinit var p2pSender: Publisher

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

            configurationReadService.startAndWait()
            membershipP2PReadService.startAndWait()
            configurationReadService.bootstrapConfig(bootConfig)

            p2pSender = publisherFactory.createPublisher(
                PublisherConfig("membership_p2p_test"),
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
        var callback: ((RegistrationState?, Record<String, RegistrationCommand>) -> Unit)? = null

        class TestProcessor : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {
            override fun onNext(
                state: RegistrationState?,
                event: Record<String, RegistrationCommand>
            ): StateAndEventProcessor.Response<RegistrationState> {
                callback?.invoke(state, event)
                return StateAndEventProcessor.Response(null, emptyList())
            }

            override val keyClass = String::class.java
            override val stateValueClass = RegistrationState::class.java
            override val eventValueClass = RegistrationCommand::class.java

        }

        val registrationRequestSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("membership_p2p_test", Schemas.Membership.REGISTRATION_COMMANDS),
            TestProcessor(),
            messagingConfig = bootConfig
        ).also { it.start() }

        var result: Pair<RegistrationState?, Record<String, RegistrationCommand>>? = null
        callback = { s, e ->
            registrationRequestSubscription.close()
            result = Pair(s, e)
        }

        val groupId = UUID.randomUUID().toString()
        val source = MemberX500Name.parse("O=Alice,C=GB,L=London").toString()
        val destination = MemberX500Name.parse("O=MGM,C=GB,L=London").toString()
        val registrationId = UUID.randomUUID().toString()

        p2pSender.publish(
            listOf(
                Record(
                    Schemas.P2P.P2P_IN_TOPIC,
                    UUID.randomUUID().toString(),
                    AppMessage(
                        UnauthenticatedMessage(
                            UnauthenticatedMessageHeader(
                                HoldingIdentity(source, groupId),
                                HoldingIdentity(destination, groupId),
                                MEMBERSHIP_P2P_SUBSYSTEM
                            ),
                            MembershipRegistrationRequest(
                                registrationId,
                                KeyValuePairList(
                                    listOf(
                                        KeyValuePair(
                                            MEMBER_CONTEXT_KEY,
                                            MEMBER_CONTEXT_VALUE
                                        )
                                    )
                                ).toByteBuffer(),
                                CryptoSignatureWithKey(
                                    ByteBuffer.wrap("fakeKey".encodeToByteArray()),
                                    ByteBuffer.wrap("fakeSig".encodeToByteArray()),
                                )
                            ).toByteBuffer()
                        )
                    )
                )
            )
        )

        eventually {
            assertThat(result).isNotNull
        }
    }
}