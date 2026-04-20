package com.midas.data.storage

expect class KeyValueStorage() {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
