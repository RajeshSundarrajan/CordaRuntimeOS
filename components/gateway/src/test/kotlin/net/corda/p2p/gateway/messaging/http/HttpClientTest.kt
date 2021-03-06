package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateHandler
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URI
import java.security.KeyStore
import java.security.cert.PKIXBuilderParameters
import javax.net.ssl.TrustManagerFactory

class HttpClientTest {
    private val destinationInfo = DestinationInfo(
        uri = URI("http://www.r3.com:3023"),
        sni = "sni",
        legalName = null
    )
    private val trustStore = mock<KeyStore>()
    private val sslConfiguration = mock<SslConfiguration> {
        on { trustStore } doReturn trustStore
        on { revocationCheck } doReturn RevocationConfig(RevocationConfigMode.OFF)
    }
    private val loop = mock<EventLoop> {
        on { execute(any<Runnable>()) } doAnswer {
            (it.arguments.first() as Runnable).run()
        }
    }
    private val writeGroup = mock<EventLoopGroup> {
        on { next() } doReturn loop
    }
    private val nettyGroup = mock<EventLoopGroup>()
    private val listener = mock<HttpEventListener>()
    private val channel = mock<Channel>()
    private val pkixParams = mockConstruction(PKIXBuilderParameters::class.java)
    private val trustManagerFactory = mockStatic(TrustManagerFactory::class.java).also {
        val factory = mock<TrustManagerFactory> {
            on { trustManagers } doReturn emptyArray()
        }
        it.`when`<TrustManagerFactory> {
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        }.doReturn(factory)
    }
    private val bootstrapHandler = argumentCaptor<ChannelInitializer<SocketChannel>>()
    private val connectFuture = mock<ChannelFuture>()
    private val bootstrap = mockConstruction(Bootstrap::class.java) { mock, _ ->
        whenever(mock.group(nettyGroup)).doReturn(mock)
        whenever(mock.channel(NioSocketChannel::class.java)).doReturn(mock)
        whenever(mock.handler(bootstrapHandler.capture())).doReturn(mock)
        whenever(mock.connect(any<String>(), any())).doReturn(connectFuture)
    }

    @AfterEach
    fun cleanUp() {
        pkixParams.close()
        trustManagerFactory.close()
        bootstrap.close()
    }

    private val client = HttpClient(
        destinationInfo, sslConfiguration, writeGroup, nettyGroup, listener
    )

    @Test
    fun `start get the next write group`() {
        client.start()

        verify(writeGroup).next()
    }

    @Test
    fun `start get the next write group only once`() {
        client.start()
        client.start()
        client.start()

        verify(writeGroup, times(1)).next()
    }

    @Test
    fun `start after stop will call the next write group again`() {
        client.start()
        client.stop()
        client.start()

        verify(writeGroup, times(2)).next()
    }

    @Test
    fun `close will stop the client channel`() {
        client.start()
        client.onOpen(HttpConnectionEvent(channel))
        client.stop()

        verify(channel).close()
    }

    @Test
    fun `close will wait for channel to be closed`() {
        val sync = mock<ChannelFuture>()
        doReturn(sync).whenever(channel).close()
        client.start()
        client.onOpen(HttpConnectionEvent(channel))
        client.stop()

        verify(sync).sync()
    }

    @Test
    fun `write will try to connect`() {
        client.start()

        client.write(byteArrayOf(1, 3, 5))

        verify(bootstrap.constructed().first()).connect("www.r3.com", 3023)
    }

    @Test
    fun `write will try to connect only once`() {
        client.start()

        client.write(byteArrayOf(1, 3, 5))
        client.write(byteArrayOf(6))

        verify(bootstrap.constructed().first(), times(1)).connect("www.r3.com", 3023)
    }

    @Test
    fun `write will not try to connect if we have a channel`() {
        client.start()
        client.onOpen(HttpConnectionEvent(channel))

        client.write(byteArrayOf(7))

        assertThat(bootstrap.constructed()).isEmpty()
    }

    @Test
    fun `write will write the correct data`() {
        val request = argumentCaptor<DefaultFullHttpRequest>()
        whenever(channel.writeAndFlush(request.capture())).doReturn(mock())
        client.start()
        client.onOpen(HttpConnectionEvent(channel))

        client.write(byteArrayOf(1, 5))

        assertSoftly {
            it.assertThat(request.firstValue.uri()).isEqualTo("https://www.r3.com:3023/gateway/send")
            it.assertThat(request.firstValue.method()).isEqualTo(HttpMethod.POST)
            it.assertThat(request.firstValue.content().array()).isEqualTo(byteArrayOf(1, 5))
        }
    }

    @Test
    fun `write will add requests to the queue`() {
        client.start()

        client.write(byteArrayOf(1))
        client.write(byteArrayOf(2))
        client.write(byteArrayOf(3))

        client.onOpen(HttpConnectionEvent(channel))

        verify(channel, times(3)).writeAndFlush(any())
    }

