metadata {
	definition (name: "Signal Strength Sensor", namespace: "autobridge", author: "autobridge") {
		capability "Signal Strength"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		valueTile("rssi", "device.rssi", decoration: "flat", width: 3, height: 2) {
			state "rssi", label:'${currentValue} dBm'
		}			
		standardTile("refresh", "device.refresh") {
			state "refresh", label:'Refresh', action: "refresh", icon:"st.secondary.refresh-icon"
		}
	}
}

// BEGIN common
def setCheckInterval() { sendEvent(name: "checkInterval", value: 3600, displayed: false) }
def installed() { setCheckInterval() }
def updated() { setCheckInterval() }
def ping() { refresh() }
def refresh() { parent.requestChildRefresh(device.deviceNetworkId) }
// END common