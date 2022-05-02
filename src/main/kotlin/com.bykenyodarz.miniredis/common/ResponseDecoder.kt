package com.bykenyodarz.miniredis.common

import com.bykenyodarz.miniredis.exceptions.ReadEmptyException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

class ResponseDecoder(private val channel: SocketChannel) {
    private val byteBuffer = ByteBuffer.allocate(1024)

    private var isOnlyGetCR = false

    private val sb = StringBuilder()

    private var stringLength = 0

    private val wordList: MutableList<String> = ArrayList()

    private var mark: Byte = 0

    private var size = 0

    init {
        byteBuffer.clear()
        byteBuffer.flip()
        isOnlyGetCR = false
        sb.setLength(0)
        stringLength = 0
        wordList.clear()
        mark = 0
        size = 0
    }

    @Throws(Exception::class)
    fun decode(): Boolean {
        var isComplete = true
        try {
            decode0()
        } catch (e: ReadEmptyException) {
            isComplete = false
        }
        return isComplete
    }

    @Throws(Exception::class)
    private fun decode0() {
        // mark por defecto a 0, si no es 0, es un estado intermedio
        mark = if (mark.toInt() != 0) mark else readOneByte()
        when (mark) {
            '*'.code.toByte() -> {
                // size por defecto a 0, si no es 0, es un estado intermedio
                size = if (size != 0) size else readInteger()
                while (size-- > 0) {
                    decode0() // Function Recursiva
                }
            }
            '$'.code.toByte() -> {
                // stringLength: El valor predeterminado es 0. Si no es 0, una parte de la cadena se ha leído antes.
                stringLength = if (stringLength != 0) stringLength else readInteger() + 2 // +2: agregar CRLF
                readFixString()
                // se guarda el String si no hay una Exception
                saveString()
            }
            '+'.code.toByte() -> {
                readString()
                // se guarda el String si no hay una Exception
                saveString()
            }
        }
    }

    private fun saveString() {
        wordList.add(sb.toString())
        sb.setLength(0)
        mark = 0
    }

    fun clear() {
        isOnlyGetCR = false
        sb.setLength(0)
        stringLength = 0
        wordList.clear()
        mark = 0
        size = 0
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
        if (getCR) {   // solo lee CR, no lee LF
            isOnlyGetCR = true
            readString()
        } else {    // Se lee desde la mitad sin hacer CR
            readString()
        }
    }

    @Throws(Exception::class)
    private fun readOneByte(): Byte {
        readToBuffer()
        return byteBuffer.get()
    }

    @Throws(Exception::class)
    private fun readInteger(): Int {
        readString()
        val number = sb.toString().toInt()
        sb.setLength(0)
        mark = 0
        return number
    }

    /**
     * Leer un String de tamaño fijo
     */
    @Throws(Exception::class)
    private fun readFixString() {
        readToBuffer()
        val bytes: ByteArray
        if (byteBuffer.remaining() < stringLength) {
            val currentSize = byteBuffer.remaining()
            bytes = ByteArray(currentSize)
            byteBuffer[bytes]
            val str = String(bytes, StandardCharsets.UTF_8)
            sb.append(str)
            stringLength -= currentSize
            readFixString() // Leer recursivamente los datos restantes
        } else {
            bytes = ByteArray(stringLength)
            byteBuffer[bytes]
            val str = String(bytes, StandardCharsets.UTF_8)
            sb.append(str.replace("\r|\n".toRegex(), "")) // Supresión de CRLF
            stringLength = 0
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