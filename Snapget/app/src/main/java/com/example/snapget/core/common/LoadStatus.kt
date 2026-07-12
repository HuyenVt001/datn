package com.example.snapget.core.common

sealed class LoadStatus(val description: String = "") {
    class Init : LoadStatus()
    class Loading : LoadStatus()
    class Success : LoadStatus()
    class Error(val error: String) : LoadStatus(error)
}
