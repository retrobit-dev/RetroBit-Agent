package dev.retrobit.agent.local

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** A token may split an emoji or any UTF-8 character. Never decode tokens separately. */
internal class Utf8Pieces {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = byteArrayOf()

    fun append(piece: ByteArray, end: Boolean = false): String {
        val input = ByteBuffer.wrap(pending + piece)
        val chars = CharBuffer.allocate(input.remaining() + 2)
        decoder.decode(input, chars, end)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        if (end) decoder.flush(chars)
        chars.flip()
        return chars.toString()
    }
}
