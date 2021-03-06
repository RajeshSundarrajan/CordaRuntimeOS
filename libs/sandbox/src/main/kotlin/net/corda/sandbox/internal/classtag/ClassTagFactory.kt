package net.corda.sandbox.internal.classtag

import net.corda.sandbox.internal.sandbox.Sandbox
import org.osgi.framework.Bundle

/** Creates, serialises and deserialises [ClassTag] objects. */
internal interface ClassTagFactory {
    /**
     * Creates and serialises a [ClassTag].
     *
     * Throws `SandboxException` if the [bundle] does not have a symbolic name, or [sandbox] is neither a public
     * sandbox nor a CPK sandbox.
     */
    fun createSerialised(
        isStaticClassTag: Boolean,
        isPublicBundle: Boolean,
        bundle: Bundle,
        sandbox: Sandbox
    ): String

    /**
     * Deserialises a [ClassTag].
     *
     * Throws `SandboxException` if the [serialisedClassTag] cannot be deserialised.
     */
    fun deserialise(serialisedClassTag: String): ClassTag
}