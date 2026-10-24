package com.elegoo.carcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

// --- Protocol Data Models ---
@Serializable
data class ElegooCommand(
    val H: Int = 1,
    val N: Int,
    val D1: Int = 0,
    val D2: Int = 0,
    val D3: Int = 0
)

data class CarTelemetry(
    val ultrasonicDistanceCm: Int = 0,
    val batteryVoltage: Float = 0.0f,
    val leftEncoder: Long = 0,
    val rightEncoder: Long = 0,
    val isConnected: Boolean = false,
    val lastError: String? = null
)

// --- Transport Interface & TCP Implementation ---
interface CarTransport {
    suspend fun connect(ip: String, port: Int): Boolean
    suspend fun disconnect()
    suspend fun sendCommand(cmd: ElegooCommand): Boolean
    fun isConnected(): Boolean
}

class TcpCarTransport(
    private val onTelemetryReceived: (String) -> Unit
) : CarTransport {

    // 1. Explicit lifecycle container for the background reading loop
    private val transportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null

    // 2. Mark fields as @Volatile to guarantee visibility across different threads
    @Volatile private var socket: Socket? = null
    @Volatile private var outputStream: OutputStream? = null

    override suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        println("Connecting TcpCarTransport")
        try {
            disconnect() // Ensure past connections are cleaned up safely

            val newSocket = Socket(ip, port).apply { soTimeout = 3000 }
            socket = newSocket
            outputStream = newSocket.getOutputStream()

            // FIX: Launch the listening loop on the dedicated transportScope instead of withContext.
            // This prevents connect() from hanging indefinitely.
            listeningJob = transportScope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                    // Use currentCoroutineContext().isActive to cooperatively react to cancellation
                    while (currentCoroutineContext().isActive && newSocket.isConnected && !newSocket.isClosed) {
                        val line = reader.readLine() ?: break

                        // FIX: Ensure the telemetry string callback is executed safely on the Main thread
                        // so the UI can process it directly without thread violations.
                        withContext(Dispatchers.Main) {
                            onTelemetryReceived(line)
                        }
                    }
                } catch (e: Exception) {
                    println("Handled/Closed drop: " + e.message)
                } finally {
                    // Auto-cleanup if the remote server drops the connection
                    disconnect()
                }
            }
            true
        } catch (e: Exception) {
            println("Client connect failed")
            e.printStackTrace()
            false
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        println("Disconnecting TcpCarTransport")
        try {
            // Cancel the infinite reading loop immediately
            listeningJob?.cancelAndJoin()
            listeningJob = null

            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            println("Disconnect failed" + e.message)
            //e.printStackTrace()

        } finally {
            socket = null
            outputStream = null
        }
    }

    override suspend fun sendCommand(cmd: ElegooCommand): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentStream = outputStream ?: return@withContext false
            val jsonStr = Json.encodeToString(cmd) + "\n"
            currentStream.write(jsonStr.toByteArray(StandardCharsets.UTF_8))
            currentStream.flush()
            true
        } catch (e: Exception) {
            println("Send command failed")
            e.printStackTrace()

            false
        }
    }

    // Thread-safe view thanks to @Volatile visibility mappings
    override fun isConnected(): Boolean = socket?.isConnected == true && !(socket?.isClosed ?: true)
}

// --- ViewModel ---
class CarControllerViewModel : ViewModel() {
    private val _telemetry = MutableStateFlow(CarTelemetry())
    val telemetry: StateFlow<CarTelemetry> = _telemetry.asStateFlow()

    private val transport: CarTransport = TcpCarTransport { rawPacket ->
        parseIncomingTelemetry(rawPacket)
    }

    fun toggleConnection(ip: String = "192.168.4.1", port: Int = 100) {
        viewModelScope.launch {
            if (_telemetry.value.isConnected) {
                transport.disconnect()
                _telemetry.value = _telemetry.value.copy(isConnected = false)
            } else {
                val success = transport.connect(ip, port)
                _telemetry.value = _telemetry.value.copy(
                    isConnected = success,
                    lastError = if (!success) "Failed to connect to $ip:$port" else null
                )
            }
        }
    }

