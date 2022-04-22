package net.corda.membership.impl

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.base.util.parseSet
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.ENDPOINTS
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.GROUP_ID
import net.corda.v5.membership.IDENTITY_KEYS
import net.corda.v5.membership.IDENTITY_KEY_HASHES
import net.corda.v5.membership.IS_MGM
import net.corda.v5.membership.MEMBER_STATUS_ACTIVE
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MODIFIED_TIME
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.PARTY_NAME
import net.corda.v5.membership.PARTY_OWNING_KEY
import net.corda.v5.membership.PLATFORM_VERSION
import net.corda.v5.membership.SERIAL
import net.corda.v5.membership.SOFTWARE_VERSION
import net.corda.v5.membership.STATUS
import java.net.URL
import java.security.PublicKey
import java.time.Instant

class MemberInfoImpl(
    override val memberProvidedContext: MemberContext,
    override val mgmProvidedContext: MGMContext
) : MemberInfo {

    init {
        require(endpoints.isNotEmpty()) { "Node must have at least one address." }
        require(platformVersion > 0) { "Platform version must be at least 1." }
        require(softwareVersion.isNotEmpty()) { "Node software version must not be blank." }
    }

    override val name: MemberX500Name get() = memberProvidedContext.parse(PARTY_NAME)

    override val owningKey: PublicKey get() = memberProvidedContext.parse(PARTY_OWNING_KEY)

    override val identityKeys: List<PublicKey> get() = memberProvidedContext.parseList(IDENTITY_KEYS)

    override val platformVersion: Int get() = memberProvidedContext.parse(PLATFORM_VERSION)

    override val serial: Long get() = memberProvidedContext.parse(SERIAL)

    override val isActive: Boolean get() = mgmProvidedContext.parse(STATUS, String::class.java) == MEMBER_STATUS_ACTIVE

    /** Identity certificate or null for non-PKI option. Certificate subject and key should match party */
    // TODO we will need a CertPath converter somewhere
    /*@JvmStatic
    val MemberInfo.certificate: CertPath?
        get() = memberProvidedContext.readAs(CERTIFICATE)*/

    override val groupId: String
        get() = memberProvidedContext.parse(GROUP_ID)

    override val addresses: List<NetworkHostAndPort>
        get() = endpoints.map {
            with(URL(it.url)) { NetworkHostAndPort(host, port) }
        }

    override val endpoints: List<EndpointInfo>
        get() = memberProvidedContext.parseList(ENDPOINTS)

    override val softwareVersion: String
        get() = memberProvidedContext.parse(SOFTWARE_VERSION)

    override val status: String
        get() = mgmProvidedContext.parse(STATUS)

    override val modifiedTime: Instant?
        get() = mgmProvidedContext.parse(MODIFIED_TIME)

    override val identityKeyHashes: Collection<PublicKeyHash>
        get() = memberProvidedContext.parseSet(IDENTITY_KEY_HASHES)

    override val isMgm: Boolean
        get() = mgmProvidedContext.parseOrNull(IS_MGM) ?: false

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MemberInfoImpl) return false
        if (this === other) return true
        return memberProvidedContext == other.memberProvidedContext
                && mgmProvidedContext == other.mgmProvidedContext
    }

    override fun hashCode(): Int {
        var result = memberProvidedContext.hashCode()
        result = 31 * result + mgmProvidedContext.hashCode()
        return result
    }
}