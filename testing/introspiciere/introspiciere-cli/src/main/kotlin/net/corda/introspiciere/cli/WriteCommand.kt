package net.corda.introspiciere.cli

import net.corda.introspiciere.domain.KafkaMessage
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import picocli.CommandLine
import java.io.File
import java.nio.ByteBuffer

@CommandLine.Command(name = "write")
class WriteCommand : BaseCommand() {

    @CommandLine.Option(names = ["--topic"], required = true, description = ["Topic name"])
    private lateinit var topicName: String

    @CommandLine.Option(names = ["--key"], description = ["Message key"])
    private lateinit var key: String

    @CommandLine.Option(names = ["--schema"],
        required = true,
        description = ["Qualified name of the schema of the message"])
    private lateinit var schemaName: String

    @CommandLine.Option(names = ["--file", "-f"],
        defaultValue = "-",
        description = ["Input file. Use '-' to read from stdin."])
    private lateinit var file: String

    @Suppress("UNUSED_VARIABLE")
    override fun run() {
        val reader = if (file == "-") stdin else File(file).inputStream()

        val schemaClass = Class.forName(schemaName)
        val getClassSchema = schemaClass.getMethod("getClassSchema")
        val schema = getClassSchema.invoke(null) as Schema
        val decoder = DecoderFactory().jsonDecoder(schema, reader)
        val datum = GenericDatumReader<GenericData.Record>(schema)
        val record = datum.read(null, decoder)

        val getEncoder = schemaClass.getMethod("getEncoder")
        val encoder = getEncoder.invoke(null)
        val encode = encoder::class.java.methods.first { it.name == "encode" && it.parameterCount == 1 }
        val buffer = encode.invoke(encoder, record) as ByteBuffer

        val kafkaMessage = KafkaMessage(
            topic = topicName,
            key = key,
            schema = buffer.toByteArray(),
            schemaClass = schemaName
        )

        httpClient.sendMessage(kafkaMessage)
        println("Message successfully sent to topic $topicName")
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val array = ByteArray(remaining())
        get(array)
        return array
    }
}