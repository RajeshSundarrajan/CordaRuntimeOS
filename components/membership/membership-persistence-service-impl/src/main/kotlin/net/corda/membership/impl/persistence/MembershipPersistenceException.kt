package net.corda.membership.impl.persistence

import net.corda.v5.base.exceptions.CordaRuntimeException

class MembershipPersistenceException(msg: String) : CordaRuntimeException(msg)