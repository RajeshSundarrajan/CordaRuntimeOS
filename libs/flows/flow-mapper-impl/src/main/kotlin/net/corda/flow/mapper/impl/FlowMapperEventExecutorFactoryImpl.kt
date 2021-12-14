package net.corda.flow.mapper.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartRPCFlowExecutor
import org.osgi.service.component.annotations.Component

@Component(service = [FlowMapperEventExecutorFactory::class])
class FlowMapperEventExecutorFactoryImpl : FlowMapperEventExecutorFactory{

    override fun create(eventKey: String, flowMapperEvent: FlowMapperEvent, state: FlowMapperState?, flowMapperTopics: FlowMapperTopics):
            FlowMapperEventExecutor {
        return when (val payload = flowMapperEvent.payload) {
            is SessionEvent -> SessionEventExecutor(eventKey, flowMapperTopics, flowMapperEvent.messageDirection, payload, state)
            is StartRPCFlow -> StartRPCFlowExecutor(eventKey, flowMapperTopics.flowEventTopic, payload, state)
            is ExecuteCleanup -> ExecuteCleanupEventExecutor(eventKey)
            is ScheduleCleanup -> ScheduleCleanupEventExecutor(eventKey, payload, state)

            else -> throw NotImplementedError(
                "The event type '${payload.javaClass.name}' is not supported.")
        }
    }
}