definition(
    name: "AutoBridge",
    namespace: "autobridge",
    author: "autobridge",
    description: "AutoBridge connector for SmartThings",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
	subscribe(location, null, processEvent, [filterEvents: false])
    searchForDevices()
    runEvery30Minutes(searchForDevices)
}

def searchForDevices() {
	log.info "Searching for devices..."
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:AutoBridge:1", physicalgraph.device.Protocol.LAN))
}

def parseDniPath(deviceNetworkId) {
    def dniParts = deviceNetworkId.split(':')
    return [ targetID: dniParts[0], sourceID: dniParts[1], deviceID: dniParts[2] ]
}

def getChildDeviceInfos() {
   return getAllChildDevices().collect { [ device: it, path: parseDniPath(it.deviceNetworkId) ] }
}

def processEvent(event) {
	def lanMessage = parseLanMessage(event.description)
    
    log.info("Processing operation '${lanMessage?.json?.autoBridgeOperation ?: lanMessage?.ssdpTerm ?: lanMessage.body}' from '${lanMessage.mac}'...")

	switch (lanMessage?.ssdpTerm) {
    	case "urn:schemas-upnp-org:device:AutoBridge:1":
        	if (!state.targetHostMap)
            	state.targetHostMap = [:]
                
            def targetID = lanMessage.ssdpUSN.split(':')[1]
            state.targetHostMap[targetID] = lanMessage.networkAddress
            
            break;
    }

	switch (lanMessage?.json?.autoBridgeOperation) {
    	case "syncSources":
        	def sourceIDs = lanMessage.json.sourceIDs.toSet()

			getChildDeviceInfos()
            	.findAll { it.path.targetID == lanMessage.json.targetID }
                .findAll { !sourceIDs.contains(it.path.sourceID) }
                .each { deleteChildDevice(it.device.deviceNetworkId) }
                
            searchForDevices()

        	break;
    	case "syncSourceDevices":
        	def existingDeviceIDChildDeviceMap = getChildDeviceInfos()
            	.findAll { it.path.targetID == lanMessage.json.targetID && it.path.sourceID == lanMessage.json.sourceID }
                .collectEntries { [ (it.path.deviceID): it.device ] }
            
            def deviceIDs = lanMessage.json.devices.collect { it.deviceID }.toSet()

			existingDeviceIDChildDeviceMap
            	.findAll { !deviceIDs.contains(it.key) }
                .each { deleteChildDevice(it.value.deviceNetworkId) }
        	
            lanMessage.json.devices.each {
            	def existingChildDevice = existingDeviceIDChildDeviceMap[it.deviceID]
                
                if (existingChildDevice == null) {
                	addChildDevice(it.namespace, it.typeName, lanMessage.json.targetID + ':' + lanMessage.json.sourceID + ':' + it.deviceID, null, [name: it.name, label: it.name])
                } else if (existingChildDevice.name != it.name) {
                	if (existingChildDevice.name == existingChildDevice.label)
                    	existingChildDevice.label = it.name
                    
                	existingChildDevice.name = it.name
                }
            }
            
            break;
    	case "syncDeviceState":
        	def childDevice = getChildDevice(lanMessage.json.targetID + ':' + lanMessage.json.sourceID + ':' + lanMessage.json.deviceID);
            
            if (lanMessage.json.propertyName == "image" && lanMessage.json.propertyValue != "")
            	childDevice?.storeImage(
                	java.util.UUID.randomUUID().toString().replaceAll('-',''),
                    new ByteArrayInputStream(lanMessage.json.propertyValue.decodeBase64())
                )
            else
            	childDevice?.sendEvent(name: lanMessage.json.propertyName, value: lanMessage.json.propertyValue);

			// doesn't seem to care if some handlers don't implement this
            childDevice?.onEventSent(lanMessage.json.propertyName, lanMessage.json.propertyValue);

			break;
    }
}

def requestChildStateChange(childDeviceNetworkId, propertyName, propertyValue) {
    log.info("Requesting state change for '$childDeviceNetworkId' for property '$propertyName' to '$propertyValue'...")

    def dniPath = parseDniPath(childDeviceNetworkId)
	sendMessage(dniPath.targetID, [sourceID: dniPath.sourceID, deviceID: dniPath.deviceID, propertyName: propertyName, propertyValue: propertyValue])
}

def requestChildRefresh(childDeviceNetworkId) {
    log.info("Requesting refresh for '$childDeviceNetworkId'...")

    def dniPath = parseDniPath(childDeviceNetworkId)
	sendMessage(dniPath.targetID, [sourceID: dniPath.sourceID, deviceID: dniPath.deviceID])
}

def computeSignatureString(validationKey, dateString, bodyString) {
	if (validationKey == null)
    	return "";

    def hmacAlgorithm = javax.crypto.Mac.getInstance("HmacSHA256")
    hmacAlgorithm.init(new javax.crypto.spec.SecretKeySpec(validationKey, "HmacSHA256"))
    
    def signatureSource = "$dateString$bodyString".getBytes("UTF-8")
    def signatureBytes = hmacAlgorithm.doFinal(signatureSource)

    return org.apache.commons.codec.binary.Base64.encodeBase64String(signatureBytes)
}

def sendMessage(targetID, message) {
	def dateString = new Date().format("EEE, dd MMM yyyy HH:mm:ss z", TimeZone.getTimeZone("UTC"))
    
	def bodyString = new groovy.json.JsonBuilder([
        targetID: targetID,
        message: message,
    ]).toString()

	sendHubCommand(new physicalgraph.device.HubAction([
		method: 'POST',
		path: "/" + targetID,
		headers: [
        	Host: state.targetHostMap[targetID] + ":040B", // port 1035
            Date: dateString,
            Authorization: computeSignatureString(null, dateString, bodyString), // TODO get validation key from state
        ],
        body: bodyString
	]))
}
