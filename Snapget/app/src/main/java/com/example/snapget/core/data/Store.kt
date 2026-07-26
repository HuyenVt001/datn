package com.example.snapget.core.data

interface Store {
    fun getValue(key: String): String
    fun setValue(key: String, value: String)
}
