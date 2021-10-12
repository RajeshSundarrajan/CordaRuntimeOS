package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.EventLogSubscriptionWithDominoLogic
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer

class OutboundMessageHandlerTest {
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configurationReaderService = mock<ConfigurationReadService>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn mock()
    }
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createEventLogSubscription(
                any(),
                any<EventLogProcessor<String, LinkOutMessage>>(),
                any(),
                any()
            )
        } doReturn mock()
    }
    private val connectionManager = mockConstruction(ReconfigurableConnectionManager::class.java)
    private val p2pMessageSubscription = mockConstruction(EventLogSubscriptionWithDominoLogic::class.java)
    private val p2pInPublisher = mockConstruction(PublisherWithDominoLogic::class.java)

    private val handler = OutboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        subscriptionFactory,
        publisherFactory,
    )

    @AfterEach
    fun cleanUp() {
        connectionManager.close()
        p2pMessageSubscription.close()
        p2pInPublisher.close()
    }

    @Test
    fun `children return all the children`() {
        assertThat(handler.children).containsExactlyInAnyOrder(
            connectionManager.constructed().first(),
            p2pMessageSubscription.constructed().first(),
            p2pInPublisher.constructed().first(),
        )
    }

    @Test
    fun `onNext will write message to the client`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val client = mock<HttpClient>()
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        verify(client).write(LinkInMessage(payload).toByteBuffer().array())
    }

    @Test
    fun `onNext will return empty list`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val client = mock<HttpClient>()
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        val events = handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        assertThat(events).isEmpty()
    }

    @Test
    fun `onNext will use the correct destination info for CORDA5`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val client = mock<HttpClient>()
        val destinationInfo = argumentCaptor<DestinationInfo>()
        whenever(connectionManager.constructed().first().acquire(destinationInfo.capture())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "r3.com",
                    null
                )
            )
    }

    @Test
    fun `onNext will use the correct destination info for CORDA4`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("O=PartyA, L=London, C=GB", NetworkType.CORDA_4, "https://r3.com/")
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val client = mock<HttpClient>()
        val destinationInfo = argumentCaptor<DestinationInfo>()
        whenever(connectionManager.constructed().first().acquire(destinationInfo.capture())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "b597e8858a2fa87424f5e8c39dc4f93c.p2p.corda.net",
                    X500Name("O=PartyA, L=London, C=GB")
                )
            )
    }

    @Test
    fun `onNext will not send anything for invalid arguments`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("aaa", NetworkType.CORDA_4, "https://r3.com/")
        val message = LinkOutMessage(
            headers,
            payload,
        )

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        verify(connectionManager.constructed().first(), never()).acquire(any())
    }

    @Test
    fun `onMessage will publish a record with the message`() {
        val content = AuthenticatedEncryptedDataMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, "", 1, 1)
                encryptedPayload = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val payload = LinkInMessage(content)
        val message = HttpMessage(
            statusCode = HttpResponseStatus.OK,
            payload = payload.toByteBuffer().array(),
            source = InetSocketAddress("www.r3.com", 30),
            destination = InetSocketAddress("www.r3.com", 31),
        )

        handler.onMessage(message)

        verify(p2pInPublisher.constructed().first()).publish(listOf(Record(LINK_IN_TOPIC, "key", message)))
    }

    @Test
    fun `onMessage will not publish error message`() {
        val content = AuthenticatedEncryptedDataMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, "", 1, 1)
                encryptedPayload = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val payload = LinkInMessage(content)
        val message = HttpMessage(
            statusCode = HttpResponseStatus.BAD_REQUEST,
            payload = payload.toByteBuffer().array(),
            source = InetSocketAddress("www.r3.com", 30),
            destination = InetSocketAddress("www.r3.com", 31),
        )

        handler.onMessage(message)

        verify(p2pInPublisher.constructed().first(), never()).publish(any())
    }

    @Test
    fun `onMessage will not publish empty payload`() {
        val message = HttpMessage(
            statusCode = HttpResponseStatus.OK,
            payload = byteArrayOf(),
            source = InetSocketAddress("www.r3.com", 30),
            destination = InetSocketAddress("www.r3.com", 31),
        )

        handler.onMessage(message)

        verify(p2pInPublisher.constructed().first(), never()).publish(any())
    }

    @Test
    fun `onMessage will not publish bad payload`() {
        val message = HttpMessage(
            statusCode = HttpResponseStatus.OK,
            payload = byteArrayOf(1, 2, 3),
            source = InetSocketAddress("www.r3.com", 30),
            destination = InetSocketAddress("www.r3.com", 31),
        )

        handler.onMessage(message)

        verify(p2pInPublisher.constructed().first(), never()).publish(any())
    }
}