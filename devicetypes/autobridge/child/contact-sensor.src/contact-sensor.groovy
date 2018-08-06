metadata {
	definition (name: "Contact Sensor", namespace: "autobridge/child", author: "autobridge") {
		capability "Contact Sensor"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor: "#e86d13")
			state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor: "#00A0DC")
		}
		standardTile("refresh", "device.refresh", decoration: "flat") {
			state "default", label:"", action:"Refresh.refresh", icon:"st.secondary.refresh"
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