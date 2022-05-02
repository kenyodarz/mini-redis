package com.bykenyodarz.miniredis.common

import java.nio.ByteBuffer

object ResponseEncoder {
    fun encode(wordList: List<String>): ByteBuffer {
        val byteBuffer = ByteBuffer.allocate(1024)
        // *
        val length = wordList.size
        byteBuffer.put("*$length".toByteArray()).put("\r\n".toByteArray())
        for (word in wordList) {
            // $
            val wordLength = word.length
            byteBuffer.put("$$wordLength".toByteArray()).put("\r\n".toByteArray())
            byteBuffer.put(word.toByteArray()).put("\r\n".toByteArray())
        }
        byteBuffer.flip()
        return byteBuffer
    }

    @JvmStatic
    fun encode(word: String): ByteBuffer {
        val byteBuffer = ByteBuffer.allocate(1024)
        // +
        byteBuffer.put("+$word".toByteArray()).put("\r\n".toByteArray())
        byteBuffer.flip()
        return byteBuffer
    }
}