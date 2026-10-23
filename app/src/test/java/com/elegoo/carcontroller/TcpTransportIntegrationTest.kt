package com.elegoo.carcontroller

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class TcpTransportIntegrationTest {

    private lateinit var serverSocket: ServerSocket
    private var serverPort = 0
    private val serverRunning = AtomicBoolean(true)
    private var serverThread: Thread? = null
    private var lastReceivedMessage: String? = null

    @Before
    fun startMockServer() {
        serverSocket = ServerSocket(0) // Binds to any available local port
        serverPort = serverSocket.localPort

        serverThread = Thread {
            try {
                val clientSocket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val outputStream = clientSocket.getOutputStream()

                while (serverRunning.get()) {
                    val line = reader.readLine() ?: break
                    lastReceivedMessage = line
                    // Echo back mock telemetry in Elegoo V4 format
                    val response = "U:35,V:7.4\n"
                    outputStream.write(response.toByteArray())
                    outputStream.flush()
                }
                clientSocket.close()
            } catch (e: Exception) {
                // Expected when socket closes during teardown
            }
        }.apply { start() }
    }

    @After
    fun stopMockServer() {
        serverRunning.set(false)
        try {
            serverSocket.close()
        } catch (e: Exception) {}
        serverThread?.join(1000)
    }

    @Test
    fun testTcpTransportConnectSendAndReceive() = runBlocking {
        var receivedTelemetryRaw = ""
        val transport = TcpCarTransport { rawPacket ->
            receivedTelemetryRaw = rawPacket
        }

        // Connect to local loopback server
        val connected = transport.connect("127.0.0.1", serverPort)
        assertTrue("Transport should successfully connect to local server", connected)
        assertTrue("isConnected() should report true", transport.isConnected())

        // Send a command
        val cmd = ElegooCommand(N = 4, D1 = 180, D2 = 180)
        val sent = transport.sendCommand(cmd)
        assertTrue("Command should send successfully", sent)

        // Allow background thread time to read telemetry response
        delay(300)

        assertNotNull("Server should receive message", lastReceivedMessage)
        assertTrue("Server message should contain N:4", lastReceivedMessage!!.contains("\"N\":4"))
        assertTrue("Server message should contain D1:180", lastReceivedMessage!!.contains("\"D1\":180"))

        assertTrue("Client should receive telemetry response", receivedTelemetryRaw.contains("U:35"))

        // Disconnect
        transport.disconnect()
        assertFalse("Transport should report disconnected", transport.isConnected())
    }
}