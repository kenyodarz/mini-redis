package com.bykenyodarz.miniredis.common

import java.nio.ByteBuffer

object ResponseEncoder {
    @JvmStatic
    fun encode(word: String): ByteBuffer {
        val byteBuffer = ByteBuffer.allocate(1024)
        byteBuffer.put(word.toByteArray()).put("\r\n".toByteArray())
        byteBuffer.flip()
        return byteBuffer
    }
}