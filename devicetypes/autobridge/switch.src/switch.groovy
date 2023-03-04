metadata {
	definition (name: "Switch", namespace: "autobridge", author: "autobridge") {
		capability "Switch"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		standardTile("toggle", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state("off", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#FFFFFF", action:"Switch.on", nextState:"turningon")
			state("turningon", label:'${name}', icon:"st.switches.switch.on", backgroundColor:"#00A0DC", action:"Switch.off", nextState:"turningoff")
			state("on", label:'${name}', icon:"st.switches.switch.on", backgroundColor:"#00A0DC", action:"Switch.off", nextState:"turningoff")
			state("turningoff", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#00A0DC", action:"Switch.on", nextState:"turningon")
		}
		standardTile("on", "device.switch", decoration: "flat") {
			state("default", label:'on', action:"Switch.on", icon:"st.switches.switch.on")
		}
		standardTile("off", "device.switch", decoration: "flat") {
			state("default", label:'off', action:"Switch.off", icon:"st.switches.switch.off")
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