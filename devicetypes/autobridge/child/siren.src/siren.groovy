metadata {
	definition (name: "Siren", namespace: "autobridge/child", author: "autobridge") {
		capability "Alarm"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		standardTile("toggle", "device.alarm", width: 2, height: 2) {
			state("off", label:'off', icon:"st.security.alarm.alarm", backgroundColor:"#FFFFFF", action:"Alarm.siren", nextState:"turningon")
			state("turningon", label:'turningon', icon:"st.security.alarm.alarm", backgroundColor:"#00A0DC", action:"Alarm.off", nextState:"turningoff")
			state("siren", label:'on', icon:"st.security.alarm.alarm", backgroundColor:"#00A0DC", action:"Alarm.off", nextState:"turningoff")
			state("turningoff", label:'turningoff', icon:"st.security.alarm.alarm", backgroundColor:"#00A0DC", action:"Alarm.siren", nextState:"turningon")
		}
		standardTile("on", "device.alarm", decoration: "flat") {
			state("default", label:'on', action:"Alarm.siren", icon:"st.security.alarm.alarm")
		}
		standardTile("off", "device.alarm", decoration: "flat") {
			state("default", label:'off', action:"Alarm.off", icon:"st.security.alarm.alarm")
        }
		standardTile("refresh", "device.refresh", decoration: "flat") {
			state "default", label:"", action:"Refresh.refresh", icon:"st.secondary.refresh"
		}
	}
}

def siren() { parent.requestChildStateChange(device.deviceNetworkId, "alarm", "siren") }
def both() { parent.requestChildStateChange(device.deviceNetworkId, "alarm", "siren") }
def strobe() { parent.requestChildStateChange(device.deviceNetworkId, "alarm", "off") }
def off() { parent.requestChildStateChange(device.deviceNetworkId, "alarm", "off") }

// BEGIN common
def setCheckInterval() { sendEvent(name: "checkInterval", value: 3600, displayed: false) }
def installed() { setCheckInterval() }
def updated() { setCheckInterval() }
def ping() { refresh() }
def refresh() { parent.requestChildRefresh(device.deviceNetworkId) }
// END common