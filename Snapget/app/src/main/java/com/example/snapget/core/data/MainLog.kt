package com.example.snapget.core.data

interface MainLog {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun e(tag: String, msg: String)
}
