package com.bykenyodarz.miniredis.core

import com.bykenyodarz.miniredis.client.RedisClient

class ClientMain {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RedisClient().start()
        }
    }

}