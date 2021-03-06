package net.corda.messaging.kafka.integration

class IntegrationTestProperties {
    companion object {
        const val BOOTSTRAP_SERVERS_VALUE = "localhost:9092"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }
}
