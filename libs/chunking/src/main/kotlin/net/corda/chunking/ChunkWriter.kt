package net.corda.chunking

import java.io.InputStream
import java.nio.file.Path

interface ChunkWriter {
    /**
     * Break up an [InputStream] into chunks of some unspecified size (smaller than the default Kafka message size).
     * The given [fileName] will be returned via the [ChunkWriter] and the [ChunksCombined] callback.
     */
    fun write(fileName: Path, inputStream: InputStream)

    /**
     * When a chunk is created, it is passed to the [ChunkWriteCallback], it is up to the implementer to write the
     * chunk to the appropriate destination, e.g. you'll publish this chunk on Kafka.
     *
     * The total number of times this method is called for a given binary is unknown until the [InputStream] used by
     * [write] is fully consumed, and the final zero-sized [Chunk] is written.
     */
    fun onChunk(onChunkWriteCallback: ChunkWriteCallback)
}
