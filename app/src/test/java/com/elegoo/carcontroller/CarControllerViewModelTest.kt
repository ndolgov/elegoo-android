package com.elegoo.carcontroller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarControllerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialTelemetryState() = runTest {
        val viewModel = CarControllerViewModel()
        val state = viewModel.telemetry.value

        assertEquals(false, state.isConnected)
        assertEquals(0, state.ultrasonicDistanceCm)
        assertEquals(0.0f, state.batteryVoltage)
    }
}