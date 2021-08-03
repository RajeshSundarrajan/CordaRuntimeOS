package net.corda.p2p.linkmanager.utilities

import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.sessions.SessionManagerTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey

class MockNetworkMap(nodes: List<LinkManagerNetworkMap.HoldingIdentity>) {
    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)

    val keys = HashMap<LinkManagerNetworkMap.HoldingIdentity, KeyPair>()
    private val holdingIdentityForHash = HashMap<Int, LinkManagerNetworkMap.HoldingIdentity>()

    private fun MessageDigest.hash(data: ByteArray): ByteArray {
        this.reset()
        this.update(data)
        return digest()
    }

    init {
        for (node in nodes) {
            val keyPair = keyPairGenerator.generateKeyPair()
            keys[node] = keyPair
            holdingIdentityForHash[messageDigest.hash(keyPair.public.encoded).contentHashCode()] = node
        }
    }

    interface MockLinkManagerNetworkMap : LinkManagerNetworkMap {
        fun getPrivateKeyFromPublicKey(publicKey: PublicKey): PrivateKey
        fun getKeyPair(): KeyPair
        fun getOurMemberInfo(): LinkManagerNetworkMap.MemberInfo
    }

    fun getSessionNetworkMapForNode(node: LinkManagerNetworkMap.HoldingIdentity): MockLinkManagerNetworkMap {
        return object : MockLinkManagerNetworkMap {
            override fun getPrivateKeyFromPublicKey(publicKey: PublicKey): PrivateKey {
                Assertions.assertArrayEquals(keys[node]!!.public.encoded, publicKey.encoded)
                return keys[node]!!.private
            }

            override fun getKeyPair(): KeyPair {
                return keys[node]!!
            }

            override fun getOurMemberInfo(): LinkManagerNetworkMap.MemberInfo {
                return LinkManagerNetworkMap.MemberInfo(node, getKeyPair().public, SessionManagerTest.FAKE_ENDPOINT)
            }

            override fun getMemberInfo(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.MemberInfo? {
                val publicKey = keys[holdingIdentity]?.public ?: return null
                return LinkManagerNetworkMap.MemberInfo(holdingIdentity, publicKey, SessionManagerTest.FAKE_ENDPOINT)
            }

            override fun getMemberInfoFromPublicKeyHash(hash: ByteArray): LinkManagerNetworkMap.MemberInfo? {
                val holdingIdentity = holdingIdentityForHash[hash.contentHashCode()] ?: return null
                return getMemberInfo(holdingIdentity)
            }

            override fun getNetworkType(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.NetworkType? {
                return LinkManagerNetworkMap.NetworkType.CORDA_5
            }
        }
    }
}