package net.corda.membership.persistence

import net.corda.lifecycle.Lifecycle

/**
 * Interface to be implemented by the service which persists and retrieves membership data to/from the database.
 */
interface MembershipPersistenceService : Lifecycle