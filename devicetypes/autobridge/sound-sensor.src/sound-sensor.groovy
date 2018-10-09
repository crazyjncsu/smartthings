metadata {
	definition (name: "Sound Sensor", namespace: "autobridge", author: "autobridge") {
		capability "Sound Sensor"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		valueTile("sound", "device.sound", decoration: "flat", width: 3, height: 2) {
			state "not detected", label: '${name}', icon: "st.Electronics.electronics13"
			state "detected", label: '${name}', icon: "st.Electronics.electronics13", backgroundColor: "#00a0dc"
		}			
		standardTile("refresh", "device.refresh") {
			state "refresh", label: 'Refresh', action: "refresh", icon: "st.secondary.refresh-icon"
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