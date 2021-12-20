package net.corda.flow.fiber

import co.paralleluniverse.fibers.Fiber
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import java.time.Instant

/**
 * Main data object representing snapshot of the flow stack, extracted from the Quasar stack.
 */
data class FlowStackSnapshot(
    val time: Instant,
    val flowClass: String,
    val stackFrames: List<Frame>
) {
    data class Frame(
        val stackTraceElement: StackTraceElement, // This should be the call that *pushed* the frame of [objects]
        val stackObjects: List<Any?>
    ) {
        override fun toString(): String = stackTraceElement.toString()
    }
}

@DoNotImplement
interface FlowFiber<FLOWRETURN> {

    val logic: Flow<FLOWRETURN>

    @Suspendable
    fun <SUSPENDRETURN : Any> suspend(request: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN

    @Suspendable
    fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession

    @Suspendable
    fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN

    fun updateTimedFlowTimeout(timeoutSeconds: Long)

    fun waitForCheckpoint(): Pair<Checkpoint, FlowIORequest<*>>

    fun startFlow(): Fiber<Unit>

    fun nonSerializableState(nonSerializableState: NonSerializableState)

    fun housekeepingState(housekeepingState: HousekeepingState)
}