package net.corda.flow.manager


import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.records.Record

interface FlowMetaDataFactory {
    fun createFromEvent(state: Checkpoint?, eventRecord: Record<FlowKey, FlowEvent>): FlowMetaData
}