    @Test
    fun `write while not started will do nothing`() {
        client.onOpen(HttpConnectionEvent(channel))

        client.write(byteArrayOf(1))

        verify(channel, times(0)).writeAndFlush(any())
    }

    @Test
    fun `write if stopped during execution will do nothing`() {
        var stopMe = false
        whenever(loop.execute(any())).doAnswer {
            if (stopMe) {
                client.stop()
            }
            (it.arguments.first() as Runnable).run()
        }
        client.start()

        stopMe = true
        client.write(byteArrayOf(1))

        assertThat(bootstrap.constructed()).isEmpty()
    }

    @Test
    fun `write will create the correct bootstrapper`() {
        client.start()

        client.write(byteArrayOf(1))

        verify(bootstrap.constructed().first()).group(nettyGroup)
        verify(bootstrap.constructed().first()).channel(NioSocketChannel::class.java)
    }

    @Test
    fun `write will add listener that will close if failed`() {
        val connectListener = argumentCaptor<ChannelFutureListener>()
        whenever(connectFuture.addListener(connectListener.capture())).doReturn(connectFuture)
        client.start()
        client.write(byteArrayOf(1))
        val future = mock<ChannelFuture> {
            on { isSuccess } doReturn false
            on { cause() } doReturn IOException("oops")
            on { channel() } doReturn channel
        }

        connectListener.firstValue.operationComplete(future)

        verify(listener).onClose(HttpConnectionEvent(channel))
    }

    @Test
    fun `write will add listener that will not close if not failed`() {
        val connectListener = argumentCaptor<ChannelFutureListener>()
        whenever(connectFuture.addListener(connectListener.capture())).doReturn(connectFuture)
        client.start()
        client.write(byteArrayOf(1))
        val future = mock<ChannelFuture> {
            on { isSuccess } doReturn true
        }

        connectListener.firstValue.operationComplete(future)

        verify(listener, times(0)).onClose(any())
    }

    @Test
    fun `onOpen will propagate the event`() {
        client.onOpen(HttpConnectionEvent(channel))

        verify(listener).onOpen(HttpConnectionEvent(channel))
    }

    @Test
    fun `onClose will propagate the event`() {
        client.onClose(HttpConnectionEvent(channel))

        verify(listener).onClose(HttpConnectionEvent(channel))
    }

    @Test
    fun `onClose will remove the messages from the queue after enough attempts`() {
        client.start()
        repeat(7) {
            client.write(byteArrayOf(it.toByte()))
            client.onClose(HttpConnectionEvent(channel))
        }
        client.onOpen(HttpConnectionEvent(channel))

        verify(channel, times(2)).writeAndFlush(any())
    }

    @Test
    fun `onClose will try to reconnect if there are pending messages`() {
        client.start()

        client.write(byteArrayOf())
        client.onClose(HttpConnectionEvent(channel))

        verify(bootstrap.constructed().first()).connect("www.r3.com", 3023)
    }

    @Test
    fun `onClose will not try to reconnect if there are no pending messages`() {
        client.start()

        client.onClose(HttpConnectionEvent(channel))

        assertThat(bootstrap.constructed()).isEmpty()
    }

    @Test
    fun `onMessage will propagate the event`() {
        val message = mock<HttpMessage>()
        client.onMessage(message)

        verify(listener).onMessage(message)
    }

    @Test
    fun `onMessage will remove the message from the request pull`() {
        client.start()
        client.onOpen(HttpConnectionEvent(channel))
        client.write(byteArrayOf(1))

        client.onMessage(mock())

        val channel2 = mock<Channel>()
        client.onOpen(HttpConnectionEvent(channel2))
        verify(channel2, never()).writeAndFlush(any())
    }

    @Test
    fun `onMessage will not remove the message from the request pull if there is no channel`() {
        client.start()
        client.write(byteArrayOf(1))

        client.onMessage(mock())

        client.onOpen(HttpConnectionEvent(channel))
        verify(channel, times(1)).writeAndFlush(any())
    }

    @Test
    fun `initChannel in ClientChannelInitializer add handlers to pipeline`() {
        client.start()
        client.write(byteArrayOf(1))
        client.onOpen(HttpConnectionEvent(channel))
        val pipeline = mock<ChannelPipeline>()
        val channel = mock<SocketChannel> {
            on { pipeline() } doReturn pipeline
        }
        val context = mock<ChannelHandlerContext> {
            on { channel() } doReturn channel
            on { pipeline() } doReturn pipeline
            on { executor() } doReturn mock()
        }

        bootstrapHandler.firstValue.channelRegistered(context)

        verify(pipeline).addLast(eq("sslHandler"), any<SslHandler>())
        verify(pipeline).addLast(eq("idleStateHandler"), any<IdleStateHandler>())
        verify(pipeline).addLast(any<HttpClientCodec>())
        verify(pipeline).addLast(any<HttpChannelHandler>())
    }
}
