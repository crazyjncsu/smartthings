metadata {
	definition (name: "Speech Synthesizer", namespace: "autobridge/child", author: "autobridge") {
		capability "Speech Synthesis"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common

		attribute "utterance", "string"

		command "speakTest"
	}

	tiles {
		valueTile("utterance", "device.utterance", decoration: "flat", width: 3, height: 2) {
			state "utterance", label:'${currentValue}', icon: "st.Electronics.electronics13"
		}			
		standardTile("speak", "device.utterance", decoration: "flat") {
			state("default", label: "speak test", action: "speakTest")
		}
	}
}

def speak(phrase) { parent.requestChildStateChange(device.deviceNetworkId, "utterance", phrase) }
def speakTest() { speak("testing 1 2 3") }

// BEGIN common
def setCheckInterval() { sendEvent(name: "checkInterval", value: 3600, displayed: false) }
def installed() { setCheckInterval() }
def updated() { setCheckInterval() }
def ping() { refresh() }
def refresh() { parent.requestChildRefresh(device.deviceNetworkId) }
// END common