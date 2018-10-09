metadata {
	definition (name: "Door Opener", namespace: "autobridge", author: "autobridge") {
		capability "Door Control"
        capability "Switch"
		capability "Contact Sensor"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		standardTile("toggle", "device.door", width: 2, height: 2) {
			state("closed", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#FFFFFF", action:"Door Control.open")
			state("opening", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#00A0DC", action:"Door Control.close")
			state("open", label:'${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#00A0DC", action:"Door Control.close")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#00A0DC", action:"Door Control.open")
		}
		standardTile("open", "device.door", decoration: "flat") {
			state("default", label:'open', action:"Door Control.open", icon:"st.doors.garage.garage-opening")
		}
		standardTile("close", "device.door", decoration: "flat") {
			state("default", label:'close', action:"Door Control.close", icon:"st.doors.garage.garage-closing")
        }
		standardTile("refresh", "device.refresh", decoration: "flat") {
			state "default", label:"", action:"Refresh.refresh", icon:"st.secondary.refresh"
		}
	}
}

def open() { parent.requestChildStateChange(device.deviceNetworkId, "door", "open") }
def close() { parent.requestChildStateChange(device.deviceNetworkId, "door", "closed") }
def on() { parent.requestChildStateChange(device.deviceNetworkId, "door", "open") }
def off() { parent.requestChildStateChange(device.deviceNetworkId, "door", "closed") }

def onEventSent(name, value) {
	switch (value) {
    	case "open":
        case "opening":
        case "closing":
        	sendEvent(name: "switch", value: "on")
            sendEvent(name: "contact", value: "open")
        	break;
        case "closed":
        	sendEvent(name: "switch", value: "off")
            sendEvent(name: "contact", value: "closed")
        	break;
    }
}

// BEGIN common
def setCheckInterval() { sendEvent(name: "checkInterval", value: 3600, displayed: false) }
def installed() { setCheckInterval() }
def updated() { setCheckInterval() }
def ping() { refresh() }
def refresh() { parent.requestChildRefresh(device.deviceNetworkId) }
// END common