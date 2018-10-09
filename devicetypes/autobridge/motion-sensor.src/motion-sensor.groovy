metadata {
	definition (name: "Motion Sensor", namespace: "autobridge", author: "autobridge") {
		capability "Motion Sensor"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		valueTile("motion", "device.motion", decoration: "flat", width: 3, height: 2) {
			state "inactive", label: 'no motion', icon: "st.motion.motion.inactive"
			state "active", label: 'motion', icon: "st.motion.motion.inactive", backgroundColor: "#00a0dc"
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