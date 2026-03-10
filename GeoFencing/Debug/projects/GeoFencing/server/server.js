/**
 * GeoFencing WebSocket Server
 * 
 * This server acts as a relay between Android devices and the Tizen TV app.
 * Run this on a PC connected to the same WiFi network.
 * 
 * Usage:
 *   1. Install Node.js if not already installed
 *   2. Run: npm install ws
 *   3. Run: node server.js
 *   4. Note the IP address and port shown
 *   5. Enter the IP and port in both TV app and Android app
 */

const WebSocket = require('ws');
const http = require('http');
const os = require('os');

// Configuration
const PORT = 8080;

// Get local IP address
function getLocalIP() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return 'localhost';
}

// Create HTTP server
const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(`
        <!DOCTYPE html>
        <html>
        <head>
            <title>GeoFencing Server</title>
            <style>
                body { font-family: Arial, sans-serif; padding: 40px; background: #1a1a2e; color: white; }
                h1 { color: #3498db; }
                .info { background: #16213e; padding: 20px; border-radius: 10px; margin: 20px 0; }
                .highlight { color: #3498db; font-weight: bold; }
            </style>
        </head>
        <body>
            <h1>GeoFencing WebSocket Server</h1>
            <div class="info">
                <p>Server is running!</p>
                <p>IP Address: <span class="highlight">${getLocalIP()}</span></p>
                <p>Port: <span class="highlight">${PORT}</span></p>
                <p>WebSocket URL: <span class="highlight">ws://${getLocalIP()}:${PORT}</span></p>
            </div>
            <div class="info">
                <h3>Connected Clients:</h3>
                <p>TV Apps: <span class="highlight" id="tv-count">${tvClients.size}</span></p>
                <p>Android Devices: <span class="highlight" id="device-count">${androidClients.size}</span></p>
            </div>
        </body>
        </html>
    `);
});

// Create WebSocket server
const wss = new WebSocket.Server({ server });

// Track connected clients
const tvClients = new Map();       // TV app connections
const androidClients = new Map();  // Android device connections

// Broadcast message to all TV clients
function broadcastToTV(message) {
    const msgString = JSON.stringify(message);
    tvClients.forEach((client, id) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(msgString);
        }
    });
}

// Broadcast all device locations
function broadcastAllLocations() {
    const devices = [];
    androidClients.forEach((data, deviceId) => {
        if (data.location) {
            devices.push({
                deviceId: deviceId,
                deviceName: data.name || deviceId,
                lat: data.location.lat,
                lng: data.location.lng
            });
        }
    });
    
    if (devices.length > 0) {
        broadcastToTV({
            type: 'locations',
            devices: devices
        });
    }
}

// WebSocket connection handler
wss.on('connection', (ws, req) => {
    const clientIP = req.socket.remoteAddress;
    let clientId = null;
    let clientType = null;
    
    console.log(`New connection from ${clientIP}`);
    
    ws.on('message', (data) => {
        try {
            const message = JSON.parse(data);
            console.log('Received:', message);
            
            switch (message.type) {
                case 'register':
                    clientId = message.deviceId || `client-${Date.now()}`;
                    clientType = message.clientType;
                    
                    if (clientType === 'tv') {
                        tvClients.set(clientId, ws);
                        console.log(`TV app registered: ${clientId}`);
                        
                        // Send current device locations to new TV client
                        broadcastAllLocations();
                        
                    } else if (clientType === 'android') {
                        androidClients.set(clientId, {
                            ws: ws,
                            name: message.deviceName || clientId,
                            location: null
                        });
                        console.log(`Android device registered: ${clientId} (${message.deviceName || 'unnamed'})`);
                    }
                    
                    // Send confirmation
                    ws.send(JSON.stringify({
                        type: 'registered',
                        clientId: clientId,
                        serverTime: Date.now()
                    }));
                    break;
                    
                case 'location':
                    // Android device sending location
                    if (clientType === 'android' && clientId) {
                        const deviceData = androidClients.get(clientId);
                        if (deviceData) {
                            deviceData.location = {
                                lat: message.lat,
                                lng: message.lng
                            };
                            
                            // Forward to all TV clients
                            broadcastToTV({
                                type: 'location',
                                deviceId: clientId,
                                deviceName: deviceData.name,
                                lat: message.lat,
                                lng: message.lng
                            });
                        }
                    }
                    break;
                    
                case 'ping':
                    ws.send(JSON.stringify({ type: 'pong', time: Date.now() }));
                    break;
            }
        } catch (e) {
            console.error('Error parsing message:', e);
        }
    });
    
    ws.on('close', () => {
        console.log(`Connection closed: ${clientId || 'unknown'}`);
        
        if (clientType === 'tv' && clientId) {
            tvClients.delete(clientId);
        } else if (clientType === 'android' && clientId) {
            androidClients.delete(clientId);
            
            // Notify TV apps that device disconnected
            broadcastToTV({
                type: 'deviceDisconnected',
                deviceId: clientId
            });
        }
    });
    
    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
    });
});

// Start server
server.listen(PORT, '0.0.0.0', () => {
    const localIP = getLocalIP();
    console.log('');
    console.log('========================================');
    console.log('   GeoFencing WebSocket Server');
    console.log('========================================');
    console.log('');
    console.log(`   HTTP:      http://${localIP}:${PORT}`);
    console.log(`   WebSocket: ws://${localIP}:${PORT}`);
    console.log('');
    console.log('   Use this IP in your TV and Android apps');
    console.log('');
    console.log('========================================');
    console.log('');
});

// Periodic status update
setInterval(() => {
    console.log(`Status: ${tvClients.size} TV clients, ${androidClients.size} Android devices`);
}, 30000);
