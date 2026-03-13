/**
 * GeoFencing Tizen TV Web App
 * Tracks multiple device locations and shows them on a map with geofence
 * Enhanced version with toast notifications, alerts, and TV remote navigation
 */

// =====================================================
// CONFIGURATION
// =====================================================

const CONFIG = {
    // Samsung R&D Institute, Noida Sector 126 coordinates
    CENTER: {
        lat: 28.5445,
        lng: 77.3340,
        name: "Samsung R&D Institute, Noida Sector 126"
    },
    // Geofence radius in meters
    RADIUS: 200,
    // Map zoom level
    ZOOM: 17,
    // Reconnect interval in ms
    RECONNECT_INTERVAL: 5000,
    // Device timeout in ms (remove inactive devices)
    DEVICE_TIMEOUT: 30000,
    // Toast display duration in ms
    TOAST_DURATION: 5000
};

// =====================================================
// GLOBAL VARIABLES
// =====================================================

let map = null;
let centerMarker = null;
let geofenceCircle = null;
let websocket = null;
let devices = new Map(); // deviceId -> { marker, data, isInside, color, lastUpdate }
let isConnected = false;
let reconnectTimer = null;
let deviceTimeoutChecker = null;
let soundEnabled = true;
let isFullscreen = false;

// Device colors for different devices
const DEVICE_COLORS = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9'
];
let colorIndex = 0;

// Focusable elements for TV navigation
let focusableElements = [];
let currentFocusIndex = -1;

// =====================================================
// INITIALIZATION
// =====================================================

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    console.log('Initializing GeoFencing App...');
    
    // Initialize the map
    initializeMap();
    
    // Setup event listeners
    setupEventListeners();
    
    // Setup TV remote key handling
    setupKeyHandlers();
    
    // Setup focusable elements for TV navigation
    setupFocusNavigation();
    
    // Update UI with initial values
    updateUI();
    
    // Start device timeout checker
    startDeviceTimeoutChecker();
    
    console.log('App initialized successfully');
}

// =====================================================
// MAP FUNCTIONS
// =====================================================

function initializeMap() {
    map = L.map('map', {
        center: [CONFIG.CENTER.lat, CONFIG.CENTER.lng],
        zoom: CONFIG.ZOOM,
        zoomControl: true,
        attributionControl: true,
        fadeAnimation: false,
        zoomAnimation: false,
        markerZoomAnimation: false,
        preferCanvas: true
    });
    
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '� OpenStreetMap contributors',
        maxZoom: 19
    }).addTo(map);
    
    centerMarker = L.marker([CONFIG.CENTER.lat, CONFIG.CENTER.lng], {
        icon: createCenterIcon()
    }).addTo(map);
    
    centerMarker.bindPopup(`
        <strong>${CONFIG.CENTER.name}</strong><br>
        Center Location<br>
        Lat: ${CONFIG.CENTER.lat}<br>
        Lng: ${CONFIG.CENTER.lng}
    `);
    
    geofenceCircle = L.circle([CONFIG.CENTER.lat, CONFIG.CENTER.lng], {
        radius: CONFIG.RADIUS,
        color: '#3498db',
        fillColor: '#3498db',
        fillOpacity: 0.2,
        weight: 3
    }).addTo(map);
    
    geofenceCircle.bindPopup(`Geofence: ${CONFIG.RADIUS}m radius`);
    
    console.log('Map initialized');
}

function createCenterIcon() {
    return L.divIcon({
        className: 'center-marker',
        html: `<div class="center-marker-inner">
                 <div class="center-marker-pulse"></div>
                 <div class="center-marker-dot"></div>
               </div>`,
        iconSize: [30, 30],
        iconAnchor: [15, 15]
    });
}

