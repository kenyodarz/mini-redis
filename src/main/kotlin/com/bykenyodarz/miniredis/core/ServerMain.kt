package com.bykenyodarz.miniredis.core

import com.bykenyodarz.miniredis.server.RedisServer

class ServerMain {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RedisServer().start()
        }
    }
}