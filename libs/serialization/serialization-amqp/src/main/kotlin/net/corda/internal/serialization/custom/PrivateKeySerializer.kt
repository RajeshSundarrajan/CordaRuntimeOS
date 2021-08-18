package net.corda.internal.serialization.custom

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationContext.UseCase.Storage
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.Schema
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.checkUseCase
import net.corda.v5.crypto.Crypto
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PrivateKey

object PrivateKeySerializer
    : CustomSerializer.Implements<PrivateKey>(
        PrivateKey::class.java
) {

    override val schemaForDocumentation = Schema(listOf(RestrictedType(
            type.toString(),
            "",
            listOf(type.toString()),
            AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java),
            descriptor,
            emptyList()
    )))

    override fun writeDescribedObject(obj: PrivateKey, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
        checkUseCase(Storage)
        output.writeObject(obj.encoded, data, clazz, context)
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext
    ): PrivateKey {
        val bits = input.readObject(obj, serializationSchemas, metadata, ByteArray::class.java, context) as ByteArray
        return Crypto.decodePrivateKey(bits)
    }
}