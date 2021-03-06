package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.simulator.AppSimulator.Companion.KAFKA_BOOTSTRAP_SERVER
import net.corda.p2p.app.simulator.AppSimulator.Companion.KAFKA_BOOTSTRAP_SERVER_KEY
import net.corda.p2p.app.simulator.AppSimulator.Companion.PRODUCER_CLIENT_ID
import net.corda.v5.base.util.contextLogger
import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.Properties

class Receiver(private val subscriptionFactory: SubscriptionFactory,
               private val receiveTopic: String,
               private val metadataTopic: String,
               private val kafkaProperties: Properties,
               private val clients: Int): Closeable {

    companion object {
        private val logger = contextLogger()
    }

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val subscriptions = mutableListOf<Subscription<*, *>>()

    fun start() {
        (1..clients).forEach { client ->
            val subscriptionConfig = SubscriptionConfig("app-simulator-receiver", receiveTopic, client)
            val kafkaConfig = ConfigFactory.empty()
                .withValue(KAFKA_BOOTSTRAP_SERVER_KEY, ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString()))
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-receiver-$client"))
            val subscription = subscriptionFactory.createEventLogSubscription(subscriptionConfig,
                InboundMessageProcessor(metadataTopic), kafkaConfig, null)
            subscription.start()
            subscriptions.add(subscription)
        }
        logger.info("Started consuming messages fom $receiveTopic. When you want to stop the consumption, you can do so using Ctrl+C.")
    }

    override fun close() {
        subscriptions.forEach { it.stop() }
    }

    private inner class InboundMessageProcessor(val destinationTopic: String): EventLogProcessor<String, AppMessage> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessage>
            get() = AppMessage::class.java

        override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
            val now = Instant.now()
            return events.map {
                val authenticatedMessage = it.value!!.message as AuthenticatedMessage
                val payload = objectMapper.readValue<MessagePayload>(authenticatedMessage.payload.array())
                val messageReceivedEvent = MessageReceivedEvent(payload.sender,
                    authenticatedMessage.header.messageId, payload.sendTimestamp, now, Duration.between(payload.sendTimestamp, now))
                Record(destinationTopic, messageReceivedEvent.messageId, objectMapper.writeValueAsString(messageReceivedEvent))
            }
        }

    }


}