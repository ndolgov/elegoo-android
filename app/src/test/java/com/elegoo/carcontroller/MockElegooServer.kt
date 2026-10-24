package com.elegoo.carcontroller

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class MockElegooServer {
    private var serverSocket: ServerSocket? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe channel to safely hand off incoming client commands to the test
    val receivedMessages = Channel<String>(Channel.UNLIMITED)

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start() {
        serverSocket = ServerSocket(0) // Binds to any available local port

        serverScope.launch {
            try {
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                // Occurs gracefully when socket closes on teardown
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        println("Server accepted a client on port: ${socket.port}")

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)

        coroutineScope {

            // 1. Launch a background coroutine for PERIODIC SENDING
            val sendJob = launch {
                try {
                    var count = 0
                    var u = 35
                    var v = 7.4
                    while (isActive && !socket.isClosed) {
                        writer.println("U:${u},V:${"%.2f".format(v)},ID:${count}")
                        writer.flush()
                        delay(1_000) // Non-blocking delay

                        count++
                        u += 10
                        v *= 2.3
                    }
                } catch (e: Exception) {
                    println("Send error on : ${e.message}")
                }
            }

            // 2. Run the RECEIVE LOOP on the main coroutine flow
            try {
                while (isActive && !socket.isClosed) {
                    // readLine() blocks this specific IO thread, but does NOT affect the sender coroutine
                    val command = reader.readLine() ?: break

                    println("Server received command: $command")
                }
            } catch (e: Exception) {
                println("Receive error on port ${socket.port}: ${e.message}")
            } finally {
                // If the receive loop exits (client disconnected), cancel the sender loop
                sendJob.cancel()
                println("Client disconnected on port: ${socket.port}")
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverScope.cancel()
    }
}
