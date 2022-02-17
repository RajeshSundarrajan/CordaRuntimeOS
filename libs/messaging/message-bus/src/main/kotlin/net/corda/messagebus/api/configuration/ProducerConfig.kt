package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ProducerRoles

data class ProducerConfig(
    val clientId: String,
    val instanceId: Int?,
    // TODO: this isn't the correct way to specify topic prefix
    val topicPrefix: String,
    val role: ProducerRoles
)