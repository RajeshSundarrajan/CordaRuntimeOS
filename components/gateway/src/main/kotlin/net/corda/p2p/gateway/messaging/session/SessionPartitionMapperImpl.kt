package net.corda.p2p.gateway.messaging.session

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.SessionPartitions
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import java.util.concurrent.ConcurrentHashMap

class SessionPartitionMapperImpl(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: Config,
) : SessionPartitionMapper, LifecycleWithDominoTile {

    companion object {
        const val CONSUMER_GROUP_ID = "session_partitions_mapper"
    }

    private val sessionPartitionsMapping = ConcurrentHashMap<String, List<Int>>()
    private val processor = SessionPartitionProcessor()
    override val dominoTile = DominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, ::createResources)

    private val sessionPartitionSubscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, SESSION_OUT_PARTITIONS),
        processor,
        nodeConfiguration
    )

    override fun getPartitions(sessionId: String): List<Int>? {
        return if (!isRunning) {
            throw IllegalStateException("getPartitions invoked, while session partition mapper is not running.")
        } else {
            sessionPartitionsMapping[sessionId]
        }
    }

    private inner class SessionPartitionProcessor :
        CompactedProcessor<String, SessionPartitions> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<SessionPartitions>
            get() = SessionPartitions::class.java

        override fun onSnapshot(currentData: Map<String, SessionPartitions>) {
            sessionPartitionsMapping.putAll(currentData.map { it.key to it.value.partitions })
            dominoTile.resourcesStarted(false)
        }

        override fun onNext(
            newRecord: Record<String, SessionPartitions>,
            oldValue: SessionPartitions?,
            currentData: Map<String, SessionPartitions>
        ) {
            if (newRecord.value == null) {
                sessionPartitionsMapping.remove(newRecord.key)
            } else {
                sessionPartitionsMapping[newRecord.key] = newRecord.value!!.partitions
            }
        }
    }

    fun createResources(resources: ResourcesHolder) {
        sessionPartitionSubscription.start()
        resources.keep {
            sessionPartitionSubscription.stop()
        }
    }
}
