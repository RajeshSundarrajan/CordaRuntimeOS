package net.corda.messaging.kafka.utils

import net.corda.messaging.api.exception.CordaRPCAPISenderException
import org.apache.kafka.common.TopicPartition
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Data structure to keep track of active partitions and futures for a given sender and partition listener pair
 *
 * [futuresInPartitionMap] is a map where the key is the partition number we listen for responses on for the futures we
 * hold in the value part of this map
 */
class FutureTracker<TRESP> {

    private val futuresInPartitionMap = ConcurrentHashMap<Int, WeakValueHashMap<String, CompletableFuture<TRESP>>>()

    fun addFuture(correlationId: String, future: CompletableFuture<TRESP>, partition: Int) {
        if (futuresInPartitionMap[partition] == null) {
            future.completeExceptionally(
                CordaRPCAPISenderException(
                    "Repartition event!! Partition was removed before we could send the request. Please retry"
                )
            )
        } else {
            futuresInPartitionMap[partition]?.put(correlationId, future)
        }
    }

    fun getFuture(correlationId: String, partition: Int): CompletableFuture<TRESP>? {
        return futuresInPartitionMap[partition]?.get(correlationId)
    }

    fun removeFuture(correlationId: String, partition: Int) {
        futuresInPartitionMap[partition]?.remove(correlationId)
    }

    fun addPartitions(partitions: List<TopicPartition>) {
        for (partition in partitions) {
            futuresInPartitionMap[partition.partition()] = WeakValueHashMap()
        }
    }

    fun removePartitions(partitions: List<TopicPartition>) {
        for (partition in partitions) {
            val futures = futuresInPartitionMap[partition.partition()]
            for (key in futures!!.keys) {
                futures[key]?.completeExceptionally(
                    CordaRPCAPISenderException(
                        "Repartition event!! Results for this future can no longer be returned"
                    )
                )
            }
            futuresInPartitionMap.remove(partition.partition())
        }
    }

}