package net.corda.components.examples.pubsub

import com.typesafe.config.Config
import net.corda.components.examples.pubsub.processor.DemoPubSubProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunPubSub(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: Config
) : Lifecycle {

    companion object {
        val log: Logger = contextLogger()
        const val groupName = "pubsubGroup"
        const val pubsubTopic = "PubsubTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override var isRunning: Boolean = false

    fun reStart(newConfig: Config) {
        log.info("Restarting pubsub subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating pubsub subscription")
            isRunning = true
            val processor = DemoPubSubProcessor()
            subscription = subscriptionFactory.createPubSubSubscription(
                SubscriptionConfig(groupName, pubsubTopic),
                processor,
                null,
                config
            )

            log.info("Starting pubsub subscription")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping pubsub sub.")
        isRunning = false
        subscription?.stop()
    }
}
