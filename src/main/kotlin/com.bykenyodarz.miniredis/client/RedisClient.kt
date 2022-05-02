package com.bykenyodarz.miniredis.client

import com.bykenyodarz.miniredis.common.ResponseDecoder
import com.bykenyodarz.miniredis.common.ResponseEncoder
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.*

class RedisClient {

    @Throws(Exception::class)
    fun start() {
        val selector = Selector.open()
        val socketChannel = SocketChannel.open()
        with(socketChannel) {
            configureBlocking(false)
            register(selector, SelectionKey.OP_CONNECT)
            connect(InetSocketAddress("127.0.0.1", 9000))
            println("Connected to hostname 127.0.0.1 and port " + 9000)
        }
        while (true) {
            val n = selector.select()
            if (n == 0) {
                continue
            }
            val iterator: MutableIterator<*> = selector.selectedKeys().iterator()
            while (iterator.hasNext()) {
                val key = iterator.next() as SelectionKey
                val channel = key.channel() as SocketChannel
                if (key.isConnectable && channel.finishConnect()) {
                    channel.register(selector, SelectionKey.OP_READ)

                    sendToServer(channel)
                }
                if (key.isReadable) {

                    // Leer la respuesta del servidor
                    if (key.attachment() == null) {
                        key.attach(ResponseDecoder(channel))
                    }
                    val decoder = key.attachment() as ResponseDecoder
                    if (decoder.decode()) {
                        val wordList = decoder.getWordList(false)
                        println(wordList.toTypedArray().contentToString())
                        decoder.clear()

                        sendToServer(channel)
                    }
                }
                iterator.remove()
            }
        }
    }

    @Throws(Exception::class)
    private fun sendToServer(channel: SocketChannel) {
        val scanner = Scanner(System.`in`)
        val str = scanner.nextLine()
        val byteBuffer = ResponseEncoder.encode(listOf(str))
        while (byteBuffer.hasRemaining()) {
            channel.write(byteBuffer)
        }
    }


}