function createDeviceIcon(color, isInside, deviceName, isOffline) {
    const borderColor = isOffline ? '#95a5a6' : (isInside ? '#27ae60' : '#e74c3c');
    const bgColor = isOffline ? '#7f8c8d' : color;
    const label = deviceName || '';
    return L.divIcon({
        className: 'device-marker',
        html: `<div class="device-marker-label">${label}</div>
               <div class="device-marker-inner" style="background-color: ${bgColor}; border-color: ${borderColor};">
                 <div class="device-marker-pin" style="border-top-color: ${bgColor};"></div>
               </div>`,
        iconSize: [24, 50],
        iconAnchor: [12, 50],
        popupAnchor: [0, -50]
    });
}

// =====================================================
// GEOFENCE CALCULATIONS
// =====================================================

function calculateDistance(lat1, lng1, lat2, lng2) {
    const R = 6371000;
    const dLat = toRadians(lat2 - lat1);
    const dLng = toRadians(lng2 - lng1);
    
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
              Math.sin(dLng / 2) * Math.sin(dLng / 2);
    
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    
    return R * c;
}

function toRadians(degrees) {
    return degrees * (Math.PI / 180);
}

function isInsideGeofence(lat, lng) {
    const distance = calculateDistance(CONFIG.CENTER.lat, CONFIG.CENTER.lng, lat, lng);
    return distance <= CONFIG.RADIUS;
}

// =====================================================
// REVERSE GEOCODING
// =====================================================

