package net.corda.crypto

/**
 * Provides operations to create [CryptoLibraryClientsFactory] for environments such as p2p components where
 * the caller already has the member id.
 */
interface CryptoLibraryClientsFactoryProvider : AutoCloseable {
    /**
     * Gets an instance of [CryptoLibraryClientsFactory]
     */
    fun get(memberId: String, requestingComponent: String): CryptoLibraryClientsFactory
}