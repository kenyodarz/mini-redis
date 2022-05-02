package com.bykenyodarz.miniredis.server

import com.bykenyodarz.miniredis.utils.Operations
import com.bykenyodarz.miniredis.common.ResponseDecoder
import com.bykenyodarz.miniredis.common.ResponseEncoder.encode
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class RedisServer {

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
                            val message = Operations.operation(wordList)
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

}