function reverseGeocode(lat, lng) {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=16&addressdetails=1`;
    return fetch(url, {
        headers: { 'Accept-Language': 'en' }
    })
    .then(response => response.json())
    .then(data => {
        if (data && data.address) {
            const a = data.address;
            // Build a human-readable short name
            const parts = [];
            if (a.road) parts.push(a.road);
            if (a.neighbourhood) parts.push(a.neighbourhood);
            else if (a.suburb) parts.push(a.suburb);
            if (a.city || a.town || a.village) parts.push(a.city || a.town || a.village);
            return parts.length > 0 ? parts.join(', ') : (data.display_name || 'Unknown area');
        }
        return 'Unknown area';
    })
    .catch(err => {
        console.error('Reverse geocode error:', err);
        return 'Unknown area';
    });
}

// =====================================================
// DEVICE MANAGEMENT
// =====================================================

function updateDeviceLocation(deviceId, lat, lng, deviceName) {
    const isInside = isInsideGeofence(lat, lng);
    const distance = calculateDistance(CONFIG.CENTER.lat, CONFIG.CENTER.lng, lat, lng);
    const wasInside = devices.has(deviceId) ? devices.get(deviceId).isInside : null;
    
    if (devices.has(deviceId)) {
        const device = devices.get(deviceId);
        device.marker.setLatLng([lat, lng]);
        device.marker.setIcon(createDeviceIcon(device.color, isInside, deviceName, false));
        device.data = { lat, lng, name: deviceName, distance, timestamp: Date.now() };
        device.isInside = isInside;
        device.isOffline = false;
        device.lastUpdate = Date.now();
        device.marker.setPopupContent(createDevicePopup(deviceId, deviceName, lat, lng, distance, isInside, null));
    } else {
        const color = DEVICE_COLORS[colorIndex % DEVICE_COLORS.length];
        colorIndex++;
        
        const marker = L.marker([lat, lng], {
            icon: createDeviceIcon(color, isInside, deviceName, false)
        }).addTo(map);
        
        marker.bindPopup(createDevicePopup(deviceId, deviceName, lat, lng, distance, isInside, null));
        
        devices.set(deviceId, {
            marker,
            color,
            data: { lat, lng, name: deviceName, distance, timestamp: Date.now() },
            isInside,
            isOffline: false,
            lastUpdate: Date.now()
        });
    }
    
    // Check if device moved outside (was inside before or new device outside)
    if (!isInside && (wasInside === true || wasInside === null)) {
        triggerOutsideAlert(deviceName || deviceId, lat, lng, distance);
    }
    
    updateDeviceList();
    updateStatistics();
}

function createDevicePopup(deviceId, deviceName, lat, lng, distance, isInside, areaName) {
    let status;
    if (areaName !== undefined && areaName !== null) {
        status = '<span style="color: #95a5a6;">Offline</span>';
    } else {
        status = isInside ? 
            '<span style="color: #27ae60;">Inside Geofence</span>' : 
            '<span style="color: #e74c3c;">Outside Geofence</span>';
    }
    
    let areaInfo = '';
    if (areaName) {
        areaInfo = `<br><em style="color:#f39c12;">Last seen near: ${areaName}</em>`;
    }
    
    return `
        <strong>${deviceName || deviceId}</strong><br>
        ${status}<br>
        Distance: ${distance.toFixed(1)}m<br>
        Lat: ${lat.toFixed(6)}<br>
        Lng: ${lng.toFixed(6)}${areaInfo}
    `;
}

function removeDevice(deviceId, lastLat, lastLng) {
    if (devices.has(deviceId)) {
        const device = devices.get(deviceId);
        const name = device.data.name || deviceId;
        const lat = lastLat || device.data.lat;
        const lng = lastLng || device.data.lng;
        
        // Mark as offline instead of removing — keep pin grayed out
        device.isOffline = true;
        device.marker.setIcon(createDeviceIcon(device.color, device.isInside, name, true));
        
        // Reverse geocode to get area name
        reverseGeocode(lat, lng).then(areaName => {
            device.marker.setPopupContent(
                createDevicePopup(deviceId, name, lat, lng, device.data.distance, device.isInside, areaName)
            );
            showToast(
                `${name} disconnected<br>Last seen near: ${areaName}`,
                'info'
            );
        });
        
        updateDeviceList();
        updateStatistics();
    }
}

function clearAllDevices() {
    devices.forEach((device) => {
        map.removeLayer(device.marker);
    });
    devices.clear();
    colorIndex = 0;
    updateDeviceList();
    updateStatistics();
}

// =====================================================
// DEVICE TIMEOUT CHECKER
// =====================================================

function startDeviceTimeoutChecker() {
    deviceTimeoutChecker = setInterval(() => {
        const now = Date.now();
        const devicesToRemove = [];
        
        devices.forEach((device, deviceId) => {
            if (!device.isOffline && now - device.lastUpdate > CONFIG.DEVICE_TIMEOUT) {
                devicesToRemove.push(deviceId);
            }
        });
        
        devicesToRemove.forEach(deviceId => {
            console.log(`Device ${deviceId} timed out`);
            removeDevice(deviceId);
        });
    }, 5000);
}

// =====================================================
// ALERTS & NOTIFICATIONS
// =====================================================

function triggerOutsideAlert(deviceName, lat, lng, distance) {
    const safeName = deviceName || 'Unknown Device';

    showToast(
        `<div class="toast-alert-card">` +
        `<div class="toast-alert-header">` +
        `<span class="toast-alert-icon">!</span>` +
        `<div class="toast-alert-title-block">` +
        `<div class="toast-alert-title">Device Left Geofence</div>` +
        `<div class="toast-alert-subtitle">${safeName}</div>` +
        `</div>` +
        `</div>` +
        `<div class="toast-alert-details">` +
        `<div class="toast-alert-row"><span>Distance</span><strong>${distance.toFixed(0)} m</strong></div>` +
        `<div class="toast-alert-row"><span>Latitude</span><strong>${lat.toFixed(6)}</strong></div>` +
        `<div class="toast-alert-row"><span>Longitude</span><strong>${lng.toFixed(6)}</strong></div>` +
        `</div>` +
        `</div>`,
        'alert'
    );
    
    // Flash red border
    flashAlertBorder();
    
    // Play sound
    playAlertSound();
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = message;
    
    container.appendChild(toast);
    
    // Trigger animation
    setTimeout(() => toast.classList.add('show'), 10);
    
    // Remove after duration
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, CONFIG.TOAST_DURATION);
}

function flashAlertBorder() {
    const border = document.getElementById('alert-border');
    border.classList.add('active');
    
    setTimeout(() => {
        border.classList.remove('active');
    }, 2000);
}

function playAlertSound() {
    if (!soundEnabled) return;
    
    try {
        const audio = document.getElementById('alert-sound');
        if (audio) {
            audio.currentTime = 0;
            audio.play().catch(e => console.log('Audio play failed:', e));
        }
    } catch (e) {
        console.log('Sound error:', e);
    }
}

function toggleSound() {
    soundEnabled = !soundEnabled;
    const icon = document.getElementById('sound-icon');
    const btn = document.getElementById('sound-btn');
    
    if (soundEnabled) {
        icon.textContent = '??';
        btn.classList.add('active');
    } else {
        icon.textContent = '??';
        btn.classList.remove('active');
    }
    
    showToast(soundEnabled ? 'Sound enabled' : 'Sound muted', 'info');
}

// =====================================================
// FULLSCREEN MODE
// =====================================================

function toggleFullscreen() {
    isFullscreen = !isFullscreen;
    const sidebar = document.getElementById('sidebar');
    const header = document.getElementById('header');
    const footer = document.getElementById('footer');
    
    if (isFullscreen) {
        sidebar.style.display = 'none';
        header.classList.add('minimal');
        footer.style.display = 'none';
    } else {
        sidebar.style.display = 'flex';
        header.classList.remove('minimal');
        footer.style.display = 'block';
    }
    
    // Refresh map size
    setTimeout(() => map.invalidateSize(), 100);
}

// =====================================================
// WEBSOCKET CONNECTION
// =====================================================

function connectToServer() {
    const serverIP = document.getElementById('server-ip').value;
    const serverPort = document.getElementById('server-port').value;
    const wsUrl = `ws://${serverIP}:${serverPort}`;
    
    console.log(`Connecting to ${wsUrl}...`);
    updateConnectionStatus('connecting');
    
    try {
        websocket = new WebSocket(wsUrl);
        
        websocket.onopen = function() {
            console.log('WebSocket connected');
            isConnected = true;
            updateConnectionStatus('connected');
            showToast('Connected to server', 'success');
            
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
            
            websocket.send(JSON.stringify({
                type: 'register',
                clientType: 'tv',
                appName: 'GeoFencing'
            }));
        };
        
        websocket.onmessage = function(event) {
            handleServerMessage(event.data);
        };
        
        websocket.onclose = function() {
            console.log('WebSocket disconnected');
            isConnected = false;
            updateConnectionStatus('disconnected');
            
            reconnectTimer = setTimeout(() => {
                if (!isConnected) {
                    console.log('Attempting to reconnect...');
                    connectToServer();
                }
            }, CONFIG.RECONNECT_INTERVAL);
        };
        
        websocket.onerror = function(error) {
            console.error('WebSocket error:', error);
            updateConnectionStatus('error');
            showToast('Connection error', 'alert');
        };
        
    } catch (e) {
        console.error('Failed to create WebSocket:', e);
        updateConnectionStatus('error');
    }
}

