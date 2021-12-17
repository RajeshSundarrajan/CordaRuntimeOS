package net.corda.virtualnode.write

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.VirtualNodeInfo

/**
 * [VirtualNodeInfo] writer interface.  The [VirtualNodeInfo] contains its own
 * key, [HoldingIdentity].
 *
 * This interface complements [VirtualNodeInfoReader]
 */
interface VirtualNodeInfoWriteService : Lifecycle {
    /** Put a new [VirtualNodeInfo] into some implementation (e.g. a Kafka component) */
    fun put(virtualNodeInfo: VirtualNodeInfo)
    
    /** Remove [VirtualNodeInfo] some implementation (e.g. a Kafka component) */
    fun remove(virtualNodeInfo: VirtualNodeInfo)
}