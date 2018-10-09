metadata {
	definition (name: "Camera", namespace: "autobridge", author: "autobridge") {
		capability "Image Capture"

		// BEGIN common
		capability "Actuator"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
		// END common
	}

	tiles {
		carouselTile("cameraDetails", "device.image", width: 3, height: 2)
		standardTile("take", "device.image", decoration: "flat") {
			state("default", label: "take", action: "Image Capture.take", icon: "st.camera.camera")
		}
        main "take"
        details("cameraDetails", "take")
	}
}

def take() { parent.requestChildStateChange(device.deviceNetworkId, "image", "") }

// BEGIN common
def setCheckInterval() { sendEvent(name: "checkInterval", value: 3600, displayed: false) }
def installed() { setCheckInterval() }
def updated() { setCheckInterval() }
def ping() { refresh() }
def refresh() { parent.requestChildRefresh(device.deviceNetworkId) }
// END common