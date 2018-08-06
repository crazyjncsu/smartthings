metadata {
	definition (name: "Light Sensor", namespace: "autobridge/child", author: "autobridge") {
		capability "Illuminance Measurement"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		valueTile("illuminance", "device.illuminance", decoration: "flat", width: 3, height: 2) {
			state "illuminance", label: '${currentValue} lux', icon: "st.Weather.weather14"
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