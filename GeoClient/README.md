# GeoClient

GeoClient is an Android client app for the GeoFence system.

It connects to the Android server app running on another phone over the same Wi-Fi network and sends one of two simulated locations:

- `Send Inside`: sends a location inside the 200 meter geofence
- `Send Outside`: sends a location outside the 200 meter geofence

Each phone uses its device name and device id, so the Tizen TV app can show multiple pins on the map for multiple connected phones.

## Project Purpose

Use this app on additional phones.

Flow:

1. Start the `GeoLocation` server app on one Android phone
2. Connect the Tizen TV app to that server phone
3. Open `GeoClient` on other phones
4. Enter the server phone IP and port
5. Tap `Connect to Server`
6. Tap `Send Inside` or `Send Outside`

## Open In Android Studio On Windows

1. Open Android Studio
2. Click `Open`
3. Select the `GeoClient` folder
4. Let Gradle sync finish

If Android Studio asks for SDK setup, install the required Android SDK and accept the defaults.

## Requirements

- Android Studio on Windows
- Android SDK installed
- Two Android phones on the same Wi-Fi
- The server phone must already be running the `GeoLocation` app

## Build Notes

This project includes:

- `gradlew`
- `gradlew.bat`
- Gradle wrapper configuration

If build fails on Windows because SDK is not configured, create or update `local.properties` in the `GeoClient` folder with your local SDK path:

```properties
sdk.dir=C:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

## Run Steps

1. Build and install the app on a client phone
2. Open the app
3. Enter the server phone IP address shown in the `GeoLocation` app
4. Keep port as `8080` unless you changed it in the server app
5. Tap `Connect to Server`
6. Tap `Send Inside` or `Send Outside`

## Expected Behavior

- The client phone connects to the server phone through WebSocket
- The server relays the location to the Tizen TV app
- The TV app shows a pin for that phone
- The pin label shows the phone name
- Multiple client phones can connect and show multiple pins

## Current App Behavior

This client app does not use GPS.

It only sends simulated inside/outside coordinates around this center point:

- Latitude: `28.5445`
- Longitude: `77.3340`
- Radius: `200m`

## Main Files

- `app/src/main/java/com/example/geoclient/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## Troubleshooting

If the app does not connect:

- Make sure both phones are on the same Wi-Fi
- Make sure the server app is running first
- Make sure IP and port are correct
- Make sure port `8080` is not blocked

If the TV app does not show the pin:

- Make sure the TV is connected to the same server phone
- Check that the client app shows `Connected`
- Tap `Send Inside` or `Send Outside` again