function disconnectFromServer() {
    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
    
    if (websocket) {
        websocket.close();
        websocket = null;
    }
    isConnected = false;
    updateConnectionStatus('disconnected');
    showToast('Disconnected', 'info');
}

function handleServerMessage(data) {
    try {
        const message = JSON.parse(data);
        console.log('Received message:', message);
        
        switch (message.type) {
            case 'location':
                updateDeviceLocation(
                    message.deviceId,
                    message.lat,
                    message.lng,
                    message.deviceName || message.deviceId
                );
                break;
                
            case 'locations':
                if (Array.isArray(message.devices)) {
                    message.devices.forEach(device => {
                        updateDeviceLocation(
                            device.deviceId,
                            device.lat,
                            device.lng,
                            device.deviceName || device.deviceId
                        );
                    });
                }
                break;
                
            case 'deviceDisconnected':
                removeDevice(message.deviceId, message.lastLat, message.lastLng);
                break;
                
            case 'clearAll':
                clearAllDevices();
                break;
                
            case 'registered':
                console.log('Registered with server, clientId:', message.clientId);
                break;
                
            default:
                console.log('Unknown message type:', message.type);
        }
    } catch (e) {
        console.error('Error parsing message:', e);
    }
}

// =====================================================
// UI UPDATES
// =====================================================

