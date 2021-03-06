package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.schema.TestSchema
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.NetworkMapEntry
import net.corda.p2p.NetworkType
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class StubNetworkMap(subscriptionFactory: SubscriptionFactory): LinkManagerNetworkMap, Lifecycle {

    private val processor = NetworkMapEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("network-map", TestSchema.NETWORK_MAP_TOPIC)
    private val subscription = subscriptionFactory.createCompactedSubscription(subscriptionConfig, processor)
    private val keyDeserialiser = KeyDeserialiser()

    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var running: Boolean = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        lock.write {
            if (!running) {
                subscription.start()
                running = true
            }
        }
    }

    override fun stop() {
        lock.write {
            if (running) {
                subscription.stop()
                running = false
            }
        }
    }

    @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    override fun getMemberInfo(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.MemberInfo? {
        lock.read {
            if (!running) {
                throw IllegalStateException("getMemberInfo operation invoked while component was stopped.")
            }

            return processor.netmapEntriesByHoldingIdentity[holdingIdentity]?.toMemberInfo()
        }
    }

    override fun getMemberInfo(hash: ByteArray, groupId: String): LinkManagerNetworkMap.MemberInfo? {
        lock.read {
            if (!running) {
                throw IllegalStateException("getMemberInfo operation invoked while component was stopped.")
            }

            return processor.netMapEntriesByGroupIdPublicKeyHash[groupId]?.get(ByteBuffer.wrap(hash))?.toMemberInfo()
        }
    }

    override fun getNetworkType(groupId: String): LinkManagerNetworkMap.NetworkType? {
        lock.read {
            if (!running) {
                throw IllegalStateException("getNetworkType operation invoked while component was stopped.")
            }

            return processor.netMapEntriesByGroupIdPublicKeyHash[groupId]?.values?.first()?.networkType?.toLMNetworkType()
        }
    }

    private fun NetworkMapEntry.toMemberInfo():LinkManagerNetworkMap.MemberInfo {
        return LinkManagerNetworkMap.MemberInfo(
            LinkManagerNetworkMap.HoldingIdentity(this.holdingIdentity.x500Name, this.holdingIdentity.groupId),
            keyDeserialiser.toPublicKey(this.publicKey.array(), this.publicKeyAlgorithm),
            this.publicKeyAlgorithm.toKeyAlgorithm(),
            LinkManagerNetworkMap.EndPoint(this.address)
        )
    }

    private fun KeyAlgorithm.toKeyAlgorithm(): net.corda.p2p.crypto.protocol.api.KeyAlgorithm {
        return when(this) {
            KeyAlgorithm.ECDSA -> net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA
            KeyAlgorithm.RSA -> net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA
        }
    }

    private fun NetworkType.toLMNetworkType(): LinkManagerNetworkMap.NetworkType {
        return when(this) {
            NetworkType.CORDA_4 -> LinkManagerNetworkMap.NetworkType.CORDA_4
            NetworkType.CORDA_5 -> LinkManagerNetworkMap.NetworkType.CORDA_5
        }
    }

    private class NetworkMapEntryProcessor: CompactedProcessor<String, NetworkMapEntry> {

        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

        val netMapEntriesByGroupIdPublicKeyHash = ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, NetworkMapEntry>>()
        val netmapEntriesByHoldingIdentity = ConcurrentHashMap<LinkManagerNetworkMap.HoldingIdentity, NetworkMapEntry>()

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<NetworkMapEntry>
            get() = NetworkMapEntry::class.java

        override fun onSnapshot(currentData: Map<String, NetworkMapEntry>) {
            currentData.forEach { (_, networkMapEntry) -> addEntry(networkMapEntry) }
        }

        override fun onNext(
            newRecord: Record<String, NetworkMapEntry>,
            oldValue: NetworkMapEntry?,
            currentData: Map<String, NetworkMapEntry>
        ) {
            if (newRecord.value == null) {
                if (oldValue != null) {
                    val publicKeyHash = calculateHash(oldValue.publicKey.array())
                    netMapEntriesByGroupIdPublicKeyHash[oldValue.holdingIdentity.groupId]!!.remove(ByteBuffer.wrap(publicKeyHash))
                    netmapEntriesByHoldingIdentity.remove(oldValue.holdingIdentity.toLMHoldingIdentity())
                }
            } else {
                addEntry(newRecord.value!!)
            }
        }

        private fun addEntry(networkMapEntry: NetworkMapEntry) {
            if (!netMapEntriesByGroupIdPublicKeyHash.containsKey(networkMapEntry.holdingIdentity.groupId)) {
                netMapEntriesByGroupIdPublicKeyHash[networkMapEntry.holdingIdentity.groupId] = ConcurrentHashMap()
            }

            val publicKeyHash = calculateHash(networkMapEntry.publicKey.array())
            netMapEntriesByGroupIdPublicKeyHash[networkMapEntry.holdingIdentity.groupId]!![ByteBuffer.wrap(publicKeyHash)] = networkMapEntry
            netmapEntriesByHoldingIdentity[networkMapEntry.holdingIdentity.toLMHoldingIdentity()] = networkMapEntry
        }

        private fun HoldingIdentity.toLMHoldingIdentity(): LinkManagerNetworkMap.HoldingIdentity {
            return LinkManagerNetworkMap.HoldingIdentity(this.x500Name, this.groupId)
        }

        fun calculateHash(publicKey: ByteArray): ByteArray {
            messageDigest.reset()
            messageDigest.update(publicKey)
            return messageDigest.digest()
        }

    }


}