package com.bykenyodarz.miniredis.server

import com.bykenyodarz.miniredis.common.Constants
import com.bykenyodarz.miniredis.common.ResponseDecoder
import com.bykenyodarz.miniredis.common.ResponseEncoder.encode
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RedisServer {
    private val redisMap: MutableMap<String, String> = ConcurrentHashMap()
    private val redisMapSorted: MutableMap<String, SortedMap<String, Int>> = ConcurrentHashMap()

    @Throws(Exception::class)
    fun start() {
        val serverSocketChannel = ServerSocketChannel.open()
        val serverSocket = serverSocketChannel.socket()
        val selector = Selector.open()
        with(serverSocketChannel) {
            configureBlocking(false)
            register(selector, SelectionKey.OP_ACCEPT)
        }
        // Puerto 6379
        serverSocket.bind(InetSocketAddress(9000))
        println("Listen to " + 9000)
        while (true) {
            val n = selector.select()
            if (n == 0) {
                continue
            }
            val iterator: MutableIterator<*> = selector.selectedKeys().iterator()
            while (iterator.hasNext()) {
                val key = iterator.next() as SelectionKey
                if (key.isAcceptable) {
                    val server = key.channel() as ServerSocketChannel
                    val socketChannel = server.accept()
                    println(socketChannel.localAddress.toString() + " accepted")
                    socketChannel.configureBlocking(false)
                    socketChannel.register(selector, SelectionKey.OP_READ)
                }
                if (key.isReadable) {
                    val socketChannel = key.channel() as SocketChannel
                    if (key.attachment() == null) {
                        key.attach(ResponseDecoder(socketChannel))
                    }
                    val decoder = key.attachment() as ResponseDecoder
                    try {
                        val isComplete = decoder.decode()
                        if (isComplete) {
                            val wordList = decoder.getWordList(true)
                            println(wordList.toTypedArray().contentToString())
                            val message = operate(wordList)
                            decoder.clear()

                            send(message, socketChannel)
                        }
                    } catch (e: Exception) {
                        key.cancel()
                        socketChannel.socket().close()
                        socketChannel.close()
                    }
                }
                iterator.remove()
            }
        }
    }

    @Throws(Exception::class)
    private fun send(message: String?, channel: SocketChannel) {
        val writeBuffer = encode(message!!)
        while (writeBuffer.hasRemaining()) {
            channel.write(writeBuffer)
        }
    }

    @Throws(Exception::class)
    private fun operate(wordList: List<String>): String {
        var result: String?
        when {
            wordList[0].uppercase() == "SET" -> {
                when {
                    wordList.size <= 2 -> result = "ERR wrong number of arguments for 'set' command"
                    else -> {
                        redisMap[wordList[1]] = wordList[2]
                        result = "OK"

                    }
                }
            }
            wordList[0].uppercase() == "GET" -> {
                if (wordList.size <= 1) {
                    result = "ERR wrong number of arguments for 'get' command"
                } else {
                    result = redisMap[wordList[1]]
                    if (result == null) {
                        result = "nil"
                    }
                }
            }
            wordList[0].uppercase() == "DEL" -> {
                result = if (redisMap[wordList[1]] == null) {
                    "(integer) 0"
                } else {
                    redisMap.remove(wordList[1])
                    "(integer) 1"
                }
            }
            wordList[0].uppercase() == "DBSIZE" -> {
                val count = redisMap.size
                result = "(integer) $count"
            }
            wordList[0].uppercase() == "INCR" -> {
                if (redisMap[wordList[1]] == null) {
                    redisMap[wordList[1]] = "1"
                    result = "(integer) ${redisMap[wordList[1]]}"
                } else if (Constants.stringIsInteger(redisMap[wordList[1]]!!)) {
                    var number = Integer.parseInt(redisMap[wordList[1]]!!)
                    number++
                    redisMap[wordList[1]] = number.toString()
                    result = "(integer) $number"
                } else {
                    redisMap.remove(wordList[1])
                    result = "ERR value is not an integer or out of range"
                }
            }
            wordList[0].uppercase() == "ZADD" -> {
                val int: Int
                when {
                    wordList.size <= 3 -> result = "ERR wrong number of arguments for 'zadd' command"
                    else -> {
                        if (redisMapSorted[wordList[1]].isNullOrEmpty()) {
                            redisMapSorted[wordList[1]] = TreeMap()
                            redisMapSorted[wordList[1]]!![wordList[3]] = wordList[2].toInt()
                            int = 1
                        } else {
                            if (redisMapSorted[wordList[1]]?.get(wordList[3]) == null) {
                                redisMapSorted[wordList[1]]!![wordList[3]] = wordList[2].toInt()
                                int = 1
                            } else {
                                redisMapSorted[wordList[1]]!![wordList[3]] = wordList[2].toInt()
                                int = 0
                            }
                        }
                        result = "(integer) $int"
                    }
                }
            }
            wordList[0].uppercase() == "ZCARD" -> {
                result = when (wordList.size) {
                    2 -> {
                        val int: Int = if (redisMapSorted[wordList[1]].isNullOrEmpty()) 0
                        else redisMapSorted[wordList[1]]!!.size
                        "(integer) $int"
                    }
                    else -> "ERR wrong number of arguments for 'zcard' command"
                }

            }
            wordList[0].uppercase() == "ZRANK" -> {
                result = when (wordList.size) {
                    3 -> {
                        if (redisMapSorted[wordList[1]].isNullOrEmpty()) "nil"
                        else {
                            if (redisMapSorted[wordList[1]]!!.keys.indexOf(wordList[2]) == -1) "nil"
                            else "(integer) ${redisMapSorted["ss"]!!
                                .toList()
                                .sortedBy { (_, value) -> value}
                                .toMap().keys
                                .indexOf(wordList[2])}"
                        }
                    }
                    else -> "ERR wrong number of arguments for 'zrank' command"
                }

            }
            wordList[0].uppercase() == "ZRANGE" -> {
                result = when (wordList.size) {
                    4 -> {
                        if (redisMapSorted[wordList[1]].isNullOrEmpty()) "empty array"
                        else {
                            if (wordList[3] == "-1") {
                                println("${redisMapSorted["ss"]!!.keys}")
                                "${redisMapSorted["ss"]!!
                                    .toList()
                                    .sortedBy { (_, value) -> value}
                                    .toMap().keys}"
                            } else {
                                "${
                                    redisMapSorted["ss"]!!
                                        .toList()
                                        .sortedBy { (_, value) -> value}
                                        .toMap().keys
                                        .toTypedArray()
                                        .slice(wordList[2].toInt()..wordList[3].toInt())
                                }"
                            }
                        }
                    }
                    else -> "ERR wrong number of arguments for 'zrange' command"
                }

            }
            else -> result = "ERR unknown command `${wordList[0]}`, with args beginning with:"
        }
        return result
    }

}