function updateConnectionStatus(status) {
    const indicator = document.getElementById('status-indicator');
    const text = document.getElementById('status-text');
    const btn = document.getElementById('connect-btn');
    
    indicator.className = status;
    
    switch (status) {
        case 'connected':
            text.textContent = 'Connected';
            btn.textContent = 'Disconnect';
            btn.className = 'btn connected focusable';
            break;
        case 'connecting':
            text.textContent = 'Connecting...';
            btn.textContent = 'Connecting...';
            btn.className = 'btn connecting focusable';
            break;
        case 'disconnected':
            text.textContent = 'Disconnected';
            btn.textContent = 'Connect';
            btn.className = 'btn focusable';
            break;
        case 'error':
            text.textContent = 'Connection Error';
            btn.textContent = 'Retry';
            btn.className = 'btn error focusable';
            break;
    }
}

function updateDeviceList() {
    const listContainer = document.getElementById('device-list');
    const countElement = document.getElementById('device-count');
    
    countElement.textContent = `${devices.size} device${devices.size !== 1 ? 's' : ''}`;
    
    let html = '';
    devices.forEach((device, deviceId) => {
        let statusClass, statusIcon, statusText;
        if (device.isOffline) {
            statusClass = 'offline';
            statusIcon = '';
            statusText = 'Offline';
        } else if (device.isInside) {
            statusClass = 'inside';
            statusIcon = '';
            statusText = `${device.data.distance.toFixed(0)}m`;
        } else {
            statusClass = 'outside';
            statusIcon = '';
            statusText = `${device.data.distance.toFixed(0)}m`;
        }
        
        html += `
            <div class="device-item ${statusClass}" data-device-id="${deviceId}">
                <div class="device-color" style="background-color: ${device.isOffline ? '#7f8c8d' : device.color}"></div>
                <div class="device-info">
                    <div class="device-name">${device.data.name || deviceId}</div>
                    <div class="device-distance">${statusText} ${statusIcon}</div>
                </div>
            </div>
        `;
    });
    
    listContainer.innerHTML = html || '<div class="no-devices">No devices connected</div>';
}

function updateStatistics() {
    let insideCount = 0;
    let outsideCount = 0;
    
    devices.forEach((device) => {
        if (device.isInside) {
            insideCount++;
        } else {
            outsideCount++;
        }
    });
    
    document.getElementById('inside-count').textContent = insideCount;
    document.getElementById('outside-count').textContent = outsideCount;
}

function updateUI() {
    document.getElementById('center-name').textContent = CONFIG.CENTER.name;
    document.getElementById('center-coords').textContent = 
        `${CONFIG.CENTER.lat.toFixed(4)}� N, ${CONFIG.CENTER.lng.toFixed(4)}� E`;
    document.getElementById('radius-value').textContent = `${CONFIG.RADIUS} meters`;
}

// =====================================================
// EVENT LISTENERS
// =====================================================

function setupEventListeners() {
    document.getElementById('connect-btn').addEventListener('click', function() {
        if (isConnected) {
            disconnectFromServer();
        } else {
            connectToServer();
        }
    });
    
    document.getElementById('device-list').addEventListener('click', function(e) {
        const deviceItem = e.target.closest('.device-item');
        if (deviceItem) {
            const deviceId = deviceItem.dataset.deviceId;
            const device = devices.get(deviceId);
            if (device) {
                map.setView([device.data.lat, device.data.lng], CONFIG.ZOOM);
                device.marker.openPopup();
            }
        }
    });
    
    document.getElementById('fullscreen-btn').addEventListener('click', toggleFullscreen);
    document.getElementById('sound-btn').addEventListener('click', toggleSound);
}

// =====================================================
// TV REMOTE NAVIGATION
// =====================================================

function setupFocusNavigation() {
    focusableElements = Array.from(document.querySelectorAll('.focusable'));
    
    focusableElements.forEach((el, index) => {
        el.addEventListener('focus', () => {
            currentFocusIndex = index;
            el.classList.add('focused');
        });
        
        el.addEventListener('blur', () => {
            el.classList.remove('focused');
        });
    });
}

