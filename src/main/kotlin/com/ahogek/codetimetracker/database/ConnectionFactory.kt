package com.ahogek.codetimetracker.database

import java.sql.Connection
import java.sql.DriverManager

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-05 02:07:46
 */
fun interface ConnectionFactory {
    fun getConnection(): Connection
}

class DriverManagerConnectionFactory(private val url: String) : ConnectionFactory {
    override fun getConnection(): Connection {
        return DriverManager.getConnection(url)
    }
}

typealias ConnectionSupplier = () -> Connection

fun ConnectionSupplier.asFactory(): ConnectionFactory = ConnectionFactory { this() }