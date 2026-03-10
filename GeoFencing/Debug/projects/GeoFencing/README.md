# GeoFencing - Tizen TV Web App

A geofencing application for Samsung Tizen TV that displays device locations on a map and determines if devices are inside or outside a defined geofence area.

## Features

- **Interactive Map**: Displays a map centered on Samsung R&D Institute, Noida Sector 126
- **Geofence Circle**: 200-meter radius geofence visualization
- **Real-time Tracking**: Receives location updates from multiple Android devices via WebSocket
- **Device Status**: Shows whether each device is inside or outside the geofence
- **Device List**: Sidebar showing all connected devices with their status
- **TV Remote Support**: Navigate using TV remote (arrow keys, OK button, volume for zoom)

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Android App 1  │────▶│                  │     │                 │
├─────────────────┤     │   WebSocket      │────▶│   Tizen TV      │
│  Android App 2  │────▶│   Server (PC)    │     │   Web App       │
├─────────────────┤     │                  │     │                 │
│  Android App N  │────▶│                  │     │                 │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

## Project Structure

```
GeoFencing/
├── config.xml          # Tizen app configuration
├── index.html          # Main HTML file
├── main.js             # JavaScript with map & geofencing logic
├── css/
│   └── style.css       # Styling for the TV app
├── server/
│   ├── server.js       # Node.js WebSocket server
│   └── package.json    # Server dependencies
└── README.md           # This file
```

## Setup Instructions

### 1. WebSocket Server Setup (on a PC)

The server acts as a relay between Android devices and the Tizen TV app.

```bash
# Navigate to server folder
cd GeoFencing/server

# Install dependencies
npm install

# Start the server
npm start
```

The server will display:
- Local IP address (e.g., `192.168.1.100`)
- Port number (`8080`)

**Note**: Both the TV and PC must be on the same WiFi network.

### 2. Tizen TV App Setup

#### Using Tizen Studio:

1. Open Tizen Studio
2. Import the project: **File → Import → Tizen Project**
3. Select the `GeoFencing` folder
4. Build the project: **Project → Build Project**
5. Run on emulator or TV: **Run → Run As → Tizen Web Application**

#### Deploying to Samsung TV:

1. Enable Developer Mode on your TV:
   - Go to **Apps**
   - Press **1-2-3-4-5** on remote
   - Enable Developer Mode
   - Enter your PC's IP address
   - Restart TV

2. In Tizen Studio:
   - Go to **Device Manager**
   - Add your TV's IP address
   - Click **Connect**
   - Right-click project → **Run As → Tizen Web Application**

### 3. Using the App

1. Start the WebSocket server on your PC
2. Launch the app on your Tizen TV
3. Enter the server IP address shown by the server
4. Click **Connect**
5. The map will display with the geofence
6. When Android devices connect and send locations, pins will appear on the map

## TV Remote Controls

| Key | Action |
|-----|--------|
| ↑↓←→ | Pan the map |
| OK/Enter | Reset view to center |
| Volume +/- | Zoom in/out |
| BACK | Exit the app |

## Demo Mode

For testing without Android devices, open the browser console and run:

```javascript
GeoFencing.startDemoMode();
```

This will add simulated devices that move around the geofence area.

## WebSocket Message Protocol

### Device → Server → TV Message Flow

#### Android Device Registration:
```json
{
  "type": "register",
  "clientType": "android",
  "deviceId": "unique-device-id",
  "deviceName": "Phone 1"
}
```

#### Location Update:
```json
{
  "type": "location",
  "lat": 28.5445,
  "lng": 77.3340
}
```

#### TV App Registration:
```json
{
  "type": "register",
  "clientType": "tv",
  "appName": "GeoFencing"
}
```

### Server → TV Messages

#### Single Location Update:
```json
{
  "type": "location",
  "deviceId": "device-1",
  "deviceName": "Phone 1",
  "lat": 28.5445,
  "lng": 77.3340
}
```

#### Device Disconnected:
```json
{
  "type": "deviceDisconnected",
  "deviceId": "device-1"
}
```

## Android App Requirements (for future development)

The Android app should:

1. Connect to the WebSocket server
2. Register with type `android`
3. Send location updates periodically
4. Handle reconnection on disconnect

Example Android WebSocket connection (pseudocode):

```kotlin
// Connect to server
val ws = WebSocket("ws://192.168.1.100:8080")

// Register device
ws.send("""
  {
    "type": "register",
    "clientType": "android",
    "deviceId": "${Build.ID}",
    "deviceName": "My Phone"
  }
""")

// Send location updates
locationProvider.onLocationChanged { location ->
    ws.send("""
      {
        "type": "location",
        "lat": ${location.latitude},
        "lng": ${location.longitude}
      }
    """)
}
```

## Configuration

### Center Location
Edit `main.js` to change the geofence center:

```javascript
const CONFIG = {
    CENTER: {
        lat: 28.5445,        // Latitude
        lng: 77.3340,        // Longitude
        name: "Samsung R&D Institute, Noida Sector 126"
    },
    RADIUS: 200,             // Geofence radius in meters
    ZOOM: 17                 // Default map zoom level
};
```

### Server Port
Edit `server/server.js` to change the port:

```javascript
const PORT = 8080;
```

## Troubleshooting

### TV can't connect to server:
- Ensure both devices are on the same WiFi network
- Check if firewall is blocking port 8080
- Try pinging the server IP from TV

### Map not loading:
- Check internet connectivity on TV
- Ensure Leaflet CDN is accessible

### Devices not showing on map:
- Verify WebSocket connection (green indicator)
- Check Android app is sending correct JSON format
- Look at server console for connection logs

## Technologies Used

- **Leaflet.js**: Open-source map library
- **WebSocket**: Real-time communication
- **Node.js**: WebSocket server
- **Tizen Web API**: TV app framework

## License

MIT License