function moveFocus(direction) {
    if (focusableElements.length === 0) return;
    
    if (currentFocusIndex === -1) {
        currentFocusIndex = 0;
    } else {
        if (direction === 'next') {
            currentFocusIndex = (currentFocusIndex + 1) % focusableElements.length;
        } else {
            currentFocusIndex = (currentFocusIndex - 1 + focusableElements.length) % focusableElements.length;
        }
    }
    
    focusableElements[currentFocusIndex].focus();
}

function setupKeyHandlers() {
    document.addEventListener('keydown', function(e) {
        switch (e.keyCode) {
            case 10009: // BACK key
                if (typeof tizen !== 'undefined') {
                    tizen.application.getCurrentApplication().exit();
                }
                break;
                
            case 38: // UP
                if (document.activeElement.tagName === 'INPUT') return;
                if (isFullscreen) {
                    map.panBy([0, -50]);
                } else {
                    moveFocus('prev');
                }
                e.preventDefault();
                break;
                
            case 40: // DOWN
                if (document.activeElement.tagName === 'INPUT') return;
                if (isFullscreen) {
                    map.panBy([0, 50]);
                } else {
                    moveFocus('next');
                }
                e.preventDefault();
                break;
                
            case 37: // LEFT
                if (document.activeElement.tagName === 'INPUT') return;
                map.panBy([-50, 0]);
                break;
                
            case 39: // RIGHT
                if (document.activeElement.tagName === 'INPUT') return;
                map.panBy([50, 0]);
                break;
                
            case 13: // OK/Enter
                if (document.activeElement === document.getElementById('connect-btn')) {
                    document.getElementById('connect-btn').click();
                } else if (document.activeElement.tagName !== 'INPUT') {
                    map.setView([CONFIG.CENTER.lat, CONFIG.CENTER.lng], CONFIG.ZOOM);
                }
                break;
                
            case 70: // F key - Fullscreen
                toggleFullscreen();
                break;
                
            case 77: // M key - Mute
                toggleSound();
                break;
                
            case 427: // Volume Up
            case 107: // Plus key
                map.zoomIn();
                break;
                
            case 428: // Volume Down
            case 109: // Minus key
                map.zoomOut();
                break;
        }
    });
    
    if (typeof tizen !== 'undefined' && tizen.tvinputdevice) {
        try {
            tizen.tvinputdevice.registerKey('VolumeUp');
            tizen.tvinputdevice.registerKey('VolumeDown');
        } catch (e) {
            console.log('Could not register volume keys:', e);
        }
    }
}

// =====================================================
// DEMO MODE
// =====================================================

function startDemoMode() {
    console.log('Starting demo mode...');
    
    const demoDevices = [
        { id: 'phone-1', name: 'Phone 1' },
        { id: 'phone-2', name: 'Phone 2' },
        { id: 'phone-3', name: 'Phone 3' }
    ];
    
    demoDevices.forEach((device, index) => {
        setTimeout(() => {
            const angle = Math.random() * 2 * Math.PI;
            const distance = Math.random() * 300;
            
            const lat = CONFIG.CENTER.lat + (distance / 111000) * Math.cos(angle);
            const lng = CONFIG.CENTER.lng + (distance / (111000 * Math.cos(toRadians(CONFIG.CENTER.lat)))) * Math.sin(angle);
            
            updateDeviceLocation(device.id, lat, lng, device.name);
        }, index * 1000);
    });
    
    setInterval(() => {
        devices.forEach((device, deviceId) => {
            const movement = 0.00005;
            const newLat = device.data.lat + (Math.random() - 0.5) * movement;
            const newLng = device.data.lng + (Math.random() - 0.5) * movement;
            
            // Reset lastUpdate to prevent timeout in demo
            device.lastUpdate = Date.now();
            updateDeviceLocation(deviceId, newLat, newLng, device.data.name);
        });
    }, 3000);
}

// Expose to global scope
window.GeoFencing = {
    startDemoMode,
    updateDeviceLocation,
    removeDevice,
    clearAllDevices,
    connectToServer,
    disconnectFromServer,
    toggleFullscreen,
    toggleSound,
    showToast
};
