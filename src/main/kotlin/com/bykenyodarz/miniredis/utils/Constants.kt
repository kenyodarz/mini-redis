package com.bykenyodarz.miniredis.utils

class Constants {
    companion object {
        @JvmStatic
        fun stringIsInteger(string: String): Boolean {
            var numeric = true

            try {
                Integer.parseInt(string)
            } catch (e: NumberFormatException) {
                numeric = false
            }
            return numeric
        }
    }
}