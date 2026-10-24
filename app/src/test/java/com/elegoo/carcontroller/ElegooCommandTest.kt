package com.elegoo.carcontroller

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class ElegooCommandTest {

    @Test
    fun testDifferentialDriveCommandSerialization() {
        val cmd = ElegooCommand(H = 1, N = 4, D1 = 150, D2 = 200)
        val jsonString = Json.encodeToString(cmd)

        //assertTrue(jsonString.contains("\"H\":1"))
        assertTrue(jsonString.contains("\"N\":4"))
        assertTrue(jsonString.contains("\"D1\":150"))
        assertTrue(jsonString.contains("\"D2\":200"))
    }

    @Test
    fun testServoCommandSerialization() {
        val cmd = ElegooCommand(H = 1, N = 5, D1 = 1, D2 = 90)
        val jsonString = Json.encodeToString(cmd)

        assertTrue(jsonString.contains("\"N\":5"))
        assertTrue(jsonString.contains("\"D1\":1"))
        assertTrue(jsonString.contains("\"D2\":90"))
    }

    @Test
    fun testModeSwitchCommandSerialization() {
        val cmd = ElegooCommand(H = 1, N = 101, D1 = 2)
        val jsonString = Json.encodeToString(cmd)

        assertTrue(jsonString.contains("\"N\":101"))
        assertTrue(jsonString.contains("\"D1\":2"))
    }
}