package net.corda.membership.impl.registration.dynamic

import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.parse

interface RegistrationRequest: LayeredPropertyMap {
    val registrationId: String
}

class RegistrationRequestImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, RegistrationRequest {

    override val registrationId: String
        get() = parse(MemberInfoExtension.REGISTRATION_ID)

    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RegistrationRequestImpl) return false
        return map == other.map
    }
}