    fun drive(leftSpeed: Int, rightSpeed: Int) {
        viewModelScope.launch {
            // N: 4 represents Differential Drive command in Elegoo V4 protocol
            transport.sendCommand(ElegooCommand(N = 4, D1 = leftSpeed, D2 = rightSpeed))
        }
    }

    fun setPanTilt(panAngle: Int, tiltAngle: Int) {
        viewModelScope.launch {
            // N: 5 represents Servo Control
            transport.sendCommand(ElegooCommand(N = 5, D1 = 1, D2 = panAngle))
            transport.sendCommand(ElegooCommand(N = 5, D1 = 2, D2 = tiltAngle))
        }
    }

    fun setMode(mode: Int) {
        viewModelScope.launch {
            // N: 101 represents Mode Switching
            transport.sendCommand(ElegooCommand(N = 101, D1 = mode))
        }
    }

    private fun parseIncomingTelemetry(raw: String) {
        // Minimal parsing for incoming JSON or line protocols
        if (raw.contains("U:")) {
            val dist = raw.substringAfter("U:").substringBefore(",").toIntOrNull() ?: 0
            _telemetry.value = _telemetry.value.copy(ultrasonicDistanceCm = dist)
        }
    }
}

// --- UI Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = CarControllerViewModel()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CarDashboardScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun CarDashboardScreen(viewModel: CarControllerViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    var panValue by remember { mutableStateOf(90f) }
    var tiltValue by remember { mutableStateOf(90f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Status Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (telemetry.isConnected) Color(0xFF1E3A1E) else Color(0xFF3A1E1E)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ELEGOO V4.0 Controller",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (telemetry.isConnected) "Status: Connected (192.168.4.1)" else "Status: Disconnected",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Button(
                    onClick = { viewModel.toggleConnection() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telemetry.isConnected) Color.Red else Color(0xFF2E7D32)
                    )
                ) {
                    Text(if (telemetry.isConnected) "Disconnect" else "Connect")
                }
            }
        }

        // Telemetry Displays
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TelemetryTile(
                title = "Sonar Distance",
                value = "${telemetry.ultrasonicDistanceCm} cm",
                icon = Icons.Default.Sensors
            )
            TelemetryTile(
                title = "Battery",
                value = if (telemetry.batteryVoltage > 0) "${telemetry.batteryVoltage} V" else "N/A",
                icon = Icons.Default.BatteryFull
            )
        }

        // Movement Control Buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Drive Controls", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Button(onClick = { viewModel.drive(180, 180) }) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Forward")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Button(onClick = { viewModel.drive(-150, 150) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Left")
                    }
                    Button(
                        onClick = { viewModel.drive(0, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                    Button(onClick = { viewModel.drive(150, -150) }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Right")
                    }
                }
                Button(onClick = { viewModel.drive(-180, -180) }) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Reverse")
                }
            }
        }

        // Pan/Tilt Servo Controls
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Camera Servo Pan/Tilt", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pan: ${panValue.toInt()}°", fontSize = 12.sp)
                Slider(
                    value = panValue,
                    onValueChange = {
                        panValue = it
                        viewModel.setPanTilt(panValue.toInt(), tiltValue.toInt())
                    },
                    valueRange = 0f..180f
                )
                Text("Tilt: ${tiltValue.toInt()}°", fontSize = 12.sp)
                Slider(
                    value = tiltValue,
                    onValueChange = {
                        tiltValue = it
                        viewModel.setPanTilt(panValue.toInt(), tiltValue.toInt())
                    },
                    valueRange = 0f..180f
                )
            }
        }

        // Autonomous Modes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = { viewModel.setMode(1) }) { Text("Tracking") }
            OutlinedButton(onClick = { viewModel.setMode(2) }) { Text("Obstacle") }
            OutlinedButton(onClick = { viewModel.setMode(3) }) { Text("Follow") }
        }
    }
}

@Composable
fun TelemetryTile(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(160.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 11.sp, color = Color.Gray)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
