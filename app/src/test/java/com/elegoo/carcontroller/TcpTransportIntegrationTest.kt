package com.elegoo.carcontroller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class TcpTransportIntegrationTest {

    private lateinit var mockServer: MockElegooServer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockServer = MockElegooServer()
        mockServer.start()
    }

    @After
    fun tearDown() {
        mockServer.stop()
    }

    @Test
    fun testTcpTransportConnectSendAndReceive() = runBlocking {
        val telemetry = ConcurrentLinkedQueue<String>()

        val transport = TcpCarTransport { rawPacket ->
            println("Client received telemetry: $rawPacket.")
            telemetry.add(rawPacket)
        }

        val connected = transport.connect("127.0.0.1", mockServer.port)
        assertTrue("Transport should successfully connect to local server", connected)
        assertTrue("isConnected() should report true", transport.isConnected())

        val command = ElegooCommand(N = 4, D1 = 180, D2 = 180)
        val sent = transport.sendCommand(command)
        assertTrue("Command should send successfully", sent)

        delay(3_000)

        println("Telemetry: $telemetry")
        assertTrue(
            "Client should receive telemetry response",
            telemetry.poll().contains("U:35")
        )

        transport.disconnect()
        assertFalse("Transport should report disconnected", transport.isConnected())

        println("Done with assertions")
    }
}
