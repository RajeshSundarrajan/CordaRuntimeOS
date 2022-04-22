package net.corda.membership.impl.p2p

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.registry.AvroSchemaRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MembershipP2PReadServiceImplTest {

    lateinit var membershipP2PReadServiceImpl: MembershipP2PReadServiceImpl

    private var eventHandler: LifecycleEventHandler? = null
    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
    private val subscription: Subscription<String, AppMessage> = mock()

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            eventHandler = it.arguments[1] as LifecycleEventHandler
            coordinator
        }
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createDurableSubscription(
                any(),
                any<DurableProcessor<String, AppMessage>>(),
                any(),
                eq(null)
            )
        } doReturn subscription
    }
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()

    private val testConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))

    @BeforeEach
    fun setUp() {
        membershipP2PReadServiceImpl = MembershipP2PReadServiceImpl(
            lifecycleCoordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            avroSchemaRegistry
        )
    }

    @Test
    fun `start starts coordinator`() {
        membershipP2PReadServiceImpl.start()

        verify(coordinator).start()
    }

    @Test
    fun `stop stops coordinator`() {
        membershipP2PReadServiceImpl.stop()

        verify(coordinator).stop()
    }

    @Test
    fun `start event creates new registration handle`() {
        eventHandler?.processEvent(StartEvent(), coordinator)

        verify(registrationHandle, never()).close()
        verify(coordinator).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        )
    }

    @Test
    fun `start event closes old registration handle and creates new registration handle if one exists`() {
        eventHandler?.processEvent(StartEvent(), coordinator)
        eventHandler?.processEvent(StartEvent(), coordinator)

        verify(registrationHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        )
    }

    @Test
    fun `stop event sets status to down but closes no handles or subscriptions if they don't exist yet`() {
        eventHandler?.processEvent(StopEvent(), coordinator)

        verify(registrationHandle, never()).close()
        verify(configHandle, never()).close()
        verify(subscription, never()).close()
        verify(coordinator).updateStatus(
            eq(LifecycleStatus.DOWN), any()
        )
    }

    @Test
    fun `stop event sets status to down and closes handles and subscription when they have been created`() {
        eventHandler?.processEvent(StartEvent(), coordinator)
        eventHandler?.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )
        eventHandler?.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
        eventHandler?.processEvent(StopEvent(), coordinator)

        verify(registrationHandle).close()
        verify(configHandle).close()
        verify(subscription).close()
        verify(coordinator).updateStatus(
            eq(LifecycleStatus.DOWN), any()
        )
    }

    @Test
    fun `registration status change to UP follows config changes`() {
        eventHandler?.processEvent(StartEvent(), coordinator)
        eventHandler?.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )

        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `registration status change to UP a second time recreates the config change handle`() {
        eventHandler?.processEvent(StartEvent(), coordinator)
        eventHandler?.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )
        eventHandler?.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )

        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `registration status change to DOWN set the component status to down`() {
        eventHandler?.processEvent(StartEvent(), coordinator)
        eventHandler?.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.DOWN
            ),
            coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription, never()).close()
    }

    @Test
    fun `registration status change to DOWN set the component status to down and closes the subscription if already created`() {
        eventHandler?.processEvent(StartEvent(), coordinator)
        eventHandler?.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
        eventHandler?.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.DOWN
            ),
            coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription).close()
    }

    @Test
    fun `Config changed event creates subscription and sets status to UP`() {
        eventHandler?.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )

        verify(subscription, never()).stop()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        verify(subscriptionFactory).createDurableSubscription(
            any(),
            any<DurableProcessor<String, AppMessage>>(),
            any(),
            eq(null)
        )
        verify(subscription).start()
    }

    @Test
    fun `Config changed event closes original subscription before creating a new one`() {
        eventHandler?.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
        eventHandler?.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )

        verify(subscription).close()
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.UP), any())
        verify(subscriptionFactory, times(2)).createDurableSubscription(
            any(),
            any<DurableProcessor<String, AppMessage>>(),
            any(),
            eq(null)
        )
        verify(subscription, times(2)).start()
    }
}