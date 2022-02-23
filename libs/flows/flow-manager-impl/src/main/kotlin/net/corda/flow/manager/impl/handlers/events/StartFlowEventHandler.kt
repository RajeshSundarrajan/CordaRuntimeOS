package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [FlowEventHandler::class])
class StartFlowEventHandler : FlowEventHandler<StartFlow> {

    private companion object {
        val log = contextLogger()
    }

    override val type = StartFlow::class.java

    override fun preProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        requireNoExistingCheckpoint(context)
        val state = StateMachineState.newBuilder()
            .setSuspendCount(0)
            .setIsKilled(false)
            .setWaitingFor(null)
            .setSuspendedOn(null)
            .build()
        val checkpoint = Checkpoint.newBuilder()
            .setFlowKey(context.inputEvent.flowKey)
            .setFiber(ByteBuffer.wrap(byteArrayOf()))
            .setFlowStartContext(context.inputEventPayload.startContext)
            .setFlowState(state)
            .setSessions(mutableListOf())
            .setFlowStackItems(mutableListOf())
            .build()
        return context.copy(checkpoint = checkpoint)
    }

    private fun requireNoExistingCheckpoint(context: FlowEventContext<StartFlow>) {
        if (context.checkpoint != null) {
            val message = "Flow start event for ${context.inputEvent.flowKey} should have been deduplicated and will not be started"
            log.error(message)
            throw FlowProcessingException(message)
        }
    }

    override fun runOrContinue(context: FlowEventContext<StartFlow>): FlowContinuation {
        return FlowContinuation.Run()
    }

    override fun postProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        return context
    }
}