package com.bykenyodarz.miniredis.common

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.*

class Operations {
    companion object {
        private val redisMap: MutableMap<String, String> = ConcurrentHashMap()
        private val redisMapSorted: MutableMap<String, SortedMap<String, Int>> = ConcurrentHashMap()

        @JvmStatic
        @Throws(Exception::class)
        fun operation(wordList: List<String>): String {
            var result: String?
            when (wordList[0].uppercase()) {
                "SET" -> {
                    result = operationSet(wordList)
                }
                "GET" -> {
                    if (wordList.size <= 1) {
                        result = "ERR wrong number of arguments for 'get' command"
                    } else {
                        result = redisMap[wordList[1]]
                        if (result == null) {
                            result = "nil"
                        }
                    }
                }
                "DEL" -> {
                    result = if (redisMap[wordList[1]] == null) {
                        "(integer) 0"
                    } else {
                        redisMap.remove(wordList[1])
                        "(integer) 1"
                    }
                }
                "DBSIZE" -> {
                    val count = redisMap.size
                    result = "(integer) $count"
                }
                "INCR" -> {
                    result = operationIncr(wordList)
                }
                "ZADD" -> {

                    result = operationZAdd(wordList)
                }
                "ZCARD" -> {
                    result = operationZCard(wordList)

                }
                "ZRANK" -> {
                    result = operationZRRank(wordList)

                }
                "ZRANGE" -> {
                    result = operationZRange(wordList)
                }
                else -> result = "ERR unknown command `${wordList[0]}`, with args beginning with:"
            }
            return result
        }

        @OptIn(DelicateCoroutinesApi::class)
        private fun operationSet(wordList: List<String>): String {
            val result: String
            when (wordList.size) {
                3 -> {
                    redisMap[wordList[1]] = wordList[2]
                    result = "OK"
                }
                5 -> {
                    redisMap[wordList[1]] = wordList[2]
                    result = "OK"
                    val job = GlobalScope.async {
                        delay(wordList[4].toLong() * 1000)
                        redisMap.remove(wordList[1])
                    }
                    job.isCompleted
                }
                else -> {
                    result = "ERR wrong number of arguments for 'set' command"
                }
            }
            return result
        }

        private fun operationIncr(wordList: List<String>): String {
            val result: String
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
            return result
        }

        private fun operationZAdd(wordList: List<String>): String {
            val result: String
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
            return result
        }

        private fun operationZCard(wordList: List<String>) = when (wordList.size) {
            2 -> {
                val int: Int = if (redisMapSorted[wordList[1]].isNullOrEmpty()) 0
                else redisMapSorted[wordList[1]]!!.size
                "(integer) $int"
            }
            else -> "ERR wrong number of arguments for 'zcard' command"
        }

        private fun operationZRRank(wordList: List<String>) = when (wordList.size) {
            3 -> {
                if (redisMapSorted[wordList[1]].isNullOrEmpty()) "nil"
                else {
                    if (redisMapSorted[wordList[1]]!!.keys.indexOf(wordList[2]) == -1) "nil"
                    else "(integer) ${
                        redisMapSorted["ss"]!!.toList().sortedBy { (_, value) -> value }
                            .toMap().keys.indexOf(wordList[2])
                    }"
                }
            }
            else -> "ERR wrong number of arguments for 'zrank' command"
        }

        private fun operationZRange(wordList: List<String>) = when (wordList.size) {
            4 -> {
                if (redisMapSorted[wordList[1]].isNullOrEmpty()) "empty array"
                else {
                    if (wordList[3] == "-1") {
                        println("${redisMapSorted["ss"]!!.keys}")
                        "${
                            redisMapSorted["ss"]!!.toList().sortedBy { (_, value) -> value }.toMap().keys
                        }"
                    } else {
                        "${
                            redisMapSorted["ss"]!!.toList().sortedBy { (_, value) -> value }.toMap().keys.toTypedArray()
                                .slice(wordList[2].toInt()..wordList[3].toInt())
                        }"
                    }
                }
            }
            else -> "ERR wrong number of arguments for 'zrange' command"
        }
    }
}