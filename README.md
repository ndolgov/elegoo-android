# ELEGOO Smart Robot Car Kit V4.0 - Android Controller

A modern, open-source Android application built with Kotlin and Jetpack Compose to control the ELEGOO Smart Robot Car Kit V4.0 over Wi-Fi (TCP socket protocol).

## Features
- **Differential Drive Control**: Real-time speed and direction commands.
- **Servo Control**: Pan and tilt controls for camera/ultrasonic sensor platform.
- **Mode Switching**: Autonomous modes (Obstacle Avoidance, Line Tracking, Human Follow).
- **Telemetry Processing**: Parses incoming JSON status packets and presents live sensor feedback.
- **Modern Jetpack Compose UI**: Clean dark-mode dashboard interface with real-time signal indicators.
- **Pluggable Architecture**: Modular transport layer (`CarTransport`) allowing simple extension to Bluetooth RFCOMM or adding MJPEG video streams in future iterations.

## Prerequisites
- Android Studio Ladybug (2024.2.1) or newer / IntelliJ IDEA with Android plugin.
- JDK 17 or modern JDK supported by Android Gradle Plugin 8.7+.
- Android Device running Android 8.0 (API 26) or higher.

## Protocol Compatibility
Supports the default ELEGOO V4.0 TCP JSON protocol:
- Target IP: `192.168.4.1`
- Port: `100`
- Commands: Differential Drive (`{"H":1, "N":4, "D1":..., "D2":...}`), Servos (`{"H":1, "N":5, "D1":..., "D2":...}`), Mode Switch (`{"H":1, "N":101, "D1":...}`).

## Building and Deployment (Without Google Play Store)

1. **Clone/Extract Project**:
   Extract this ZIP file to your preferred local directory.

2. **Open in Android Studio**:
   Open Android Studio -> **File** -> **Open...** -> select the project root folder.

3. **Enable Developer Options & USB Debugging on your Android Phone**:
   - Go to **Settings** -> **About phone**.
   - Tap **Build number** 7 times until you see "You are now a developer!".
   - Go back to **Settings** -> **System** -> **Developer options**.
   - Enable **USB debugging**.

4. **Connect Phone & Install via ADB / Studio**:
   - Connect your phone to your PC via USB cable. Accept the "Allow USB debugging" prompt on your phone screen.
   - Click the green **Run 'app'** button (Shift + F10) in Android Studio.

5. **Generating Standalone APK for Sideloading**:
   - In Android Studio, select **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
   - Once built, click **locate** or find the generated file at:
     `app/build/outputs/apk/debug/app-debug.apk`
   - You can copy `app-debug.apk` directly to your phone via USB or download link and install it via your phone's File Manager (enable "Install unknown apps" when prompted).

```sh
./gradlew testDebugUnitTest
```