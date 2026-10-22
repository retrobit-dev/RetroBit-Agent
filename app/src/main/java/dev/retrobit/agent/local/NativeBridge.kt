package dev.retrobit.agent.local

/** Called only on a worker dispatcher; kept by R8 because JNI resolves names. */
internal object NativeBridge {
    init { System.loadLibrary("retrobit_local") }
    external fun probe(): String
    external fun open(path: ByteArray, context: Int, threads: Int, temperature: Float): Long
    external fun begin(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>): Int
    /** Empty bytes = prompt batch processed, null = end of generation. */
    external fun next(handle: Long): ByteArray?
    external fun close(handle: Long)
}

/** Injectable protocol: unit tests exercise cancellation/cleanup without a model. */
internal interface LocalBackend {
    fun open(path: ByteArray, context: Int, threads: Int, temperature: Float): Long
    fun begin(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>): Int
    fun next(handle: Long): ByteArray?
    fun close(handle: Long)
}

internal object JniBackend : LocalBackend {
    override fun open(path: ByteArray, context: Int, threads: Int, temperature: Float) = NativeBridge.open(path, context, threads, temperature)
    override fun begin(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>) = NativeBridge.begin(handle, roles, contents)
    override fun next(handle: Long) = NativeBridge.next(handle)
    override fun close(handle: Long) = NativeBridge.close(handle)
}
