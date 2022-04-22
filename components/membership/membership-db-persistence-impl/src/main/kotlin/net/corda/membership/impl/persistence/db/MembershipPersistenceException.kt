package net.corda.membership.impl.persistence.db

import net.corda.v5.base.exceptions.CordaRuntimeException

class MembershipPersistenceException(msg: String) : CordaRuntimeException(msg)