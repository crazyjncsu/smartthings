metadata {
	definition (name: "Light", namespace: "autobridge/child", author: "autobridge") {
		capability "Light"
		capability "Switch"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		standardTile("toggle", "device.switch", width: 2, height: 2) {
			state("off", label:'${name}', icon:"st.switches.light.off", backgroundColor:"#FFFFFF", action:"Switch.on", nextState:"turningon")
			state("turningon", label:'${name}', icon:"st.switches.light.on", backgroundColor:"#00A0DC", action:"Switch.off", nextState:"turningoff")
			state("on", label:'${name}', icon:"st.switches.light.on", backgroundColor:"#00A0DC", action:"Switch.off", nextState:"turningoff")
			state("turningoff", label:'${name}', icon:"st.switches.light.off", backgroundColor:"#00A0DC", action:"Switch.on", nextState:"turningon")
		}
		standardTile("on", "device.switch", decoration: "flat") {
			state("default", label:'on', action:"Switch.on", icon:"st.switches.light.on")
		}
		standardTile("off", "device.switch", decoration: "flat") {
			state("default", label:'off', action:"Switch.off", icon:"st.switches.light.off")
        }
		standardTile("refresh", "device.refresh", decoration: "flat") {
			state "default", label:"", action:"Refresh.refresh", icon:"st.secondary.refresh"
		}
	}
}

def on() { parent.requestChildStateChange(device.deviceNetworkId, "switch", "on") }
def off() { parent.requestChildStateChange(device.deviceNetworkId, "switch", "off") }

// BEGIN common
def setCheckInterval() { sendEvent(name: "checkInterval", value: 3600, displayed: false) }
def installed() { setCheckInterval() }
def updated() { setCheckInterval() }
def ping() { refresh() }
def refresh() { parent.requestChildRefresh(device.deviceNetworkId) }
// END common