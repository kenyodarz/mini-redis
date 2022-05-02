package com.bykenyodarz.miniredis.common

import com.bykenyodarz.miniredis.exceptions.ReadEmptyException
import com.bykenyodarz.miniredis.utils.Constants
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class ResponseDecoder(private val channel: SocketChannel) {
    private val byteBuffer = ByteBuffer.allocate(1024)

    private var isOnlyGetCR = false

    private val sb = StringBuilder()

    private val wordList: MutableList<String> = ArrayList()



    init {
        byteBuffer.clear()
        byteBuffer.flip()
        isOnlyGetCR = false
        sb.setLength(0)
        wordList.clear()
    }

    @Throws(Exception::class)
    fun decode(): Boolean {
        var isComplete = true
        try {
            readString()
            saveString()
        } catch (e: ReadEmptyException) {
            isComplete = false
        }
        return isComplete
    }

    private fun saveString() {
        wordList.add(sb.toString())
        sb.setLength(0)
    }

    fun clear() {
        sb.setLength(0)
        wordList.clear()
    }

    /**
     * Leer String hasta que se encuentre CRLF
     */
    @Throws(Exception::class)
    private fun readString() {
        readToBuffer()
        var b: Byte
        var getCR = isOnlyGetCR
        while (byteBuffer.hasRemaining()) {
            b = byteBuffer.get()
            if (b == '\r'.code.toByte()) {
                getCR = true
                continue
            }
            if (getCR && b == '\n'.code.toByte()) {
                return  // Lectura Completa
            }
            sb.append(Char(b.toUShort()))
        }
            readString()
        if (getCR) {   // solo lee CR, no lee LF
            isOnlyGetCR = true
        } else {    // Se lee desde la mitad sin hacer CR
            readString()
        }
    }

    /**
     * Si byteBuffer está vacío, lee los datos en byteBuffer
     * devolviendo una Exception dependiendo de cada caso
     */
    @Throws(Exception::class)
    private fun readToBuffer() {
        if (!byteBuffer.hasRemaining()) {
            byteBuffer.clear()
            val count = channel.read(byteBuffer)
            when {
                count < 0 -> {
                    throw RuntimeException("connection error")
                }
                count == 0 -> {
                    throw ReadEmptyException("read empty")
                }
                else -> byteBuffer.flip()
            }
        }
    }

    fun getWordList(isServer: Boolean): List<String> {
        val str = wordList.joinToString(separator = " ")
        val strArray = str.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (isServer) {
            createList(strArray)
        } else {
            listOf(str.trim())
        }
    }

    private fun createList(array: Array<String>): List<String> {
        val wordList: MutableList<String> = when(array[0].uppercase()){
            "ZADD", "ZCARD", "ZRANK", "ZRANGE" -> array.toMutableList()
            else -> operateMap(array)
        }
        return wordList
    }

    private fun operateMap(array: Array<String>):MutableList<String> {
        val wordList: MutableList<String> = ArrayList()
        val string3rd = StringBuilder()
        array.forEachIndexed { i, s ->
            run {
                when (i) {
                    0 -> wordList.add(0, s)
                    1 -> wordList.add(1, s)
                    else -> {
                        when {
                            array.size == 3 -> {
                                wordList.add(2, s)
                            }
                            s.uppercase() == "EX" -> {
                                wordList.add(2, string3rd.trim().toString())
                                wordList.add(3, s)
                            }
                            Constants.stringIsInteger(s) -> {
                                when {
                                    wordList[2].isEmpty() -> wordList.add(2, string3rd.trim().toString())
                                    else -> wordList[2] = string3rd.trim().toString()
                                }
                                wordList.add(
                                    if (wordList[3].isEmpty()) {
                                        3
                                    } else {
                                        4
                                    }, s
                                )
                            }
                            array.size == i + 1 -> {
                                string3rd.append(" ")
                                string3rd.append(s)
                                wordList.add(2, string3rd.trim().toString())
                            }
                            else -> {
                                string3rd.append(" ")
                                string3rd.append(s)
                            }
                        }
                    }
                }
            }
        }
        return wordList
    }

}