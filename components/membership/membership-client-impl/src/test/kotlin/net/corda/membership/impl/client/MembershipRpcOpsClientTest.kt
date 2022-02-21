package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.httprpc.types.MemberRegistrationRequest
import net.corda.membership.httprpc.types.RegistrationAction
import net.corda.membership.impl.client.lifecycle.MembershipRpcOpsClientLifecycleHandler
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipRpcOpsClientTest {
    companion object {
        private const val VIRTUAL_NODE_ID = "nodeId"
    }

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private var lifecycleHandler: MembershipRpcOpsClientLifecycleHandler? = null

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
        on { createCoordinator(any(), any()) } doAnswer {
            lifecycleHandler = it.arguments[1] as MembershipRpcOpsClientLifecycleHandler
            coordinator
        }
    }

    private var rpcRequest: MembershipRpcRequest?  = null

    private lateinit var rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>

    private lateinit var publisherFactory: PublisherFactory

    private val configurationReadService: ConfigurationReadService = mock()

    private lateinit var rpcOpsClient: MembershipRpcOpsClientImpl

    private val bootConfig: SmartConfig = mock()
    private val messagingConfig: SmartConfig = mock {
        on(it.withFallback(any())).thenReturn(mock())
    }

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    @Suppress("UNCHECKED_CAST")
    private fun setUpRpcSender() {
        // re-sets the rpc request
        rpcRequest = null
        // kicks off the MessagingConfigurationReceived event to be able to mock the rpc sender
        lifecycleHandler?.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
            coordinator
        )
    }

    private val request = MemberRegistrationRequest(VIRTUAL_NODE_ID, RegistrationAction.REQUEST_JOIN)

    @BeforeEach
    fun setUp() {
        rpcSender = mock {
            on { sendRequest(any()) } doAnswer {
                rpcRequest = it.arguments.first() as MembershipRpcRequest
                CompletableFuture.completedFuture(
                    MembershipRpcResponse(
                        MembershipRpcResponseContext(
                            rpcRequest!!.requestContext.requestId,
                            rpcRequest!!.requestContext.requestTimestamp,
                            Instant.now()
                        ),
                        RegistrationResponse(
                            Instant.now(),
                            RegistrationStatus.SUBMITTED,
                            1,
                            KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                            KeyValuePairList(emptyList())
                        )
                    )
                )
            }
        }

        publisherFactory = mock {
            on {
                createRPCSender(
                    any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(),
                    any()
                )
            } doReturn rpcSender
        }

        rpcOpsClient = MembershipRpcOpsClientImpl(
            lifecycleCoordinatorFactory,
            publisherFactory,
            configurationReadService
        )
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        rpcOpsClient.start()
        assertTrue(rpcOpsClient.isRunning)
        rpcOpsClient.stop()
        assertFalse(rpcOpsClient.isRunning)
    }

    @Test
    fun `rpc sender sends the expected request - starting registration process`() {
        rpcOpsClient.start()
        setUpRpcSender()
        rpcOpsClient.startRegistration(request)
        rpcOpsClient.stop()

        val requestSent = rpcRequest?.request as RegistrationRequest

        assertEquals(request.virtualNodeId, requestSent.virtualNodeId)
        assertEquals(request.action.name, requestSent.registrationAction.name)
    }

    @Test
    fun `rpc sender sends the expected request - checking registration progress`() {
        rpcOpsClient.start()
        setUpRpcSender()
        rpcOpsClient.checkRegistrationProgress(request.virtualNodeId)
        rpcOpsClient.stop()

        val requestSent = rpcRequest?.request as RegistrationStatusRequest

        assertEquals(request.virtualNodeId, requestSent.virtualNodeId)
    }

    @Test
    fun `should fail when rpc sender is not ready`() {
        rpcOpsClient.start()
        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains("RPC sender") }
        rpcOpsClient.stop()
    }

    @Test
    fun `should fail when service is not running`() {
        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains("MembershipRpcOpsClientImpl is not running.") }
    }

    @Test
    fun `should fail when there is an RPC sender exception while sending the request`() {
        rpcOpsClient.start()
        setUpRpcSender()
        val message = "Sender exception."
        whenever(rpcSender.sendRequest(any())).thenThrow(CordaRPCAPISenderException(message))
        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains(message) }
        rpcOpsClient.stop()
    }

    @Test
    fun `should fail when response is null`() {
        rpcOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest!!.requestContext.requestId,
                        rpcRequest!!.requestContext.requestTimestamp,
                        Instant.now()
                    ),
                    null
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains("null") }
        rpcOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different ids`() {
        rpcOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        "wrongId",
                        rpcRequest!!.requestContext.requestTimestamp,
                        Instant.now()
                    ),
                    RegistrationResponse(
                        Instant.now(),
                        RegistrationStatus.SUBMITTED,
                        1,
                        KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                        KeyValuePairList(emptyList())
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains("ID") }
        rpcOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different requestTimestamp`() {
        rpcOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest!!.requestContext.requestId,
                        Instant.now().plusMillis(10000000),
                        Instant.now()
                    ),
                    RegistrationResponse(
                        Instant.now(),
                        RegistrationStatus.SUBMITTED,
                        1,
                        KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                        KeyValuePairList(emptyList())
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains("timestamp") }
        rpcOpsClient.stop()
    }

    @Test
    fun `should fail when response type is not the expected type`() {
        rpcOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest!!.requestContext.requestId,
                        rpcRequest!!.requestContext.requestTimestamp,
                        Instant.now()
                    ),
                    "WRONG RESPONSE TYPE"
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertTrue { ex.message!!.contains("Expected class") }
        rpcOpsClient.stop()
    }
}