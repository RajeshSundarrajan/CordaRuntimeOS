package net.corda.flow.scheduler

import net.corda.data.flow.state.Checkpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

interface FlowWakeUpScheduler : StateAndEventListener<String, Checkpoint> {
    fun onConfigChange(config: Map<String, SmartConfig>)
}