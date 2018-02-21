metadata {
	definition (name: "Alarm Contact ID Sender", namespace: "crazyjncsu", author: "Jake Morgan") {
		capability "Actuator"
		capability "Momentary"
		capability "Notification"
        
        command 'notifyBurglary'
        command 'notifyPanic'
        command 'notifyFire'
	}

	tiles {
		standardTile('notifyBurglary', 'device.status') { state('default', action: 'notifyBurglary', label: 'Burglary') }
		standardTile('notifyPanic', 'device.status') { state('default', action: 'notifyPanic', label: 'Panic') }
		standardTile('notifyFire', 'device.status') { state('default', action: 'notifyFire', label: 'Fire') }
	}
}

def push() {
	deviceNotification('Burglary');
}

def deviceNotification(String notification) {
	// CRAP, doesn't look like we can send TCP outside of our subnet
}

def notifyBurglary() {
	deviceNotification('Burglary');
}

def notifyPanic() {
	deviceNotification('Panic');
}

def notifyFire() {
	deviceNotification('Fire');
}